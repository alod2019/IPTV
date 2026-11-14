package ru.alod.iptv.data

import androidx.room.withTransaction
import java.io.StringReader
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.alod.iptv.data.epg.EpgLoader
import ru.alod.iptv.data.epg.ParsedEpgProgram
import ru.alod.iptv.data.local.ChannelEntity
import ru.alod.iptv.data.local.IptvDatabase
import ru.alod.iptv.data.local.PlaylistEntity
import ru.alod.iptv.data.local.ProgramEntity
import ru.alod.iptv.data.m3u.M3uParser
import ru.alod.iptv.data.m3u.ParsedPlaylist
import ru.alod.iptv.data.source.PlaylistSourceLoader
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.ImportResult
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistImportException
import ru.alod.iptv.domain.model.PlaylistSource
import ru.alod.iptv.domain.model.Program
import ru.alod.iptv.domain.repository.PlaylistRepository

class DefaultPlaylistRepository(
  private val database: IptvDatabase,
  private val sourceLoader: PlaylistSourceLoader,
  private val parser: M3uParser,
  private val clock: () -> Long = System::currentTimeMillis,
  private val epgLoader: EpgLoader? = null,
) : PlaylistRepository {
  private val dao = database.playlistDao()

  override fun observePlaylists(): Flow<List<Playlist>> =
    dao.observePlaylists().map { playlists -> playlists.map(::toPlaylist) }

  override fun observeChannels(playlistId: Long): Flow<List<Channel>> = combine(
    dao.observeChannels(playlistId),
    clockTicks(),
  ) { channels, now ->
    val programsByChannel = dao.currentAndNextPrograms(playlistId, now)
      .groupBy(ProgramEntity::channelTvgId)
    channels.map { channel ->
      val channelPrograms = channel.tvgId?.let(programsByChannel::get).orEmpty()
      val current = channelPrograms.firstOrNull { it.startMillis <= now && it.endMillis > now }
      val next = channelPrograms.firstOrNull { it.startMillis > now }
      toChannel(channel, current, next)
    }
  }

  override suspend fun importUrl(
    name: String,
    url: String,
    confirmEpg: suspend () -> Boolean,
  ): ImportResult {
    val parsed = parser.parse(StringReader(sourceLoader.loadUrl(url)))
    val epg = loadEpg(url, parsed.epgUrl, parsed.epgUrl != null && confirmEpg())
    return createPlaylist(name, PlaylistSource.URL, url.trim(), parsed, epg)
  }

  override suspend fun importFile(
    name: String,
    uri: String,
    confirmEpg: suspend () -> Boolean,
  ): ImportResult {
    val parsed = parser.parse(StringReader(sourceLoader.loadFile(uri)))
    val epg = loadEpg(uri, parsed.epgUrl, parsed.epgUrl != null && confirmEpg())
    return createPlaylist(name, PlaylistSource.FILE, uri, parsed, epg)
  }

  override suspend fun refresh(playlistId: Long): ImportResult {
    val playlist = dao.playlist(playlistId) ?: throw PlaylistImportException(ImportError.FILE_ACCESS)
    val text = when (playlist.sourceType) {
      PlaylistSource.URL.name -> sourceLoader.loadUrl(playlist.location)
      PlaylistSource.FILE.name -> sourceLoader.loadFile(playlist.location)
      else -> throw PlaylistImportException(ImportError.INVALID_FORMAT)
    }
    val parsed = parser.parse(StringReader(text))
    val epg = loadEpg(playlist.location, parsed.epgUrl ?: playlist.epgUrl, playlist.epgEnabled)
    return replaceChannels(playlist, parsed, epg)
  }

  override suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

  override suspend fun setFavorite(channelId: Long, isFavorite: Boolean) =
    dao.setFavorite(channelId, isFavorite)

  override suspend fun setLastChannel(playlistId: Long, channelId: Long) =
    dao.setLastChannel(playlistId, channelId)

  override suspend fun getChannel(channelId: Long): Channel? = dao.channel(channelId)?.let { toChannel(it) }

  private suspend fun loadEpg(playlistLocation: String, rawUrl: String?, enabled: Boolean): EpgLoadResult {
    val url = if (enabled) resolveEpgUrl(playlistLocation, rawUrl) else null
    if (url == null) return EpgLoadResult()
    if (epgLoader == null) return EpgLoadResult(url = url)
    return try {
      EpgLoadResult(url = url, programs = epgLoader.load(url))
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Exception) {
      EpgLoadResult(url = url, failed = true)
    }
  }

  private fun resolveEpgUrl(playlistLocation: String, rawUrl: String?): String? {
    val value = rawUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching {
      val uri = URI(value)
      if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
        value
      } else {
        URI(playlistLocation).resolve(uri).toString().takeIf {
          it.startsWith("http://") || it.startsWith("https://")
        }
      }
    }.getOrNull()
  }

  private suspend fun createPlaylist(
    name: String,
    source: PlaylistSource,
    location: String,
    parsed: ParsedPlaylist,
    epg: EpgLoadResult,
  ): ImportResult {
    requireNotEmpty(parsed)
    return database.withTransaction {
      val playlist = PlaylistEntity(
        name = name.trim().ifBlank { DEFAULT_PLAYLIST_NAME },
        sourceType = source.name,
        location = location,
        updatedAt = clock(),
        skippedChannels = parsed.skippedChannels,
        lastChannelId = null,
        epgUrl = epg.url,
        epgEnabled = epg.url != null,
        epgUpdatedAt = if (epg.failed || epg.url == null) null else clock(),
      )
      val playlistId = dao.insertPlaylist(playlist)
      val ids = dao.insertChannels(parsed.toEntities(playlistId))
      if (epg.url != null && !epg.failed) {
        dao.insertPrograms(epg.programs.toEntities(playlistId))
      }
      ImportResult(playlistId, ids.size, parsed.skippedChannels, epg.failed)
    }
  }

  private suspend fun replaceChannels(
    playlist: PlaylistEntity,
    parsed: ParsedPlaylist,
    epg: EpgLoadResult,
  ): ImportResult {
    requireNotEmpty(parsed)
    return database.withTransaction {
      val previous = dao.channels(playlist.id)
      val favorites = previous.filter { it.isFavorite }.mapTo(mutableSetOf()) { it.stableKey }
      val lastStableKey = previous.firstOrNull { it.id == playlist.lastChannelId }?.stableKey
      dao.deleteChannels(playlist.id)
      val entities = parsed.toEntities(playlist.id, favorites)
      val ids = dao.insertChannels(entities)
      val lastChannelId = entities.indices.firstOrNull { entities[it].stableKey == lastStableKey }?.let(ids::get)
      if (epg.url != null && !epg.failed) {
        dao.deletePrograms(playlist.id)
        dao.insertPrograms(epg.programs.toEntities(playlist.id))
      }
      dao.updateAfterImport(
        playlistId = playlist.id,
        name = playlist.name,
        updatedAt = clock(),
        skippedChannels = parsed.skippedChannels,
        lastChannelId = lastChannelId,
        epgUrl = epg.url,
        epgEnabled = epg.url != null,
        epgUpdatedAt = when {
          epg.url == null -> null
          epg.failed -> playlist.epgUpdatedAt
          else -> clock()
        },
      )
      ImportResult(playlist.id, ids.size, parsed.skippedChannels, epg.failed)
    }
  }

  private fun requireNotEmpty(parsed: ParsedPlaylist) {
    if (parsed.channels.isEmpty()) throw PlaylistImportException(ImportError.EMPTY)
  }

  private fun clockTicks(): Flow<Long> = flow {
    while (true) {
      emit(clock())
      delay(60_000)
    }
  }

  private fun List<ParsedEpgProgram>.toEntities(playlistId: Long): List<ProgramEntity> = map { program ->
    ProgramEntity(
      playlistId = playlistId,
      channelTvgId = program.channelId,
      title = program.title,
      description = program.description,
      startMillis = program.startMillis,
      endMillis = program.endMillis,
    )
  }

  private fun ParsedPlaylist.toEntities(
    playlistId: Long,
    favorites: Set<String> = emptySet(),
  ): List<ChannelEntity> {
    val occurrences = mutableMapOf<String, Int>()
    return channels.map { channel ->
      val baseKey = channel.tvgId?.let { "tvg:$it" } ?: "url:${channel.streamUrl.trim()}"
      val occurrence = (occurrences[baseKey] ?: 0) + 1
      occurrences[baseKey] = occurrence
      val stableKey = if (occurrence == 1) baseKey else "$baseKey#$occurrence"
      ChannelEntity(
        playlistId = playlistId,
        stableKey = stableKey,
        name = channel.name,
        streamUrl = channel.streamUrl,
        logoUrl = channel.logoUrl,
        groupName = channel.groupName,
        tvgId = channel.tvgId,
        isFavorite = stableKey in favorites,
      )
    }
  }

  private fun toPlaylist(entity: PlaylistEntity) = Playlist(
    id = entity.id,
    name = entity.name,
    source = PlaylistSource.valueOf(entity.sourceType),
    location = entity.location,
    updatedAt = entity.updatedAt,
    skippedChannels = entity.skippedChannels,
    lastChannelId = entity.lastChannelId,
    epgUrl = entity.epgUrl,
    epgEnabled = entity.epgEnabled,
  )

  private fun toChannel(
    entity: ChannelEntity,
    current: ProgramEntity? = null,
    next: ProgramEntity? = null,
  ) = Channel(
    id = entity.id,
    playlistId = entity.playlistId,
    stableKey = entity.stableKey,
    name = entity.name,
    streamUrl = entity.streamUrl,
    logoUrl = entity.logoUrl,
    groupName = entity.groupName,
    tvgId = entity.tvgId,
    isFavorite = entity.isFavorite,
    currentProgram = current?.toProgram(),
    nextProgram = next?.toProgram(),
  )

  private fun ProgramEntity.toProgram() = Program(
    title = title,
    description = description,
    startMillis = startMillis,
    endMillis = endMillis,
  )

  private data class EpgLoadResult(
    val url: String? = null,
    val programs: List<ParsedEpgProgram> = emptyList(),
    val failed: Boolean = false,
  )

  private companion object {
    const val DEFAULT_PLAYLIST_NAME = "IPTV"
  }
}

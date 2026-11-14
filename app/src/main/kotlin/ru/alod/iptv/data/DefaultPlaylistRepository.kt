package ru.alod.iptv.data

import androidx.room.withTransaction
import java.io.StringReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.alod.iptv.data.local.ChannelEntity
import ru.alod.iptv.data.local.IptvDatabase
import ru.alod.iptv.data.local.PlaylistEntity
import ru.alod.iptv.data.m3u.M3uParser
import ru.alod.iptv.data.m3u.ParsedPlaylist
import ru.alod.iptv.data.source.PlaylistSourceLoader
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.ImportResult
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistImportException
import ru.alod.iptv.domain.model.PlaylistSource
import ru.alod.iptv.domain.repository.PlaylistRepository

class DefaultPlaylistRepository(
  private val database: IptvDatabase,
  private val sourceLoader: PlaylistSourceLoader,
  private val parser: M3uParser,
  private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {
  private val dao = database.playlistDao()

  override fun observePlaylists(): Flow<List<Playlist>> =
    dao.observePlaylists().map { playlists -> playlists.map(::toPlaylist) }

  override fun observeChannels(playlistId: Long): Flow<List<Channel>> =
    dao.observeChannels(playlistId).map { channels -> channels.map(::toChannel) }

  override suspend fun importUrl(name: String, url: String): ImportResult {
    val parsed = parser.parse(StringReader(sourceLoader.loadUrl(url)))
    return createPlaylist(name, PlaylistSource.URL, url.trim(), parsed)
  }

  override suspend fun importFile(name: String, uri: String): ImportResult {
    val parsed = parser.parse(StringReader(sourceLoader.loadFile(uri)))
    return createPlaylist(name, PlaylistSource.FILE, uri, parsed)
  }

  override suspend fun refresh(playlistId: Long): ImportResult {
    val playlist = dao.playlist(playlistId) ?: throw PlaylistImportException(ImportError.FILE_ACCESS)
    val text = when (playlist.sourceType) {
      PlaylistSource.URL.name -> sourceLoader.loadUrl(playlist.location)
      PlaylistSource.FILE.name -> sourceLoader.loadFile(playlist.location)
      else -> throw PlaylistImportException(ImportError.INVALID_FORMAT)
    }
    val parsed = parser.parse(StringReader(text))
    return replaceChannels(playlist, parsed)
  }

  override suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

  override suspend fun setFavorite(channelId: Long, isFavorite: Boolean) =
    dao.setFavorite(channelId, isFavorite)

  override suspend fun setLastChannel(playlistId: Long, channelId: Long) =
    dao.setLastChannel(playlistId, channelId)

  override suspend fun getChannel(channelId: Long): Channel? = dao.channel(channelId)?.let(::toChannel)

  private suspend fun createPlaylist(
    name: String,
    source: PlaylistSource,
    location: String,
    parsed: ParsedPlaylist,
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
      )
      val playlistId = dao.insertPlaylist(playlist)
      val ids = dao.insertChannels(parsed.toEntities(playlistId))
      ImportResult(playlistId, ids.size, parsed.skippedChannels)
    }
  }

  private suspend fun replaceChannels(playlist: PlaylistEntity, parsed: ParsedPlaylist): ImportResult {
    requireNotEmpty(parsed)
    return database.withTransaction {
      val previous = dao.channels(playlist.id)
      val favorites = previous.filter { it.isFavorite }.mapTo(mutableSetOf()) { it.stableKey }
      val lastStableKey = previous.firstOrNull { it.id == playlist.lastChannelId }?.stableKey
      dao.deleteChannels(playlist.id)
      val entities = parsed.toEntities(playlist.id, favorites)
      val ids = dao.insertChannels(entities)
      val lastChannelId = entities.indices.firstOrNull { entities[it].stableKey == lastStableKey }?.let(ids::get)
      dao.updateAfterImport(
        playlistId = playlist.id,
        name = playlist.name,
        updatedAt = clock(),
        skippedChannels = parsed.skippedChannels,
        lastChannelId = lastChannelId,
      )
      ImportResult(playlist.id, ids.size, parsed.skippedChannels)
    }
  }

  private fun requireNotEmpty(parsed: ParsedPlaylist) {
    if (parsed.channels.isEmpty()) throw PlaylistImportException(ImportError.EMPTY)
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
  )

  private fun toChannel(entity: ChannelEntity) = Channel(
    id = entity.id,
    playlistId = entity.playlistId,
    stableKey = entity.stableKey,
    name = entity.name,
    streamUrl = entity.streamUrl,
    logoUrl = entity.logoUrl,
    groupName = entity.groupName,
    tvgId = entity.tvgId,
    isFavorite = entity.isFavorite,
  )

  private companion object {
    const val DEFAULT_PLAYLIST_NAME = "IPTV"
  }
}

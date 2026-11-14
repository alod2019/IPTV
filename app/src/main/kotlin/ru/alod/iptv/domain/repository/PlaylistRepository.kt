package ru.alod.iptv.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportResult
import ru.alod.iptv.domain.model.Playlist

interface PlaylistRepository {
  fun observePlaylists(): Flow<List<Playlist>>

  fun observeChannels(playlistId: Long): Flow<List<Channel>>

  suspend fun importUrl(
    name: String,
    url: String,
    confirmEpg: suspend () -> Boolean = { true },
  ): ImportResult

  suspend fun importFile(
    name: String,
    uri: String,
    confirmEpg: suspend () -> Boolean = { true },
  ): ImportResult

  suspend fun refresh(playlistId: Long): ImportResult

  suspend fun deletePlaylist(playlistId: Long)

  suspend fun setFavorite(channelId: Long, isFavorite: Boolean)

  suspend fun setLastChannel(playlistId: Long, channelId: Long)

  suspend fun getChannel(channelId: Long): Channel?
}

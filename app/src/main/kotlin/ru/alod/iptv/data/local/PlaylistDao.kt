package ru.alod.iptv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY id")
  fun observePlaylists(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY groupName, name COLLATE NOCASE")
  fun observeChannels(playlistId: Long): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM playlists WHERE id = :playlistId")
  suspend fun playlist(playlistId: Long): PlaylistEntity?

  @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
  suspend fun channels(playlistId: Long): List<ChannelEntity>

  @Query("SELECT * FROM channels WHERE id = :channelId")
  suspend fun channel(channelId: Long): ChannelEntity?

  @Insert
  suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertChannels(channels: List<ChannelEntity>): List<Long>

  @Query("DELETE FROM channels WHERE playlistId = :playlistId")
  suspend fun deleteChannels(playlistId: Long)

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylist(playlistId: Long)

  @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt, skippedChannels = :skippedChannels, lastChannelId = :lastChannelId WHERE id = :playlistId")
  suspend fun updateAfterImport(playlistId: Long, name: String, updatedAt: Long, skippedChannels: Int, lastChannelId: Long?)

  @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
  suspend fun setFavorite(channelId: Long, isFavorite: Boolean)

  @Query("UPDATE playlists SET lastChannelId = :channelId WHERE id = :playlistId")
  suspend fun setLastChannel(playlistId: Long, channelId: Long)
}

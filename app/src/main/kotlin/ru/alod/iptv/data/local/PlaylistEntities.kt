package ru.alod.iptv.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val sourceType: String,
  val location: String,
  val updatedAt: Long?,
  val skippedChannels: Int,
  val lastChannelId: Long?,
  val epgUrl: String? = null,
  val epgEnabled: Boolean = false,
  val epgUpdatedAt: Long? = null,
)

@Entity(
  tableName = "channels",
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index("playlistId"),
    Index("groupName"),
    Index("isFavorite"),
    Index(value = ["playlistId", "stableKey"], unique = true),
  ],
)
data class ChannelEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val playlistId: Long,
  val stableKey: String,
  val name: String,
  val streamUrl: String,
  val logoUrl: String?,
  val groupName: String?,
  val tvgId: String?,
  val isFavorite: Boolean,
)

@Entity(
  tableName = "programs",
  primaryKeys = ["playlistId", "channelTvgId", "startMillis"],
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("playlistId"), Index(value = ["playlistId", "channelTvgId", "startMillis"])],
)
data class ProgramEntity(
  val playlistId: Long,
  val channelTvgId: String,
  val title: String,
  val description: String?,
  val startMillis: Long,
  val endMillis: Long,
)

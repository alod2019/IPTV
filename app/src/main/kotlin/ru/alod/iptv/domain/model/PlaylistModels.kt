package ru.alod.iptv.domain.model

enum class PlaylistSource {
  URL,
  FILE,
}

data class Playlist(
  val id: Long,
  val name: String,
  val source: PlaylistSource,
  val location: String,
  val updatedAt: Long?,
  val skippedChannels: Int,
  val lastChannelId: Long?,
)

data class Channel(
  val id: Long,
  val playlistId: Long,
  val stableKey: String,
  val name: String,
  val streamUrl: String,
  val logoUrl: String?,
  val groupName: String?,
  val tvgId: String?,
  val isFavorite: Boolean,
)

data class ImportResult(
  val playlistId: Long,
  val channelCount: Int,
  val skippedChannels: Int,
)

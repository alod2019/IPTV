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
  val epgUrl: String? = null,
  val epgEnabled: Boolean = false,
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
  val currentProgram: Program? = null,
  val nextProgram: Program? = null,
)

data class Program(
  val title: String,
  val description: String?,
  val startMillis: Long,
  val endMillis: Long,
)

data class ImportResult(
  val playlistId: Long,
  val channelCount: Int,
  val skippedChannels: Int,
  val epgError: Boolean = false,
)

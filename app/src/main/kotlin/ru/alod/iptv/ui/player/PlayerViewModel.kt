package ru.alod.iptv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.repository.PlaylistRepository

class PlayerViewModel(
  application: Application,
  private val repository: PlaylistRepository,
  private val channelId: Long,
) : AndroidViewModel(application) {
  private val _uiState = MutableStateFlow(PlayerUiState())
  val uiState: StateFlow<PlayerUiState> = _uiState
  private var exoPlayer: ExoPlayer? = null

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      _uiState.value = _uiState.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
    }

    override fun onPlayerError(error: PlaybackException) {
      _uiState.value = _uiState.value.copy(isBuffering = false, error = error.toPlayerError())
    }
  }

  init {
    viewModelScope.launch {
      val channel = repository.getChannel(channelId)
      if (channel == null) {
        _uiState.value = PlayerUiState(isLoading = false, error = PlayerError.NOT_FOUND)
      } else {
        createPlayer(channel)
      }
    }
  }

  fun resume() {
    if (_uiState.value.error == null) exoPlayer?.play()
  }

  fun pause() {
    exoPlayer?.pause()
  }

  fun retry() {
    exoPlayer?.let { player ->
      _uiState.value = _uiState.value.copy(error = null, isBuffering = true)
      player.prepare()
      player.play()
    }
  }

  private fun createPlayer(channel: Channel) {
    exoPlayer?.release()
    val player = ExoPlayer.Builder(getApplication()).build().apply {
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
          .build(),
        true,
      )
      setHandleAudioBecomingNoisy(true)
      addListener(listener)
      setMediaItem(MediaItem.fromUri(channel.streamUrl))
      prepare()
      playWhenReady = true
    }
    exoPlayer = player
    _uiState.value = PlayerUiState(channel = channel, player = player, isLoading = false, isBuffering = true)
  }

  private fun PlaybackException.toPlayerError(): PlayerError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> PlayerError.NETWORK
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerError.TIMEOUT
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> PlayerError.UNSUPPORTED
    else -> PlayerError.GENERIC
  }

  override fun onCleared() {
    exoPlayer?.removeListener(listener)
    exoPlayer?.release()
    exoPlayer = null
    super.onCleared()
  }
}

data class PlayerUiState(
  val channel: Channel? = null,
  val player: Player? = null,
  val isLoading: Boolean = true,
  val isBuffering: Boolean = false,
  val error: PlayerError? = null,
)

enum class PlayerError {
  NETWORK,
  TIMEOUT,
  UNSUPPORTED,
  NOT_FOUND,
  GENERIC,
}

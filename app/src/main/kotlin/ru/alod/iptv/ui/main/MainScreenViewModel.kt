package ru.alod.iptv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.ImportResult
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistImportException
import ru.alod.iptv.domain.repository.PlaylistRepository

class MainScreenViewModel(
  private val repository: PlaylistRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
  val uiState: StateFlow<MainScreenUiState> = _uiState
  private var playlistsJob: Job? = null
  private var channelsJob: Job? = null

  init {
    observePlaylists()
  }

  fun retry() {
    _uiState.value = MainScreenUiState.Loading
    observePlaylists()
  }

  fun selectPlaylist(playlistId: Long) {
    _uiState.value = content().copy(selectedPlaylistId = playlistId, channels = emptyList(), selectedGroup = null)
    observeChannels(playlistId)
  }

  fun setQuery(query: String) {
    _uiState.value = content().copy(query = query)
  }

  fun selectGroup(group: String?) {
    _uiState.value = content().copy(selectedGroup = group)
  }

  fun toggleFavorites() {
    _uiState.value = content().let { it.copy(favoritesOnly = !it.favoritesOnly) }
  }

  fun importUrl(name: String, url: String) = runImport {
    repository.importUrl(name, url)
  }

  fun importFile(name: String, uri: String) = runImport {
    repository.importFile(name, uri)
  }

  fun refresh() {
    val playlistId = content().selectedPlaylistId ?: return
    runImport { repository.refresh(playlistId) }
  }

  fun deleteSelectedPlaylist() {
    val playlistId = content().selectedPlaylistId ?: return
    viewModelScope.launch {
      runCatching { repository.deletePlaylist(playlistId) }
        .onFailure { _uiState.value = content().copy(operationError = it.toImportError()) }
    }
  }

  fun setFavorite(channel: Channel) {
    viewModelScope.launch {
      runCatching { repository.setFavorite(channel.id, !channel.isFavorite) }
        .onFailure { _uiState.value = content().copy(operationError = it.toImportError()) }
    }
  }

  fun selectChannel(channel: Channel) {
    viewModelScope.launch { runCatching { repository.setLastChannel(channel.playlistId, channel.id) } }
  }

  fun dismissError() {
    _uiState.value = content().copy(operationError = null)
  }

  fun dismissImportMessage() {
    _uiState.value = content().copy(skippedChannels = null)
  }

  private fun observePlaylists() {
    playlistsJob?.cancel()
    playlistsJob = viewModelScope.launch {
      repository.observePlaylists()
        .catch { _uiState.value = MainScreenUiState.Error(it.toImportError()) }
        .collect { playlists ->
          val content = content()
          val selectedId = content.selectedPlaylistId
            ?.takeIf { id -> playlists.any { it.id == id } }
            ?: playlists.firstOrNull()?.id
          _uiState.value = content.copy(playlists = playlists, selectedPlaylistId = selectedId)
          if (selectedId != content.selectedPlaylistId || content.channelsJobNeeded) {
            observeChannels(selectedId)
          }
        }
    }
  }

  private fun runImport(import: suspend () -> ImportResult) {
    if (content().isRefreshing) return
    _uiState.value = content().copy(isRefreshing = true, operationError = null)
    viewModelScope.launch {
      runCatching { import() }
        .onSuccess { result ->
          _uiState.value = content().copy(
            isRefreshing = false,
            selectedPlaylistId = result.playlistId,
            skippedChannels = result.skippedChannels.takeIf { it > 0 },
          )
          observeChannels(result.playlistId)
        }
        .onFailure {
          _uiState.value = content().copy(isRefreshing = false, operationError = it.toImportError())
        }
    }
  }

  private fun observeChannels(playlistId: Long?) {
    channelsJob?.cancel()
    if (playlistId == null) {
      _uiState.value = content().copy(channels = emptyList(), channelsJobNeeded = false)
      return
    }
    _uiState.value = content().copy(channelsJobNeeded = false)
    channelsJob = viewModelScope.launch {
      repository.observeChannels(playlistId)
        .catch { _uiState.value = content().copy(operationError = it.toImportError()) }
        .collect { channels -> _uiState.value = content().copy(channels = channels) }
    }
  }

  private fun content(): MainScreenUiState.Content = when (val state = _uiState.value) {
    is MainScreenUiState.Content -> state
    else -> MainScreenUiState.Content()
  }

  private fun Throwable.toImportError(): ImportError =
    (this as? PlaylistImportException)?.error ?: ImportError.NETWORK
}

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState

  data class Error(val error: ImportError) : MainScreenUiState

  data class Content(
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val channels: List<Channel> = emptyList(),
    val query: String = "",
    val selectedGroup: String? = null,
    val favoritesOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    val operationError: ImportError? = null,
    val skippedChannels: Int? = null,
    internal val channelsJobNeeded: Boolean = true,
  ) : MainScreenUiState {
    val selectedPlaylist: Playlist? get() = playlists.firstOrNull { it.id == selectedPlaylistId }
    val groups: List<String> get() = channels.mapNotNull { it.groupName }.distinct().sorted()
    val visibleChannels: List<Channel>
      get() = channels.filter { channel ->
        (!favoritesOnly || channel.isFavorite) &&
          (selectedGroup == null || channel.groupName == selectedGroup) &&
          (query.isBlank() || channel.name.contains(query.trim(), ignoreCase = true))
      }
  }
}

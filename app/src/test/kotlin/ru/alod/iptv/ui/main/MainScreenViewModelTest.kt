package ru.alod.iptv.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.ImportResult
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistImportException
import ru.alod.iptv.domain.model.PlaylistSource
import ru.alod.iptv.domain.repository.PlaylistRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakePlaylistRepository(playlists = emptyFlow()))

    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun channels_areDisplayedAndFiltered() = runTest {
    val repository = FakePlaylistRepository()
    val viewModel = MainScreenViewModel(repository)

    val initial = viewModel.uiState.value as MainScreenUiState.Content
    assertEquals(listOf("News", "Film"), initial.visibleChannels.map(Channel::name))

    viewModel.setQuery("film")
    assertEquals(listOf("Film"), content(viewModel).visibleChannels.map(Channel::name))

    viewModel.setQuery("")
    viewModel.toggleFavorites()
    assertEquals(listOf("News"), content(viewModel).visibleChannels.map(Channel::name))

    viewModel.toggleFavorites()
    viewModel.selectGroup("Movies")
    assertEquals(listOf("Film"), content(viewModel).visibleChannels.map(Channel::name))
  }

  @Test
  fun successfulImport_selectsPlaylistAndShowsSkippedCount() = runTest {
    val repository = FakePlaylistRepository()
    val viewModel = MainScreenViewModel(repository)

    viewModel.importUrl("Test", "https://example.com/list.m3u")

    val state = content(viewModel)
    assertEquals(1L, state.selectedPlaylistId)
    assertEquals(2, state.skippedChannels)
    assertFalse(state.isRefreshing)
  }

  @Test
  fun failedImport_keepsContentAndExposesError() = runTest {
    val repository = FakePlaylistRepository().apply {
      importError = PlaylistImportException(ImportError.TIMEOUT)
    }
    val viewModel = MainScreenViewModel(repository)

    viewModel.refresh()

    val state = content(viewModel)
    assertEquals(ImportError.TIMEOUT, state.operationError)
    assertEquals(2, state.channels.size)
    assertFalse(state.isRefreshing)
  }

  @Test
  fun deletingSelectedPlaylist_usesRepository() = runTest {
    val repository = FakePlaylistRepository()
    val viewModel = MainScreenViewModel(repository)

    viewModel.deleteSelectedPlaylist()

    assertEquals(1L, repository.deletedPlaylistId)
  }

  private fun content(viewModel: MainScreenViewModel) = viewModel.uiState.value as MainScreenUiState.Content
}

private class FakePlaylistRepository(
  playlists: Flow<List<Playlist>>? = null,
) : PlaylistRepository {
  private val playlist = Playlist(1, "Test", PlaylistSource.URL, "https://example.com/list.m3u", null, 2, null)
  private val playlistsFlow = MutableStateFlow(listOf(playlist))
  private val observablePlaylists = playlists ?: playlistsFlow
  private val channelsFlow = MutableStateFlow(
    listOf(
      Channel(1, 1, "one", "News", "https://example.com/news.m3u8", null, "News", null, true),
      Channel(2, 1, "two", "Film", "https://example.com/film.m3u8", null, "Movies", null, false),
    ),
  )
  var importError: Throwable? = null
  var deletedPlaylistId: Long? = null

  override fun observePlaylists(): Flow<List<Playlist>> = observablePlaylists

  override fun observeChannels(playlistId: Long): Flow<List<Channel>> = channelsFlow

  override suspend fun importUrl(name: String, url: String): ImportResult = importResult()

  override suspend fun importFile(name: String, uri: String): ImportResult = importResult()

  override suspend fun refresh(playlistId: Long): ImportResult = importResult()

  override suspend fun deletePlaylist(playlistId: Long) {
    deletedPlaylistId = playlistId
    playlistsFlow.value = emptyList()
  }

  override suspend fun setFavorite(channelId: Long, isFavorite: Boolean) {
    channelsFlow.value = channelsFlow.value.map {
      if (it.id == channelId) it.copy(isFavorite = isFavorite) else it
    }
  }

  override suspend fun setLastChannel(playlistId: Long, channelId: Long) = Unit

  override suspend fun getChannel(channelId: Long): Channel? = channelsFlow.value.firstOrNull { it.id == channelId }

  private fun importResult(): ImportResult {
    importError?.let { throw it }
    return ImportResult(1, 2, 2)
  }
}

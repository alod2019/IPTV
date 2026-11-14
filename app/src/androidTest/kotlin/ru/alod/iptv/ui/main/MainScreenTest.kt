package ru.alod.iptv.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistSource

class MainScreenTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun channelClick_opensSelectedChannel() {
    var selectedChannel: Long? = null
    composeTestRule.setContent {
      TestMainScreen(onChannelClick = { selectedChannel = it.id })
    }

    composeTestRule.onNodeWithText("Sample1").assertIsDisplayed().performClick()

    assertEquals(1L, selectedChannel)
  }

  @Test
  fun programGuide_displaysCurrentAndNext() {
    composeTestRule.setContent { TestMainScreen() }

    composeTestRule.onNodeWithText("Сейчас: Morning").assertIsDisplayed()
    composeTestRule.onNodeWithText("Далее: Next").assertIsDisplayed()
  }

  @Test
  fun emptyState_opensAddPlaylistDialog() {
    composeTestRule.setContent { TestMainScreen(state = MainScreenUiState.Content()) }

    composeTestRule.onNodeWithText("Добавьте первый плейлист").assertIsDisplayed()
    composeTestRule.onNodeWithText("Добавить плейлист").performClick()

    composeTestRule.onNodeWithText("URL M3U/M3U8-плейлиста").assertIsDisplayed()
  }

  @Test
  fun urlDialog_passesEnteredDataWithoutEpgCheckbox() {
    var imported: Pair<String, String>? = null
    composeTestRule.setContent {
      TestMainScreen(
        state = MainScreenUiState.Content(),
        onImportUrl = { name, url -> imported = name to url },
      )
    }
    composeTestRule.onNodeWithText("Добавить плейлист").performClick()
    composeTestRule.onNodeWithText("Название плейлиста").performTextInput("Test")
    composeTestRule.onNodeWithText("URL M3U/M3U8-плейлиста").performTextInput("https://example.com/list.m3u")

    composeTestRule.onNodeWithText("Использовать EPG").assertDoesNotExist()
    composeTestRule.onNodeWithText("Добавить URL").performClick()

    assertEquals("Test" to "https://example.com/list.m3u", imported)
  }

  @Test
  fun epgConfirmation_allowsImportWithoutEpg() {
    var useEpg: Boolean? = null
    composeTestRule.setContent {
      TestMainScreen(
        state = MainScreenUiState.Content(isEpgConfirmationVisible = true),
        onEpgConfirmation = { useEpg = it },
      )
    }

    composeTestRule.onNodeWithText("В плейлисте найдена EPG").assertIsDisplayed()
    composeTestRule.onNodeWithText("Без EPG").performClick()

    assertEquals(false, useEpg)
  }
}

@androidx.compose.runtime.Composable
private fun TestMainScreen(
  state: MainScreenUiState.Content = MainScreenUiState.Content(
    playlists = listOf(Playlist(1, "Test", PlaylistSource.URL, "", null, 0, null)),
    selectedPlaylistId = 1,
    channels = listOf(
      Channel(
        1, 1, "one", "Sample1", "https://example.com/1.m3u8", null, "News", null, false,
        currentProgram = ru.alod.iptv.domain.model.Program("Morning", null, 0, 60_000),
        nextProgram = ru.alod.iptv.domain.model.Program("Next", null, 60_000, 120_000),
      ),
      Channel(2, 1, "two", "Sample2", "https://example.com/2.m3u8", null, "Movies", null, true),
    ),
  ),
  onChannelClick: (Channel) -> Unit = {},
  onImportUrl: (String, String) -> Unit = { _, _ -> },
  onEpgConfirmation: (Boolean) -> Unit = {},
) {
  MainScreenContent(
    state = state,
    onPlaylistSelect = {},
    onQueryChange = {},
    onGroupSelect = {},
    onFavoritesToggle = {},
    onImportUrl = onImportUrl,
    onImportFile = { _, _ -> },
    onEpgConfirmation = onEpgConfirmation,
    onRefresh = {},
    onDeletePlaylist = {},
    onFavoriteToggle = {},
    onChannelClick = onChannelClick,
    onDismissError = {},
    onDismissImportMessage = {},
  )
}

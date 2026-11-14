package ru.alod.iptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var server: MockWebServer
  private val application = ApplicationProvider.getApplicationContext<IptvApplication>()

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    runBlocking {
      application.container.repository.observePlaylists().first().forEach {
        application.container.repository.deletePlaylist(it.id)
      }
      server.enqueue(MockResponse().setBody(playlist()))
      application.container.repository.importUrl("Test", server.url("/playlist.m3u").toString())
    }
  }

  @After
  fun tearDown() {
    runBlocking {
      application.container.repository.observePlaylists().first().forEach {
        application.container.repository.deletePlaylist(it.id)
      }
    }
    server.shutdown()
  }

  @Test
  fun channelClick_afterBack_opensNewlySelectedChannel() {
    composeTestRule.setContent { MainNavigation() }

    composeTestRule.onNodeWithText("First").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("First").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Назад").performClick()

    composeTestRule.onNodeWithText("Second").assertIsDisplayed().performClick()

    composeTestRule.onNodeWithText("Second").assertIsDisplayed()
    composeTestRule.onNodeWithText("First").assertDoesNotExist()
  }

  private fun playlist(): String = """
    #EXTM3U
    #EXTINF:-1,First
    https://example.invalid/first.m3u8
    #EXTINF:-1,Second
    https://example.invalid/second.m3u8
  """.trimIndent()
}

package ru.alod.iptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var server: MockWebServer
  private lateinit var segmentRequested: CountDownLatch
  private val application = ApplicationProvider.getApplicationContext<IptvApplication>()

  @Before
  fun setUp() {
    server = MockWebServer()
    segmentRequested = CountDownLatch(1)
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/playlist.m3u" -> MockResponse().setBody(playlist())
        "/live.m3u8" -> MockResponse()
          .setHeader("Content-Type", "application/vnd.apple.mpegurl")
          .setBody(hlsManifest())
        "/segment.ts" -> {
          segmentRequested.countDown()
          val segment = InstrumentationRegistry.getInstrumentation().context.assets
            .open("hls/segment.ts").use { it.readBytes() }
          MockResponse()
            .setHeader("Content-Type", "video/mp2t")
            .setBody(Buffer().write(segment))
        }
        else -> MockResponse().setResponseCode(404)
      }
    }
    server.start()
    runBlocking {
      application.container.repository.observePlaylists().first().forEach {
        application.container.repository.deletePlaylist(it.id)
      }
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
    composeTestRule.onNodeWithText("First").assertDoesNotExist()
    composeTestRule.onRoot().performTouchInput { click(center) }
    composeTestRule.onNodeWithText("First").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Назад").performClick()

    composeTestRule.onNodeWithText("Second").assertIsDisplayed().performClick()

    composeTestRule.onRoot().performTouchInput { click(center) }
    composeTestRule.onNodeWithText("Second").assertIsDisplayed()
    composeTestRule.onNodeWithText("First").assertDoesNotExist()
  }

  @Test
  fun playerControls_tapShowsUiAndTimeoutHidesIt() {
    composeTestRule.setContent { MainNavigation() }

    composeTestRule.onNodeWithText("First").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("First").assertDoesNotExist()
    composeTestRule.waitUntil { !systemBarsVisible() }
    assertTrue("Stream was not requested", segmentRequested.await(10, TimeUnit.SECONDS))

    composeTestRule.onRoot().performTouchInput { click(center) }

    composeTestRule.onNodeWithText("First").assertIsDisplayed()
    composeTestRule.waitUntil { systemBarsVisible() }
    composeTestRule.waitUntil(timeoutMillis = 7_000) {
      composeTestRule.onAllNodesWithText("First").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.waitUntil { !systemBarsVisible() }
  }

  private fun systemBarsVisible(): Boolean {
    var visible = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val windowInsets = composeTestRule.activity.window.decorView.rootWindowInsets
      val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
      visible = insets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
        insets.isVisible(WindowInsetsCompat.Type.navigationBars())
    }
    return visible
  }

  private fun playlist(): String = """
    #EXTM3U
    #EXTINF:-1,First
    ${server.url("/live.m3u8")}
    #EXTINF:-1,Second
    https://example.invalid/second.m3u8
  """.trimIndent()

  private fun hlsManifest(): String = buildString {
    appendLine("#EXTM3U")
    appendLine("#EXT-X-VERSION:3")
    appendLine("#EXT-X-TARGETDURATION:1")
    appendLine("#EXT-X-MEDIA-SEQUENCE:0")
    repeat(30) {
      appendLine("#EXTINF:1.0,")
      appendLine(server.url("/segment.ts"))
    }
    appendLine("#EXT-X-ENDLIST")
  }.trimEnd()
}

package ru.alod.iptv.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HlsPlaybackTest {
  private lateinit var server: MockWebServer
  private var player: ExoPlayer? = null

  @Before
  fun setUp() {
    server = MockWebServer()
    val segment = InstrumentationRegistry.getInstrumentation().context.assets
      .open("hls/segment.ts").use { it.readBytes() }
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/live.m3u8" -> MockResponse()
          .setHeader("Content-Type", "application/vnd.apple.mpegurl")
          .setBody(hlsManifest())
        "/segment.ts" -> MockResponse()
          .setHeader("Content-Type", "video/mp2t")
          .setBody(Buffer().write(segment))
        else -> MockResponse().setResponseCode(404)
      }
    }
    server.start()
  }

  @After
  fun tearDown() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      player?.release()
      player = null
    }
    server.shutdown()
  }

  @Test
  fun localHlsStream_reachesReadyState() {
    val ready = CountDownLatch(1)
    val playbackError = AtomicReference<PlaybackException>()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mediaUrl = server.url("/live.m3u8").toString()

    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      player = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            playbackError.set(error)
            ready.countDown()
          }
        })
        setMediaItem(MediaItem.fromUri(mediaUrl))
        prepare()
      }
    }

    assertTrue("Player did not become ready", ready.await(15, TimeUnit.SECONDS))
    assertNull(playbackError.get())
    assertNotNull(player)
  }

  private fun hlsManifest(): String = """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-TARGETDURATION:1
    #EXT-X-MEDIA-SEQUENCE:0
    #EXTINF:1.0,
    ${server.url("/segment.ts")}
    #EXT-X-ENDLIST
  """.trimIndent()
}

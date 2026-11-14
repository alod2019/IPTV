package ru.alod.iptv.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.alod.iptv.data.local.IptvDatabase
import ru.alod.iptv.data.m3u.M3uParser
import ru.alod.iptv.data.source.PlaylistSourceLoader
import ru.alod.iptv.domain.model.PlaylistImportException

@RunWith(AndroidJUnit4::class)
class DefaultPlaylistRepositoryTest {
  private lateinit var database: IptvDatabase
  private lateinit var server: MockWebServer
  private lateinit var repository: DefaultPlaylistRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    database = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java).build()
    server = MockWebServer()
    server.start()
    repository = DefaultPlaylistRepository(
      database,
      PlaylistSourceLoader(OkHttpClient(), context.contentResolver),
      M3uParser(),
      clock = { 42L },
    )
  }

  @After
  fun tearDown() {
    database.close()
    server.shutdown()
  }

  @Test
  fun refresh_preservesFavoriteLastChannelAndCacheOnFailure() = runBlocking {
    server.enqueue(MockResponse().setBody(playlist("News", "Movies")))
    val imported = repository.importUrl("Test", server.url("/list.m3u").toString())
    val initial = repository.observeChannels(imported.playlistId).first()
    val news = initial.single { it.name == "News" }
    repository.setFavorite(news.id, true)
    repository.setLastChannel(imported.playlistId, news.id)

    server.enqueue(MockResponse().setBody(playlist("News", "Documentary")))
    repository.refresh(imported.playlistId)
    val refreshed = repository.observeChannels(imported.playlistId).first()
    assertTrue(refreshed.single { it.name == "News" }.isFavorite)
    assertEquals(
      refreshed.single { it.name == "News" }.id,
      repository.observePlaylists().first().single().lastChannelId,
    )

    server.enqueue(MockResponse().setResponseCode(500))
    var failed = false
    try {
      repository.refresh(imported.playlistId)
    } catch (_: PlaylistImportException) {
      failed = true
    }
    assertTrue(failed)
    assertEquals(refreshed, repository.observeChannels(imported.playlistId).first())
  }

  private fun playlist(vararg names: String): String = buildString {
    append("#EXTM3U\n")
    names.forEachIndexed { index, name ->
      append("#EXTINF:-1 tvg-id=\"$name\",$name\n")
      append("https://streams.test/$name.m3u8\n")
    }
  }
}

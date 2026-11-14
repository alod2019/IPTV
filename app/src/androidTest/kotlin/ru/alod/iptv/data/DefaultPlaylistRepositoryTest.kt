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

  @Test
  fun epgImport_loadsAndMapsCurrentAndNextPrograms() = runBlocking {
    val m3u = "#EXTM3U url-tvg=\"${server.url("/guide.xml")}\"\n" +
      "#EXTINF:-1 tvg-id=\"News\",News\nhttps://streams.test/news.m3u8\n"
    val xml = """
      <tv>
        <programme start="19700101000000 +0000" stop="19700101000200 +0000" channel="News"><title>Morning</title></programme>
        <programme start="19700101000200 +0000" stop="19700101000400 +0000" channel="News"><title>Next</title></programme>
      </tv>
    """.trimIndent()
    server.enqueue(MockResponse().setBody(m3u))
    server.enqueue(MockResponse().setBody(xml))
    val epgRepository = DefaultPlaylistRepository(
      database,
      PlaylistSourceLoader(OkHttpClient(), ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver),
      M3uParser(),
      clock = { 60_000L },
      epgLoader = ru.alod.iptv.data.epg.EpgLoader(OkHttpClient(), ru.alod.iptv.data.epg.XmlTvParser()),
    )

    val result = epgRepository.importUrl("Test", server.url("/list.m3u").toString(), confirmEpg = { true })
    val channel = epgRepository.observeChannels(result.playlistId).first().single()

    assertEquals("Morning", channel.currentProgram?.title)
    assertEquals("Next", channel.nextProgram?.title)
    assertTrue(epgRepository.observePlaylists().first().single().epgEnabled)
  }

  @Test
  fun epgImport_atProgramStart_queriesOnlyCurrentAndNext() = runBlocking {
    val m3u = "#EXTM3U url-tvg=\"${server.url("/guide.xml")}\"\n" +
      "#EXTINF:-1 tvg-id=\"News\",News\nhttps://streams.test/news.m3u8\n"
    val xml = """
      <tv>
        <programme start="19700101000000 +0000" stop="19700101000100 +0000" channel="News"><title>Past</title></programme>
        <programme start="19700101000100 +0000" stop="19700101000200 +0000" channel="News"><title>Ended</title></programme>
        <programme start="19700101000200 +0000" stop="19700101000400 +0000" channel="News"><title>Current</title></programme>
        <programme start="19700101000400 +0000" stop="19700101000600 +0000" channel="News"><title>Next</title></programme>
        <programme start="19700101000600 +0000" stop="19700101000800 +0000" channel="News"><title>Later</title></programme>
      </tv>
    """.trimIndent()
    server.enqueue(MockResponse().setBody(m3u))
    server.enqueue(MockResponse().setBody(xml))
    val epgRepository = DefaultPlaylistRepository(
      database,
      PlaylistSourceLoader(OkHttpClient(), ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver),
      M3uParser(),
      clock = { 120_000L },
      epgLoader = ru.alod.iptv.data.epg.EpgLoader(OkHttpClient(), ru.alod.iptv.data.epg.XmlTvParser()),
    )

    val result = epgRepository.importUrl("Test", server.url("/list.m3u").toString(), confirmEpg = { true })
    val channel = epgRepository.observeChannels(result.playlistId).first().single()
    val selectedPrograms = database.playlistDao().currentAndNextPrograms(result.playlistId, 120_000L)

    assertEquals("Current", channel.currentProgram?.title)
    assertEquals("Next", channel.nextProgram?.title)
    assertEquals(listOf("Current", "Next"), selectedPrograms.map { it.title })
  }

  @Test
  fun ignoredEpg_doesNotRequestGuide() = runBlocking {
    val m3u = "#EXTM3U url-tvg=\"${server.url("/guide.xml")}\"\n" +
      "#EXTINF:-1 tvg-id=\"News\",News\nhttps://streams.test/news.m3u8\n"
    server.enqueue(MockResponse().setBody(m3u))
    val epgRepository = DefaultPlaylistRepository(
      database,
      PlaylistSourceLoader(OkHttpClient(), ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver),
      M3uParser(),
      epgLoader = ru.alod.iptv.data.epg.EpgLoader(OkHttpClient(), ru.alod.iptv.data.epg.XmlTvParser()),
    )

    val result = epgRepository.importUrl("Test", server.url("/list.m3u").toString(), confirmEpg = { false })

    assertEquals(1, server.requestCount)
    assertTrue(!epgRepository.observePlaylists().first().single().epgEnabled)
    assertEquals(null, epgRepository.observeChannels(result.playlistId).first().single().currentProgram)
  }

  @Test
  fun playlistWithoutEpg_doesNotRequestConfirmation() = runBlocking {
    server.enqueue(MockResponse().setBody(playlist("News")))
    var confirmationRequested = false

    repository.importUrl("Test", server.url("/list.m3u").toString()) {
      confirmationRequested = true
      true
    }

    assertTrue(!confirmationRequested)
  }

  @Test
  fun channels_preservePlaylistOrderAfterImportAndRefresh() = runBlocking {
    server.enqueue(MockResponse().setBody(playlist("Zulu", "Alpha", "Mike")))
    val imported = repository.importUrl("Test", server.url("/list.m3u").toString())

    assertEquals(
      listOf("Zulu", "Alpha", "Mike"),
      repository.observeChannels(imported.playlistId).first().map { it.name },
    )

    server.enqueue(MockResponse().setBody(playlist("Bravo", "Zulu", "Alpha")))
    repository.refresh(imported.playlistId)

    assertEquals(
      listOf("Bravo", "Zulu", "Alpha"),
      repository.observeChannels(imported.playlistId).first().map { it.name },
    )
  }

  private fun playlist(vararg names: String): String = buildString {
    append("#EXTM3U\n")
    names.forEachIndexed { index, name ->
      append("#EXTINF:-1 tvg-id=\"$name\",$name\n")
      append("https://streams.test/$name.m3u8\n")
    }
  }
}

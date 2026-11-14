package ru.alod.iptv.data.epg

import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgLoaderTest {
  private val server = MockWebServer()

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun loadsGzippedXmlTv() = runTest {
    val xml = "<tv><programme start=\"19700101000000 +0000\" stop=\"19700101000200 +0000\" channel=\"one\"><title>News</title></programme></tv>"
    val compressed = java.io.ByteArrayOutputStream().also { output ->
      GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
    }.toByteArray()
    server.enqueue(MockResponse().setBody(Buffer().write(compressed)))
    server.start()

    val programs = EpgLoader(OkHttpClient(), XmlTvParser()).load(server.url("/guide.xml.gz").toString())

    assertEquals("News", programs.single().title)
  }
}

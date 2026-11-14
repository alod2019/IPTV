package ru.alod.iptv.data.m3u

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uEpgParserTest {
  @Test
  fun readsUrlTvgFromHeader() {
    val result = M3uParser().parse(
      StringReader("#EXTM3U url-tvg=\"https://example.com/guide.xml.gz\"\n#EXTINF:-1,News\nhttps://example.com/news.m3u8"),
    )

    assertEquals("https://example.com/guide.xml.gz", result.epgUrl)
  }

  @Test
  fun supportsAlternativeEpgAttributes() {
    val result = M3uParser().parse(StringReader("#EXTM3U x-tvg-url=\"guide.xml\""))

    assertEquals("guide.xml", result.epgUrl)
  }
}

package ru.alod.iptv.data.m3u

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
  private val parser = M3uParser()

  @Test
  fun parsesMetadataAndGroups() {
    val result = parse(
      """
      #EXTM3U
      #EXTINF:-1 tvg-id="news.one" tvg-name="News One" tvg-logo="https://img.test/logo.png" group-title="News",Первый новостной
      https://stream.test/one.m3u8
      #EXTINF:-1 group-title="Movies",Кино, Премиум
      #EXTGRP:Для взрослых
      https://stream.test/movie.ts
      """.trimIndent(),
    )

    assertEquals(2, result.channels.size)
    assertEquals("Первый новостной", result.channels[0].name)
    assertEquals("news.one", result.channels[0].tvgId)
    assertEquals("News", result.channels[0].groupName)
    assertEquals("https://img.test/logo.png", result.channels[0].logoUrl)
    assertEquals("Кино, Премиум", result.channels[1].name)
    assertEquals("Для взрослых", result.channels[1].groupName)
    assertEquals(0, result.skippedChannels)
  }

  @Test
  fun parsesBomCrLfAndChannelWithoutMetadata() {
    val result = parser.parse(StringReader("\uFEFF#EXTM3U\r\nhttps://stream.test/live.m3u8?token=1\r\n"))

    assertEquals("live.m3u8", result.channels.single().name)
    assertNull(result.channels.single().groupName)
  }

  @Test
  fun skipsOnlyMalformedRecords() {
    val result = parse(
      """
      #EXTM3U
      #EXTINF:-1,Broken
      not-a-supported-url
      #EXTINF:-1,Also broken
      #EXTINF:-1,Working
      https://stream.test/working.m3u8
      """.trimIndent(),
    )

    assertEquals("Working", result.channels.single().name)
    assertEquals(2, result.skippedChannels)
  }

  @Test
  fun ignoresUnknownDirectivesAndBlankLines() {
    val result = parse(
      """
      #EXTM3U
      #EXTVLCOPT:network-caching=1000
      #EXTINF:-1,Channel

      https://stream.test/channel
      """.trimIndent(),
    )

    assertEquals(1, result.channels.size)
    assertEquals(0, result.skippedChannels)
  }

  @Test
  fun usesTvgNameWhenTitleIsMissing() {
    val result = parse("#EXTINF:-1 tvg-name=\"Fallback name\",\nhttps://stream.test/live")

    assertEquals("Fallback name", result.channels.single().name)
  }

  @Test
  fun acceptsUnquotedAttributes() {
    val result = parse("#EXTINF:-1 tvg-id=42 group-title=Sport,Match\nhttps://stream.test/match")

    assertEquals("42", result.channels.single().tvgId)
    assertEquals("Sport", result.channels.single().groupName)
  }

  private fun parse(text: String): ParsedPlaylist = parser.parse(StringReader(text))
}

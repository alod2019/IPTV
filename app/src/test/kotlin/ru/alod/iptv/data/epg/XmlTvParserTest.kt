package ru.alod.iptv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTvParserTest {
  @Test
  fun parsesProgramsTitlesDescriptionsAndTimezones() {
    val programs = XmlTvParser().parse(
      """
      <tv>
        <programme start="19700101000000 +0000" stop="19700101000200 +0000" channel="news.one">
          <title>Morning news</title>
          <desc>Live update</desc>
        </programme>
        <programme start="19700101000200 +0000" stop="19700101000400 +0000" channel="news.one">
          <title>Interview</title>
        </programme>
        <programme start="19700101010000 +0300" stop="19700101020000 +0300" channel="news.two">
          <title>Новости</title>
        </programme>
      </tv>
      """.trimIndent().byteInputStream(),
    )

    assertEquals(3, programs.size)
    assertEquals("Morning news", programs[0].title)
    assertEquals("Live update", programs[0].description)
    assertEquals(0L, programs[0].startMillis)
    assertEquals(120_000L, programs[0].endMillis)
    assertEquals("Interview", programs[1].title)
    assertNull(programs[1].description)
    assertEquals("Новости", programs[2].title)
    assertEquals(-2 * 60 * 60 * 1000L, programs[2].startMillis)
  }

  @Test
  fun skipsProgramWithoutValidDates() {
    val programs = XmlTvParser().parse(
      """
      <tv>
        <programme start="invalid" stop="19700101000200 +0000" channel="news.one"><title>Broken</title></programme>
        <programme start="19700101000000 +0000" stop="19700101000200 +0000" channel="news.one"><title>Valid</title></programme>
      </tv>
      """.trimIndent().byteInputStream(),
    )

    assertEquals("Valid", programs.single().title)
  }
}

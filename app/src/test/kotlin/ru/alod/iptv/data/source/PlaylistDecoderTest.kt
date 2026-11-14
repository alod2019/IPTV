package ru.alod.iptv.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDecoderTest {
  @Test
  fun decodesUtf8Bom() {
    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Канал".toByteArray()

    assertEquals("Канал", PlaylistDecoder.decode(bytes))
  }

  @Test
  fun fallsBackToWindows1251() {
    val bytes = "Новости".toByteArray(charset("windows-1251"))

    assertEquals("Новости", PlaylistDecoder.decode(bytes))
  }
}

package ru.alod.iptv.data.epg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.PlaylistImportException

class EpgLoader(
  private val client: OkHttpClient,
  private val parser: XmlTvParser,
) {
  suspend fun load(url: String): List<ParsedEpgProgram> = withContext(Dispatchers.IO) {
    val request = try {
      Request.Builder().url(url).build()
    } catch (exception: IllegalArgumentException) {
      throw PlaylistImportException(ImportError.INVALID_FORMAT, exception)
    }
    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw PlaylistImportException(ImportError.HTTP)
        val body = response.body ?: throw PlaylistImportException(ImportError.EMPTY)
        val compressed = body.byteStream().readLimited(MAXIMUM_COMPRESSED_BYTES)
        val content = if (compressed.isGzip()) {
          GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readLimited(MAXIMUM_XML_BYTES) }
        } else {
          compressed
        }
        parser.parse(ByteArrayInputStream(content))
      }
    } catch (exception: PlaylistImportException) {
      throw exception
    } catch (exception: SocketTimeoutException) {
      throw PlaylistImportException(ImportError.TIMEOUT, exception)
    } catch (exception: IOException) {
      throw PlaylistImportException(ImportError.NETWORK, exception)
    } catch (exception: Exception) {
      throw PlaylistImportException(ImportError.INVALID_FORMAT, exception)
    }
  }

  private fun ByteArray.isGzip(): Boolean =
    size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()

  private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val count = read(buffer)
      if (count < 0) break
      total += count
      if (total > limit) throw PlaylistImportException(ImportError.TOO_LARGE)
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  private companion object {
    const val MAXIMUM_COMPRESSED_BYTES = 50 * 1024 * 1024
    const val MAXIMUM_XML_BYTES = 100 * 1024 * 1024
  }
}

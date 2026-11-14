package ru.alod.iptv.data.source

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.PlaylistImportException

class PlaylistSourceLoader(
  private val client: OkHttpClient,
  private val contentResolver: ContentResolver,
  private val maximumBytes: Int = MAXIMUM_PLAYLIST_BYTES,
) {
  suspend fun loadUrl(url: String): String = withContext(Dispatchers.IO) {
    val request = try {
      Request.Builder().url(url.trim()).build()
    } catch (exception: IllegalArgumentException) {
      throw PlaylistImportException(ImportError.INVALID_FORMAT, exception)
    }
    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw PlaylistImportException(ImportError.HTTP)
        val body = response.body ?: throw PlaylistImportException(ImportError.EMPTY)
        PlaylistDecoder.decode(body.byteStream().readLimited(maximumBytes))
      }
    } catch (exception: PlaylistImportException) {
      throw exception
    } catch (exception: SocketTimeoutException) {
      throw PlaylistImportException(ImportError.TIMEOUT, exception)
    } catch (exception: IOException) {
      throw PlaylistImportException(ImportError.NETWORK, exception)
    }
  }

  suspend fun loadFile(uri: String): String = withContext(Dispatchers.IO) {
    try {
      val stream = contentResolver.openInputStream(Uri.parse(uri))
        ?: throw PlaylistImportException(ImportError.FILE_ACCESS)
      stream.use { PlaylistDecoder.decode(it.readLimited(maximumBytes)) }
    } catch (exception: PlaylistImportException) {
      throw exception
    } catch (exception: SecurityException) {
      throw PlaylistImportException(ImportError.FILE_ACCESS, exception)
    } catch (exception: FileNotFoundException) {
      throw PlaylistImportException(ImportError.FILE_ACCESS, exception)
    } catch (exception: IOException) {
      throw PlaylistImportException(ImportError.FILE_ACCESS, exception)
    }
  }

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
    const val MAXIMUM_PLAYLIST_BYTES = 10 * 1024 * 1024
  }
}

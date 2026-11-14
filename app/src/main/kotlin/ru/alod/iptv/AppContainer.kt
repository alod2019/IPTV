package ru.alod.iptv

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import ru.alod.iptv.data.DefaultPlaylistRepository
import ru.alod.iptv.data.local.IptvDatabase
import ru.alod.iptv.data.m3u.M3uParser
import ru.alod.iptv.data.source.PlaylistSourceLoader
import ru.alod.iptv.domain.repository.PlaylistRepository

class AppContainer(context: Context) {
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private val database = IptvDatabase.create(context)

  val repository: PlaylistRepository = DefaultPlaylistRepository(
    database = database,
    sourceLoader = PlaylistSourceLoader(httpClient, context.contentResolver),
    parser = M3uParser(),
  )
}

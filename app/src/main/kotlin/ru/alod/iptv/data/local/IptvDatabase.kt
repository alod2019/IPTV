package ru.alod.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [PlaylistEntity::class, ChannelEntity::class, ProgramEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class IptvDatabase : RoomDatabase() {
  abstract fun playlistDao(): PlaylistDao

  companion object {
    fun create(context: Context): IptvDatabase =
      Room.databaseBuilder(context, IptvDatabase::class.java, "iptv.db").build()
  }
}

@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for cache management.
 */
@Database(
    entities = [
        CachedSong::class,
        CachedAlbum::class,
        DownloadQueue::class,
        CacheMetadata::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CacheDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    companion object {
        private const val DATABASE_NAME = "melos_cache_db"

        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun getDatabase(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Type converters for Room database.
 */
class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus {
        return DownloadStatus.valueOf(value)
    }
}

package com.example.blue_book.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object MediaCacheProvider {
    @Volatile private var simpleCache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: buildCache(context.applicationContext).also { simpleCache = it }
        }
    }

    private fun buildCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, NoOpCacheEvictor(), dbProvider)
    }
}



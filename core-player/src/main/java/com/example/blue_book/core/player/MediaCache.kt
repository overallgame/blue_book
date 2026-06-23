package com.example.blue_book.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object MediaCacheProvider {
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024

    @Volatile private var simpleCache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: buildCache(context.applicationContext).also { simpleCache = it }
        }
    }

    fun clear(context: Context) {
        synchronized(this) {
            simpleCache?.release()
            simpleCache = null
        }
        val cacheDir = File(context.applicationContext.cacheDir, "media_cache")
        cacheDir.deleteRecursively()
    }

    private fun buildCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, dbProvider)
    }
}



@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.blue_book.core.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class PlayerFactories(
    val renderersFactory: RenderersFactory,
    val trackSelector: TrackSelector,
    val loadControl: LoadControl,
    val mediaSourceFactory: MediaSource.Factory,
    val analyticsListener: Player.Listener? = null
)

object PlayerFactoriesProvider {
    fun default(context: Context): PlayerFactories {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 12000, 250, 500)
            .build()

        // OkHttp 客户端（可添加鉴权/UA/重试等拦截器）
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        val upstreamFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)

        // CacheDataSource（命中缓存优先，失败回源）
        val cache = MediaCacheProvider.get(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)
            .setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy(3) {})

        val analytics = BasicPlayerAnalytics(defaultAnalyticsLogger())

        return PlayerFactories(
            renderersFactory = renderersFactory,
            trackSelector = trackSelector,
            loadControl = loadControl,
            mediaSourceFactory = mediaSourceFactory,
            analyticsListener = analytics
        )
    }
}



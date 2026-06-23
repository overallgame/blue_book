@file:Suppress("UnstableApi")
package com.example.blue_book.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
import com.example.blue_book.network.VideoOkHttpProvider
data class PlayerFactories @OptIn(UnstableApi::class) constructor
    (
    val renderersFactory: RenderersFactory,
    val trackSelector: TrackSelector,
    val loadControl: LoadControl,
    val mediaSourceFactory: MediaSource.Factory,
    val analyticsListener: Player.Listener? = null
)

object PlayerFactoriesProvider {
    @OptIn(UnstableApi::class)
    fun default(context: Context): PlayerFactories {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 12000, 250, 500)
            .build()

        val okHttpClient = VideoOkHttpProvider.getInstance(context)

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



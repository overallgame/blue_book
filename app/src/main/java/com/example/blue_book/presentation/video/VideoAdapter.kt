package com.example.blue_book.presentation.video

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.databinding.VideoItemViewBinding
import com.example.blue_book.core.player.PlayerEngine
import com.example.blue_book.core.player.PlayerEnginePool
import com.example.blue_book.core.player.ExoPlayerEngine
import com.example.blue_book.core.player.gl.GlVideoSurfaceView
import com.example.blue_book.core.player.gl.GlSurfaceProvider
import com.example.blue_book.core.player.gl.FilterType
import android.view.View
import android.widget.SeekBar
import androidx.media3.common.util.UnstableApi
import com.example.blue_book.core.player.PlayerEvents

@UnstableApi
class VideoAdapter(
    context: Context,
    private val onClickLike: (VideoCardInfo) -> Unit,
    private val onClickCollect: (VideoCardInfo) -> Unit,
    private val onPlayerError: (String) -> Unit,
    private val onRequestPlayUrl: (VideoCardInfo) -> Unit
) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

	private val videoList: MutableList<VideoCardInfo> = mutableListOf()
    private val viewHolderMap = mutableMapOf<Int, ViewHolder>()
    private val enginePool = PlayerEnginePool(maxSize = 3) { ExoPlayerEngine(context) }
    private val useGl = true
    private val retryCountByUrl = mutableMapOf<String, Int>()

    inner class ViewHolder(private val binding: VideoItemViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentUrl: String? = null
        private var engine: PlayerEngine? = null
        private var glView: GlVideoSurfaceView? = null
        private var glProvider: GlSurfaceProvider? = null
        private var filterType: FilterType = FilterType.GRAY
        private var filterIntensity: Int = 100 // 0..100
        private var eventBridge: PlayerEvents? = null

        private fun releaseEngineToPool() {
            val url = currentUrl
            val e = engine
            if (url.isNullOrBlank() || e == null) {
                engine = null
                currentUrl = null
                return
            }
            eventBridge?.let { eb -> e.removeListener(eb) }
            eventBridge = null
            e.setSurfaceProvider(null)
            binding.videoItemVideoPlayer.player = null
            enginePool.release(url)
            engine = null
            currentUrl = null
        }

        fun bind(videoInfo: VideoCardInfo) {
            val url = videoInfo.playUrl
            val previousUrl = currentUrl
            if (!previousUrl.isNullOrBlank() && previousUrl != url) {
                releaseEngineToPool()
            }
            currentUrl = url

            binding.videoItemNickname.text = videoInfo.nickname
            binding.videoItemDescription.text = videoInfo.description
            binding.videoItemLoveNumber.text = formatCount(videoInfo.like)
            binding.videoItemCollectionNumber.text = formatCount(videoInfo.collection)

            binding.videoItemLoveNumber.setOnClickListener { onClickLike(videoInfo) }
            binding.videoItemCollectionNumber.setOnClickListener { onClickCollect(videoInfo) }

            // 若开启 GL 渲染，创建专用 GL Surface 并通过 provider 注入给引擎
            if (url.isBlank()) {
                onRequestPlayUrl(videoInfo)
                return
            } else if (useGl) {
                if (glView == null) {
                    glView = GlVideoSurfaceView(binding.root.context)
                    glView!!.setFilter(filterType)
                    glProvider = GlSurfaceProvider(glView!!)
                    binding.videoItemGlContainer.addView(glView, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    binding.videoItemVideoPlayer.visibility = View.GONE
                }
                binding.videoItemFilterToggle.visibility = View.VISIBLE
                binding.videoItemFilterToggle.setOnClickListener {
                    filterType = when (filterType) {
                        FilterType.NONE -> FilterType.GRAY
                        FilterType.GRAY -> FilterType.WARM
                        FilterType.WARM -> FilterType.NONE
                    }
                    glView?.setFilter(filterType)
                    if (filterType == FilterType.NONE) binding.videoItemFilterIntensity.visibility = View.GONE
                }
                binding.videoItemFilterToggle.setOnLongClickListener {
                    val v = if (binding.videoItemFilterIntensity.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    binding.videoItemFilterIntensity.visibility = v
                    true
                }
                binding.videoItemFilterIntensity.progress = filterIntensity
                binding.videoItemFilterIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        filterIntensity = progress
                        glView?.setIntensity(progress / 100f)
                        glView?.requestRender()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
                releaseEngineToPool()
                currentUrl = url
                engine = enginePool.acquire(url).apply {
                    setSurfaceProvider(glProvider)
                    attachEvents(this, url)
                    bindTo(binding.videoItemVideoPlayer)
                    prepare(url)
                    pause()
                }
            } else {
                binding.videoItemFilterToggle.visibility = View.GONE
                releaseEngineToPool()
                currentUrl = url
                engine = enginePool.acquire(url).apply {
                    attachEvents(this, url)
                    bindTo(binding.videoItemVideoPlayer)
                    prepare(url)
                    pause()
                }
            }
        }

        fun play() = engine?.play()
        fun pause() = engine?.pause()

        fun release() {
            if (useGl) {
                releaseEngineToPool()
                glProvider?.release()
                glProvider = null
                glView?.let { binding.videoItemGlContainer.removeView(it) }
                glView = null
                binding.videoItemVideoPlayer.visibility = View.GONE
            } else {
                releaseEngineToPool()
            }
        }

        private fun attachEvents(playerEngine: PlayerEngine, url: String) {
            val bridge = object : PlayerEvents {
                override fun onReady() { retryCountByUrl.remove(url) }
                override fun onError(message: String) {
                    val cnt = retryCountByUrl.getOrDefault(url, 0)
                    if (cnt < 2) {
                        retryCountByUrl[url] = cnt + 1
                        playerEngine.prepare(url)
                        playerEngine.pause()
                    } else {
                        onPlayerError(message.ifEmpty { "播放失败" })
                    }
                }
            }
            eventBridge = bridge
            playerEngine.addListener(bridge)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = VideoItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        viewHolderMap[position] = holder
        holder.bind(videoList[position])
        // 非 GL 路径：预加载下一条
        if (!useGl) {
            val next = position + 1
            if (next < videoList.size) {
                val nv = videoList[next]
                val nextUrl = nv.playUrl
                if (nextUrl.isNotBlank()) {
                    enginePool.preload(nextUrl, nextUrl)
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        viewHolderMap.entries.removeIf { it.value == holder }
        holder.release()
    }

    override fun getItemCount(): Int = videoList.size

    fun addFirstVideo(video: VideoCardInfo) {
        videoList.add(0, video)
    }

    fun submitAppend(newVideos: List<VideoCardInfo>) {
        val uniqueNewVideos = newVideos.filterNot { newVideo ->
            videoList.any { existingVideo -> existingVideo.aid == newVideo.aid }
        }
        if (uniqueNewVideos.isNotEmpty()) {
            val start = videoList.size
            videoList.addAll(uniqueNewVideos)
            notifyItemRangeInserted(start, uniqueNewVideos.size)
        }
    }

    fun updateVideoList(video: VideoCardInfo) {
        videoList.indexOfFirst { it.aid == video.aid }.takeIf { it != -1 }?.let { index ->
            videoList[index] = video
            notifyItemChanged(index)
        }
    }

    fun playAtPosition(position: Int) { viewHolderMap[position]?.play() }
    fun pauseAtPosition(position: Int) { viewHolderMap[position]?.pause() }
    fun pauseAll() { viewHolderMap.values.forEach { it.pause() } }
    fun resumeCurrent(position: Int) { viewHolderMap[position]?.play() }
    fun release() { enginePool.releaseAll() }

    private fun formatCount(v: Int): String = if (v > 10000) "%.1fw".format(v / 10000.0) else v.toString()

    fun preloadByPosition(pos: Int) {
        if (pos in 0 until videoList.size) {
            val url = videoList[pos].playUrl
            if (url.isNotBlank()) {
                enginePool.preload(url, url)
            }
        }
    }

    fun releaseByPosition(pos: Int) {
        if (pos in 0 until videoList.size) {
            val url = videoList[pos].playUrl
            if (url.isNotBlank()) {
                enginePool.release(url)
            }
        }
    }
}

package com.example.blue_book.presentation.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.R
import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.databinding.VideoItemViewBinding
import com.example.blue_book.core.player.PlayerEngine
import com.example.blue_book.core.player.PlayerEnginePool
import com.example.blue_book.core.player.ExoPlayerEngine
import com.example.blue_book.core.player.gl.GlVideoSurfaceView
import com.example.blue_book.core.player.gl.GlSurfaceProvider
import com.example.blue_book.core.player.gl.FilterType
import com.example.blue_book.core.player.PlayerEvents

@UnstableApi
class VideoAdapter(
    context: Context,
    private val onClickLike: (VideoCardInfo) -> Unit,
    private val onClickCollect: (VideoCardInfo) -> Unit,
    private val onClickComment: (VideoCardInfo) -> Unit,
    private val onClickShare: (VideoCardInfo) -> Unit,
    private val onClickAvatar: (VideoCardInfo) -> Unit,
    private val onPlayerError: (String) -> Unit,
    private val onRequestPlayUrl: (VideoCardInfo) -> Unit
) : ListAdapter<VideoCardInfo, VideoAdapter.ViewHolder>(VideoDiffCallback()) {

    private val viewHolderMap = mutableMapOf<Int, ViewHolder>()
    private val enginePool = PlayerEnginePool(maxSize = 3) { ExoPlayerEngine(context) }
    private val useGl = true
    private val retryCountByUrl = mutableMapOf<String, Int>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).aid

    inner class ViewHolder(private val binding: VideoItemViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentUrl: String? = null
        private var engine: PlayerEngine? = null
        private var glView: GlVideoSurfaceView? = null
        private var glProvider: GlSurfaceProvider? = null
        private var filterType: FilterType = FilterType.GRAY
        private var filterIntensity: Int = 100
        private var eventBridge: PlayerEvents? = null
        private var currentVideo: VideoCardInfo? = null

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
            currentVideo = videoInfo
            val url = videoInfo.playUrl
            val previousUrl = currentUrl
            if (!previousUrl.isNullOrBlank() && previousUrl != url) {
                releaseEngineToPool()
            }
            currentUrl = url

            // Bind basic info
            binding.videoItemNickname.text = videoInfo.nickname
            binding.videoItemDescription.text = videoInfo.description
            binding.videoItemLikeCount.text = formatCount(videoInfo.like)
            binding.videoItemCollectCount.text = formatCount(videoInfo.collection)
            binding.videoItemCommentCount.text = formatCount(videoInfo.commentCount)

            // Bind avatar
            Glide.with(binding.root.context)
                .load(videoInfo.avatar)
                .placeholder(R.drawable.ic_launcher_background)
                .circleCrop()
                .into(binding.videoItemAvatar)

            // Bind like state
            binding.videoItemLikeBtn.setImageResource(
                if (videoInfo.isLike) R.drawable.like_icon3 else R.drawable.like_icon2
            )

            // Bind collect state (if available in VideoCardInfo)
            // binding.videoItemCollectBtn.setImageResource(...)

            // Set click listeners for right side action buttons
            binding.videoItemLikeBtn.setOnClickListener {
                currentVideo?.let(onClickLike)
            }

            binding.videoItemCollectBtn.setOnClickListener {
                currentVideo?.let(onClickCollect)
            }

            binding.videoItemCommentBtn.setOnClickListener {
                currentVideo?.let(onClickComment)
            }

            binding.videoItemShareBtn.setOnClickListener {
                currentVideo?.let(onClickShare)
            }

            binding.videoItemAvatar.setOnClickListener {
                currentVideo?.let(onClickAvatar)
            }

            // Filter controls
            if (url.isBlank()) {
                onRequestPlayUrl(videoInfo)
                return
            } else if (useGl) {
                if (glView == null) {
                    glView = GlVideoSurfaceView(binding.root.context)
                    glView?.setFilter(filterType)
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

        fun updateLike(like: Int, isLike: Boolean) {
            binding.videoItemLikeCount.text = formatCount(like)
            binding.videoItemLikeBtn.setImageResource(
                if (isLike) R.drawable.like_icon3 else R.drawable.like_icon2
            )
        }

        fun updateCollect(collect: Int) {
            binding.videoItemCollectCount.text = formatCount(collect)
        }

        fun updateCommentCount(commentCount: Int) {
            binding.videoItemCommentCount.text = formatCount(commentCount)
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
        holder.bind(getItem(position))
        if (!useGl) {
            val next = position + 1
            if (next < itemCount) {
                val nv = getItem(next)
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

    override fun getItemCount(): Int = itemCount

    fun addFirstVideo(video: VideoCardInfo) {
        val newList = mutableListOf(video).apply { addAll(currentList) }
        submitList(newList)
    }

    fun submitAppend(newVideos: List<VideoCardInfo>) {
        val uniqueNewVideos = newVideos.filterNot { newVideo ->
            currentList.any { existingVideo -> existingVideo.aid == newVideo.aid }
        }
        if (uniqueNewVideos.isNotEmpty()) {
            val newList = currentList.toMutableList().apply { addAll(uniqueNewVideos) }
            submitList(newList)
        }
    }

    fun updateVideoList(video: VideoCardInfo) {
        val idx = currentList.indexOfFirst { it.aid == video.aid }
        if (idx == -1) return
        val newList = currentList.toMutableList().apply { this[idx] = video }
        submitList(newList)
    }

    fun playAtPosition(position: Int) { viewHolderMap[position]?.play() }
    fun pauseAtPosition(position: Int) { viewHolderMap[position]?.pause() }
    fun pauseAll() { viewHolderMap.values.forEach { it.pause() } }
    fun resumeCurrent(position: Int) { viewHolderMap[position]?.play() }
    fun release() { enginePool.releaseAll() }

    private fun formatCount(v: Int): String = when {
        v >= 10000 -> "%.1fw".format(v / 10000.0)
        v >= 1000 -> "%.1fk".format(v / 1000.0)
        else -> v.toString()
    }

    fun preloadByPosition(pos: Int) {
        if (pos in 0 until itemCount) {
            val url = getItem(pos).playUrl
            if (url.isNotBlank()) {
                enginePool.preload(url, url)
            }
        }
    }

    fun releaseByPosition(pos: Int) {
        if (pos in 0 until itemCount) {
            val url = getItem(pos).playUrl
            if (url.isNotBlank()) {
                enginePool.release(url)
            }
        }
    }

    private sealed interface VideoPayload {
        data class LikeChanged(val like: Int, val isLike: Boolean) : VideoPayload
        data class CollectChanged(val collect: Int) : VideoPayload
        data class CommentCountChanged(val commentCount: Int) : VideoPayload
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<VideoCardInfo>() {
        override fun areItemsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo): Boolean {
            return oldItem.aid == newItem.aid
        }

        override fun areContentsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: VideoCardInfo, newItem: VideoCardInfo): Any? {
            val payloads = mutableListOf<VideoPayload>()
            if (oldItem.isLike != newItem.isLike || oldItem.like != newItem.like) {
                payloads.add(VideoPayload.LikeChanged(newItem.like, newItem.isLike))
            }
            if (oldItem.collection != newItem.collection) {
                payloads.add(VideoPayload.CollectChanged(newItem.collection))
            }
            if (oldItem.commentCount != newItem.commentCount) {
                payloads.add(VideoPayload.CommentCountChanged(newItem.commentCount))
            }
            return payloads.ifEmpty { null }
        }
    }
}

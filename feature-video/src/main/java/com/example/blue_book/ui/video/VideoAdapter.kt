package com.example.blue_book.ui.video

import android.content.Context
import android.view.LayoutInflater
import java.util.LinkedHashMap
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.feature_video.R
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_video.databinding.VideoItemViewBinding
import com.example.blue_book.core.player.PlayerEngine
import com.example.blue_book.core.player.PlayerEnginePool
import com.example.blue_book.core.player.ExoPlayerEngine
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

    private val viewHolderMap = mutableMapOf<Long, ViewHolder>()
    private val enginePool = PlayerEnginePool(maxSize = 3) { ExoPlayerEngine(context) }
    private val savedPositions = LinkedHashMap<String, Long>(100, 0.75f, true)

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).aid

    inner class ViewHolder(private val binding: VideoItemViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentUrl: String? = null
        private var engine: PlayerEngine? = null
        private var eventBridge: PlayerEvents? = null
        private var currentVideo: VideoCardInfo? = null
        private var isProgressTracking = false

        private fun releaseEngineToPool() {
            val url = currentUrl
            val e = engine
            if (url.isNullOrBlank() || e == null) {
                engine = null
                currentUrl = null
                return
            }
            savedPositions[url] = e.currentPosition()
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
            val isNewUrl = currentUrl != url
            if (isNewUrl && !currentUrl.isNullOrBlank()) {
                releaseEngineToPool()
            }
            currentUrl = url

            binding.videoItemNickname.text = videoInfo.nickname
            binding.videoItemDescription.text = videoInfo.description
            binding.videoItemLikeCount.text = formatCount(videoInfo.like)
            binding.videoItemCollectCount.text = formatCount(videoInfo.collection)
            binding.videoItemCommentCount.text = formatCount(videoInfo.commentCount)

            Glide.with(binding.root.context)
                .load(videoInfo.avatar)
                .placeholder(R.drawable.ic_launcher_background)
                .circleCrop()
                .into(binding.videoItemAvatar)

            binding.videoItemLikeBtn.setImageResource(
                if (videoInfo.isLike) R.drawable.like_icon3 else R.drawable.like_icon2
            )

            // 收藏按钮视觉切换
            binding.videoItemCollectBtn.setImageResource(
                if (videoInfo.isCollect) R.drawable.collection_icon1 else R.drawable.collection_icon
            )

            binding.videoItemLikeBtn.setOnClickListener { currentVideo?.let(onClickLike) }
            binding.videoItemCollectBtn.setOnClickListener { currentVideo?.let(onClickCollect) }
            binding.videoItemCommentBtn.setOnClickListener { currentVideo?.let(onClickComment) }
            binding.videoItemShareBtn.setOnClickListener { currentVideo?.let(onClickShare) }
            binding.videoItemAvatar.setOnClickListener { currentVideo?.let(onClickAvatar) }

            // 进度条 — 只在暂停时可见
            binding.videoItemProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) engine?.seekTo(progress * engine!!.duration() / 1000L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isProgressTracking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { isProgressTracking = false }
            })

            if (url.isBlank()) {
                onRequestPlayUrl(videoInfo)
                return
            }
            if (isNewUrl) {
                engine = enginePool.acquire(url).apply {
                    attachEvents(this)
                    bindTo(binding.videoItemVideoPlayer)
                    prepare(url)
                    savedPositions[url]?.takeIf { it > 0 }?.let { seekTo(it) }
                    pause()
                }
            }
        }

        fun play() {
            engine?.play()
            binding.videoItemProgress.visibility = View.GONE
        }

        fun pause() {
            engine?.pause()
            val e = engine ?: return
            val dur = e.duration()
            if (dur > 0) {
                binding.videoItemProgress.max = 1000
                binding.videoItemProgress.progress = ((e.currentPosition() * 1000L) / dur).toInt()
                binding.videoItemProgress.visibility = View.VISIBLE
            }
        }

        private fun attachEvents(playerEngine: PlayerEngine) {
            eventBridge = object : PlayerEvents {
                override fun onError(message: String, errorCode: Int) {
                    onPlayerError(message.ifEmpty { "播放失败" })
                }
            }
            playerEngine.addListener(eventBridge!!)
        }

        fun release() {
            releaseEngineToPool()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = VideoItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        viewHolderMap[video.aid] = holder
        holder.bind(video)
        val next = position + 1
        if (next < itemCount) {
            val nv = getItem(next)
            if (nv.playUrl.isNotBlank()) enginePool.preload(nv.playUrl, nv.playUrl)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        viewHolderMap.entries.removeIf { it.value == holder }
        holder.release()
    }

    fun addFirstVideo(video: VideoCardInfo) {
        submitList(mutableListOf(video).apply { addAll(currentList) })
    }

    fun submitAppend(newVideos: List<VideoCardInfo>) {
        val unique = newVideos.filterNot { nv -> currentList.any { it.aid == nv.aid } }
        if (unique.isNotEmpty()) submitList(currentList.toMutableList().apply { addAll(unique) })
    }

    fun updateVideoList(video: VideoCardInfo) {
        val idx = currentList.indexOfFirst { it.aid == video.aid }
        if (idx == -1) return
        submitList(currentList.toMutableList().apply { this[idx] = video })
    }

    fun playAtPosition(position: Int) {
        if (position in 0 until itemCount) viewHolderMap[getItem(position).aid]?.play()
    }

    fun pauseAtPosition(position: Int) {
        if (position in 0 until itemCount) viewHolderMap[getItem(position).aid]?.pause()
    }

    fun pauseAll() { viewHolderMap.values.forEach { it.pause() } }
    fun restore() {}
    fun release() { enginePool.releaseAll(); savedPositions.clear() }

    fun preloadByPosition(pos: Int) {
        if (pos in 0 until itemCount) {
            val url = getItem(pos).playUrl
            if (url.isNotBlank()) enginePool.preload(url, url)
        }
    }

    fun releaseByPosition(pos: Int) {
        if (pos in 0 until itemCount) {
            val url = getItem(pos).playUrl
            if (url.isNotBlank()) enginePool.release(url)
        }
    }

    private fun formatCount(v: Int): String = when {
        v >= 10000 -> "%.1fw".format(v / 10000.0)
        v >= 1000 -> "%.1fk".format(v / 1000.0)
        else -> v.toString()
    }

    private sealed interface VideoPayload {
        data class LikeChanged(val like: Int, val isLike: Boolean) : VideoPayload
        data class CollectChanged(val collect: Int) : VideoPayload
        data class CommentCountChanged(val commentCount: Int) : VideoPayload
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<VideoCardInfo>() {
        override fun areItemsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo) = oldItem.aid == newItem.aid
        override fun areContentsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo) = oldItem == newItem
        override fun getChangePayload(oldItem: VideoCardInfo, newItem: VideoCardInfo): Any? {
            val payloads = mutableListOf<VideoPayload>()
            if (oldItem.isLike != newItem.isLike || oldItem.like != newItem.like)
                payloads.add(VideoPayload.LikeChanged(newItem.like, newItem.isLike))
            if (oldItem.collection != newItem.collection)
                payloads.add(VideoPayload.CollectChanged(newItem.collection))
            if (oldItem.commentCount != newItem.commentCount)
                payloads.add(VideoPayload.CommentCountChanged(newItem.commentCount))
            return payloads.ifEmpty { null }
        }
    }
}

package com.example.blue_book.ui.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.blue_book.core.player.ExoPlayerEngine
import com.example.blue_book.core.player.PlayerEngine
import com.example.blue_book.core.player.PlayerEnginePool
import com.example.blue_book.core.player.PlayerEvents
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_video.R
import com.example.blue_book.feature_video.databinding.VideoItemViewBinding

@UnstableApi
class VideoAdapter(
    context: Context,
    /** 宿主是否常驻底部导航（见 IMainHost.providesBottomNav）：决定底部互动栏要不要自己避让系统栏 */
    private val hostProvidesBottomNav: Boolean,
    private val currentUserId: Long,
    private val onClickBack: () -> Unit,
    private val onClickLike: (VideoCardInfo) -> Unit,
    private val onClickCollect: (VideoCardInfo) -> Unit,
    private val onClickComment: (VideoCardInfo) -> Unit,
    private val onClickShare: (VideoCardInfo) -> Unit,
    private val onClickFollow: (VideoCardInfo) -> Unit,
    private val onClickFullscreen: () -> Unit,
    private val onExitFullscreen: () -> Unit,
    private val onClickAvatar: (VideoCardInfo) -> Unit,
    private val onPlayerError: (Long, String) -> Unit,
    private val onRequestPlayUrl: (VideoCardInfo) -> Unit
) : ListAdapter<VideoCardInfo, VideoAdapter.ViewHolder>(VideoDiffCallback()) {

    private val viewHolderMap = mutableMapOf<Long, ViewHolder>()
    private val enginePool = PlayerEnginePool(maxSize = 5) { ExoPlayerEngine(context) }
    private val savedPositions = LinkedHashMap<String, Long>(100, 0.75f, true)

    /** 全屏（横屏）模式：由播放页进入/退出，翻页后新 item 也保持全屏态 */
    private var fullscreenMode = false

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).aid

    /** 按位置取条目（ListAdapter 的 getItem 为 protected，供播放页按索引取数据） */
    fun itemAt(position: Int): VideoCardInfo? = currentList.getOrNull(position)

    /** 切换全屏：立即应用到全部已绑定条目（含当前可见项） */
    fun setFullscreen(enabled: Boolean) {
        fullscreenMode = enabled
        viewHolderMap.values.forEach { it.applyFullscreen(enabled) }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(private val binding: VideoItemViewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentUrl: String? = null

        /** 引擎池的键（视频身份）。不能复用 currentUrl：release 时 currentVideo 已切到新条目 */
        private var currentAid: Long = 0L
        private var engine: PlayerEngine? = null
        private var eventBridge: PlayerEvents? = null
        private var currentVideo: VideoCardInfo? = null
        private var isProgressTracking = false
        private var hasTags = false

        /** 长按倍速：延迟进入 2x；UP/CANCEL 必须复位，否则引擎回到对象池后仍是 2x */
        private val gestureHandler = Handler(Looper.getMainLooper())
        private var speedRunnable: Runnable? = null

        /** 双击点赞（手势必由手势层消费才能收到上一次 UP，见 init 内的触摸处理） */
        private val tapDetector = GestureDetector(
            itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val video = currentVideo ?: return false
                    if (!video.isLike) onClickLike(video)
                    playLikeHeart()
                    return true
                }
            }
        )

        init {
            // 全面屏：黑色背景延展到系统栏后方，内容仅避让**状态栏**。
            // 底部按宿主分两种：Tab 宿主（MainActivity）内容区下方常驻底部导航，
            // 那块空间已被导航栏占住（导航栏自己按 bars.bottom 抬高），页面再加一次就是重复占位；
            // 独立播放页（VideoActivity）没有导航栏，底部互动栏必须自己让开系统手势条，
            // 否则「说点什么/点赞/收藏/评论」会被压住点不到。
            // 让位加在互动栏自身的 padding 上，视频画面仍铺满到屏幕边缘。
            // （全屏时系统栏隐藏，bars 自然为 0）
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(top = bars.top)
                binding.videoItemBottomBar.updatePadding(
                    bottom = if (hostProvidesBottomNav) 0 else bars.bottom
                )
                insets
            }
            // 手势层：消费触摸（返回 true）以保证收到 UP/CANCEL——双击检测依赖上一次 UP，
            // 长按倍速依赖 UP/CANCEL 复位；翻页滑动时 ViewPager2 会下发 CANCEL
            binding.videoItemGestureLayer.setOnTouchListener { _, event ->
                tapDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        speedRunnable?.let { gestureHandler.removeCallbacks(it) }
                        speedRunnable = Runnable {
                            engine?.setSpeed(LONG_PRESS_SPEED)
                            binding.videoItemSpeedHint.visibility = View.VISIBLE
                        }
                        gestureHandler.postDelayed(speedRunnable!!, LONG_PRESS_SPEED_DELAY_MS)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetSpeed()
                }
                true
            }
        }

        /** 恢复 1x 并隐藏提示（触摸结束 / 离屏回收 / 归还引擎时都要调用） */
        private fun resetSpeed() {
            speedRunnable?.let { gestureHandler.removeCallbacks(it) }
            speedRunnable = null
            engine?.setSpeed(1f)
            binding.videoItemSpeedHint.visibility = View.GONE
        }

        /** 双击点赞爱心动画 */
        private fun playLikeHeart() {
            binding.videoItemHeart.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0.3f
                scaleY = 0.3f
                animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
                    .withEndAction {
                        animate().alpha(0f).setDuration(200).withEndAction {
                            visibility = View.GONE
                        }
                    }
            }
        }

        private fun releaseEngineToPool() {
            val url = currentUrl
            val e = engine
            if (url.isNullOrBlank() || e == null) {
                // 提前返回也必须复位倍速提示：长按的 Runnable 可能已把提示条置为可见，
                // 而 bind() 不会重置该视图的可见性，holder 复用后会残留一个「2x」角标
                resetSpeed()
                engine = null
                currentUrl = null
                return
            }
            resetSpeed()
            savedPositions[url] = e.currentPosition()
            eventBridge?.let { eb -> e.removeListener(eb) }
            eventBridge = null
            e.setSurfaceProvider(null)
            binding.videoItemVideoPlayer.player = null
            enginePool.release(currentAid)
            engine = null
            currentUrl = null
            currentAid = 0L
        }

        fun bind(videoInfo: VideoCardInfo) {
            currentVideo = videoInfo
            val url = videoInfo.playUrl
            val isNewUrl = currentUrl != url
            if (isNewUrl && !currentUrl.isNullOrBlank()) {
                releaseEngineToPool()
            }
            currentUrl = url

            // 标题与话题标签：description 中的 #标签 解析到标签行，剩余文本作标题
            val tokens = videoInfo.description.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val tags = tokens.filter { it.startsWith("#") }
            val titleText = tokens.filterNot { it.startsWith("#") }.joinToString(" ")
            binding.videoItemDescription.text = titleText.ifEmpty { videoInfo.description }
            if (tags.isEmpty()) {
                hasTags = false
                binding.videoItemTags.visibility = View.GONE
            } else {
                hasTags = true
                binding.videoItemTags.visibility = View.VISIBLE
                binding.videoItemTags.text = tags.joinToString(" ")
            }

            binding.videoItemNickname.text = videoInfo.nickname
            binding.videoItemLikeCount.text = formatCount(videoInfo.like)
            binding.videoItemCollectCount.text = formatCount(videoInfo.collection)
            binding.videoItemCommentCount.text = formatCount(videoInfo.commentCount)

            Glide.with(binding.root.context)
                .load(videoInfo.avatar)
                .placeholder(R.drawable.ic_launcher_background)
                .circleCrop()
                .into(binding.videoItemAvatar)

            // 首帧封面：新视频起播前显示封面图，首帧渲染(onReady)后淡出；复用引擎已有画面则不盖封面
            if ((isNewUrl || engine == null) && videoInfo.image.isNotBlank()) {
                binding.videoItemCover.visibility = View.VISIBLE
                binding.videoItemCover.alpha = 1f
                Glide.with(binding.root.context).load(videoInfo.image).centerCrop()
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(binding.videoItemCover)
            } else {
                binding.videoItemCover.visibility = View.GONE
            }

            // 播放错误态复位 + 重试（重新 prepare 当前地址）
            binding.videoItemError.visibility = View.GONE
            binding.videoItemErrorRetry.setOnClickListener {
                binding.videoItemError.visibility = View.GONE
                val url = currentUrl ?: return@setOnClickListener
                engine?.prepare(url)
                engine?.play()
            }

            binding.videoItemLikeBtn.setImageResource(
                if (videoInfo.isLike) R.drawable.icon_love_selected else R.drawable.icon_love
            )

            // 收藏按钮视觉切换
            binding.videoItemCollectBtn.setImageResource(
                if (videoInfo.isCollect) R.drawable.icon_collect_selected else R.drawable.icon_collect
            )

            binding.videoItemLikeBtn.setOnClickListener { currentVideo?.let(onClickLike) }
            binding.videoItemCollectBtn.setOnClickListener { currentVideo?.let(onClickCollect) }
            binding.videoItemCommentBtn.setOnClickListener { currentVideo?.let(onClickComment) }
            binding.videoItemCommentInput.setOnClickListener { currentVideo?.let(onClickComment) }
            // 关注按钮：自己的视频不显示；按 isFollowed 切换"关注/已关注"样式
            val isSelf = videoInfo.uploaderId != 0L && videoInfo.uploaderId == currentUserId
            binding.videoItemFollowBtn.visibility = if (isSelf) View.GONE else View.VISIBLE
            binding.videoItemFollowBtn.text = if (videoInfo.isFollowed) "已关注" else "关注"
            binding.videoItemFollowBtn.setTextColor(
                binding.root.context.getColor(
                    // 播放页固定深色：品牌蓝底用固定白字，不随主题（夜版 onPrimary 会变深灰）
                    if (videoInfo.isFollowed) R.color.video_text_secondary else R.color.video_on_surface
                )
            )
            binding.videoItemFollowBtn.setBackgroundResource(
                if (videoInfo.isFollowed) R.drawable.shape_video_pill else R.drawable.shape_follow_btn
            )
            binding.videoItemFollowBtn.setOnClickListener { currentVideo?.let(onClickFollow) }
            binding.videoItemFullscreen.setOnClickListener { onClickFullscreen() }
            binding.videoItemExitFullscreen.setOnClickListener { onExitFullscreen() }
            binding.videoItemAvatar.setOnClickListener { currentVideo?.let(onClickAvatar) }
            binding.videoItemBack.setOnClickListener { onClickBack() }
            binding.videoItemShare.setOnClickListener { currentVideo?.let(onClickShare) }

            applyFullscreen(fullscreenMode)

            // 进度条 — 只在暂停时可见
            binding.videoItemProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val e = engine ?: return
                    if (e.duration() > 0) e.seekTo(progress * e.duration() / 1000L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isProgressTracking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { isProgressTracking = false }
            })

            if (url.isBlank()) {
                onRequestPlayUrl(videoInfo)
                return
            }
            if (isNewUrl) {
                engine = enginePool.acquire(videoInfo.aid).apply {
                    attachEvents(this)
                    bindTo(binding.videoItemVideoPlayer)
                    prepare(url)
                    savedPositions[url]?.takeIf { it > 0 }?.let { seekTo(it) }
                    pause()
                }
                currentAid = videoInfo.aid
            }
        }

        /**
         * 全屏（横屏）模式：视频区域解除上下边距铺满屏幕、resizeMode 切 ZOOM（无黑边），
         * 隐藏页面浮层（顶栏/作者行/标题/标签/底部互动栏），保留进度条与退出全屏按钮。
         * 退出时恢复 bind() 确定的默认可见性（hasTags 由 bind 记录）。
         */
        fun applyFullscreen(enabled: Boolean) {
            binding.videoItemContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = if (enabled) 0 else dp(84)
                bottomMargin = if (enabled) 0 else dp(56)
            }
            binding.videoItemVideoPlayer.resizeMode =
                if (enabled) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            val hidden = if (enabled) View.GONE else View.VISIBLE
            binding.videoItemBack.visibility = hidden
            binding.videoItemShare.visibility = hidden
            binding.videoItemAuthorRow.visibility = hidden
            binding.videoItemDescription.visibility = hidden
            binding.videoItemTags.visibility = if (enabled) View.GONE else if (hasTags) View.VISIBLE else View.GONE
            binding.videoItemBottomBar.visibility = hidden
            binding.videoItemFullscreen.visibility = hidden
            binding.videoItemExitFullscreen.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        private fun dp(v: Int): Int = (v * itemView.resources.displayMetrics.density).toInt()

        /** payload 局部刷新：只更新互动区图标与计数，不重新 bind 播放器 */
        fun bindLikeChange(videoInfo: VideoCardInfo, like: Int, isLike: Boolean) {            currentVideo = videoInfo
            binding.videoItemLikeBtn.setImageResource(
                if (isLike) R.drawable.icon_love_selected else R.drawable.icon_love
            )
            binding.videoItemLikeCount.text = formatCount(like)
        }

        fun bindCollectChange(videoInfo: VideoCardInfo, collect: Int, isCollect: Boolean) {
            currentVideo = videoInfo
            binding.videoItemCollectBtn.setImageResource(
                if (isCollect) R.drawable.icon_collect_selected else R.drawable.icon_collect
            )
            binding.videoItemCollectCount.text = formatCount(collect)
        }

        fun bindCommentCountChange(videoInfo: VideoCardInfo, commentCount: Int) {
            currentVideo = videoInfo
            binding.videoItemCommentCount.text = formatCount(commentCount)
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
                override fun onReady() {
                    // 首帧已渲染，淡出封面
                    binding.videoItemCover.animate().alpha(0f).setDuration(250).withEndAction {
                        binding.videoItemCover.visibility = View.GONE
                    }
                }

                override fun onError(message: String, errorCode: Int) {
                    onPlayerError(currentVideo?.aid ?: 0L, message.ifEmpty { "播放失败" })
                    binding.videoItemErrorText.text = message.ifBlank { "播放失败" }
                    binding.videoItemError.visibility = View.VISIBLE
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val video = getItem(position)
        // payload 局部刷新：只更新点赞/收藏/评论的图标与计数，不重新 bind 播放器，避免打断播放
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        for (p in payloads.filterIsInstance<VideoPayload>()) {
            when (p) {
                is VideoPayload.LikeChanged -> holder.bindLikeChange(video, p.like, p.isLike)
                is VideoPayload.CollectChanged -> holder.bindCollectChange(video, p.collect, p.isCollect)
                is VideoPayload.CommentCountChanged -> holder.bindCommentCountChange(video, p.commentCount)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = getItem(position)
        viewHolderMap[video.aid] = holder
        holder.bind(video)
        val next = position + 1
        if (next < itemCount) {
            val nv = getItem(next)
            if (nv.playUrl.isNotBlank()) enginePool.preload(nv.aid, nv.playUrl)
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
    fun release() { enginePool.releaseAll(); savedPositions.clear() }

    fun preloadByPosition(pos: Int) {
        if (pos in 0 until itemCount) {
            val item = getItem(pos)
            if (item.playUrl.isNotBlank()) enginePool.preload(item.aid, item.playUrl)
        }
    }

    // 注意：这里曾有 releaseByPosition(pos)，用于回收"滑过去的位置"的引擎。
    // 它会在持有者仍存活时把引擎归还对象池，于是该实例可能被另一个 ViewHolder
    // acquire 走，旧持有者随后的回收动作就会解绑**他人**的 surface（黑屏且无报错）。
    // 引擎回收现在只发生在持有者作用域内：onViewRecycled → holder.release()，
    // 以及 bind() 换 URL 时；池自身负责按所有权淘汰（见 PlayerEnginePool）。

    private fun formatCount(v: Int): String = when {
        v >= 10000 -> "%.1fw".format(v / 10000.0)
        v >= 1000 -> "%.1fk".format(v / 1000.0)
        else -> v.toString()
    }

    private sealed interface VideoPayload {
        data class LikeChanged(val like: Int, val isLike: Boolean) : VideoPayload
        data class CollectChanged(val collect: Int, val isCollect: Boolean) : VideoPayload
        data class CommentCountChanged(val commentCount: Int) : VideoPayload
    }

    private companion object {
        /** 长按倍速：延迟与倍率 */
        const val LONG_PRESS_SPEED_DELAY_MS = 300L
        const val LONG_PRESS_SPEED = 2f
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<VideoCardInfo>() {
        override fun areItemsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo) = oldItem.aid == newItem.aid
        override fun areContentsTheSame(oldItem: VideoCardInfo, newItem: VideoCardInfo) = oldItem == newItem
        override fun getChangePayload(oldItem: VideoCardInfo, newItem: VideoCardInfo): Any? {
            val payloads = mutableListOf<VideoPayload>()
            if (oldItem.isLike != newItem.isLike || oldItem.like != newItem.like)
                payloads.add(VideoPayload.LikeChanged(newItem.like, newItem.isLike))
            if (oldItem.collection != newItem.collection)
                payloads.add(VideoPayload.CollectChanged(newItem.collection, newItem.isCollect))
            if (oldItem.commentCount != newItem.commentCount)
                payloads.add(VideoPayload.CommentCountChanged(newItem.commentCount))
            return payloads.ifEmpty { null }
        }
    }
}

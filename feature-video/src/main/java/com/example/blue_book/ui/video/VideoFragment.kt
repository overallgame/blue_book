package com.example.blue_book.ui.video

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.host.mainHost
import com.example.blue_book.network.CurrentUser
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.feature_video.databinding.VideoPageBinding
import com.example.blue_book.ui.comment.CommentBottomSheet
import com.example.blue_book.widget.LoginGuideDialog
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class VideoFragment : Fragment() {

	private var _binding: VideoPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: VideoViewModel by viewModels()

	/** 当前登录用户，用于评论区的"我的评论"判定 */
	@Inject
	lateinit var currentUser: CurrentUser

	private lateinit var adapter: VideoAdapter

	/** 全屏（横屏）播放状态 */
	private var isFullscreen = false

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = VideoPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		adapter = VideoAdapter(
			requireContext(),
			// Tab 宿主下方有底部导航，独立播放页没有——决定互动栏要不要自己避让系统栏
			hostProvidesBottomNav = mainHost?.providesBottomNav == true,
			currentUserId = currentUser.userId ?: 0L,
			onClickBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
			onClickLike = { video -> viewModel.dispatch(VideoIntent.ToggleLike(video)) },
			onClickCollect = { video -> viewModel.dispatch(VideoIntent.ToggleCollect(video)) },
			onClickComment = { video ->
				CommentBottomSheet.newInstance(video.aid, currentUser.userId ?: 0L)
					.show(parentFragmentManager, CommentBottomSheet.TAG)
			},
			onClickShare = { video -> shareVideo(video) },
			onClickFollow = { video -> viewModel.dispatch(VideoIntent.ToggleFollow(video)) },
			// 全屏：横屏播放（旋转 + 隐藏系统栏 + 视频铺满，见 enterFullscreen）
			onClickFullscreen = { enterFullscreen() },
			onExitFullscreen = { exitFullscreen() },
			// 头像点击 → 作者主页（后端 /api/v2/users/{id} 提供资料与作品）
			onClickAvatar = { video ->
				if (video.uploaderId > 0) {
					TheRouter.build(RoutePath.USER_PROFILE)
						.withLong(ExtraKeys.EXTRA_USER_ID, video.uploaderId)
						.navigation(requireContext())
				} else {
					Toast.makeText(requireContext(), "作者信息缺失", Toast.LENGTH_SHORT).show()
				}
			},
			// 播放错误由 item 内错误视图（文案+重试按钮）承载；Toast 仅在转码未完成时提示
			onPlayerError = { aid, message ->
				if (aid > 0) viewModel.dispatch(VideoIntent.CheckTranscode(aid, message))
			},
			onRequestPlayUrl = { v -> viewModel.dispatch(VideoIntent.RequestPlayUrl(v.aid, v.cid)) }
		)

		binding.videoViewPager.adapter = adapter
		binding.videoViewPager.offscreenPageLimit = 1
		binding.videoViewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
		binding.videoViewPager.registerOnPageChangeCallback(object :
			ViewPager2.OnPageChangeCallback() {
			private var currentPosition = 0
			override fun onPageSelected(position: Int) {
				adapter.pauseAtPosition(currentPosition)
				if (foreground) adapter.playAtPosition(position)
				currentPosition = position
				// 播放量上报（每视频每会话一次，静默）：以 adapter 当前条目为准，
				// 首页带视频进入时 state.items 与 adapter 列表相差一条，按 state 取会报错视频
				adapter.itemAt(position)?.let { video ->
					viewModel.dispatch(VideoIntent.ReportView(video.aid))
				}
				if (position == adapter.itemCount - 1) {
					viewModel.dispatch(VideoIntent.LoadMore)
				}
				// 预加载窗口：position+1，position+2
				// （不再释放 position-2：那会在持有者存活时归还引擎，导致 surface 被他人解绑；
				//   引擎回收现由 onViewRecycled 与换 URL 触发，池按所有权淘汰）
				adapter.preloadByPosition(position + 1)
				adapter.preloadByPosition(position + 2)
			}

			override fun onPageScrollStateChanged(state: Int) {
				when (state) {
					ViewPager2.SCROLL_STATE_DRAGGING -> adapter.pauseAtPosition(currentPosition)
					// 拖动后回弹到原页时 onPageSelected 不会派发，这里兜底恢复播放
					ViewPager2.SCROLL_STATE_IDLE -> if (foreground) adapter.playAtPosition(currentPosition)
				}
			}
		})

		observeViewModel()
		initEmptyState()
		observeCommentDelta()
		initByArgs()
		// 全屏时返回键先退全屏（同一回调处理顶栏返回与物理返回）。
		// 这里必须用带 viewLifecycleOwner 的重载：它在 Fragment 到达 STARTED 时才入队，
		// 晚于宿主在 onCreate 里注册的 Tab 返回逻辑（返回键后入队者优先），
		// 所以全屏时本回调先拿到返回键。换成不带 owner 的重载会抢不到。
		requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
	}

	/** 全屏模式下拦截返回：先退全屏，不退出页面 */
	private val backCallback = object : androidx.activity.OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			exitFullscreen()
		}
	}

	/** 进入全屏（横屏播放）：视频铺满 + 隐藏系统栏 */
	private fun enterFullscreen() {
		if (isFullscreen) return
		isFullscreen = true
		backCallback.isEnabled = true
		adapter.setFullscreen(true)
		mainHost?.enterFullscreen()
	}

	/** 退出全屏：恢复竖屏布局与系统栏 */
	fun exitFullscreen() {
		if (!isFullscreen) return
		isFullscreen = false
		backCallback.isEnabled = false
		adapter.setFullscreen(false)
		mainHost?.exitFullscreen()
	}

	/** 空列表态：显示提示 + 重试入口（重新加载走退出重进语义，直接刷新当前模式数据） */
	private fun initEmptyState() {
		binding.videoEmptyRetry.setOnClickListener { viewModel.retryInit() }
	}

	/** 评论弹层关闭后回传评论数增量 → 交 ViewModel 同步 state 与列表（防旧值回退） */
	private fun observeCommentDelta() {
		parentFragmentManager.setFragmentResultListener(CommentBottomSheet.RESULT_COMMENT_DELTA, this) { _, bundle ->
			val videoId = bundle.getLong(CommentBottomSheet.KEY_VIDEO_ID, -1L)
			val delta = bundle.getInt(CommentBottomSheet.KEY_DELTA, 0)
			if (videoId != -1L && delta != 0) {
				viewModel.dispatch(VideoIntent.AdjustCommentCount(videoId, delta))
			}
		}
	}

	private fun shareVideo(video: VideoCardInfo) {
		val text = "分享视频：${video.description}（来自 ${video.nickname}）"
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, text)
		}
		try {
			startActivity(Intent.createChooser(intent, "分享到"))
		} catch (_: android.content.ActivityNotFoundException) {
			Toast.makeText(requireContext(), "没有可用的分享应用", Toast.LENGTH_SHORT).show()
		}
	}

	private fun initByArgs() {
		val args = arguments
		val firstVideo = args?.let {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				it.getParcelable(ExtraKeys.EXTRA_VIDEO, VideoCardInfo::class.java)
			} else {
				@Suppress("DEPRECATION")
				it.getParcelable(ExtraKeys.EXTRA_VIDEO)
			}
		}
		when (args?.getString(ExtraKeys.EXTRA_SOURCE)) {
			"search" -> dispatchFromSource(firstVideo, VideoUiState.Mode.Search, keyword = args.getString(ExtraKeys.EXTRA_KEYWORD).orEmpty())
			"liked" -> dispatchFromSource(firstVideo, VideoUiState.Mode.Liked)
			"collected" -> dispatchFromSource(firstVideo, VideoUiState.Mode.Collected)
			"user_videos" -> dispatchFromSource(
				firstVideo,
				VideoUiState.Mode.UserVideos,
				userId = args.getLong(ExtraKeys.EXTRA_SOURCE_USER_ID, 0L)
			)

			else -> {
				firstVideo?.let { adapter.addFirstVideo(it) }
				viewModel.dispatch(VideoIntent.InitRandom)
			}
		}
	}

	/** 来源列表进入：首条为点击项，后续由 ViewModel 按模式用游标续拉（方案 B：不跨页传列表） */
	private fun dispatchFromSource(
		firstVideo: VideoCardInfo?,
		mode: VideoUiState.Mode,
		keyword: String = "",
		userId: Long = 0L
	) {
		// 无点击项（缺参数/参数丢失）时保留来源模式，仅退化为"从第一页开始拉取"。
		// 不能退化成随机流：否则"我的喜欢/收藏/作品"会静默变成不相关内容，
		// 且 UserVideos 还会丢掉 userId。
		firstVideo?.let { adapter.addFirstVideo(it) }
		viewModel.dispatch(VideoIntent.InitFromSource(mode, firstVideo, keyword, userId))
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitAppend(state.items)
						// 列表为空且不在加载中：展示空态（搜索无结果 / 加载失败），有错误信息时优先展示
						if (state.items.isEmpty() && !state.isLoading) {
							binding.videoEmpty.visibility = View.VISIBLE
							binding.videoEmptyText.text = state.message ?: "暂无相关视频"
						} else {
							binding.videoEmpty.visibility = View.GONE
						}
					}
				}
				launch {
					viewModel.uiEffect.collect {
						when (it) {
							is VideoUiEffect.ShowToast -> Toast.makeText(
								requireContext(),
								it.message,
								Toast.LENGTH_SHORT
							).show()

							is VideoUiEffect.UpdateItem -> {
								adapter.updateVideoList(it.item)
								// 播放地址后到（RequestPlayUrl → 局部重绑）时兜底起播：
								// 当前页正是该条，否则会停在首帧不动
								val position = binding.videoViewPager.currentItem
								if (adapter.itemAt(position)?.aid == it.item.aid) {
									adapter.playAtPosition(position)
								}
							}

							// 未登录触发互动：弹登录引导卡片
							VideoUiEffect.ShowLoginGuide -> LoginGuideDialog.show(requireActivity())
						}
					}
				}
			}
		}
	}

	private var savedPosition: Int = 0

	/** 页面是否处于前台：分页回调只在可见时自动播放（否则 onPause 之后到达的回调会把播放重新拉起） */
	private var foreground = true

	override fun onPause() {
		super.onPause()
		foreground = false
		savedPosition = binding.videoViewPager.currentItem
		adapter.pauseAll()
	}

	override fun onResume() {
		super.onResume()
		foreground = true
		adapter.playAtPosition(savedPosition)
	}

	override fun onStop() {
		super.onStop()
		// 不在此处 release()，否则回到前台时 Engine 已销毁，restore() 无法恢复播放。
		// Engine 在 onDestroyView() 中统一释放。
		adapter.pauseAll()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		adapter.release()
		_binding = null
	}
}
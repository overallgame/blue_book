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
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.feature_video.databinding.VideoPageBinding
import com.example.blue_book.ui.comment.CommentBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class VideoFragment : Fragment() {

	private var _binding: VideoPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: VideoViewModel by viewModels()
	private lateinit var adapter: VideoAdapter

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
			onClickBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
			onClickLike = { video -> viewModel.dispatch(VideoIntent.ToggleLike(video)) },
			onClickCollect = { video -> viewModel.dispatch(VideoIntent.ToggleCollect(video)) },
			onClickComment = { video ->
				CommentBottomSheet.newInstance(video.aid, video.cid)
					.show(parentFragmentManager, CommentBottomSheet.TAG)
			},
			onClickShare = { video -> shareVideo(video) },
			onClickFollow = { _ ->
				// 关注功能待后续实现（需要服务端关注 API）
				Toast.makeText(requireContext(), "关注功能开发中", Toast.LENGTH_SHORT).show()
			},
			onClickFullscreen = {
				Toast.makeText(requireContext(), "全屏播放开发中", Toast.LENGTH_SHORT).show()
			},
			onClickAvatar = { video ->
				// 作者主页功能待后续实现（需要服务端用户主页 API）
				Toast.makeText(requireContext(), "作者主页功能开发中", Toast.LENGTH_SHORT).show()
			},
			onPlayerError = { msg ->
				Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
				adapter.playAtPosition(position)
				currentPosition = position
				if (position == adapter.itemCount - 1) {
					viewModel.dispatch(VideoIntent.LoadMore)
				}
				// 预加载窗口：position+1，position+2；释放 position-2
				adapter.releaseByPosition(position - 2)
				adapter.preloadByPosition(position + 1)
				adapter.preloadByPosition(position + 2)
			}

			override fun onPageScrollStateChanged(state: Int) {
				if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
					adapter.pauseAtPosition(currentPosition)
				}
			}
		})

		observeViewModel()
		initByArgs()
	}

	private fun shareVideo(video: VideoCardInfo) {
		val text = "分享视频：${video.description}（来自 ${video.nickname}）"
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, text)
		}
		try {
			startActivity(Intent.createChooser(intent, "分享到"))
		} catch (e: android.content.ActivityNotFoundException) {
			Toast.makeText(requireContext(), "没有可用的分享应用", Toast.LENGTH_SHORT).show()
		}
	}

	private fun initByArgs() {
		val firstVideo = arguments?.let { args ->
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				args.getParcelable(ExtraKeys.EXTRA_VIDEO, VideoCardInfo::class.java)
			} else {
				@Suppress("DEPRECATION")
				args.getParcelable(ExtraKeys.EXTRA_VIDEO)
			}
		}
		firstVideo?.let { adapter.addFirstVideo(it) }
		when (arguments?.getString(ExtraKeys.EXTRA_TAG)) {
			"search" -> viewModel.dispatch(
				VideoIntent.InitSearch(
					arguments?.getString(ExtraKeys.EXTRA_KEYWORD).orEmpty()
				)
			)

			else -> viewModel.dispatch(VideoIntent.InitRandom)
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { viewModel.uiState.collect { state -> adapter.submitAppend(state.items) } }
				launch {
					viewModel.uiEffect.collect {
						when (it) {
							is VideoUiEffect.ShowToast -> Toast.makeText(
								requireContext(),
								it.message,
								Toast.LENGTH_SHORT
							).show()

							is VideoUiEffect.UpdateItem -> adapter.updateVideoList(it.item)
						}
					}
				}
			}
		}
	}

	private var savedPosition: Int = 0

	override fun onPause() {
		super.onPause()
		savedPosition = binding.videoViewPager.currentItem
		adapter.pauseAll()
	}

	override fun onResume() {
		super.onResume()
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
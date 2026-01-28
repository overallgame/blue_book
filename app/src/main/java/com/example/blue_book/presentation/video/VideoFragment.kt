package com.example.blue_book.presentation.video

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
import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.databinding.VideoPageBinding
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
			onClickLike = { video -> toggleLike(video) },
			onClickCollect = { video -> toggleCollect(video) },
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
				adapter.preloadByPosition(position + 1)
				adapter.preloadByPosition(position + 2)
				adapter.releaseByPosition(position - 2)
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

	private fun initByArgs() {
		val firstVideo = arguments?.let { args ->
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				args.getParcelable("EXTRA_VIDEO", VideoCardInfo::class.java)
			} else {
				@Suppress("DEPRECATION")
				args.getParcelable("EXTRA_VIDEO")
			}
		}
		firstVideo?.let { adapter.addFirstVideo(it) }
		when (arguments?.getString("TAG_SHOW")) {
			"search" -> viewModel.dispatch(
				VideoIntent.InitSearch(
					arguments?.getString("keyword").orEmpty()
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

	private fun toggleLike(video: VideoCardInfo) {
		val newStatus = !video.isLike
		val newLikeNumber = video.like + if (newStatus) +1 else -1
		adapter.updateVideoList(video.copy(isLike = newStatus, like = newLikeNumber))
	}

	private fun toggleCollect(video: VideoCardInfo) {
		val newStatus = !video.isCollect
		val newCollectionNumber = video.collection + if (newStatus) +1 else -1
		adapter.updateVideoList(video.copy(isCollect = newStatus, collection = newCollectionNumber))
	}

	override fun onPause() {
		super.onPause()
		adapter.pauseAll()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		adapter.release()
		_binding = null
	}
}
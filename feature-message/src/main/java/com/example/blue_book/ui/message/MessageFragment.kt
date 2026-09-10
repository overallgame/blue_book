package com.example.blue_book.ui.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.feature_message.databinding.MessagePageBinding
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 消息中心：真实通知列表；点击标记已读并跳转（关注→作者主页，互动→对应视频） */
@AndroidEntryPoint
class MessageFragment : Fragment() {

	private var _binding: MessagePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MessageViewModel by viewModels()
	private lateinit var adapter: MessageAdapter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = MessagePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initRecyclerView()
		initSwipeRefresh()
		observeViewModel()
		viewModel.dispatch(MessageIntent.Init)
	}

	private fun initRecyclerView() {
		adapter = MessageAdapter { item -> handleItemClick(item) }
		binding.messageRecycleView.layoutManager = LinearLayoutManager(requireContext())
		binding.messageRecycleView.adapter = adapter
		// 触底加载更多
		binding.messageRecycleView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				val state = viewModel.uiState.value
				if (!recyclerView.canScrollVertically(1) && !state.isLoading && state.hasMore) {
					viewModel.dispatch(MessageIntent.LoadMore)
				}
			}
		})
	}

	private fun initSwipeRefresh() {
		binding.messageSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(MessageIntent.Refresh)
		}
	}

	/** 点击：标记已读并跳转 */
	private fun handleItemClick(item: MessageItem) {
		viewModel.dispatch(MessageIntent.MarkRead(item.id))
		when (item.type) {
			MessageType.Follow -> {
				if (item.senderId > 0) {
					TheRouter.build(RoutePath.USER_PROFILE)
						.withLong(ExtraKeys.EXTRA_USER_ID, item.senderId)
						.navigation(requireContext())
				}
			}

			MessageType.Like, MessageType.Comment, MessageType.Collect -> {
				val videoId = item.videoId
				if (videoId != null && videoId > 0) {
					openVideo(videoId)
				}
			}

			MessageType.System -> Unit
		}
	}

	/** 按 id 拉取视频卡后进入播放页（首条为该视频） */
	private fun openVideo(videoId: Long) {
		viewLifecycleOwner.lifecycleScope.launch {
			val card = withContext(Dispatchers.IO) {
				TheRouter.get(IVideoProvider::class.java)?.fetchVideoById(videoId)?.getOrNull()
			}
			if (card != null) {
				TheRouter.build(RoutePath.VIDEO)
					.withParcelable(ExtraKeys.EXTRA_VIDEO, card)
					.navigation(requireContext())
			} else {
				Toast.makeText(requireContext(), "视频不存在或已删除", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitList(state.items)
						binding.messageSwipeRefreshLayout.isRefreshing = false
						val hasItems = state.items.isNotEmpty()
						binding.messageRecycleView.visibility = if (hasItems) View.VISIBLE else View.GONE
						binding.messageEmptyLayout.visibility = if (hasItems) View.GONE else View.VISIBLE
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MessageEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
						}
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

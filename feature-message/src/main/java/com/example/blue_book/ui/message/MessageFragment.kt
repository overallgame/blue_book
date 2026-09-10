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
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.provider.IVideoProvider
import com.example.blue_book.router.ExtraKeys
import com.example.blue_book.router.RoutePath
import com.example.blue_book.widget.LoginGuideDialog
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 消息中心：需登录使用；未登录进入弹引导卡片且不加载，登录后自动加载 */
@AndroidEntryPoint
class MessageFragment : Fragment() {

	private var _binding: MessagePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MessageViewModel by viewModels()
	private lateinit var adapter: MessageAdapter

	/** 游客状态（未登录） */
	private var isGuest = false

	/** 是否已按登录态初始化加载 */
	private var initialized = false

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
	}

	override fun onResume() {
		super.onResume()
		// 每次可见：同步登录态；未登录弹引导卡片，登录后首次进入加载
		viewLifecycleOwner.lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: false
			}
			if (!isAdded) return@launch
			isGuest = !logged
			if (logged) {
				if (!initialized) {
					initialized = true
					viewModel.dispatch(MessageIntent.Init)
				} else {
					// 再次可见时刷新首屏，避免新通知必须手动下拉才出现
					viewModel.dispatch(MessageIntent.Refresh)
				}
			} else {
				LoginGuideDialog.show(requireActivity())
			}
		}
	}

	private fun initRecyclerView() {
		adapter = MessageAdapter(
			onClick = { item -> handleItemClick(item) },
			onLongClick = { item -> showItemMenu(item) }
		)
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
			if (isGuest) {
				LoginGuideDialog.show(requireActivity())
				binding.messageSwipeRefreshLayout.isRefreshing = false
				return@setOnRefreshListener
			}
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

	/** 长按：删除单条 / 清空全部 */
	private fun showItemMenu(item: MessageItem) {
		androidx.appcompat.app.AlertDialog.Builder(requireContext())
			.setItems(arrayOf("删除这条通知", "清空全部通知")) { _, which ->
				when (which) {
					0 -> viewModel.dispatch(MessageIntent.Delete(item.id))
					1 -> viewModel.dispatch(MessageIntent.ClearAll)
				}
			}
			.show()
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

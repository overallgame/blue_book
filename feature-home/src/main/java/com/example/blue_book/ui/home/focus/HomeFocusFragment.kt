package com.example.blue_book.ui.home.focus

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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.blue_book.data.VideoCardInfo
import com.example.blue_book.feature_home.databinding.HomeFocusPageBinding
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.ui.home.HomeActivity
import com.example.blue_book.widget.LoginGuideDialog
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关注流：需登录使用。
 * 未登录进入：弹登录引导卡片 + 展示空态；登录后自动加载并刷新。
 */
@AndroidEntryPoint
class HomeFocusFragment : Fragment() {

	private var _binding: HomeFocusPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: HomeFocusViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter
	private var isLoading = false
	private var noMoreToasted = false

	/** 游客状态（未登录） */
	private var isGuest = false

	/** 是否已加载过一次（登录态首次进入时触发） */
	private var initialized = false

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomeFocusPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefresh()
		initRecyclerView()
		initEmptyState()
		observeViewModel()
	}

	override fun onResume() {
		super.onResume()
		// 每次可见：同步登录态；未登录弹引导卡片，登录后首次进入自动加载
		viewLifecycleOwner.lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: false
			}
			if (!isAdded) return@launch
			isGuest = !logged
			if (logged) {
				binding.homeFocusEmpty.visibility = View.GONE
				if (!initialized) {
					initialized = true
					viewModel.dispatch(HomeFocusIntent.Refresh)
				}
			} else {
				binding.homeFocusEmpty.visibility = View.VISIBLE
				LoginGuideDialog.show(requireActivity())
			}
		}
	}

	private fun initSwipeRefresh() {
		binding.mainFocusPagerSwipeRefreshLayout.setOnRefreshListener {
			if (isGuest) {
				LoginGuideDialog.show(requireActivity())
				binding.mainFocusPagerSwipeRefreshLayout.isRefreshing = false
				return@setOnRefreshListener
			}
			noMoreToasted = false
			viewModel.dispatch(HomeFocusIntent.Refresh)
		}
	}

	private fun initEmptyState() {
		binding.homeFocusEmptyLogin.setOnClickListener {
			LoginGuideDialog.show(requireActivity())
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> guardLike(v) },
			onClickItem = { v ->
				(requireActivity() as HomeActivity).navigateToVideoPlayer(v)
			}
		)
		binding.mainFocusRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@HomeFocusFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1)) {
						val state = viewModel.uiState.value
						if (state.hasMore && !state.isLoading) {
							isLoading = true
							viewModel.dispatch(HomeFocusIntent.LoadMore)
						} else if (!state.hasMore && !state.isLoading && state.items.isNotEmpty() && !noMoreToasted) {
							noMoreToasted = true
							Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
						}
					}
				}
			})
		}
	}

	/** 未登录触发点赞：弹登录引导卡片 */
	private fun guardLike(v: VideoCardInfo) {
		if (isGuest) {
			LoginGuideDialog.show(requireActivity())
		} else {
			viewModel.dispatch(HomeFocusIntent.ToggleLike(v))
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitAppend(state.items)
						isLoading = state.isLoading
						binding.mainFocusPagerSwipeRefreshLayout.isRefreshing = false
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is HomeFocusEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							is HomeFocusEffect.UpdateItem -> adapter.updateVideoList(effect.item)
							HomeFocusEffect.ShowLoginGuide -> LoginGuideDialog.show(requireActivity())
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

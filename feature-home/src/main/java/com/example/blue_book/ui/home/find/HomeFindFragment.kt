package com.example.blue_book.ui.home.find

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
import com.example.blue_book.ui.home.HomeActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.blue_book.feature_home.databinding.HomeFindPageBinding
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.widget.LoginGuideDialog
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeFindFragment : Fragment() {

	private var _binding: HomeFindPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: HomeFindViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter

	private var isLoading = false
	private var noMoreToasted = false

	/** 游客状态（未登录）：可浏览视频，点赞等互动需登录 */
	private var isGuest = false

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomeFindPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefreshLayout()
		initRecyclerView()
		observeViewModel()
		viewModel.dispatch(HomeFindIntent.Init)
	}

	override fun onResume() {
		super.onResume()
		// 同步游客状态（用于互动守卫）
		viewLifecycleOwner.lifecycleScope.launch {
			isGuest = !withContext(Dispatchers.IO) {
				TheRouter.get(IAuthProvider::class.java)?.isLoggedIn() ?: true
			}
		}
	}

	/** 未登录触发点赞：弹登录引导卡片 */
	private fun guardLike(v: com.example.blue_book.data.VideoCardInfo) {
		if (isGuest) {
			LoginGuideDialog.show(requireActivity())
		} else {
			viewModel.dispatch(HomeFindIntent.ToggleLike(v))
		}
	}

	private fun initSwipeRefreshLayout() {
		binding.mainFindPagerSwipeRefreshLayout.setOnRefreshListener {
			noMoreToasted = false
			viewModel.dispatch(HomeFindIntent.Refresh)
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> guardLike(v) },
			onClickItem = { v ->
				(requireActivity() as HomeActivity).navigateToVideoPlayer(v)
			}
		)
		binding.mainFindRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@HomeFindFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1)) {
						val state = viewModel.uiState.value
						if (state.hasMore && !state.isLoading) {
							isLoading = true
							viewModel.dispatch(HomeFindIntent.LoadMore)
						} else if (!state.hasMore && !state.isLoading && state.items.isNotEmpty() && !noMoreToasted) {
							noMoreToasted = true
							Toast.makeText(requireContext(), "没有更多了", Toast.LENGTH_SHORT).show()
						}
					}
				}
			})
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.uiState.collect { state ->
						adapter.submitAppend(state.items)
						isLoading = state.isLoading
						binding.mainFindPagerSwipeRefreshLayout.isRefreshing = false
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is HomeFindEffect.ShowToast -> Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
							is HomeFindEffect.UpdateItem -> adapter.updateVideoList(effect.item)
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
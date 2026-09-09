package com.example.blue_book.ui.mine.page

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
import com.example.blue_book.feature_mine.databinding.MineCollectionPageBinding
import com.example.blue_book.ui.mine.MineActivity
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MineCollectionFragment : Fragment() {

	private var _binding: MineCollectionPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MineCollectionViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter
	private var isLoading = false

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = MineCollectionPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefresh()
		initRecyclerView()
		initEmptyState()
		observeViewModel()
		viewModel.dispatch(MineCollectionIntent.Init)
	}

	private var firstResume = true

	/** 从播放页返回时刷新（首帧由 Init 承担，跳过避免双请求） */
	/** 空态重试：重新触发下拉刷新逻辑 */
	private fun initEmptyState() {
		binding.mineCollectionEmptyRetry.setOnClickListener {
			viewModel.dispatch(MineCollectionIntent.Refresh)
		}
	}

	override fun onResume() {
		super.onResume()
		if (firstResume) {
			firstResume = false
		} else {
			viewModel.dispatch(MineCollectionIntent.Refresh)
		}
	}

	private fun initSwipeRefresh() {
		binding.mineCollectionPagerSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(MineCollectionIntent.Refresh)
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> viewModel.dispatch(MineCollectionIntent.ToggleCollect(v)) },
			onClickItem = { v ->
				(requireActivity() as MineActivity).navigateToVideoPlayer(v, source = "collected")
			}
		)
		binding.mineCollectionRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@MineCollectionFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1) && !isLoading) {
						isLoading = true
						viewModel.dispatch(MineCollectionIntent.LoadMore)
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
						binding.mineCollectionPagerSwipeRefreshLayout.isRefreshing = false
						// 空态：无数据且不在加载中（加载失败/无内容）时展示，优先显示错误信息
						if (state.items.isEmpty() && !state.isLoading) {
							binding.mineCollectionEmpty.visibility = View.VISIBLE
							binding.mineCollectionEmptyText.text = state.message ?: "暂无内容"
						} else {
							binding.mineCollectionEmpty.visibility = View.GONE
						}
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MineCollectionEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							is MineCollectionEffect.UpdateItem -> adapter.updateVideoList(effect.item)
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

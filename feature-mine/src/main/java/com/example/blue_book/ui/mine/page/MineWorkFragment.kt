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
import com.example.blue_book.feature_mine.databinding.MineWorkPageBinding
import com.example.blue_book.router.openVideoPlayer
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MineWorkFragment : Fragment() {

	private var _binding: MineWorkPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MineWorkViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter
	private var isLoading = false
	private var noMoreToasted = false

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = MineWorkPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefresh()
		initRecyclerView()
		initEmptyState()
		observeViewModel()
		viewModel.dispatch(MineWorkIntent.Init)
	}

	private var firstResume = true

	/** 从播放页返回时刷新（首帧由 Init 承担，跳过避免双请求） */
	/** 空态重试：重新触发下拉刷新逻辑 */
	private fun initEmptyState() {
		binding.mineWorkEmptyRetry.setOnClickListener {
			viewModel.dispatch(MineWorkIntent.Refresh)
		}
	}

	override fun onResume() {
		super.onResume()
		if (firstResume) {
			firstResume = false
		} else {
			viewModel.dispatch(MineWorkIntent.Refresh)
		}
	}

	private fun initSwipeRefresh() {
		binding.mineWorkPagerSwipeRefreshLayout.setOnRefreshListener {
			noMoreToasted = false
			viewModel.dispatch(MineWorkIntent.Refresh)
		}
	}

	/** 长按作品 → 删除确认（删除后服务端软删并清理点赞/收藏记录） */
	private fun confirmDelete(v: com.example.blue_book.data.VideoCardInfo) {
		androidx.appcompat.app.AlertDialog.Builder(requireContext())
			.setTitle("删除作品")
			.setMessage("确定删除这条作品吗？删除后不可恢复。")
			.setPositiveButton("删除") { _, _ ->
				viewModel.dispatch(MineWorkIntent.DeleteItem(v))
			}
			.setNegativeButton("取消", null)
			.show()
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> viewModel.dispatch(MineWorkIntent.ToggleLike(v)) },
			onClickItem = { v ->
				openVideoPlayer(
					requireContext(),
					v,
					source = "user_videos",
					userId = viewModel.uiState.value.items.firstOrNull()?.uploaderId ?: v.uploaderId
				)
			},
			onLongClickItem = { v -> confirmDelete(v) }
		)
		binding.mineWorkRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@MineWorkFragment.adapter
				addOnScrollListener(object : RecyclerView.OnScrollListener() {
					override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
						super.onScrolled(recyclerView, dx, dy)
						if (!recyclerView.canScrollVertically(1) && !isLoading) {
							val state = viewModel.uiState.value
							if (state.hasMore) {
								isLoading = true
								viewModel.dispatch(MineWorkIntent.LoadMore)
							} else if (state.items.isNotEmpty() && !noMoreToasted) {
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
						binding.mineWorkPagerSwipeRefreshLayout.isRefreshing = false
						// 空态：无数据且不在加载中（加载失败/无内容）时展示，优先显示错误信息
						if (state.items.isEmpty() && !state.isLoading) {
							binding.mineWorkEmpty.visibility = View.VISIBLE
							binding.mineWorkEmptyText.text = state.message ?: "暂无内容"
						} else {
							binding.mineWorkEmpty.visibility = View.GONE
						}
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MineWorkEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							is MineWorkEffect.UpdateItem -> adapter.updateVideoList(effect.item)
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

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
import com.example.blue_book.feature_mine.databinding.MineLovePageBinding
import com.example.blue_book.ui.mine.MineActivity
import com.example.blue_book.widget.PreVideoAdapter
import com.example.blue_book.widget.SpaceItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MineLoveFragment : Fragment() {

	private var _binding: MineLovePageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: MineLoveViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter
	private var isLoading = false

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = MineLovePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initSwipeRefresh()
		initRecyclerView()
		observeViewModel()
		viewModel.dispatch(MineLoveIntent.Init)
	}

	private fun initSwipeRefresh() {
		binding.mineLovePagerSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(MineLoveIntent.Refresh)
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> viewModel.dispatch(MineLoveIntent.ToggleLike(v)) },
			onClickItem = { v ->
				(requireActivity() as MineActivity).navigateToVideoPlayer(v)
			}
		)
		binding.mineLoveRecycleView.run {
			layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
				gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
			}
			addItemDecoration(SpaceItem(8))
			adapter = this@MineLoveFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1) && !isLoading) {
						isLoading = true
						viewModel.dispatch(MineLoveIntent.LoadMore)
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
						binding.mineLovePagerSwipeRefreshLayout.isRefreshing = false
					}
				}
				launch {
					viewModel.uiEffect.collect { effect ->
						when (effect) {
							is MineLoveEffect.ShowToast -> Toast.makeText(
								requireContext(), effect.message, Toast.LENGTH_SHORT
							).show()
							is MineLoveEffect.UpdateItem -> adapter.updateVideoList(effect.item)
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

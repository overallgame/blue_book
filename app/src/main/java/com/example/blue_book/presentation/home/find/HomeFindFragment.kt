package com.example.blue_book.presentation.home.find

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.R
import com.example.blue_book.databinding.HomeFindPageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFindFragment : Fragment() {

	private var _binding: HomeFindPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: HomeFindViewModel by viewModels()
	private lateinit var adapter: PreVideoAdapter

	private var isLoading = false

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

	private fun initSwipeRefreshLayout() {
		binding.mainFindPagerSwipeRefreshLayout.setOnRefreshListener {
			viewModel.dispatch(HomeFindIntent.Refresh)
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { v -> viewModel.dispatch(HomeFindIntent.ToggleLike(v)) },
			onClickItem = { v ->
				val args = Bundle().apply { putParcelable("EXTRA_VIDEO", v) }
				findNavController().navigate(R.id.videoFragment, args)
			}
		)
		binding.mainFindRecycleView.run {
			layoutManager = GridLayoutManager(requireContext(), 2)
			addItemDecoration(SpaceItem(16))
			adapter = this@HomeFindFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1) && !isLoading) {
						isLoading = true
						viewModel.dispatch(HomeFindIntent.LoadMore)
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
package com.example.blue_book.presentation.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blue_book.R
import com.example.blue_book.common.bean.VideoCardInfo
import com.example.blue_book.databinding.SearchResultPageBinding
import com.example.blue_book.presentation.home.find.PreVideoAdapter
import com.example.blue_book.presentation.home.find.SpaceItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AfterSearchFragment : Fragment() {

    private var _binding: SearchResultPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchResultViewModel by viewModels()

    private var isLoading = false
    private var keyword: String = ""

	private lateinit var adapter: PreVideoAdapter

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = SearchResultPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		keyword = arguments?.getString("keyword").orEmpty()
		initToolbar()
		initRecyclerView()
		observeViewModel()
		viewModel.dispatch(SearchIntent.Init(keyword))
	}

	private fun initToolbar() {
		binding.afterSearchBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
		binding.afterSearchComment.setText(keyword)
		binding.afterSearchSearch.setOnClickListener {
			adapterSubmitClear()
			keyword = binding.afterSearchComment.text?.toString().orEmpty()
			viewModel.dispatch(SearchIntent.Init(keyword))
		}
	}

	private fun initRecyclerView() {
		adapter = PreVideoAdapter(
			onClickLike = { video -> toggleLike(video) },
			onClickItem = { v ->
				val args = Bundle().apply {
					putParcelable("EXTRA_VIDEO", v)
					putString("TAG_SHOW", "search")
					putString("keyword", keyword)
				}
				findNavController().navigate(R.id.videoFragment, args)
			}
		)
		binding.afterSearchRecycleView.run {
			layoutManager = GridLayoutManager(requireContext(), 2)
			addItemDecoration(SpaceItem(2))
			adapter = this@AfterSearchFragment.adapter
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					super.onScrolled(recyclerView, dx, dy)
					if (!recyclerView.canScrollVertically(1) && !isLoading) {
						isLoading = true
						viewModel.dispatch(SearchIntent.LoadMore)
					}
				}
			})
		}
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { viewModel.uiState.collect { state ->
					adapter.submitAppend(state.items)
					isLoading = state.isLoading
				} }
				launch { viewModel.uiEffect.collect { effect ->
					when (effect) {
						is SearchUiEffect.UpdateItem -> adapter.updateVideoList(effect.item)
						is SearchUiEffect.ShowToast -> { /* 可按需提示 */ }
					}
				} }
			}
		}
	}

	private fun toggleLike(video: VideoCardInfo) {
		val newStatus = !video.isLike
		val newLikeNumber = video.like + if (newStatus) +1 else -1
		val newVideo = video.copy(isLike = newStatus, like = newLikeNumber)
		adapter.updateVideoList(newVideo)
		viewModel.dispatch(SearchIntent.ToggleLike(newVideo))
	}

	private fun adapterSubmitClear() {
		// 简单清理：重建适配器以清空数据，避免引入额外方法
		adapter = PreVideoAdapter(
			onClickLike = { v -> toggleLike(v) },
			onClickItem = { v ->
				val args = Bundle().apply {
					putParcelable("EXTRA_VIDEO", v)
					putString("TAG_SHOW", "search")
					putString("keyword", keyword)
				}
				findNavController().navigate(R.id.videoFragment, args)
			}
		)
		binding.afterSearchRecycleView.adapter = adapter
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



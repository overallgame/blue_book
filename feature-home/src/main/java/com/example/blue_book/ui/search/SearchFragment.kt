package com.example.blue_book.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.data.SearchHistoryStore
import com.example.blue_book.data.remote.SearchRemoteDataSource
import com.example.blue_book.feature_home.R
import com.example.blue_book.host.mainHost
import com.example.blue_book.feature_home.databinding.SearchPageBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.core.widget.doAfterTextChanged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {

	private var _binding: SearchPageBinding? = null
	private val binding get() = _binding!!

	@Inject
	lateinit var historyStore: SearchHistoryStore

	@Inject
	lateinit var searchRemote: SearchRemoteDataSource

	/** 输入联想防抖任务 */
	private var suggestJob: Job? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = SearchPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initWindowInsets()
		binding.searchToolbar.setNavigationOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
		binding.searchSearch.setOnClickListener {
			val keyword = binding.searchComment.text?.toString().orEmpty().trim()
			if (keyword.isBlank()) {
				Toast.makeText(requireContext(), "请输入搜索内容", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}
			performSearch(keyword)
		}
		binding.searchHistoryClear.setOnClickListener {
			viewLifecycleOwner.lifecycleScope.launch {
				historyStore.clear()
				renderHistory()
			}
		}
		renderStaticGroups()
		bindSuggestInput()
		viewLifecycleOwner.lifecycleScope.launch {
			renderHistory()
		}
	}

	/**
	 * 工具栏避让状态栏。本页是 Tab 容器内的二级页，宿主为全面屏
	 * （内容延展到系统栏后方），不自己避让会被状态栏压住。
	 * 底部无需处理：底部导航栏常驻在内容区下方。
	 */
	private fun initWindowInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(binding.searchToolbar) { v, insets ->
			v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
			insets
		}
	}

	/** 猜你想搜：输入变化时防抖拉取匹配联想（与初始化同源，空输入回热门推荐） */
	private fun bindSuggestInput() {
		binding.searchComment.doAfterTextChanged { editable ->
			val keyword = editable?.toString().orEmpty().trim()
			suggestJob?.cancel()
			suggestJob = viewLifecycleOwner.lifecycleScope.launch {
				delay(300)
				val terms = withContext(Dispatchers.IO) {
					searchRemote.suggest(keyword.ifBlank { null }).getOrNull()
				}
				if (!isAdded) return@launch
				val fallback = if (keyword.isBlank()) SUGGESTED else emptyList()
				renderSuggested(terms?.filter { it.isNotBlank() }.orEmpty().ifEmpty { fallback })
			}
		}
	}

	/** 发起搜索：写入历史并跳转结果页 */
	private fun performSearch(keyword: String) {
		viewLifecycleOwner.lifecycleScope.launch {
			historyStore.add(keyword)
			mainHost?.navigateToSearchResult(keyword)
		}
	}

	private suspend fun renderHistory() {
		val history = historyStore.get()
		val group = binding.searchHistoryGroup
		group.removeAllViews()
		if (history.isEmpty()) {
			binding.searchHistorySection.visibility = View.GONE
			return
		}
		binding.searchHistorySection.visibility = View.VISIBLE
		history.forEach { term -> group.addChip(term) { performSearch(term) } }
	}

	private fun renderStaticGroups() {
		// 猜你想搜优先后端推荐，失败/为空时回退静态词
		viewLifecycleOwner.lifecycleScope.launch {
			val suggested = withContext(Dispatchers.IO) { searchRemote.suggest(null).getOrNull() }
				?.filter { it.isNotBlank() }
				.orEmpty()
			if (!isAdded) return@launch
			renderSuggested(suggested.ifEmpty { SUGGESTED })
		}
		// 热搜优先用后端数据，失败/为空时回退静态词
		viewLifecycleOwner.lifecycleScope.launch {
			val hot = withContext(Dispatchers.IO) { searchRemote.hotSearches().getOrNull() }
				?.filter { it.isNotBlank() }
				.orEmpty()
			if (!isAdded) return@launch
			val terms = hot.ifEmpty { HOT }
			binding.searchHotGroup.removeAllViews()
			terms.take(10).forEach { term -> binding.searchHotGroup.addChip(term) { performSearch(term) } }
		}
	}

	private fun renderSuggested(terms: List<String>) {
		binding.searchSuggestedGroup.removeAllViews()
		terms.take(10).forEach { term -> binding.searchSuggestedGroup.addChip(term) { performSearch(term) } }
	}

	private fun ChipGroup.addChip(text: String, onClick: () -> Unit) {
		val chip = Chip(requireContext()).apply {
			this.text = text
			chipBackgroundColor = android.content.res.ColorStateList.valueOf(
				requireContext().getColor(R.color.md_theme_surfaceContainerLow)
			)
			setTextColor(requireContext().getColor(R.color.md_theme_onSurfaceVariant))
			chipStrokeWidth = 0f
			textSize = 13f
		}
		chip.setOnClickListener { onClick() }
		addView(chip)
	}

	override fun onDestroyView() {
		suggestJob?.cancel()
		super.onDestroyView()
		_binding = null
	}

	private companion object {
		/** 暂用静态推荐词，待后端热搜/推荐接口后替换 */
		val SUGGESTED = listOf("美食探店", "旅行攻略", "穿搭", "健身", "宠物日常", "摄影", "数码测评")
		val HOT = listOf("热门视频", "日常", "风景", "美食", "生活记录")
	}
}

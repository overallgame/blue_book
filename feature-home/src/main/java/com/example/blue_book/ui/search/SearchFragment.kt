package com.example.blue_book.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.data.SearchHistoryStore
import com.example.blue_book.feature_home.R
import com.example.blue_book.ui.home.HomeActivity
import com.example.blue_book.feature_home.databinding.SearchPageBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {

	private var _binding: SearchPageBinding? = null
	private val binding get() = _binding!!

	@Inject
	lateinit var historyStore: SearchHistoryStore

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = SearchPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
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
		viewLifecycleOwner.lifecycleScope.launch {
			renderHistory()
		}
	}

	/** 发起搜索：写入历史并跳转结果页 */
	private fun performSearch(keyword: String) {
		viewLifecycleOwner.lifecycleScope.launch {
			historyStore.add(keyword)
			(requireActivity() as HomeActivity).navigateToSearchResult(keyword)
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
		SUGGESTED.forEach { term -> binding.searchSuggestedGroup.addChip(term) { performSearch(term) } }
		HOT.forEach { term -> binding.searchHotGroup.addChip(term) { performSearch(term) } }
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
		super.onDestroyView()
		_binding = null
	}

	private companion object {
		/** 暂用静态推荐词，待后端热搜/推荐接口后替换 */
		val SUGGESTED = listOf("美食探店", "旅行攻略", "穿搭", "健身", "宠物日常", "摄影", "数码测评")
		val HOT = listOf("热门视频", "日常", "风景", "美食", "生活记录")
	}
}

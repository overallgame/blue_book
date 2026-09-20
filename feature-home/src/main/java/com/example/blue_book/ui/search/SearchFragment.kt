package com.example.blue_book.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blue_book.data.SearchHistoryStore
import com.example.blue_book.data.remote.SearchRemoteDataSource
import com.example.blue_book.feature_home.R
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
		binding.searchToolbar.setNavigationOnClickListener {
			// 本页是 SearchActivity 的第一层，没有可弹的返回栈：交给返回键逻辑（结束本页回到来源）
			requireActivity().onBackPressedDispatcher.onBackPressed()
		}
		binding.searchSearch.setOnClickListener { submitSearch() }
		// 软键盘右下角的「搜索」键（SearchInputStyle 里的 imeOptions=actionSearch）
		// 与点「搜索」按钮等价——不接的话那个键按下去没反应
		binding.searchComment.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				submitSearch()
				true
			} else {
				false
			}
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
	 * 工具栏避让状态栏、根布局避让系统手势条。
	 *
	 * 本页在 SearchActivity 内，Activity 是全屏延展的、下方没有底部导航，
	 * 所以底部内边距要自己加（加在根布局的 padding 上，底色仍延展到屏幕边缘）。
	 *
	 * 顶部内边距加在 **AppBarLayout** 而不是 MaterialToolbar 上：加在工具栏自身时，
	 * 它内部给返回键做垂直居中用的是含内边距的高度，返回箭头会比输入框低 4dp（实测）。
	 */
	private fun initWindowInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(binding.searchAppbar) { v, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.updatePadding(top = bars.top)
			// 底部让开系统手势条；加在根布局的 padding 上，底色仍延展到屏幕边缘
			binding.root.updatePadding(bottom = bars.bottom)
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

	/** 「搜索」按钮与软键盘搜索键共用：空输入只提示，不发起搜索 */
	private fun submitSearch() {
		val keyword = binding.searchComment.text?.toString().orEmpty().trim()
		if (keyword.isBlank()) {
			Toast.makeText(requireContext(), "请输入搜索内容", Toast.LENGTH_SHORT).show()
			return
		}
		performSearch(keyword)
	}

	/**
	 * 发起搜索：**先跳结果页，再异步写历史**。
	 *
	 * 顺序不能反：`historyStore.add` 走 DataStore，是真挂起点（IO 派发）。
	 * 若先写历史再跳转，挂起期间用户按 Home/锁屏会让 FragmentManager 进入已保存状态，
	 * 恢复执行时提交事务就会抛 `Can not perform this action after onSaveInstanceState`。
	 * 先跳转则点击当帧就完成提交；写历史放后台，用 Activity 作用域（视图已被替换掉，
	 * 用 viewLifecycleOwner 会被取消），失败也不影响已经发起的搜索。
	 */
	private fun performSearch(keyword: String) {
		(requireActivity() as SearchActivity).navigateToSearchResult(keyword)
		requireActivity().lifecycleScope.launch { historyStore.add(keyword) }
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
			// 跟随主题的色板统一在 lib-base（本模块的 R 里没有这些 id，库模块的 R 只含自己的资源），
			// 故显式写 lib_base.R —— 与 VideoAdapter 取 text_on_dark_* 是同一写法
			chipBackgroundColor = android.content.res.ColorStateList.valueOf(
				requireContext().getColor(com.example.blue_book.lib_base.R.color.md_theme_surfaceContainerLow)
			)
			setTextColor(
				requireContext().getColor(com.example.blue_book.lib_base.R.color.md_theme_onSurfaceVariant)
			)
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

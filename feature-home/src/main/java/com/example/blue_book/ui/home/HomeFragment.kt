package com.example.blue_book.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.blue_book.feature_home.R
import com.example.blue_book.feature_home.databinding.HomePageBinding
import com.example.blue_book.provider.IAuthProvider
import com.example.blue_book.router.RoutePath
import com.example.blue_book.ui.home.find.HomeFindFragment
import com.example.blue_book.ui.home.focus.HomeFocusFragment
import com.example.blue_book.ui.home.local.HomeLocalFragment
import com.example.blue_book.widget.LoginGuideDialog
import com.therouter.TheRouter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
	@Inject
	lateinit var authProvider: IAuthProvider


	private var _binding: HomePageBinding? = null
	private val binding get() = _binding!!

	private lateinit var fragments: List<Fragment>

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomePageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initWindowInsets()
		initNavigationView()
		initViewPager()
		initRadioGroup()
		guideLoginIfNeeded()
	}

	/**
	 * 顶栏（关注/发现/本地 切换条）避让状态栏。
	 * 宿主是全面屏（内容延展到系统栏后方），本页原本依赖非全面屏窗口的自动避让，
	 * 合并到 Tab 宿主后需要自己加这一段。
	 */
	private fun initWindowInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(binding.mainPagerNavRadioGroup) { v, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.updatePadding(top = bars.top)
			insets
		}
	}

	/** 进入 App 首页时未登录则弹登录引导卡片（进程内仅一次；此层弹出可确保在首页之上可见） */
	private fun guideLoginIfNeeded() {
		viewLifecycleOwner.lifecycleScope.launch {
			val logged = withContext(Dispatchers.IO) {
				authProvider.isLoggedIn()
			}
			if (!logged && isAdded) {
				LoginGuideDialog.showIfNeeded(requireActivity())
			}
		}
	}

	private fun initNavigationView() {
		binding.mainPagerNavigationView.setNavigationItemSelectedListener { menuItem ->
			when (menuItem.itemId) {
				// id 定义在 lib-base 的 menu/drawer_menu.xml（本模块的菜单文件已删除），
				// 库模块的 R 只含本模块资源，故显式写 lib_base.R
				com.example.blue_book.lib_base.R.id.menu_backLogin -> {
					// 简化为直接回到登录入口
					TheRouter.build(RoutePath.AUTH).navigation(requireContext())
					true
				}
				else -> {
					binding.layoutMain.closeDrawers()
					true
				}
			}
		}
	}

	private fun initViewPager() {
		binding.mainPagerViewPager.run {
			fragments = listOf(HomeFocusFragment(), HomeFindFragment(), HomeLocalFragment())
			adapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
				override fun getItemCount(): Int = fragments.size
				override fun createFragment(position: Int): Fragment = fragments[position]
			}
			isUserInputEnabled = true
			currentItem = 1
			registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
				override fun onPageSelected(position: Int) {
					super.onPageSelected(position)
					when (position) {
						0 -> binding.mainPagerNavRadioGroup.check(binding.mainPagerFocus.id)
						1 -> binding.mainPagerNavRadioGroup.check(binding.mainPagerFind.id)
						2 -> binding.mainPagerNavRadioGroup.check(binding.mainPagerRegion.id)
					}
				}
			})
		}
	}

	private fun initRadioGroup() {
		binding.mainPagerNavRadioGroup.setOnCheckedChangeListener { _, checkId ->
			when (checkId) {
				binding.mainPagerFocus.id -> binding.mainPagerViewPager.currentItem = 0
				binding.mainPagerFind.id -> binding.mainPagerViewPager.currentItem = 1
				binding.mainPagerRegion.id -> binding.mainPagerViewPager.currentItem = 2
			}
		}
		binding.mainPagerNavButton.setOnClickListener {
			binding.layoutMain.openDrawer(GravityCompat.START)
		}
		binding.mainPagerSearch.setOnClickListener {
			TheRouter.build(RoutePath.SEARCH).navigation(requireContext())
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
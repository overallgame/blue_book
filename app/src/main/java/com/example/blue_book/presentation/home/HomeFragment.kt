package com.example.blue_book.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.blue_book.R
import com.example.blue_book.databinding.HomePageBinding
import com.example.blue_book.presentation.home.find.HomeFindFragment
import com.example.blue_book.presentation.home.focus.HomeFocusFragment
import com.example.blue_book.presentation.home.local.HomeLocalFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

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
		initNavigationView()
		initViewPager()
		initRadioGroup()
	}

	private fun initNavigationView() {
		binding.mainPagerNavigationView.setNavigationItemSelectedListener { menuItem ->
			when (menuItem.itemId) {
				R.id.menu_backLogin -> {
					// 简化为直接回到登录入口
					findNavController().navigate(R.id.authEntryFragment)
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
			findNavController().navigate(R.id.searchFragment)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
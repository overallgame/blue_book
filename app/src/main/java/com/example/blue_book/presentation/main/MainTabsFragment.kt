package com.example.blue_book.presentation.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.blue_book.R
import com.example.blue_book.databinding.ActivityMainBinding
import com.example.blue_book.presentation.home.HomeFragment
import com.example.blue_book.presentation.mine.MineFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainTabsFragment : Fragment(R.layout.activity_main) {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            when (position) {
                0 -> binding.mainNavRadioGroup.check(R.id.main)
                1 -> binding.mainNavRadioGroup.check(R.id.picture)
                2 -> binding.mainNavRadioGroup.check(R.id.message)
                3 -> binding.mainNavRadioGroup.check(R.id.mine)
            }
        }
    }

    private lateinit var fragments: List<Fragment>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = ActivityMainBinding.bind(view)
        val viewPager = binding.mainViewPager
        val radioGroup = binding.mainNavRadioGroup

        fragments = listOf(
            HomeFragment(),
            PicturePlaceholderFragment(),
            MessagePlaceholderFragment(),
            MineFragment()
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(pageChangeCallback)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val targetIndex = when (checkedId) {
                R.id.main -> 0
                R.id.picture -> 1
                R.id.message -> 2
                R.id.mine -> 3
                else -> 0
            }
            if (viewPager.currentItem != targetIndex) {
                viewPager.currentItem = targetIndex
            }
        }

        if (savedInstanceState == null) {
            radioGroup.check(R.id.main)
        }
    }

    override fun onDestroyView() {
        binding.mainViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        _binding = null
        super.onDestroyView()
    }
}

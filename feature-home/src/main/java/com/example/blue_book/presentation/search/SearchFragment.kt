package com.example.blue_book.presentation.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.blue_book.presentation.home.HomeActivity
import com.example.blue_book.feature_home.R
import com.example.blue_book.feature_home.databinding.SearchPageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {

	private var _binding: SearchPageBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = SearchPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.searchBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
		binding.searchSearch.setOnClickListener {
			val keyword = binding.searchComment.text?.toString().orEmpty()
			val args = Bundle().apply { putString("keyword", keyword) }
			(requireActivity() as HomeActivity).navigateToSearchResult(keyword)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
package com.example.blue_book.presentation.home.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.blue_book.databinding.HomeLocalPageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeLocalFragment : Fragment() {

	private var _binding: HomeLocalPageBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomeLocalPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



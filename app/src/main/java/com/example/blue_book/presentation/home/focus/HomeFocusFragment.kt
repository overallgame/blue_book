package com.example.blue_book.presentation.home.focus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.blue_book.databinding.HomeFoucesPageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFocusFragment : Fragment() {

	private var _binding: HomeFoucesPageBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = HomeFoucesPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



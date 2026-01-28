package com.example.blue_book.presentation.mine.page

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.blue_book.databinding.MineWorkPageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MineWorkFragment : Fragment() {

	private var _binding: MineWorkPageBinding? = null
	private val binding get() = _binding!!

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = MineWorkPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



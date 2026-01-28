package com.example.blue_book.presentation.auth.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blue_book.databinding.OpPageBinding
import com.example.blue_book.domain.usecase.IsLoggedInUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthEntryFragment : Fragment() {

	private var _binding: OpPageBinding? = null
	private val binding get() = _binding!!

	@Inject
	lateinit var isLoggedInUseCase: IsLoggedInUseCase

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = OpPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		checkLoginStatus()
		setupListeners()
	}

	private fun checkLoginStatus() {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				if (isLoggedInUseCase()) {
					findNavController().navigate(com.example.blue_book.R.id.mainTabsFragment)
				}
			} catch (_: Throwable) { }
		}
	}

	private fun setupListeners() {
		binding.opLogin.setOnClickListener {
			findNavController().navigate(com.example.blue_book.R.id.action_authEntry_to_login)
		}
		binding.opRegister.setOnClickListener {
			findNavController().navigate(com.example.blue_book.R.id.action_authEntry_to_register)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



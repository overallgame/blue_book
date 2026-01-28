package com.example.blue_book.presentation.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.blue_book.R
import com.example.blue_book.databinding.LoginPageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

	private var _binding: LoginPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: LoginViewModel by viewModels()
    private var lastMessage: String? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = LoginPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setupListeners()
		observeViewModel()
	}

	private fun setupListeners() {
		binding.logToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
		binding.logPhone.doAfterTextChanged { viewModel.dispatch(LoginIntent.PhoneChanged(it?.toString().orEmpty())) }
		binding.logPassword.doAfterTextChanged { viewModel.dispatch(LoginIntent.PasswordChanged(it?.toString().orEmpty())) }
		binding.logButton.setOnClickListener { viewModel.dispatch(LoginIntent.Submit) }
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { viewModel.uiState.collect { renderState(it) } }
				launch { viewModel.uiEffect.collect { handleEffect(it) } }
			}
		}
	}

	private fun renderState(state: LoginUiState) {
		if (binding.logPhone.text.toString() != state.phone) {
			binding.logPhone.setText(state.phone)
			binding.logPhone.setSelection(state.phone.length)
		}
		if (binding.logPassword.text.toString() != state.password) {
			binding.logPassword.setText(state.password)
			binding.logPassword.setSelection(state.password.length)
		}
		binding.logButton.isEnabled = state.isLoginEnabled && !state.isLoading
		binding.logButton.text = if (state.isLoading) "登录中..." else "登录"

		state.message?.takeIf { it.isNotBlank() && it != lastMessage }?.let {
			Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
			lastMessage = it
		}
	}

	private fun handleEffect(effect: LoginUiEffect) {
		when (effect) {
			LoginUiEffect.NavigateHome -> findNavController().navigate(R.id.action_login_to_home)
			is LoginUiEffect.ShowToast -> {
				if (effect.message.isNotBlank()) {
					Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
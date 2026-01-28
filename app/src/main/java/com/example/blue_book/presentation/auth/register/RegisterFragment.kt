package com.example.blue_book.presentation.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.blue_book.R
import com.example.blue_book.databinding.RegisterPageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

	private var _binding: RegisterPageBinding? = null
	private val binding get() = _binding!!
	private val viewModel: RegisterViewModel by viewModels()
    private var lastMessage: String? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = RegisterPageBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setupListeners()
		observeViewModel()
	}

	private fun setupListeners() {
		binding.resToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
		binding.resNickname.doAfterTextChanged { viewModel.dispatch(RegisterIntent.NicknameChanged(it?.toString().orEmpty())) }
		binding.resPhone.doAfterTextChanged { viewModel.dispatch(RegisterIntent.PhoneChanged(it?.toString().orEmpty())) }
		binding.resPassword.doAfterTextChanged { viewModel.dispatch(RegisterIntent.PasswordChanged(it?.toString().orEmpty())) }
		binding.resPassword1.doAfterTextChanged { viewModel.dispatch(RegisterIntent.ConfirmPasswordChanged(it?.toString().orEmpty())) }
		binding.resVerificationCode.doAfterTextChanged { viewModel.dispatch(RegisterIntent.VerificationCodeChanged(it?.toString().orEmpty())) }
		binding.resRequestVerificationCodeButton.setOnClickListener { viewModel.dispatch(RegisterIntent.RequestVerificationCode) }
		binding.resButton.setOnClickListener { viewModel.dispatch(RegisterIntent.Submit) }
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { viewModel.uiState.collect { renderState(it) } }
				launch { viewModel.uiEffect.collect { handleEffect(it) } }
			}
		}
	}

	private fun renderState(state: RegisterUiState) {
		update(binding.resNickname, state.nickname)
		update(binding.resPhone, state.phone)
		update(binding.resPassword, state.password)
		update(binding.resPassword1, state.confirmPassword)
		update(binding.resVerificationCode, state.verificationCode)
		binding.resRequestVerificationCodeButton.isEnabled = state.canRequestCode
		binding.resRequestVerificationCodeButton.text = if (state.countdownSeconds > 0) "重新发送(${state.countdownSeconds})" else "获取验证码"
		binding.resButton.isEnabled = state.canSubmit && !state.isLoading
        state.message?.takeIf { it.isNotBlank() && it != lastMessage }?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            lastMessage = it
        }
		binding.resButton.text = if (state.isLoading) "注册中..." else "注册"
	}

	private fun handleEffect(effect: RegisterUiEffect) {
		when (effect) {
			RegisterUiEffect.NavigateHome -> findNavController().navigate(R.id.action_register_to_home)
			is RegisterUiEffect.ShowToast -> {
				if (effect.message.isNotBlank()) {
					Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun update(editText: EditText, value: String) {
		if (editText.text.toString() != value) {
			editText.setText(value)
			editText.setSelection(value.length)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}



package com.example.blue_book.auth.ui.login

import com.example.blue_book.auth.domain.usecase.LoginUseCase
import com.example.blue_book.udf.UdfViewModel
import com.example.blue_book.auth.domain.model.LoginCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
	private val loginUseCase: LoginUseCase
) : UdfViewModel<LoginIntent, LoginUiState, LoginUiEffect>(LoginUiState()) {

	override suspend fun handleIntent(intent: LoginIntent) {
		when (intent) {
			is LoginIntent.PhoneChanged -> setState {
				copy(phone = intent.value, message = null)
			}

			is LoginIntent.PasswordChanged -> setState {
				copy(password = intent.value, message = null)
			}

			LoginIntent.Submit -> submitLogin()
		}
	}

	private suspend fun submitLogin() {
		val currentState = uiState.value
		if (!currentState.isLoginEnabled || currentState.isLoading) {
			return
		}
		// 前端预校验（与注册页一致）：手机号 11 位
		if (currentState.phone.length != 11) {
			setState { copy(message = "请输入正确的手机号") }
			return
		}

		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { loginUseCase(LoginCredentials(currentState.phone, currentState.password)) },
			onSuccess = {
				setState { copy(isLoading = false, password = "") }
				sendEffect(LoginUiEffect.NavigateHome)
			},
			onFailure = { throwable ->
				val errorMessage = throwable.message ?: "登录失败，请稍后重试"
				setState { copy(isLoading = false, message = errorMessage) }
			}
		)
	}
}

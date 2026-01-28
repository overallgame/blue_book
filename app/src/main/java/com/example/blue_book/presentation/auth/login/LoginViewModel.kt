package com.example.blue_book.presentation.auth.login

import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.LoginCredentials
import com.example.blue_book.domain.usecase.LoginUseCase
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

        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = { loginUseCase(LoginCredentials(currentState.phone, currentState.password)) },
            onSuccess = {
                setState { copy(isLoading = false) }
                sendEffect(LoginUiEffect.NavigateHome)
            },
            onFailure = { throwable ->
                val errorMessage = throwable.message ?: "登录失败，请稍后重试"
                setState { copy(isLoading = false, message = errorMessage) }
            }
        )
    }
}


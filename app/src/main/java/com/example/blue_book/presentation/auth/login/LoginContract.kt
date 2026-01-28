package com.example.blue_book.presentation.auth.login

import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState

sealed interface LoginIntent : UiIntent {
    data class PhoneChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
}

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
) : UiState {
    val isLoginEnabled: Boolean
        get() = phone.isNotBlank() && password.isNotBlank() && !isLoading
}

sealed interface LoginUiEffect : UiEffect {
    data object NavigateHome : LoginUiEffect
    data class ShowToast(val message: String) : LoginUiEffect
}


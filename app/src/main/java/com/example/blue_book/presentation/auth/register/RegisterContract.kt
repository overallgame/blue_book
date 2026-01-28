package com.example.blue_book.presentation.auth.register

import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState

sealed interface RegisterIntent : UiIntent {
    data class NicknameChanged(val value: String) : RegisterIntent
    data class PhoneChanged(val value: String) : RegisterIntent
    data class PasswordChanged(val value: String) : RegisterIntent
    data class ConfirmPasswordChanged(val value: String) : RegisterIntent
    data class VerificationCodeChanged(val value: String) : RegisterIntent
    data object RequestVerificationCode : RegisterIntent
    data object Submit : RegisterIntent
}

data class RegisterUiState(
    val nickname: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val verificationCode: String = "",
    val serverVerificationCode: String = "",
    val isLoading: Boolean = false,
    val countdownSeconds: Int = 0,
    val message: String? = null
) : UiState {
    val canRequestCode: Boolean
        get() = nickname.isNotBlank() && phone.length == 11 && countdownSeconds == 0 && !isLoading

    val canSubmit: Boolean
        get() = nickname.isNotBlank() && phone.length == 11 && password.isNotBlank() && confirmPassword.isNotBlank() && verificationCode.isNotBlank() && password == confirmPassword && !isLoading
}

sealed interface RegisterUiEffect : UiEffect {
    data object NavigateHome : RegisterUiEffect
    data class ShowToast(val message: String) : RegisterUiEffect
}


package com.example.blue_book.presentation.auth.register

import androidx.lifecycle.viewModelScope
import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.RegisterInfo
import com.example.blue_book.domain.usecase.RegisterUseCase
import com.example.blue_book.domain.usecase.SendVerificationCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val sendVerificationCodeUseCase: SendVerificationCodeUseCase
) : UdfViewModel<RegisterIntent, RegisterUiState, RegisterUiEffect>(RegisterUiState()) {

    private var countdownJob: Job? = null

    override suspend fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.NicknameChanged -> setState {
                copy(nickname = intent.value, message = null)
            }

            is RegisterIntent.PhoneChanged -> setState {
                copy(phone = intent.value, message = null)
            }

            is RegisterIntent.PasswordChanged -> setState {
                copy(password = intent.value, message = null)
            }

            is RegisterIntent.ConfirmPasswordChanged -> setState {
                copy(confirmPassword = intent.value, message = null)
            }

            is RegisterIntent.VerificationCodeChanged -> setState {
                copy(verificationCode = intent.value, message = null)
            }

            RegisterIntent.RequestVerificationCode -> requestVerificationCode()
            RegisterIntent.Submit -> submitRegister()
        }
    }

    private suspend fun requestVerificationCode() {
        val state = uiState.value
        if (!state.canRequestCode) {
            setState { copy(message = "请先填写昵称和手机号") }
            return
        }

        runResult(
            onStart = {
                setState { copy(isLoading = true, message = null, countdownSeconds = VERIFICATION_COUNTDOWN) }
                startCountdown()
            },
            call = { sendVerificationCodeUseCase(state.phone, state.nickname) },
            onSuccess = {
                setState {
                    copy(
                        isLoading = false,
                        serverVerificationCode = "",
                        message = "验证码已发送，请查收短信"
                    )
                }
            },
            onFailure = { throwable ->
                val errorMessage = throwable.message ?: "验证码发送失败，请稍后重试"
                setState { copy(isLoading = false, message = errorMessage) }
            }
        )
    }

    private suspend fun submitRegister() {
        val state = uiState.value
        if (!state.canSubmit) {
            val errorMessage = when {
                state.nickname.isBlank() -> "请输入昵称"
                state.phone.length != 11 -> "请输入正确的手机号"
                state.password.isBlank() || state.confirmPassword.isBlank() -> "请输入密码"
                state.password != state.confirmPassword -> "两次输入的密码不一致"
                state.verificationCode.isBlank() -> "请输入验证码"
                else -> "请完善注册信息"
            }
            sendEffect(RegisterUiEffect.ShowToast(errorMessage))
            return
        }

        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = {
                registerUseCase(
                    RegisterInfo(
                        nickname = state.nickname,
                        phone = state.phone,
                        password = state.password,
                        verificationCode = state.verificationCode
                    )
                )
            },
            onSuccess = {
                setState { copy(isLoading = false) }
                sendEffect(RegisterUiEffect.NavigateHome)
            },
            onFailure = { throwable ->
                val errorMessage = throwable.message ?: "注册失败，请稍后重试"
                setState { copy(isLoading = false, message = errorMessage) }
            }
        )
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = VERIFICATION_COUNTDOWN
            while (remaining > 0) {
                setState { copy(countdownSeconds = remaining) }
                delay(1000)
                remaining--
            }
            setState { copy(countdownSeconds = 0) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    companion object {
        private const val VERIFICATION_COUNTDOWN = 60
    }
}


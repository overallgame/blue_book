package com.example.blue_book.presentation.mine

import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.usecase.GetCurrentUserPhoneUseCase
import com.example.blue_book.domain.usecase.GetUserProfileUseCase
import com.example.blue_book.domain.usecase.LogoutUseCase
import com.example.blue_book.domain.usecase.UpdateUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val getCurrentUserPhone: GetCurrentUserPhoneUseCase,
    private val getUserProfile: GetUserProfileUseCase,
    private val updateUserProfile: UpdateUserProfileUseCase,
    private val logoutUseCase: LogoutUseCase
) : UdfViewModel<MineIntent, MineUiState, MineEffect>(MineUiState()) {

    override suspend fun handleIntent(intent: MineIntent) {
        when (intent) {
            MineIntent.Init -> init()
            MineIntent.Refresh -> refresh()
            MineIntent.Logout -> logout()
            is MineIntent.UpdateAvatar -> updateAvatar(intent.uri)
            is MineIntent.UpdateBackground -> updateBackground(intent.uri)
        }
    }

    private suspend fun init() {
        val phone = getCurrentUserPhone()
        if (phone.isNullOrBlank()) {
            setState { copy(isLoading = false, message = "未登录") }
            return
        }
        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = { getUserProfile(phone) },
            onSuccess = { user -> setState { copy(user = user, isLoading = false) } },
            onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
        )
    }

    private suspend fun refresh() {
        val phone = getCurrentUserPhone()
        if (phone.isNullOrBlank()) return
        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = { getUserProfile(phone) },
            onSuccess = { user -> setState { copy(user = user, isLoading = false) } },
            onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
        )
    }

    private suspend fun logout() {
        logoutUseCase()
        setState { copy(user = null) }
        sendEffect(MineEffect.ShowToast("已退出登录"))
    }

    private suspend fun updateAvatar(uri: String) {
        val current = uiState.value.user ?: return
        val updated = current.copy(avatar = uri)
        runResult(
            call = { updateUserProfile(updated) },
            onSuccess = { setState { copy(user = updated) } },
            onFailure = { e -> sendEffect(MineEffect.ShowToast(e.message ?: "更新头像失败")) }
        )
    }

    private suspend fun updateBackground(uri: String) {
        val current = uiState.value.user ?: return
        val updated = current.copy(background = uri)
        runResult(
            call = { updateUserProfile(updated) },
            onSuccess = { setState { copy(user = updated) } },
            onFailure = { e -> sendEffect(MineEffect.ShowToast(e.message ?: "更新背景失败")) }
        )
    }
}
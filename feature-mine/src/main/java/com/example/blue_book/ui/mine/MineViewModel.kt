package com.example.blue_book.ui.mine

import com.example.blue_book.udf.UdfViewModel
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.domain.usecase.GetCurrentUserPhoneUseCase
import com.example.blue_book.domain.usecase.GetUserProfileUseCase
import com.example.blue_book.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
	private val getCurrentUserPhone: GetCurrentUserPhoneUseCase,
	private val getUserProfile: GetUserProfileUseCase,
	private val userRepository: UserRepository,
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
			// 未登录：由页面弹登录引导卡片，不作为错误提示
			setState { copy(isLoading = false, message = null) }
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
		sendEffect(MineEffect.NavigateToLogin)
	}

	/** 头像走独立上传端点（/api/v2/me/avatar），成功后重拉资料获取服务端地址 */
	private suspend fun updateAvatar(uri: String) {
		if (uiState.value.user == null) return
		runResult(
			call = { userRepository.uploadAvatarFile(uri) },
			onSuccess = { refresh() },
			onFailure = { e ->
				sendEffect(MineEffect.ShowToast(e.message ?: "头像上传失败"))
				sendEffect(MineEffect.ImageUploadFailed("avatar"))
			}
		)
	}

	/** 背景图走独立上传端点（/api/v2/me/background），成功后重拉资料获取服务端地址 */
	private suspend fun updateBackground(uri: String) {
		if (uiState.value.user == null) return
		runResult(
			call = { userRepository.uploadBackgroundFile(uri) },
			onSuccess = { refresh() },
			onFailure = { e ->
				sendEffect(MineEffect.ShowToast(e.message ?: "背景图上传失败"))
				sendEffect(MineEffect.ImageUploadFailed("background"))
			}
		)
	}
}
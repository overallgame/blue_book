package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UdfViewModel
import com.example.blue_book.data.UserAccount
import com.example.blue_book.domain.usecase.GetCurrentUserPhoneUseCase
import com.example.blue_book.domain.usecase.GetUserProfileUseCase
import com.example.blue_book.domain.usecase.UpdateUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
	private val getCurrentPhone: GetCurrentUserPhoneUseCase,
	private val getUserProfile: GetUserProfileUseCase,
	private val updateUserProfile: UpdateUserProfileUseCase
) : UdfViewModel<UserProfileIntent, UserProfileUiState, UserProfileEffect>(UserProfileUiState()) {

	override suspend fun handleIntent(intent: UserProfileIntent) {
		when (intent) {
			UserProfileIntent.Init -> init()
			UserProfileIntent.Refresh -> refresh()
			is UserProfileIntent.SubmitUpdate -> submitUpdate(intent, closeOnSuccess = true)
			is UserProfileIntent.UpdateImages -> submitUpdate(
				UserProfileIntent.SubmitUpdate(avatar = intent.avatar, background = intent.background),
				closeOnSuccess = false
			)
		}
	}

	private suspend fun init() {
		val phone = getCurrentPhone() ?: return setState { copy(isLoading = false, message = "未登录") }
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { getUserProfile(phone) },
			onSuccess = { u -> setState { copy(user = u, isLoading = false) } },
			onFailure = { e -> setState { copy(isLoading = false, message = e.message ?: "加载失败") } }
		)
	}

	private suspend fun refresh() {
		init()
	}

	private suspend fun submitUpdate(i: UserProfileIntent.SubmitUpdate, closeOnSuccess: Boolean) {
		val phone = getCurrentPhone() ?: return sendEffect(UserProfileEffect.ShowToast("未登录"))
		val origin = uiState.value.user
		if (origin == null) {
			sendEffect(UserProfileEffect.ShowToast("用户信息未加载，请稍后重试"))
			return
		}
		val account = UserAccount(
			phone = phone,
			avatar = i.avatar ?: origin.avatar,
			nickname = i.nickname ?: origin.nickname,
			password = origin.password,
			introduction = i.introduction ?: origin.introduction,
			sex = i.sex ?: origin.sex,
			birthday = i.birthday ?: origin.birthday,
			career = i.career ?: origin.career,
			region = i.region ?: origin.region,
			school = i.school ?: origin.school,
			background = i.background ?: origin.background
		)
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { updateUserProfile(account) },
			onSuccess = {
				setState { copy(isLoading = false, user = account) }
				sendEffect(UserProfileEffect.ShowToast(successMessage(i, closeOnSuccess)))
				if (closeOnSuccess) sendEffect(UserProfileEffect.ClosePage)
			},
			onFailure = { e ->
				setState { copy(isLoading = false, message = e.message ?: "修改失败") }
				sendEffect(UserProfileEffect.ShowToast("修改信息失败"))
			}
		)
	}

	private fun successMessage(i: UserProfileIntent.SubmitUpdate, closeOnSuccess: Boolean): String {
		if (!closeOnSuccess) {
			return when {
				i.avatar != null -> "头像更新成功"
				else -> "背景图更新成功"
			}
		}
		return "修改信息成功"
	}
}

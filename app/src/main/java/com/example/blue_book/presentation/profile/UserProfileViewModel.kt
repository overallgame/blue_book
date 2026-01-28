package com.example.blue_book.presentation.profile

import com.example.blue_book.core.udf.UdfViewModel
import com.example.blue_book.domain.model.UserAccount
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
			is UserProfileIntent.SubmitUpdate -> submitUpdate(intent)
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

	private suspend fun submitUpdate(i: UserProfileIntent.SubmitUpdate) {
		val phone = getCurrentPhone() ?: return sendEffect(UserProfileEffect.ShowToast("未登录"))
		val account = UserAccount(
			phone = phone,
			avatar = i.avatar,
			nickname = i.nickname,
			password = null,
			introduction = i.introduction,
			sex = i.sex,
			birthday = i.birthday,
			career = i.career,
			region = i.region,
			school = i.school,
			background = i.background
		)
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { updateUserProfile(account) },
			onSuccess = {
				setState { copy(isLoading = false) }
				sendEffect(UserProfileEffect.ShowToast("修改信息成功"))
				sendEffect(UserProfileEffect.ClosePage)
			},
			onFailure = { e ->
				setState { copy(isLoading = false, message = e.message ?: "修改失败") }
				sendEffect(UserProfileEffect.ShowToast("修改信息失败"))
			}
		)
	}
}



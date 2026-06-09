package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState
import com.example.blue_book.common.bean.UserAccount

sealed interface UserProfileIntent : UiIntent {
	data object Init : UserProfileIntent
	data object Refresh : UserProfileIntent
	data class SubmitUpdate(
		val nickname: String,
		val introduction: String?,
		val sex: String?,
		val birthday: String?,
		val career: String?,
		val region: String?,
		val school: String?,
		val avatar: String?,
		val background: String?
	) : UserProfileIntent
}

data class UserProfileUiState(
	val user: UserAccount? = null,
	val isLoading: Boolean = false,
	val message: String? = null
) : UiState

sealed interface UserProfileEffect : UiEffect {
	data class ShowToast(val message: String) : UserProfileEffect
	data object ClosePage : UserProfileEffect
}



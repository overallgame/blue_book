package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState
import com.example.blue_book.data.UserAccount

sealed interface UserProfileIntent : UiIntent {
	data object Init : UserProfileIntent
	data object Refresh : UserProfileIntent

	/**
	 * 增量提交：为 null 的字段表示保持原值不变，
	 * 由 ViewModel 与当前用户信息合并后再落库
	 */
	data class SubmitUpdate(
		val nickname: String? = null,
		val introduction: String? = null,
		val sex: String? = null,
		val birthday: String? = null,
		val career: String? = null,
		val region: String? = null,
		val school: String? = null,
		val avatar: String? = null,
		val background: String? = null
	) : UserProfileIntent

	/** 头像 / 背景图选择后即时保存，成功后不关闭页面 */
	data class UpdateImages(
		val avatar: String? = null,
		val background: String? = null
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

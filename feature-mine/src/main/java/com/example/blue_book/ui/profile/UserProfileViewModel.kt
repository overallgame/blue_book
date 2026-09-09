package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UdfViewModel
import com.example.blue_book.data.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import com.example.blue_book.domain.usecase.GetCurrentUserPhoneUseCase
import com.example.blue_book.domain.usecase.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
	private val getCurrentPhone: GetCurrentUserPhoneUseCase,
	private val getUserProfile: GetUserProfileUseCase,
	private val userRepository: UserRepository
) : UdfViewModel<UserProfileIntent, UserProfileUiState, UserProfileEffect>(UserProfileUiState()) {

	override suspend fun handleIntent(intent: UserProfileIntent) {
		when (intent) {
			UserProfileIntent.Init -> init()
			UserProfileIntent.Refresh -> refresh()
			is UserProfileIntent.UpdateNickname -> updateSingleField(
				call = { phone -> userRepository.updateNickname(phone, intent.value) },
				localUpdate = { copy(nickname = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateBio -> updateSingleField(
				call = { phone -> userRepository.updateBio(phone, intent.value) },
				localUpdate = { copy(introduction = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateGender -> updateSingleField(
				call = { phone -> userRepository.updateGender(phone, intent.value) },
				localUpdate = { copy(sex = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateBirthday -> updateSingleField(
				call = { phone -> userRepository.updateBirthday(phone, intent.value) },
				localUpdate = { copy(birthday = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateOccupation -> updateSingleField(
				call = { phone -> userRepository.updateOccupation(phone, intent.value) },
				localUpdate = { copy(career = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateRegion -> updateSingleField(
				call = { phone -> userRepository.updateRegion(phone, intent.value) },
				localUpdate = { copy(region = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UpdateSchool -> updateSingleField(
				call = { phone -> userRepository.updateSchool(phone, intent.value) },
				localUpdate = { copy(school = intent.value.ifBlank { null }) }
			)
			is UserProfileIntent.UploadAvatar -> uploadImage(intent.localUri, isAvatar = true)
			is UserProfileIntent.UploadBackground -> uploadImage(intent.localUri, isAvatar = false)
			UserProfileIntent.CancelAvatarPreview -> setState { copy(avatarPreviewUri = null) }
			UserProfileIntent.CancelBackgroundPreview -> setState { copy(backgroundPreviewUri = null) }
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

	/** 单字段更新：成功后本地 copy 对应字段并广播 FieldUpdated，由字段编辑页消费后自动返回 */
	private suspend fun updateSingleField(
		call: suspend (String) -> Result<Unit>,
		localUpdate: UserAccount.() -> UserAccount
	) {
		val phone = getCurrentPhone() ?: return sendEffect(UserProfileEffect.ShowToast("未登录"))
		runResult(
			onStart = { setState { copy(isLoading = true, message = null) } },
			call = { call(phone) },
			onSuccess = {
				setState { copy(isLoading = false, user = user?.localUpdate()) }
				sendEffect(UserProfileEffect.FieldUpdated)
			},
			onFailure = { e ->
				setState { copy(isLoading = false, message = e.message ?: "修改失败") }
				sendEffect(UserProfileEffect.ShowToast("修改信息失败"))
			}
		)
	}

	/** 头像/背景图：先记录预览 URI 即时显示，上传成功后重拉用户信息获取服务端绝对地址 */
	private suspend fun uploadImage(localUri: String, isAvatar: Boolean) {
		runResult(
			onStart = {
				setState {
					copy(
						isUploadingImage = true,
						message = null,
						avatarPreviewUri = if (isAvatar) localUri else avatarPreviewUri,
						backgroundPreviewUri = if (!isAvatar) localUri else backgroundPreviewUri
					)
				}
			},
			call = { if (isAvatar) userRepository.uploadAvatarFile(localUri) else userRepository.uploadBackgroundFile(localUri) },
			onSuccess = { _ ->
				setState {
					copy(
						isUploadingImage = false,
						avatarPreviewUri = if (isAvatar) null else avatarPreviewUri,
						backgroundPreviewUri = if (!isAvatar) null else backgroundPreviewUri
					)
				}
				sendEffect(UserProfileEffect.ShowToast(if (isAvatar) "头像更新成功" else "背景图更新成功"))
				refresh()
			},
			onFailure = { e ->
				// 失败回滚：清除预览 URI，UI 回绑服务端原图
				setState {
					copy(
						isUploadingImage = false,
						message = e.message ?: "上传失败",
						avatarPreviewUri = if (isAvatar) null else avatarPreviewUri,
						backgroundPreviewUri = if (!isAvatar) null else backgroundPreviewUri
					)
				}
				sendEffect(UserProfileEffect.ShowToast("上传失败，请重试"))
			}
		)
	}
}

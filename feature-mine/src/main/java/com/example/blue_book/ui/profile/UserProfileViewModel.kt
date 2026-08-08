package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UdfViewModel
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
            is UserProfileIntent.UpdateNickname -> updateField("昵称") { userRepository.updateNickname(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateBio -> updateField("简介") { userRepository.updateBio(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateGender -> updateField("性别") { userRepository.updateGender(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateBirthday -> updateField("生日") { userRepository.updateBirthday(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateOccupation -> updateField("职业") { userRepository.updateOccupation(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateRegion -> updateField("地区") { userRepository.updateRegion(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UpdateSchool -> updateField("学校") { userRepository.updateSchool(getCurrentPhone() ?: "", intent.value) }
            is UserProfileIntent.UploadAvatar -> uploadImage("头像", intent.localUri, isAvatar = true)
            is UserProfileIntent.UploadBackground -> uploadImage("背景图", intent.localUri, isAvatar = false)
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

    private suspend fun updateField(fieldName: String, call: suspend () -> Result<Unit>) {
        runResult(
            onStart = { setState { copy(isLoading = true, message = null) } },
            call = call,
            onSuccess = {
                refresh()
                setState { copy(isLoading = false) }
                sendEffect(UserProfileEffect.ShowToast("${fieldName}更新成功"))
                sendEffect(UserProfileEffect.FieldUpdated)
            },
            onFailure = { e ->
                setState { copy(isLoading = false, message = e.message) }
                sendEffect(UserProfileEffect.ShowToast("${fieldName}更新失败"))
            }
        )
    }

    private suspend fun uploadImage(type: String, localUri: String, isAvatar: Boolean) {
        runResult(
            onStart = { setState { copy(isUploadingImage = true) } },
            call = {
                if (isAvatar) userRepository.uploadAvatarFile(localUri)
                else userRepository.uploadBackgroundFile(localUri)
            },
            onSuccess = {
                setState {
                    copy(
                        isUploadingImage = false,
                        avatarPreviewUri = if (isAvatar) null else avatarPreviewUri,
                        backgroundPreviewUri = if (!isAvatar) null else backgroundPreviewUri
                    )
                }
                refresh()
                sendEffect(UserProfileEffect.ShowToast("${type}上传成功"))
            },
            onFailure = { e ->
                setState { copy(isUploadingImage = false) }
                sendEffect(UserProfileEffect.ShowToast("${type}上传失败: ${e.message}"))
            }
        )
    }
}

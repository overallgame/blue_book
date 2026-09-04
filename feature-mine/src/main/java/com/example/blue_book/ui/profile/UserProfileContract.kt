package com.example.blue_book.ui.profile

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState
import com.example.blue_book.data.UserAccount

sealed interface UserProfileIntent : UiIntent {
    data object Init : UserProfileIntent
    data object Refresh : UserProfileIntent
    data class UpdateNickname(val value: String) : UserProfileIntent
    data class UpdateBio(val value: String) : UserProfileIntent
    data class UpdateGender(val value: String) : UserProfileIntent
    data class UpdateBirthday(val value: String) : UserProfileIntent
    data class UpdateOccupation(val value: String) : UserProfileIntent
    data class UpdateRegion(val value: String) : UserProfileIntent
    data class UpdateSchool(val value: String) : UserProfileIntent
    data class UploadAvatar(val localUri: String) : UserProfileIntent
    data class UploadBackground(val localUri: String) : UserProfileIntent
    data object CancelAvatarPreview : UserProfileIntent
    data object CancelBackgroundPreview : UserProfileIntent
}

data class UserProfileUiState(
    val user: UserAccount? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val isUploadingImage: Boolean = false,
    val avatarPreviewUri: String? = null,
    val backgroundPreviewUri: String? = null
) : UiState

sealed interface UserProfileEffect : UiEffect {
    data class ShowToast(val message: String) : UserProfileEffect
    data object ClosePage : UserProfileEffect
    data object FieldUpdated : UserProfileEffect
}

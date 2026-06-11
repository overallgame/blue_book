package com.example.blue_book.ui.mine

import com.example.blue_book.udf.UiEffect
import com.example.blue_book.udf.UiIntent
import com.example.blue_book.udf.UiState
import com.example.blue_book.data.UserAccount

sealed interface MineIntent : UiIntent {
    data object Init : MineIntent
    data object Refresh : MineIntent
    data object Logout : MineIntent
    data class UpdateAvatar(val uri: String) : MineIntent
    data class UpdateBackground(val uri: String) : MineIntent
}

data class MineUiState(
    val user: UserAccount? = null,
    val isLoading: Boolean = false,
    val message: String? = null
) : UiState

sealed interface MineEffect : UiEffect {
    data class ShowToast(val message: String) : MineEffect
    data object NavigateToLogin : MineEffect
}



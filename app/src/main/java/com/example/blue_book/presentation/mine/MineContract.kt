package com.example.blue_book.presentation.mine

import com.example.blue_book.core.udf.UiEffect
import com.example.blue_book.core.udf.UiIntent
import com.example.blue_book.core.udf.UiState
import com.example.blue_book.domain.model.UserAccount

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
}



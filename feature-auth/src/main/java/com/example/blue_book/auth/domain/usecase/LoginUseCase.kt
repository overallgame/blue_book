package com.example.blue_book.auth.domain.usecase

import com.example.blue_book.auth.domain.model.LoginCredentials
import com.example.blue_book.data.UserAccount
import com.example.blue_book.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(credentials: LoginCredentials): Result<UserAccount> {
        return authRepository.login(credentials)
    }
}

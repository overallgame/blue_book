package com.example.blue_book.auth.provider

import com.example.blue_book.auth.domain.repository.AuthRepository
import com.example.blue_book.provider.IAuthProvider

class AuthProviderImpl(
    private val authRepository: AuthRepository
) : IAuthProvider {

    override suspend fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    override suspend fun logout(): Result<Unit> = authRepository.logout()
}

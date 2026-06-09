package com.example.blue_book.domain.usecase

import com.example.blue_book.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val userRepository: AuthRepository
) {

    suspend operator fun invoke(): Result<Unit> {
        return userRepository.logout()
    }
}


package com.example.blue_book.auth.domain.usecase

import com.example.blue_book.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SendVerificationCodeUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String, nickname: String): Result<String> {
        return authRepository.sendVerificationCode(phone, nickname)
    }
}

package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class SendVerificationCodeUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(phone: String, nickname: String): Result<String> {
        return userRepository.sendVerificationCode(phone, nickname)
    }
}


package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class GetCurrentUserPhoneUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(): String? {
        return userRepository.currentUserPhone()
    }
}



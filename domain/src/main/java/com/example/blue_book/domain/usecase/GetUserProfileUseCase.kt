package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(phone: String): Result<UserAccount> {
        return userRepository.getUserProfile(phone)
    }
}



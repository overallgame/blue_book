package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class UpdateUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(account: UserAccount): Result<Unit> {
        return userRepository.updateUserProfile(account)
    }
}



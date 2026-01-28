package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.RegisterInfo
import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(info: RegisterInfo): Result<UserAccount> {
        return userRepository.register(info)
    }
}


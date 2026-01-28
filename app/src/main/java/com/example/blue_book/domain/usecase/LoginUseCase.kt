package com.example.blue_book.domain.usecase

import com.example.blue_book.domain.model.LoginCredentials
import com.example.blue_book.domain.model.UserAccount
import com.example.blue_book.domain.repository.UserRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(credentials: LoginCredentials): Result<UserAccount> {
        return userRepository.login(credentials)
    }
}


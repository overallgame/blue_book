package com.example.blue_book.auth.domain.usecase

import com.example.blue_book.auth.domain.model.RegisterInfo
import com.example.blue_book.common.bean.UserAccount
import com.example.blue_book.auth.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(info: RegisterInfo): Result<UserAccount> {
        return authRepository.register(info)
    }
}

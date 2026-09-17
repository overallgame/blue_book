package com.example.blue_book.domain.usecase

import com.example.blue_book.provider.IAuthProvider
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
	private val authProvider: IAuthProvider
) {

	suspend operator fun invoke(): Result<Unit> {
		return authProvider.logout()
	}
}

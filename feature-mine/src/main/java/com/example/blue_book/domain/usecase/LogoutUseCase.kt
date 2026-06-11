package com.example.blue_book.domain.usecase

import com.example.blue_book.provider.IAuthProvider
import com.therouter.TheRouter
import javax.inject.Inject

class LogoutUseCase @Inject constructor() {

    suspend operator fun invoke(): Result<Unit> {
        return TheRouter.get(IAuthProvider::class.java)!!.logout()
    }
}

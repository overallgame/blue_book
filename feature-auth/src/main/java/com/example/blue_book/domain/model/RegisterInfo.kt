package com.example.blue_book.domain.model

data class RegisterInfo(
    val nickname: String,
    val phone: String,
    val password: String,
    val verificationCode: String
)


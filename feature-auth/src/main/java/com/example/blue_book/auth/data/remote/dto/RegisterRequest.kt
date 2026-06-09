package com.example.blue_book.auth.data.remote.dto

data class RegisterRequest(
    val nickname: String,
    val phone: String,
    val password: String,
    val code: String
)

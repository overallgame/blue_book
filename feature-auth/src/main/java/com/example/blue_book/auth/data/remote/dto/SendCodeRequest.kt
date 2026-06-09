package com.example.blue_book.auth.data.remote.dto

data class SendCodeRequest(
    val phone: String,
    val nickname: String? = null
)

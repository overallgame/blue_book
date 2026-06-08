package com.example.blue_book.data.remote.account.dto

data class AuthV2CodeRequestDto(
	val phone: String,
	val nickname: String? = null
)

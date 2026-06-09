package com.example.blue_book.data.remote.account.dto

data class AuthV2RegisterRequestDto(
	val nickname: String,
	val phone: String,
	val password: String,
	val code: String
)

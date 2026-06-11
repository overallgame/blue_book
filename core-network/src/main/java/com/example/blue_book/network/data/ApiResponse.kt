package com.example.blue_book.network.data

data class ApiResponse<T>(
	val code: Int,
	val message: String,
	val ttl: Int,
	val data: T?
)



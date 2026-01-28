package com.example.blue_book.common.bean

data class ApiResponse<T>(
	val code: Int,
	val message: String,
	val ttl: Int,
	val data: T?
)



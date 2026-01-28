package com.example.blue_book.data.remote.account.dto

data class CommonResultDto<T>(
    val msg: String?,
    val code: Int?,
    val data: T?
)


package com.example.blue_book.network.dto

data class CommonResult<T>(
    val msg: String?,
    val code: Int?,
    val data: T?
)

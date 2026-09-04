package com.example.bluebook.file.dto

data class UploadInitRequest(
    val fileName: String,
    val fileSize: Long,
    val fileMd5: String,
    val totalChunks: Int,
    val chunkSize: Int = 2097152
)

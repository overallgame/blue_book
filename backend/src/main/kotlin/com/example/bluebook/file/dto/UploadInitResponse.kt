package com.example.bluebook.file.dto

data class UploadInitResponse(
    val uploadId: String,
    val skipUpload: Boolean = false,
    val uploadedChunks: List<Int> = emptyList()
)

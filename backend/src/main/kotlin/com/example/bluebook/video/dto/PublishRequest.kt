package com.example.bluebook.video.dto

data class PublishRequest(
    val title: String?,
    val description: String?,
    val filePath: String,
    /** 发布时定位城市（"本地"流使用，可空） */
    val region: String? = null
)

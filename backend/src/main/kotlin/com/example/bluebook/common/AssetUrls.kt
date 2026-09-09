package com.example.bluebook.common

/**
 * 媒体资源 URL 规范化：
 * 数据库只存文件系统的相对路径（如 2026-09-09/uuid.jpg），
 * 对外 DTO 下发时补上公开访问前缀（/upload/images、/upload/videos、/hls），
 * 与 nginx alias 及 Spring 静态映射保持一致，Android 端拼接 BASE_URL 即可直访。
 */
fun assetUrl(path: String?, prefix: String): String? {
    if (path.isNullOrBlank()) return null
    return when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> path
        else -> "/$prefix/$path"
    }
}

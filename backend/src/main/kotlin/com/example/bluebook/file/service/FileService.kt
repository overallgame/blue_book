package com.example.bluebook.file.service

import com.example.bluebook.common.FileTooLargeException
import com.example.bluebook.common.InvalidFileTypeException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

@Service
class FileService(
    @Value("\${app.upload.storage-path}") private val storagePath: String,
    @Value("\${app.upload.image-max-size}") private val imageMaxSize: Long
) {
    private val allowedImageTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    fun uploadImage(file: MultipartFile): String {
        if (file.size > imageMaxSize) throw FileTooLargeException()
        if ((file.contentType ?: "") !in allowedImageTypes) throw InvalidFileTypeException()

        val ext = file.originalFilename?.substringAfterLast('.') ?: "jpg"
        val dir = File("$storagePath/images/${LocalDateTime.now().toLocalDate()}")
        dir.mkdirs()
        val fileName = "${UUID.randomUUID()}.$ext"
        val dest = File(dir, fileName)
        file.transferTo(dest)
        return "${LocalDateTime.now().toLocalDate()}/$fileName"
    }
}

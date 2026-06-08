package com.example.blue_book.data.remote.file

import com.example.blue_book.data.remote.commonCall
import okhttp3.MultipartBody
import javax.inject.Inject

class FileRemoteDataSource @Inject constructor(
	private val api: FileApi
) {

	suspend fun uploadImage(part: MultipartBody.Part): Result<String> {
		return commonCall { api.uploadImage(part) }
	}
}

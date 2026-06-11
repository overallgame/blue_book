package com.example.blue_book.data.remote.file

import com.example.blue_book.network.ApiGateway
import okhttp3.MultipartBody
import javax.inject.Inject

class FileRemoteDataSource @Inject constructor(
    private val apiGateway: ApiGateway
) {
    private val api = apiGateway.createApi(FileApi::class.java)

    suspend fun uploadImage(part: MultipartBody.Part): Result<String> =
        apiGateway.commonResult { api.uploadImage(part) }
}

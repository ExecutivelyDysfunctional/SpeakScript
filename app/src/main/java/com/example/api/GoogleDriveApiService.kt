package com.example.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonClass

interface GoogleDriveApiService {

    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFile(
        @Header("Authorization") authHeader: String,
        @Part metadata: MultipartBody.Part,
        @Part file: MultipartBody.Part
    ): DriveFileResponse

    @PATCH("drive/v3/files/{fileId}")
    suspend fun updateFileMetadata(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Body metadata: DriveFileMetadata
    ): DriveFileResponse

    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String
    ): ResponseBody
}

@JsonClass(generateAdapter = true)
data class DriveFileMetadata(
    val name: String? = null,
    val parents: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileResponse(
    val id: String,
    val name: String,
    val mimeType: String? = null
)

object GoogleDriveClient {
    private const val BASE_URL = "https://www.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GoogleDriveApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GoogleDriveApiService::class.java)
    }
}

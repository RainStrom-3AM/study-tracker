package com.studytracker.sync

import com.google.gson.JsonObject
import retrofit2.http.*

interface MongoApiService {

    @Headers("Content-Type: application/json")
    @POST("b")
    suspend fun createBin(
        @Header("X-Master-Key") masterKey: String,
        @Header("X-Access-Key") accessKey: String,
        @Header("X-Bin-Name") binName: String,
        @Header("X-Bin-Private") isPrivate: Boolean = true,
        @Body body: JsonObject
    ): JsonBinResponse

    @Headers("Content-Type: application/json")
    @GET("b/{binId}/latest")
    suspend fun readBin(
        @Header("X-Master-Key") masterKey: String,
        @Header("X-Access-Key") accessKey: String,
        @Path("binId") binId: String
    ): JsonBinResponse

    @Headers("Content-Type: application/json")
    @PUT("b/{binId}")
    suspend fun updateBin(
        @Header("X-Master-Key") masterKey: String,
        @Header("X-Access-Key") accessKey: String,
        @Path("binId") binId: String,
        @Body body: JsonObject
    ): JsonBinResponse

    @Headers("Content-Type: application/json")
    @GET("c")
    suspend fun listBins(
        @Header("X-Master-Key") masterKey: String,
        @Header("X-Access-Key") accessKey: String
    ): JsonBinListResponse
}

data class JsonBinResponse(
    val metadata: JsonBinMetadata? = null,
    val record: com.google.gson.JsonElement? = null
)

data class JsonBinMetadata(
    val id: String? = null,
    val name: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class JsonBinListResponse(
    val bins: List<JsonBinMetadata>? = null
)

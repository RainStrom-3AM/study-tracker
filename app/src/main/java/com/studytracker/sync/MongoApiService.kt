package com.studytracker.sync

import retrofit2.http.*

interface MongoApiService {

    @Headers("Content-Type: application/json")
    @POST("action/find")
    suspend fun find(
        @Header("api-key") apiKey: String,
        @Body body: MongoFilter
    ): MongoFindResponse

    @Headers("Content-Type: application/json")
    @POST("action/findOne")
    suspend fun findOne(
        @Header("api-key") apiKey: String,
        @Body body: MongoFilter
    ): Map<String, Any>?

    @Headers("Content-Type: application/json")
    @POST("action/insertOne")
    suspend fun insertOne(
        @Header("api-key") apiKey: String,
        @Body body: MongoDocument
    ): MongoInsertResponse

    @Headers("Content-Type: application/json")
    @POST("action/insertMany")
    suspend fun insertMany(
        @Header("api-key") apiKey: String,
        @Body body: Map<String, Any>
    ): MongoInsertResponse

    @Headers("Content-Type: application/json")
    @POST("action/updateOne")
    suspend fun updateOne(
        @Header("api-key") apiKey: String,
        @Body body: MongoUpdate
    ): MongoUpdateResponse

    @Headers("Content-Type: application/json")
    @POST("action/updateMany")
    suspend fun updateMany(
        @Header("api-key") apiKey: String,
        @Body body: MongoUpdate
    ): MongoUpdateResponse

    @Headers("Content-Type: application/json")
    @POST("action/deleteMany")
    suspend fun deleteMany(
        @Header("api-key") apiKey: String,
        @Body body: MongoDelete
    ): MongoDeleteResponse
}

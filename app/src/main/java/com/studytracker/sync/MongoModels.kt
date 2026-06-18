package com.studytracker.sync

import com.google.gson.annotations.SerializedName

data class MongoFilter(
    @SerializedName("filter") val filter: Map<String, Any> = emptyMap(),
    @SerializedName("projection") val projection: Map<String, Any>? = null,
    @SerializedName("sort") val sort: Map<String, Any>? = null,
    @SerializedName("limit") val limit: Int? = null
)

data class MongoDocument(
    @SerializedName("document") val document: Map<String, Any>
)

data class MongoUpdate(
    @SerializedName("filter") val filter: Map<String, Any>,
    @SerializedName("update") val update: Map<String, Any>,
    @SerializedName("upsert") val upsert: Boolean = true
)

data class MongoDelete(
    @SerializedName("filter") val filter: Map<String, Any>
)

data class MongoFindResponse(
    @SerializedName("documents") val documents: List<Map<String, Any>>
)

data class MongoInsertResponse(
    @SerializedName("insertedId") val insertedId: String
)

data class MongoUpdateResponse(
    @SerializedName("matchedCount") val matchedCount: Int,
    @SerializedName("modifiedCount") val modifiedCount: Int
)

data class MongoDeleteResponse(
    @SerializedName("deletedCount") val deletedCount: Int
)

package com.neilturner.aerialviews.providers.ncmemories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NCMemoriesApi {
    @GET("/apps/memories/api/clusters/albums")
    suspend fun getAlbumList(): Response<List<Album>>

    @POST("/apps/memories/api/days")
    suspend fun getDays(
        @Body searchRequest: DaysRequest,
    ): Response<List<Day>>

    @POST("/apps/memories/api/days/{dayid}")
    suspend fun getDay(
        @Path("dayid") dayid: Int,
    ): Response<List<Image>>

    @POST("/apps/memories/api/image/info/{fileid}")
    suspend fun getFullImageInfo(
        @Path("fileid") fileid: Int,
    ): Response<Image>
}

@Serializable
data class DaysRequest(
    val fav: Int? = null,
    val albums: List<String>? = null,
)

@Serializable
data class Album(
    @SerialName("id")
    val album_id: Int = 0,
    val cluster_id: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("count")
    val count: Int = 0,
    @SerialName("days")
    val days: List<Day> = emptyList(),
)

@Serializable
data class Day(
    val dayID: Int = 0,
    val count: Int = 0,
    @SerialName("images")
    val images: List<Image> = emptyList(),
)

@Serializable
data class Image(
    @SerialName("id")
    val fileid: Int = 0,
    val etag: String = "",
    val basename: String = "",
    val albumName: String = "",
    val exif: ExifInfo? = null,
)

@Serializable
data class ExifInfo(
    val DateTimeOriginal: String? = null,
    val OffsetTimeOriginal: String? = null,
    val GPSLatitude: String? = null,
    val GPSLongitude: String? = null,
    val Title: String? = null,
    val Description: String? = null,
)

@Serializable
data class ErrorResponse(
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerialName("correlationId")
    val correlationId: String = "",
)

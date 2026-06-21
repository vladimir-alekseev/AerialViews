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
    suspend fun getAlbumDays(
        @Query("albums") albumName: String,
    ): Response<List<Day>>

    @POST("/apps/memories/api/days")
    suspend fun getFavoriteDays(
        @Query("fav") fav: Int,
    ): Response<List<Day>>

    @POST("/apps/memories/api/days")
    suspend fun getRecentDays(): Response<List<Day>>

    @POST("/apps/memories/api/days/{dayids}")
    suspend fun getPhotos(
        @Path("dayids") dayids: String,
        @Query("albums") albumName: String? = null,
        @Query("fav") fav: Int? = null,
    ): Response<List<Image>>

    @POST("/apps/memories/api/image/info/{fileid}")
    suspend fun getFullImageInfo(
        @Path("fileid") fileid: Int,
    ): Response<Image>
}

@Serializable
data class Album(
    @SerialName("album_id")
    val album_id: Int = 0,
    @SerialName("cluster_id")
    val cluster_id: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("count")
    val count: Int = 0,
)

@Serializable
data class Day(
    @SerialName("dayID")
    val dayID: Int = 0,
    val count: Int = 0,
)

@Serializable
data class Image(
    @SerialName("fileid")
    val fileid: Int = 0,
    val etag: String = "",
    @SerialName("basename")
    val basename: String = "",
    val exif: ExifInfo? = null,
    @SerialName("albumName")
    val albumName: String? = "",
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

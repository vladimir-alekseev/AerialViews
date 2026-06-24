package com.neilturner.aerialviews.providers.ncmemories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface NCMemoriesApi {
    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/clusters/albums")
    suspend fun getAlbumList(
        @Header("Authorization") credential: String,
    ): Response<List<Album>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/days")
    suspend fun getDays(
        @Header("Authorization") credential: String,
        @Query("albums") cluster_id: String? = null,
        @Query("fav") fav: Int? = null,
        @Query("vid") vid: Int? = null,
    ): Response<List<Day>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/days/{dayids}")
    suspend fun getImages(
        @Header("Authorization") credential: String,
        @Path("dayids") dayids: String,
        @Query("albums") cluster_id: String? = null,
        @Query("fav") fav: Int? = null,
        @Query("vid") vid: Int? = null,
        ): Response<List<Image>>

    @Headers("OCS-APIRequest: true")
    @GET("/apps/memories/api/image/info/{fileid}")
    suspend fun getFullImageInfo(
        @Header("Authorization") credential: String,
        @Path("fileid") fileid: Int,
    ): Response<Image>
}

@Serializable
data class Album(
    @SerialName("album_id")
    val album_id: Int,
    @SerialName("cluster_id")
    val cluster_id: String,
    @SerialName("name")
    val name: String,
    @SerialName("count")
    val count: Int,
)

@Serializable
data class Day(
    @SerialName("dayid")
    val dayid: Int,
)

@Serializable
data class Image(
    @SerialName("fileid")
    val fileid: Int,
    val etag: String,
    @SerialName("basename")
    val basename: String,
    val epoch: Long? = null,
    val filename: String? = null,
    val exif: ExifInfo? = null,
    @SerialName("albumName")
    val albumName: String? = "",
)

@Serializable
data class ExifInfo(
    val DateTimeEpoch: Long? = null,
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

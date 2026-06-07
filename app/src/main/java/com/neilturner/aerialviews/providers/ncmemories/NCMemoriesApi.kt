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
    @GET("/api/albums")
    suspend fun getAlbums(
        @Query("shared") shared: Boolean? = null,
    ): Response<List<Album>>

    @GET("/api/albums/{id}")
    suspend fun getAlbum(
        @Path("id") albumId: String,
    ): Response<Album>

    @POST("/api/search/metadata")
    suspend fun getFavoriteAssets(
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>

    @POST("/api/search/metadata")
    suspend fun getRecentAssets(
        @Body searchRequest: SearchMetadataRequest,
    ): Response<SearchAssetsResponse>
}

@Serializable
data class SearchAssetsResponse(
    val assets: AssetsResult,
)

@Serializable
data class AssetsResult(
    val items: List<Asset>,
)

@Serializable
data class SearchMetadataRequest(
    val isFavorite: Boolean? = null,
    val order: String? = null,
    val size: Int? = null,
    val withExif: Boolean? = null,
    val type: String? = null,
)

@Serializable
data class ExifInfo(
    val description: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
)

@Serializable
data class Asset(
    val id: String = "",
    val type: String = "",
    val originalPath: String = "",
    val localDateTime: String? = null,
    val description: String? = null,
    val exifInfo: ExifInfo? = null,
    val albumName: String? = null,
)

@Serializable
data class Album(
    @SerialName("id")
    val id: String = "",
    @SerialName("albumName")
    val name: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("shared")
    val type: String = "",
    @SerialName("assets")
    val assets: List<Asset> = emptyList(),
    @SerialName("assetCount")
    val assetCount: Int = 0,
)

@Serializable
data class ErrorResponse(
    val message: String = "",
    val error: String = "",
    val statusCode: Int = 0,
    @SerialName("correlationId")
    val correlationId: String = "",
)

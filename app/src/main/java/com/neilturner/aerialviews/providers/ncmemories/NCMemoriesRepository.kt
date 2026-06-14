package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.data.network.JsonHelper.buildSerializer
import com.neilturner.aerialviews.data.network.ServerConfig
import com.neilturner.aerialviews.data.network.SslHelper
import com.neilturner.aerialviews.data.network.UrlParser
import com.neilturner.aerialviews.models.enums.ProviderMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import timber.log.Timber

class NCMemoriesRepository(
    private val prefs: NCMemoriesMediaPrefs,
) {
    lateinit var server: String

    private val client by lazy {
        server = UrlParser.parseServerUrl(prefs.url)
        val serverConfig = ServerConfig(server, prefs.validateSsl)
        val okHttpClient = SslHelper().createOkHttpClient(serverConfig)
        Timber.i("Connecting to $server")

        Retrofit
            .Builder()
            .baseUrl(server)
            .client(okHttpClient)
            .addConverterFactory(buildSerializer())
            .build()
            .create(NCMemoriesApi::class.java)
    }

    suspend fun getSelectedAlbumsFromAPI(): List<Image> =
        coroutineScope {
            try {
                val selectedAlbumIds = prefs.selectedAlbumIds
                if (selectedAlbumIds.isEmpty()) {
                    return@coroutineScope Album(
                        id = "combined", // Use a special ID for the combined album
                        name = "",
                        description = "No albums selected",
                    )
                }

                Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")
                Timber.d("Selected Album IDs: $selectedAlbumIds")

                val allImages = mutableListOf<Image>()
                val successfulAlbumNames = mutableListOf<String>()
                val albumNamesByImageId = mutableMapOf<String, MutableSet<String>>()

                val albumDeferreds =
                    selectedAlbumIds.map { albumId ->
                        async {
                            Pair(albumId, client.getDays(albumId = albumId))
                        }
                    }

                val albumResponses = albumDeferreds.awaitAll()

                for (albumResponse in albumResponses) {
                    val albumId = albumResponse.first
                    val response = albumResponse.second
                    Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")

                    // TODO: apply album name to each image

                    if (response.isSuccessful) {
                        val album = response.body()
                        if (album != null) {
                            Timber.d("Successfully fetched album: ${album.name}, images: ${album.images.size}")
                            successfulAlbumNames.add(album.name)
                            val albumImages = album.imagess.map { it.copy(albumName = album.name) }
                            allImages.addAll(albumImages)
                            albumImages.forEach { image ->
                                albumNamesByImageId.getOrPut(image.id) { mutableSetOf() }
                                    .add(album.name)
                            }
                        } else {
                            Timber.e("Received null album from successful response for album ID: $albumId")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Timber.e("Failed to fetch album $albumId. Code: ${response.code()}, Error: $errorBody")
                        // Continue with other albums instead of failing completely
                    }
                }

                if (allImages.isEmpty()) {
                    throw Exception("No imagess found in any of the selected albums")
                }

                // Remove duplicate imagess based on ID
                val successfulAlbumCount = successfulAlbumNames.size
                val isSingleAlbumSelection = successfulAlbumCount == 1
                val uniqueImages =
                    allImages
                        .distinctBy { it.id }
                        .map { image ->
                            val albumNames = albumNamesByImageId[image.id].orEmpty()
                            val resolvedAlbumName =
                                when {
                                    isSingleAlbumSelection -> albumNames.singleOrNull()
                                    albumNames.size == 1 -> albumNames.first()
                                    else -> null
                                }
                            image.copy(albumName = resolvedAlbumName)
                        }
                Timber.d(
                    "Combined ${allImages.size} images from $successfulAlbumCount successful albums " +
                            "(${selectedAlbumIds.size} selected), ${uniqueImages.size} unique images",
                )

                // Return a combined album with all images
                return@coroutineScope Album(
                    id = "combined", // Use a special ID for the combined album
                    name = successfulAlbumNames.joinToString(", "),
                    description = "Combined album from $successfulAlbumCount selected albums",
                    count = uniqueImages.size,
                    images = uniqueImages,
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching selected albums")
                throw Exception("Failed to fetch selected albums", e)
            }
        }

    private fun getTypeFilter(): String? =
        when (prefs.mediaType) {
            ProviderMediaType.VIDEOS -> "VIDEO"
            ProviderMediaType.PHOTOS -> "IMAGE"
            ProviderMediaType.VIDEOS_PHOTOS -> null
            else -> null
        }

    suspend fun getFavoriteImagesFromAPI(): List<Image> {
        try {
            val count = prefs.includeFavorites.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching up to $count favorite imagess")
            val searchRequest =
                DaysRequest(
                    isFavorite = true,
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = client.getFavoriteImagess(searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val allImages = searchResponse?.images?.items ?: emptyList()
                val limitedImages = allImages.take(count)
                Timber.d("Successfully fetched ${limitedImages.size} favorite images (from ${allImages.size} total)")
                return limitedImages
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch favorites. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch favorite images: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching favorite images")
            throw Exception("Failed to fetch favorite images", e)
        }
    }

    suspend fun getRecentImagesFromAPI(): List<Image> {
        try {
            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count recent images")
            val searchRequest =
                DaysRequest(
                    size = count,
                    order = "desc",
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = client.getRecentImages(searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val images = searchResponse?.images?.items ?: emptyList()
                Timber.d("Successfully fetched ${images.size} recent images")
                return images
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch recent images. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch recent images: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching recent images")
            throw Exception("Failed to fetch recent images", e)
        }
    }

    suspend fun fetchAlbumList(): Result<List<Album>> =
        coroutineScope {
            try {
                val albumListQueryDeferred = async { client.getAlbumList() }

                val response = albumListQueryDeferred.await()
                val fetchedAlbums =
                    if (response.isSuccessful) {
                        response.body() ?: emptyList()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        val errorMessage =
                            try {
                                Json.decodeFromString<ErrorResponse>(errorBody).message
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing error body: $errorBody")
                                response.message()
                            }
                        return@coroutineScope Result.failure(Exception("${response.code()} - $errorMessage"))
                    }

                Timber.d(
                    "Fetched ${fetchedAlbums.size} albums",
                )

                Result.success(fetchedAlbums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

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
                return@coroutineScope emptyList()
//                val selectedAlbumIds = prefs.selectedAlbumIds
//                if (selectedAlbumIds.isEmpty()) {
//                    return@coroutineScope Album(
//                        album_id = 0, // Use a special ID for the combined album
//                        name = "",
//                    )
//                }
//
//                Timber.d("Attempting to fetch ${selectedAlbumIds.size} selected albums")
//                Timber.d("Selected Album IDs: $selectedAlbumIds")
//
//                val allImages = mutableListOf<Image>()
//                val successfulAlbumNames = mutableListOf<String>()
//                val albumNamesByImageId = mutableMapOf<String, MutableSet<String>>()
//
//                val albumDeferreds =
//                    selectedAlbumIds.map { albumId ->
//                        async {
//                            Pair(albumId, client.getDays(albumId = albumId))
//                        }
//                    }
//
//                val albumResponses = albumDeferreds.awaitAll()
//
//                for (albumResponse in albumResponses) {
//                    val albumId = albumResponse.first
//                    val response = albumResponse.second
//                    Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")
//
//                    if (response.isSuccessful) {
//                        val album = response.body()
//                        if (album != null) {
//                            Timber.d("Successfully fetched album: ${album.name}, images: ${album.images.size}")
//                            successfulAlbumNames.add(album.name)
//                            val albumImages = album.images.map { it.copy(albumName = album.name) }
//                            allImages.addAll(albumImages)
//                            albumImages.forEach { image ->
//                                albumNamesByImageId.getOrPut(image.id) { mutableSetOf() }
//                                    .add(album.name)
//                            }
//                        } else {
//                            Timber.e("Received null album from successful response for album ID: $albumId")
//                        }
//                    } else {
//                        val errorBody = response.errorBody()?.string()
//                        Timber.e("Failed to fetch album $albumId. Code: ${response.code()}, Error: $errorBody")
//                        // Continue with other albums instead of failing completely
//                    }
//                }
//
//                if (allImages.isEmpty()) {
//                    throw Exception("No images found in any of the selected albums")
//                }
//
//                // Remove duplicate images based on ID
//                val successfulAlbumCount = successfulAlbumNames.size
//                val isSingleAlbumSelection = successfulAlbumCount == 1
//                val uniqueImages =
//                    allImages
//                        .distinctBy { it.id }
//                        .map { image ->
//                            val albumNames = albumNamesByImageId[image.id].orEmpty()
//                            val resolvedAlbumName =
//                                when {
//                                    isSingleAlbumSelection -> albumNames.singleOrNull()
//                                    albumNames.size == 1 -> albumNames.first()
//                                    else -> null
//                                }
//                            image.copy(albumName = resolvedAlbumName)
//                        }
//                Timber.d(
//                    "Combined ${allImages.size} images from $successfulAlbumCount successful albums " +
//                            "(${selectedAlbumIds.size} selected), ${uniqueImages.size} unique images",
//                )
//
//                // Return a combined album with all images
//                return@coroutineScope Album(
//                    id = 0, // Use a special ID for the combined album
//                    name = successfulAlbumNames.joinToString(", "),
//                    count = uniqueImages.size,
//                    images = uniqueImages,
//                )
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

    suspend fun getFavoriteImagesFromAPI(): List<Image> =
        coroutineScope {
            try {
                val count =
                    prefs.includeFavorites.toIntOrNull() ?: return@coroutineScope emptyList()
                Timber.d("Fetching up to $count favorite images")

                val favoriteDaysResponse = client.getFavoriteDays(fav = 1)
                if (favoriteDaysResponse.isSuccessful) {
                    // fetch days for favorite images
                    val allDays = favoriteDaysResponse.body() ?: emptyList()

                    if (allDays.isEmpty()) {
                        throw Exception("No days found in favorites")
                    }

                    // fetch images from days
                    Timber.d("Attempting to fetch ${allDays.size} favorite days")

                    val dayids = mutableListOf<Int>()
                    allDays.forEach { day ->
                        dayids.add(day.dayID)
                    }

                    val allImagesResponse =
                        client.getPhotos(dayids = dayids.joinToString(","), fav = 1)
                    if (allImagesResponse.isSuccessful) {
                        val allImages = allImagesResponse.body() ?: emptyList()

                        if (allImages.isEmpty()) {
                            throw Exception("No images found in favorites")
                        }

                        val limitedImages = allImages.take(count)

                        // fetch image info
                        val currentAlbumName = "Favorites"

                        val limitedImagesFull = mutableListOf<Image>()

                        val imageInfoDeferreds =
                            limitedImages.map { image ->
                                async {
                                    Pair(
                                        image.fileid,
                                        client.getFullImageInfo(fileid = image.fileid)
                                    )
                                }
                            }

                        val imageInfoResponses = imageInfoDeferreds.awaitAll()

                        for (imageInfoResponsePair in imageInfoResponses) {
                            val imageId = imageInfoResponsePair.first
                            val imageInfoResponse = imageInfoResponsePair.second
                            Timber.d("API Request for image $imageId - URL: ${imageInfoResponse.raw().request.url}")

                            if (imageInfoResponse.isSuccessful) {
                                val fullInfo = imageInfoResponse.body()
                                if (fullInfo != null) {
                                    Timber.d("Successfully fetched image: ${fullInfo.basename}")

                                    // Apply album name to each image
                                    limitedImagesFull.add(fullInfo.copy(albumName = currentAlbumName))
                                } else {
                                    Timber.e("Received null image from successful response for image ID: $imageId")
                                }
                            } else {
                                val errorBody = imageInfoResponse.errorBody()?.string()
                                Timber.e("Failed to fetch image info $imageId. Code: ${imageInfoResponse.code()}, Error: $errorBody")
                                // Continue with other images
                            }
                        }

                        Timber.d("Successfully fetched ${limitedImagesFull.size} favorite images (from ${allImages.size} total)")
                        return@coroutineScope limitedImagesFull

                    } else {
                        val errorBody = allImagesResponse.errorBody()?.string()
                        Timber.e("Failed to fetch favorite images. Code: ${allImagesResponse.code()}, Error: $errorBody")
                        throw Exception("Failed to fetch favorite images: ${allImagesResponse.code()} - ${allImagesResponse.message()}")
                    }
                } else {
                    val errorBody = favoriteDaysResponse.errorBody()?.string()
                    Timber.e("Failed to fetch favorites days. Code: ${favoriteDaysResponse.code()}, Error: $errorBody")
                    throw Exception("Failed to fetch favorite days: ${favoriteDaysResponse.code()} - ${favoriteDaysResponse.message()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while fetching favorite images")
                throw Exception("Failed to fetch favorite images", e)
            }
        }

    suspend fun getRecentImagesFromAPI(): List<Image> {
        try {
            return emptyList()
//            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
//            Timber.d("Fetching $count recent images")
//            val response = client.getRecentDays()
//            if (response.isSuccessful) {
//                val searchResponse = response.body()
//                val images = searchResponse?.images?.items ?: emptyList()
//                Timber.d("Successfully fetched ${images.size} recent images")
//                return images
//            } else {
//                val errorBody = response.errorBody()?.string()
//                Timber.e("Failed to fetch recent images. Code: ${response.code()}, Error: $errorBody")
//                throw Exception("Failed to fetch recent images: ${response.code()} - ${response.message()}")
//            }
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

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

    suspend fun getSelectedAlbumFromAPI(): Album =
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

                val allAssets = mutableListOf<Asset>()
                val successfulAlbumNames = mutableListOf<String>()
                val albumNamesByAssetId = mutableMapOf<String, MutableSet<String>>()

                val albumDeferreds =
                    selectedAlbumIds.map { albumId ->
                        async {
                            Pair(albumId, client.getAlbum(albumId = albumId))
                        }
                    }

                val albumResponses = albumDeferreds.awaitAll()

                for (albumResponse in albumResponses) {
                    val albumId = albumResponse.first
                    val response = albumResponse.second
                    Timber.d("API Request for album $albumId - URL: ${response.raw().request.url}")

                    if (response.isSuccessful) {
                        val album = response.body()
                        if (album != null) {
                            Timber.d("Successfully fetched album: ${album.name}, assets: ${album.assets.size}")
                            successfulAlbumNames.add(album.name)
                            val albumAssets = album.assets.map { it.copy(albumName = album.name) }
                            allAssets.addAll(albumAssets)
                            albumAssets.forEach { asset ->
                                albumNamesByAssetId.getOrPut(asset.id) { mutableSetOf() }
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

                if (allAssets.isEmpty()) {
                    throw Exception("No assets found in any of the selected albums")
                }

                // Remove duplicate assets based on ID
                val successfulAlbumCount = successfulAlbumNames.size
                val isSingleAlbumSelection = successfulAlbumCount == 1
                val uniqueAssets =
                    allAssets
                        .distinctBy { it.id }
                        .map { asset ->
                            val albumNames = albumNamesByAssetId[asset.id].orEmpty()
                            val resolvedAlbumName =
                                when {
                                    isSingleAlbumSelection -> albumNames.singleOrNull()
                                    albumNames.size == 1 -> albumNames.first()
                                    else -> null
                                }
                            asset.copy(albumName = resolvedAlbumName)
                        }
                Timber.d(
                    "Combined ${allAssets.size} assets from $successfulAlbumCount successful albums " +
                            "(${selectedAlbumIds.size} selected), ${uniqueAssets.size} unique assets",
                )

                // Return a combined album with all assets
                return@coroutineScope Album(
                    id = "combined", // Use a special ID for the combined album
                    name = successfulAlbumNames.joinToString(", "),
                    description = "Combined album from $successfulAlbumCount selected albums",
                    assetCount = uniqueAssets.size,
                    assets = uniqueAssets,
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

    suspend fun getFavoriteAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeFavorites.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching up to $count favorite assets")
            val searchRequest =
                SearchMetadataRequest(
                    isFavorite = true,
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = client.getFavoriteAssets(searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val allAssets = searchResponse?.assets?.items ?: emptyList()
                val limitedAssets = allAssets.take(count)
                Timber.d("Successfully fetched ${limitedAssets.size} favorite assets (from ${allAssets.size} total)")
                return limitedAssets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch favorites. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch favorite assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching favorite assets")
            throw Exception("Failed to fetch favorite assets", e)
        }
    }

    suspend fun getRecentAssetsFromAPI(): List<Asset> {
        try {
            val count = prefs.includeRecent.toIntOrNull() ?: return emptyList()
            Timber.d("Fetching $count recent assets")
            val searchRequest =
                SearchMetadataRequest(
                    size = count,
                    order = "desc",
                    withExif = true,
                    type = getTypeFilter(),
                )
            val response = client.getRecentAssets(searchRequest = searchRequest)
            if (response.isSuccessful) {
                val searchResponse = response.body()
                val assets = searchResponse?.assets?.items ?: emptyList()
                Timber.d("Successfully fetched ${assets.size} recent assets")
                return assets
            } else {
                val errorBody = response.errorBody()?.string()
                Timber.e("Failed to fetch recent assets. Code: ${response.code()}, Error: $errorBody")
                throw Exception("Failed to fetch recent assets: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while fetching recent assets")
            throw Exception("Failed to fetch recent assets", e)
        }
    }

    suspend fun fetchAlbums(): Result<List<Album>> =
        coroutineScope {
            try {
                val regularDeferred = async { client.getAlbums() }
                val sharedDeferred = async { client.getAlbums(shared = true) }

                // Fetch regular albums
                val regularResponse = regularDeferred.await()
                val regularAlbums =
                    if (regularResponse.isSuccessful) {
                        regularResponse.body() ?: emptyList()
                    } else {
                        val errorBody = regularResponse.errorBody()?.string() ?: ""
                        val errorMessage =
                            try {
                                Json.decodeFromString<ErrorResponse>(errorBody).message
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing error body: $errorBody")
                                regularResponse.message()
                            }
                        return@coroutineScope Result.failure(Exception("${regularResponse.code()} - $errorMessage"))
                    }

                // Fetch shared albums
                val sharedResponse = sharedDeferred.await()
                val sharedAlbums =
                    if (sharedResponse.isSuccessful) {
                        sharedResponse.body() ?: emptyList()
                    } else {
                        // If shared albums fetch fails, log warning but continue with regular albums only
                        Timber.w("Failed to fetch shared albums: ${sharedResponse.code()} - ${sharedResponse.message()}")
                        emptyList()
                    }

                // Combine and deduplicate albums by ID
                val allAlbums = (regularAlbums + sharedAlbums).distinctBy { it.id }
                Timber.d(
                    "Fetched ${regularAlbums.size} regular albums and ${sharedAlbums.size} shared albums (${allAlbums.size} total unique)",
                )

                Result.success(allAlbums)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

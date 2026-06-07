package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesAssetPrefs
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.providers.ncmemories.Asset
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesUrlBuilder
import timber.log.Timber

class NCMemoriesAssetMapper(
    private val prefs: NCMemoriesAssetPrefs,
    private val urlBuilder: NCMemoriesUrlBuilder,
) {
    data class ProcessResults(
        val media: List<AerialMedia>,
        val excluded: Int,
        val videos: Int,
        val images: Int,
    )

    fun filterAssetsByMediaType(assets: List<Asset>): List<Asset> =
        assets.filter { asset ->
            val filename = asset.originalPath
            when {
                FileHelper.isSupportedVideoType(filename) -> prefs.includeVideos
                FileHelper.isSupportedImageType(filename) -> prefs.includePhotos
                else -> false // Exclude unsupported files
            }
        }

    fun processAssets(assets: List<Asset>): ProcessResults {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videos = 0
        var images = 0

        assets.forEach { asset ->
            val filename = asset.originalPath
            val isVideo = FileHelper.isSupportedVideoType(filename)
            val isImage = FileHelper.isSupportedImageType(filename)

            if (isVideo || isImage) {
                val rawExif = asset.exifInfo

                Timber.i(
                    "Nextcloud Memories EXIF: path=%s localDateTime=%s description=%s city=%s state=%s country=%s",
                    filename,
                    asset.localDateTime,
                    asset.description ?: rawExif?.description,
                    rawExif?.city,
                    rawExif?.state,
                    rawExif?.country,
                )

                val exif = extractExifMetadata(asset)
                val uri = urlBuilder.getAssetUri(asset.id, isVideo)
                val item =
                    AerialMedia(
                        uri,
                        metadata =
                            AerialMediaMetadata(
                                albumName = asset.albumName.orEmpty(),
                                exif = exif,
                            ),
                    ).apply {
                        source = AerialMediaSource.IMMICH
                        type = if (isVideo) AerialMediaType.VIDEO else AerialMediaType.IMAGE
                    }

                if (isVideo) {
                    videos++
                    if (prefs.includeVideos) {
                        media.add(item)
                    }
                } else {
                    images++
                    if (prefs.includePhotos) {
                        media.add(item)
                    }
                }
            } else {
                excluded++
            }
        }

        return ProcessResults(
            media = media,
            excluded = excluded,
            videos = videos,
            images = images,
        )
    }

    private fun extractExifMetadata(asset: Asset): AerialExifMetadata {
        val exifInfo = asset.exifInfo
        return AerialExifMetadata(
            date = asset.localDateTime,
            offset = null,
            city = exifInfo?.city,
            state = exifInfo?.state,
            country = exifInfo?.country,
            description = asset.description ?: exifInfo?.description,
        )
    }
}

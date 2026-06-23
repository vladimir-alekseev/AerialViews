package com.neilturner.aerialviews.providers.ncmemories

import com.neilturner.aerialviews.data.storage.FileHelper
import com.neilturner.aerialviews.models.enums.AerialMediaSource
import com.neilturner.aerialviews.models.enums.AerialMediaType
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.models.videos.AerialExifMetadata
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.AerialMediaMetadata
import com.neilturner.aerialviews.providers.ncmemories.Image
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesUrlBuilder
import com.neilturner.aerialviews.utils.toStringOrEmpty
import timber.log.Timber
import kotlin.time.Instant

class NCMemoriesImageMapper(
    private val prefs: NCMemoriesMediaPrefs,
    private val urlBuilder: NCMemoriesUrlBuilder,
) {
    data class ProcessResults(
        val media: List<AerialMedia>,
        val excluded: Int,
        val videos: Int,
        val photos: Int,
    )

    fun filterImagesByMediaType(images: List<Image>): List<Image> =
        images.filter { image ->
            val filename = image.basename
            when {
                FileHelper.isSupportedVideoType(filename) -> prefs.includeVideos
                FileHelper.isSupportedImageType(filename) -> prefs.includePhotos
                else -> false // Exclude unsupported files
            }
        }

    fun processImages(images: List<Image>): ProcessResults {
        val media = mutableListOf<AerialMedia>()
        var excluded = 0
        var videosCount = 0
        var photosCount = 0

        images.forEach { image ->
            val filename = image.basename
            val isVideo = FileHelper.isSupportedVideoType(filename)
            val isImage = FileHelper.isSupportedImageType(filename)

            if (isVideo || isImage) {
                val rawExif = image.exif
                val exif = extractExifMetadata(image)

                val uri = urlBuilder.getImageUri(image.fileid, isVideo, image.etag)
                Timber.i(
                    "Immich EXIF: basename=%s album=%s",
                    filename,
                    image.albumName.orEmpty(),
                )
                val item =
                    AerialMedia(
                        uri,
                        metadata =
                            AerialMediaMetadata(
                                exif = exif,
                                albumName = image.albumName.orEmpty(),
                                title = rawExif?.Title.orEmpty(),
                                shortDescription = image.filename.orEmpty(),
                            ),
                    ).apply {
                        source = AerialMediaSource.NCMEMORIES
                        type = if (isVideo) AerialMediaType.VIDEO else AerialMediaType.IMAGE
                    }

                if (isVideo) {
                    videosCount++
                    if (prefs.includeVideos) {
                        media.add(item)
                    }
                } else {
                    photosCount++
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
            videos = videosCount,
            photos = photosCount,
        )
    }

    private fun extractExifMetadata(image: Image): AerialExifMetadata {
        val exifInfo = image.exif
        val imageEpoch = image.epoch ?: exifInfo?.DateTimeEpoch

        return AerialExifMetadata(
            date = imageEpoch?.let { Instant.fromEpochSeconds(it) }.toString(),
            offset = exifInfo?.OffsetTimeOriginal,
            latitude = exifInfo?.GPSLatitude?.toDoubleOrNull(),
            longitude = exifInfo?.GPSLongitude?.toDoubleOrNull(),
            description = exifInfo?.Description,
        )
    }


}

package com.neilturner.aerialviews.providers.ncmemories

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.NCMemoriesImageType
import com.neilturner.aerialviews.models.enums.NCMemoriesVideoType
import com.neilturner.aerialviews.models.prefs.NCMemoriesUrlPrefs

class NCMemoriesUrlBuilder(
    private val server: String,
    private val prefs: NCMemoriesUrlPrefs,
    private val uriFactory: (String) -> Uri = { it.toUri() },
) {

    fun getImageUri(
        fileID: String,
        isVideo: Boolean,
        etag: String,
    ): Uri {
        val url: String
        url =
                // "preview" will use preview-reencoded pic as configured within NCMemories
            if (isVideo) {
                if (prefs.videoType == NCMemoriesVideoType.TRANSCODED) {
                    var client = "aerialviews"
                    var filename = "index.m3u8"
                    "$server/apps/memories/api/video/transcode/$client/$fileID/$filename"
                } else {
                    "$server/apps/memories/api/stream/$fileID"
                }
            } else {
                if (prefs.imageType == NCMemoriesImageType.ORIGINAL) {
                    "$server/apps/memories/api/image/decodable/$fileID?etag=$etag"
                } else {
                    "$server/apps/memories/api/image/preview/$fileID"
                }
            }
        return uriFactory(url)
    }
}

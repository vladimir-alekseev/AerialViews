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

    fun getAssetUri(
        id: String,
        isVideo: Boolean,
    ): Uri {
        val url: String
        url =
                // "preview" will use preview-reencoded pic as configured within NCMemories
            if (isVideo) {
                if (prefs.videoType == NCMemoriesVideoType.TRANSCODED) {
                    "$server/api/assets/$id/video/playback"
                } else {
                    "$server/api/assets/$id/original"
                }
            } else {
                if (prefs.imageType == NCMemoriesImageType.ORIGINAL) {
                    "$server/api/assets/$id/original"
                } else {
                    val size = "preview"
                    "$server/api/assets/$id/thumbnail?size=$size"
                }
            }
        return uriFactory(url)
    }
}

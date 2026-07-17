package com.neilturner.aerialviews.providers.ncmemories

import android.content.res.Resources
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
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val screenHeight = Resources.getSystem().displayMetrics.heightPixels

    fun getImageUri(
        fileID: Int,
        isVideo: Boolean,
        etag: String,
    ): Uri {
		val fileIDString = fileID.toString()
	    val url: String = if (isVideo) {
	            if (prefs.videoType == NCMemoriesVideoType.TRANSCODED) {
	                val client = "aerialviews"
	                val filename = "index.m3u8"
	                "$server/apps/memories/api/video/transcode/$client/$fileIDString/$filename"
	            } else {
	                "$server/apps/memories/api/stream/$fileID"
	            }
	        } else {
	            if (prefs.imageType == NCMemoriesImageType.ORIGINAL) {
	                "$server/apps/memories/api/image/decodable/$fileID?etag=$etag"
	            } else {
	                "$server/apps/memories/api/image/preview/$fileID?x=$screenWidth&y=$screenHeight"
	            }
	        }
        return uriFactory(url)
    }
}

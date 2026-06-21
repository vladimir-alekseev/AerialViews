package com.neilturner.aerialviews.models

import android.net.Uri
import com.neilturner.aerialviews.models.videos.AerialMedia
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class MediaPlaylistTest {
    @Test
    fun `nextItem starts after saved current position`() {
        val first = testMedia()
        val second = testMedia()
        val third = testMedia()
        val playlist = MediaPlaylist(listOf(first, second, third), startPosition = 0)

        assertSame(second, playlist.nextItem())
    }

    @Test
    fun `nextItem starts at first item when no position has been saved`() {
        val first = testMedia()
        val second = testMedia()
        val playlist = MediaPlaylist(listOf(first, second), startPosition = -1)

        assertSame(first, playlist.nextItem())
    }

    private fun testMedia() = AerialMedia(uri = mockk<Uri>(relaxed = true))
}

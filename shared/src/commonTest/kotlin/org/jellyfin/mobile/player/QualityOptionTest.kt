package org.jellyfin.mobile.player

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityOptionTest {
    @Test
    fun `offers nothing above the source's own resolution`() {
        val options = qualityOptionsFor(videoWidth = 1920, videoHeight = 1080)

        // 4K rungs would be a lie: the server cannot invent detail a 1080p file does not have.
        assertEquals(1080, options.first().maxHeight)
        assertTrue(options.none { it.maxHeight > 1080 })
    }

    @Test
    fun `a 4K source gets the whole ladder`() {
        val options = qualityOptionsFor(videoWidth = 3840, videoHeight = 2160)

        assertEquals(2160, options.first().maxHeight)
        assertEquals(360, options.last().maxHeight)
    }

    @Test
    fun `a 480p source is offered only what it can fill`() {
        val options = qualityOptionsFor(videoWidth = 854, videoHeight = 480)

        assertEquals(listOf(480, 480, 480, 360), options.map { it.maxHeight })
    }

    @Test
    fun `judges a pillarboxed source by the width it would occupy on a 16 by 9 screen`() {
        // 4:3 1440x1080 is a 1080p file. Judging it on its raw width would demote it to 720p.
        val fourThree = qualityOptionsFor(videoWidth = 1440, videoHeight = 1080)
        val sixteenNine = qualityOptionsFor(videoWidth = 1920, videoHeight = 1080)

        assertEquals(sixteenNine, fourThree)
    }

    @Test
    fun `treats 1912 as the 1080p some servers report it as`() {
        val options = qualityOptionsFor(videoWidth = 1912, videoHeight = 1080)

        assertEquals(1080, options.first().maxHeight)
    }

    @Test
    fun `offers everything when the server did not report the source's size`() {
        // Better to show a ladder the user can experiment with than to guess and hide rungs.
        val unknown = qualityOptionsFor(videoWidth = null, videoHeight = null)

        assertEquals(2160, unknown.first().maxHeight)
        assertEquals(unknown, qualityOptionsFor(videoWidth = 0, videoHeight = 0))
    }

    @Test
    fun `every rung has a distinct bitrate, since that is what identifies one`() {
        val bitrates = qualityOptionsFor(3840, 2160).map { it.bitrate }

        assertEquals(bitrates.size, bitrates.distinct().size)
        // Descending, so the menu reads best-first.
        assertEquals(bitrates.sortedDescending(), bitrates)
        // No zero rung: "auto" is the absence of a cap, not a value from this list.
        assertTrue(bitrates.all { it > 0 })
    }

    @Test
    fun `keeps the bitrates jellyfin-web uses, so clients agree on what a quality means`() {
        val bitrates = qualityOptionsFor(3840, 2160).map { it.bitrate }

        assertContains(bitrates, 120_000_000)
        assertContains(bitrates, 20_000_000)
        assertContains(bitrates, 420_000)
    }
}

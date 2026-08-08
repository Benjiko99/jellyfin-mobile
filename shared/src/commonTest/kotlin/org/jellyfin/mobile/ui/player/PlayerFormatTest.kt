package org.jellyfin.mobile.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The player's two number formatters.
 *
 * Both are hand-rolled because `String.format` is JVM-only and this code compiles for iOS, which
 * is exactly the sort of thing worth pinning.
 */
class PlayerFormatTest {
    @Test
    fun `drops the hour below an hour, and shows it above`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:07", formatTime(7_000))
        assertEquals("42:03", formatTime(2_523_000))
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("2:04:05", formatTime(7_445_000))
    }

    @Test
    fun `treats a negative position as the start rather than printing a negative clock`() {
        // The engine can report a position slightly before zero while it is still seeking.
        assertEquals("0:00", formatTime(-5_000))
    }

    @Test
    fun `scales a bitrate to one decimal place and drops a trailing zero`() {
        assertEquals("120", scaled(120_000_000, 1_000_000))
        assertEquals("20", scaled(20_000_000, 1_000_000))
        assertEquals("1.5", scaled(1_500_000, 1_000_000))
        assertEquals("8.4", scaled(8_400_000, 1_000_000))
        assertEquals("720", scaled(720_000, 1_000))
        assertEquals("420", scaled(420_000, 1_000))
    }

    @Test
    fun `rounds rather than truncating, so 1_449_000 is not reported as 1_4`() {
        assertEquals("1.4", scaled(1_449_000, 1_000_000))
        assertEquals("1.5", scaled(1_450_000, 1_000_000))
    }
}

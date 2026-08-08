// Derived from jellyfin-android, GPL-2.0
// https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/org/jellyfin/mobile/player/qualityoptions/QualityOptionsProvider.kt

package org.jellyfin.mobile.player

/**
 * One rung of the streaming-quality ladder.
 *
 * [bitrate] is what actually gets sent — `maxStreamingBitrate` on `PlaybackInfo`. [maxHeight] is
 * only the resolution that bitrate is *meant* for, used to decide which rungs are worth offering;
 * the server picks the output size itself from the profile and the cap.
 */
data class QualityOption(
    val maxHeight: Int,
    /** Bits per second. */
    val bitrate: Int,
)

/**
 * The ladder, highest first.
 *
 * **Nothing in the API describes these.** `PlaybackInfo` takes a single `maxStreamingBitrate`
 * integer and returns no menu of choices, so the list of qualities a client offers is entirely the
 * client's invention. These are jellyfin-android's numbers, which are in turn jellyfin-web's, kept
 * identical so a user switching clients sees the same options.
 *
 * There is no "auto" rung: auto is the *absence* of a cap, which the caller expresses by sending no
 * `maxStreamingBitrate` at all rather than by picking a value from here.
 */
private val DefaultQualityOptions = listOf(
    QualityOption(maxHeight = 2160, bitrate = 120_000_000),
    QualityOption(maxHeight = 2160, bitrate = 80_000_000),
    QualityOption(maxHeight = 1080, bitrate = 60_000_000),
    QualityOption(maxHeight = 1080, bitrate = 40_000_000),
    QualityOption(maxHeight = 1080, bitrate = 20_000_000),
    QualityOption(maxHeight = 1080, bitrate = 15_000_000),
    QualityOption(maxHeight = 1080, bitrate = 10_000_000),
    QualityOption(maxHeight = 720, bitrate = 8_000_000),
    QualityOption(maxHeight = 720, bitrate = 6_000_000),
    QualityOption(maxHeight = 720, bitrate = 4_000_000),
    QualityOption(maxHeight = 480, bitrate = 3_000_000),
    QualityOption(maxHeight = 480, bitrate = 1_500_000),
    QualityOption(maxHeight = 480, bitrate = 720_000),
    QualityOption(maxHeight = 360, bitrate = 420_000),
)

/**
 * The rungs worth showing for a source of this size — everything at or below its own resolution.
 *
 * Offering 4K for a 480p file would be a lie: the server cannot invent detail, so the higher rungs
 * would all deliver the same picture under different names.
 *
 * Dimensions are nullable because they come from the server's probe of the file, which can come
 * back without them. When they do, the whole ladder is offered rather than a guess.
 */
@Suppress("MagicNumber")
fun qualityOptionsFor(videoWidth: Int?, videoHeight: Int?): List<QualityOption> {
    if (videoWidth == null || videoHeight == null || videoWidth <= 0 || videoHeight <= 0) {
        return DefaultQualityOptions
    }

    // Anything narrower than 16:9 is pillarboxed on a 16:9 screen, so judge it by the width it
    // would occupy there: 4:3 1440x1080 is a 1080p file and should be offered 1080p rungs.
    // Cross-multiplied rather than divided — Android's Rational is not multiplatform.
    val maxAllowedWidth = when {
        videoWidth * 9 < videoHeight * 16 -> videoHeight * 16 / 9
        else -> videoWidth
    }

    val maxAllowedHeight = when {
        maxAllowedWidth >= 3800 -> 2160
        // Some 1080p videos are apparently reported as 1912.
        maxAllowedWidth >= 1900 -> 1080
        maxAllowedWidth >= 1260 -> 720
        maxAllowedWidth >= 620 -> 480
        else -> 360
    }

    // The ladder descends, so the rungs that fit are its tail.
    return DefaultQualityOptions.takeLastWhile { option -> option.maxHeight <= maxAllowedHeight }
}

package org.jellyfin.mobile.player

import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.msToTicks
import org.jellyfin.mobile.network.ClientInfo
import org.jellyfin.mobile.network.StreamAuthorizer
import org.jellyfin.mobile.network.TEST_DEVICE_INFO
import org.jellyfin.mobile.network.testSession
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop engine against a real decode, because nothing below the engine can be faked usefully:
 * the parts worth testing are whether libVLC's callbacks arrive, what shape the picture comes back
 * in, and how VLC's events land on [PlayerState].
 *
 * `sample.mp4` is six seconds of a generated luma ramp, 320x240 and silent, encoded here with VLC
 * itself — small enough to check in, and beholden to nobody.
 *
 * Skipped rather than failed where libVLC is absent. It is not on the classpath and never will be:
 * VLCJ binds to a VLC installed on the machine, so a build box without one is a machine that cannot
 * run this, not a broken build.
 */
class VlcjPlayerEngineTest {
    @Test
    fun `plays a file, times it, and renders frames at the picture's own size`() {
        if (!NativeDiscovery().discover()) {
            println("Skipping: libVLC is not installed on this machine")
            return
        }

        val engine = VlcjPlayerEngine(StreamAuthorizer(testSession(), ClientInfo(), TEST_DEVICE_INFO))
        try {
            engine.load(source(sampleFile()))
            engine.play()

            waitFor("a first frame") { engine.frame.value != null }
            assertEquals(PlayerStatus.Ready, engine.state.value.status)
            assertTrue(engine.state.value.playWhenReady, "playing, so the intent to play should hold")

            // The buffer VLC asks for is padded to 320x258 partway through opening this file. What
            // reaches the screen has to be the video's own shape or the picture is stretched.
            assertEquals(SAMPLE_WIDTH, engine.frame.value?.width)
            assertEquals(SAMPLE_HEIGHT, engine.frame.value?.height)

            waitFor("the clock to move") { engine.positionMs() > 0 }
            assertTrue(engine.state.value.durationMs > 0, "the length should be known once opened")

            engine.seekTo(SEEK_TARGET_MS)
            waitFor("the seek to land") { engine.positionMs() >= SEEK_TARGET_MS }

            waitFor("the end of the file") { engine.state.value.status == PlayerStatus.Ended }
            assertFalse(engine.state.value.playWhenReady, "nothing is playing once it has ended")
        } finally {
            engine.release()
        }
    }

    @Test
    fun `starts where it was left`() {
        if (!NativeDiscovery().discover()) {
            println("Skipping: libVLC is not installed on this machine")
            return
        }

        val engine = VlcjPlayerEngine(StreamAuthorizer(testSession(), ClientInfo(), TEST_DEVICE_INFO))
        try {
            // A resume point cannot be seeked to until the media is open, so the engine holds it and
            // applies it when VLC says it is ready. This is the whole of that path.
            engine.load(source(sampleFile(), startPositionMs = SEEK_TARGET_MS))
            engine.play()

            waitFor("the resume point") { engine.positionMs() >= SEEK_TARGET_MS }
        } finally {
            engine.release()
        }
    }
}

private const val SAMPLE_WIDTH = 320
private const val SAMPLE_HEIGHT = 240
private const val SEEK_TARGET_MS = 3_000L
private const val TIMEOUT_MS = 15_000L
private const val POLL_MS = 50L

/**
 * libVLC opens a path, not a classpath entry, so the sample is unpacked next to the build output.
 * Reused between runs — it is the same twelve kilobytes every time.
 */
private fun sampleFile(): File {
    val file = File(System.getProperty("java.io.tmpdir"), "jellyfin-mobile-sample.mp4")
    if (!file.exists()) {
        val resource = checkNotNull(VlcjPlayerEngineTest::class.java.getResourceAsStream("/sample.mp4")) {
            "sample.mp4 is missing from the test resources"
        }
        resource.use { input -> file.outputStream().use(input::copyTo) }
    }
    return file
}

private fun source(file: File, startPositionMs: Long = 0) = PlaybackSource(
    itemId = "item-1",
    mediaSourceId = "source-1",
    playSessionId = "session-1",
    playMethod = PlayMethod.DirectPlay,
    // A plain path rather than a `file:` URL: it is what libVLC takes, and it belongs to no host, so
    // StreamAuthorizer leaves it alone exactly as it would a third-party stream.
    url = file.absolutePath,
    isHls = false,
    startPositionTicks = startPositionMs.msToTicks(),
)

/** Polls [condition] to a deadline, because everything here happens on libVLC's own threads. */
private fun waitFor(what: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(POLL_MS)
    }
    throw AssertionError("Timed out waiting for $what")
}

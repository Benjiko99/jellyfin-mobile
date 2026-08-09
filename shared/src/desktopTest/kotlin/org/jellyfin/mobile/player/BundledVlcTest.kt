package org.jellyfin.mobile.player

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The half of bundling that can be tested anywhere. That the packaged copy is the one VLCJ then
 * loads is [VlcjPlayerEngineTest]'s territory, and needs real binaries.
 */
class BundledVlcTest {
    private val resourcesProperty = System.getProperty(RESOURCES_DIR)
    private val libraryPathProperty = System.getProperty(LIBRARY_PATH)

    @AfterTest
    fun restoreProperties() {
        // Both are global to the JVM and this test writes them, so every other test in this fork
        // would inherit whatever was left behind.
        resourcesProperty.restore(RESOURCES_DIR)
        libraryPathProperty.restore(LIBRARY_PATH)
    }

    @Test
    fun `announces the packaged libVLC ahead of anything already on the path`() {
        val resources = createTempDirectory()
        val bundled = File(resources, "vlc").apply { mkdirs() }
        System.setProperty(RESOURCES_DIR, resources.path)
        System.setProperty(LIBRARY_PATH, "/somewhere/else")

        useBundledVlc()

        assertEquals(
            "${bundled.absolutePath}${File.pathSeparator}/somewhere/else",
            System.getProperty(LIBRARY_PATH),
            "the packaged copy has to win: the machine's own VLC can be any version at all",
        )
    }

    @Test
    fun `says nothing when the app was not packaged with libVLC`() {
        // A source build on macOS or Linux, where there is nothing to bundle yet. Discovery then
        // finds the system's VLC exactly as it did before.
        System.setProperty(RESOURCES_DIR, createTempDirectory().path)
        System.clearProperty(LIBRARY_PATH)

        useBundledVlc()

        assertTrue(System.getProperty(LIBRARY_PATH) == null, "nothing to announce, so nothing said")
    }

    @Test
    fun `says nothing when there are no application resources at all`() {
        // Running from a plain `java -cp`, or from a test like this one.
        System.clearProperty(RESOURCES_DIR)
        System.clearProperty(LIBRARY_PATH)

        useBundledVlc()

        assertTrue(System.getProperty(LIBRARY_PATH) == null)
    }
}

private const val RESOURCES_DIR = "compose.application.resources.dir"
private const val LIBRARY_PATH = "jna.library.path"

private fun String?.restore(property: String) {
    if (this == null) System.clearProperty(property) else System.setProperty(property, this)
}

private fun createTempDirectory(): File =
    File.createTempFile("jellyfin-mobile-resources", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

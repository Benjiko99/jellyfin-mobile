import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject

// A plain JVM module rather than a second multiplatform one: it has exactly one target, and the
// only thing it owns is `main()`. Everything it shows lives in `:shared`, the same way `iosApp/`
// only calls `MainViewController()`.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Stated rather than left to the toolchain default, which follows whichever JVM runs Gradle (21
// here). Kotlin and Java targets have to agree or Gradle fails the build on the mismatch, and
// pinning both keeps that independent of the machine doing the building.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    // The Skiko binaries for whoever is building. `:shared` deliberately depends on
    // `compose.desktop.common` instead, so the library carries no host-specific artifact.
    implementation(compose.desktop.currentOs)
}

/**
 * libVLC, fetched from VideoLAN and laid out for the app to load instead of the machine's own VLC.
 *
 * Nobody should have to install a second application to watch a film, which is what `VlcjPlayerEngine`
 * otherwise requires — see PLAN.md §6.5. The pieces taken are the two libraries and the plugin
 * directory; the rest of the download is the VLC application itself, which we have no use for.
 *
 * **Windows only so far.** macOS ships a `.dmg` that only `hdiutil` on macOS can open, and VideoLAN
 * publishes no portable Linux build at all — libVLC there is a distribution package. Both fall back
 * to the machine's own VLC, which is what every platform did before this. Extending it means doing
 * the extraction on a machine that can be tested, rather than writing it blind here.
 */
val vlcVersion = "3.0.23"
val osName = providers.systemProperty("os.name").get()
val isWindows = osName.startsWith("Windows")
val isLinux = osName.startsWith("Linux")

abstract class DownloadVlc : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    /**
     * Pinned, and the reason the download may cross plain HTTP: `get.videolan.org` redirects to
     * whichever mirror is nearest and some of those are HTTP-only, so what guarantees these bytes is
     * this hash — published over HTTPS by VideoLAN — rather than the transport they arrived on.
     */
    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val archive: RegularFileProperty

    @TaskAction
    fun download() {
        val file = archive.get().asFile
        file.parentFile.mkdirs()

        val client = HttpClient.newBuilder()
            // ALWAYS rather than NORMAL: NORMAL refuses to follow HTTPS to HTTP, which is exactly
            // the redirect VideoLAN's mirrors hand out.
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
        val request = HttpRequest.newBuilder(URI(url.get())).build()
        val digest = MessageDigest.getInstance("SHA-256")

        client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body().use { body ->
            DigestInputStream(body, digest).use { input ->
                file.outputStream().use(input::copyTo)
            }
        }

        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        check(actual == sha256.get()) {
            "$file does not match the published checksum: expected ${sha256.get()}, got $actual"
        }
    }
}

val downloadVlc = tasks.register<DownloadVlc>("downloadVlc") {
    description = "Downloads the official VLC build that libVLC is taken from."
    url = "https://get.videolan.org/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
    sha256 = "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1"
    // Outside `build/` on purpose: eighty megabytes that a `clean` should not send us back for.
    archive = layout.projectDirectory.dir("../.gradle/vlc").file("vlc-$vlcVersion-win64.zip")
}

val bundleVlc = tasks.register<Copy>("bundleVlc") {
    description = "Lays libVLC out for `appResourcesRootDir`, in the shape a VLC install has."
    // A local, not the script's own property: the configuration cache cannot serialize a lambda
    // that reaches back into the build script, and `eachFile` below runs at execution time.
    val root = "vlc-$vlcVersion/"

    from(zipTree(downloadVlc.flatMap { it.archive })) {
        // The plugin directory has to keep its name and its shape: VLCJ points `VLC_PLUGIN_PATH` at
        // `<directory>/plugins` once it finds libvlc, which is why this mirrors an installation
        // rather than flattening everything into one folder.
        include("${root}libvlc.dll", "${root}libvlccore.dll", "${root}plugins/**")
        // Thirty-odd megabytes of VLC being an application rather than a decoder. `gui` is its Qt
        // interface and skins, which we never show; `visualization` draws bars over music;
        // `services_discovery` browses UPnP and podcasts inside VLC; `access_output`, `stream_out`
        // and `mux` are for sending a stream out, which is the server's job and not ours. Everything
        // that opens, demuxes, decodes or draws is kept.
        exclude(
            "${root}plugins/gui/**",
            "${root}plugins/visualization/**",
            "${root}plugins/services_discovery/**",
            "${root}plugins/access_output/**",
            "${root}plugins/stream_out/**",
            "${root}plugins/mux/**",
        )
        eachFile { path = path.removePrefix(root) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("vlcResources/windows/vlc"))
}

/**
 * Names libVLC as a dependency of the `.deb`, which is how Linux answers the question Windows
 * answers by bundling: `apt` pulls libVLC in when the package is installed, nobody installs anything
 * by hand, and the distribution keeps it patched.
 *
 * Bundling is not the answer there. VideoLAN publishes no Linux binaries at all — only source — and
 * the plugins a distribution builds are linked against a web of system libraries (FFmpeg, alsa,
 * pulse, X11) that would have to come along, pinned to the glibc of whichever distribution they were
 * taken from. See PLAN.md §6.5.
 *
 * `libvlc5` carries the library and `vlc-plugin-base` the plugins we actually use — `avcodec` for
 * decoding, `vmem` for the callback surface `VlcjPlayerEngine` renders through, `pulse` for sound.
 * Debian and Ubuntu names; an `.rpm` would need its own list, which is one reason `targetFormats`
 * does not offer one.
 */
val vlcDebianPackages = listOf("libvlc5", "vlc-plugin-base")

/**
 * Rewrites the control file of an already-built `.deb`.
 *
 * A patch rather than a flag because there is nowhere to put the flag: jpackage takes
 * `--linux-package-deps`, but Compose's `LinuxPlatformSettings` does not expose it and its jpackage
 * task has no free-argument hook. Unpacking and repacking with `dpkg-deb` is the one thing that
 * needs no fork of the plugin, and it runs wherever a `.deb` can be built at all, since `dpkg-deb`
 * is what built it.
 */
abstract class DeclareDebDependencies : DefaultTask() {
    /**
     * Internal, not an input: this edits the `.deb` where it lies, so there is no separate output to
     * declare and nothing worth caching. Ordering comes from `finalizedBy`.
     */
    @get:Internal
    abstract val debDirectory: DirectoryProperty

    @get:Input
    abstract val packages: ListProperty<String>

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun declare() {
        val directory = debDirectory.get().asFile
        // Absent on any host that cannot build one. This task finalizes `packageDeb`, and a
        // finalizer runs even when what it follows has failed — which on Windows and macOS it has.
        val deb = directory.listFiles().orEmpty().firstOrNull { it.extension == "deb" }
        if (deb == null) {
            logger.info("No .deb in $directory, so there is nothing to declare a dependency on")
            return
        }

        val unpacked = File(temporaryDir, "deb").apply {
            deleteRecursively()
            mkdirs()
        }
        exec.exec { commandLine("dpkg-deb", "--raw-extract", deb.absolutePath, unpacked.absolutePath) }

        val control = File(unpacked, "DEBIAN/control")
        // Loud rather than silent: a control file that is not where Debian says it is means the
        // extraction did something other than what this expects, and quietly repacking an unchanged
        // package would ship one that installs without libVLC.
        check(control.isFile) { "No control file in ${control.parentFile}, so $deb was not unpacked as expected" }
        control.writeText(control.readText().dependingOn(packages.get()))

        exec.exec { commandLine("dpkg-deb", "--build", unpacked.absolutePath, deb.absolutePath) }
        logger.lifecycle("${deb.name} now depends on ${packages.get().joinToString(", ")}")
    }

    /**
     * Adds to the `Depends:` field jpackage already wrote, rather than replacing it — it lists what
     * the launcher itself needs (`xdg-utils` and friends), and dropping that would produce a package
     * that installs and then cannot start.
     */
    private fun String.dependingOn(required: List<String>): String {
        val declaration = required.joinToString(", ")
        if (contains(declaration)) return this

        val lines = trimEnd().lines()
        val field = lines.indexOfFirst { it.startsWith("Depends:", ignoreCase = true) }
        val patched = when {
            field < 0 -> lines + "Depends: $declaration"
            else -> lines.toMutableList().apply {
                val existing = this[field].substringAfter(':').trim().trimEnd(',')
                this[field] = if (existing.isEmpty()) {
                    "Depends: $declaration"
                } else {
                    "Depends: $existing, $declaration"
                }
            }
        }
        // Trailing newline included: a control file without one is malformed.
        return patched.joinToString("\n") + "\n"
    }
}

val declareVlcDependency = tasks.register<DeclareDebDependencies>("declareVlcDebDependency") {
    description = "Adds libVLC to the Depends field of the packaged .deb."
    packages = vlcDebianPackages
}

/*
 * Wired after evaluation, because `packageDeb` does not exist while this file is being read: Compose
 * registers its packaging tasks out of the `compose.desktop` block below. The obvious alternative —
 * configuring this task from inside a `configureEach` on that one — is refused outright by Gradle,
 * which does not allow one task's configuration to reach into another's.
 *
 * A finalizer rather than a task of its own, so every route that builds a `.deb` gets the dependency
 * — `packageDeb`, `packageDistributionForCurrentOS`, and whatever CI ends up calling.
 */
afterEvaluate {
    if ("packageDeb" !in tasks.names) return@afterEvaluate

    val packageDeb = tasks.named<AbstractJPackageTask>("packageDeb")
    declareVlcDependency.configure { debDirectory = packageDeb.flatMap { it.destinationDir } }
    packageDeb.configure { finalizedBy(declareVlcDependency) }
}

compose.desktop {
    application {
        mainClass = "org.jellyfin.mobile.MainKt"

        nativeDistributions {
            // Copied next to the app, and reachable at runtime through the
            // `compose.application.resources.dir` property Compose sets for both `run` and a
            // packaged build. `BundledVlc` in `:shared` is the other half.
            if (isWindows) {
                appResourcesRootDir.set(bundleVlc.map { layout.buildDirectory.dir("vlcResources").get() })
            }

            // jpackage builds each installer on its own operating system — an .msi can only be
            // produced on Windows, a .dmg on macOS — so listing all three describes what a full
            // release needs rather than what any one machine can make.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Jellyfin"
            // Not the app's version: jpackage requires MAJOR.MINOR.PATCH with a non-zero major on
            // every platform, so it cannot carry the 0.x that `ClientInfo` reports to the server.
            packageVersion = "1.0.0"
        }
    }
}

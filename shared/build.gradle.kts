import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // Named rather than plain `jvm()` so the source sets read `desktopMain`/`desktopTest`: this
    // target is a desktop application, not a server or a library for other JVM consumers, and the
    // name is what the whole build (including `:desktopApp`) refers to it by.
    jvm("desktop") {
        compilerOptions {
            // Compose Desktop and Skiko need 11 at minimum; 17 is what jpackage-based packaging
            // expects and there is no older JVM to support here, unlike Android's minSdk.
            jvmTarget = JvmTarget.JVM_17
        }
    }

    android {
        namespace = "org.jellyfin.mobile.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            // For `LocalActivity` and `enableEdgeToEdge`, which the theme re-applies with the app's
            // own light/dark choice rather than the device's — see `SystemBarAppearance`.
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)

            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.ui)
            implementation(libs.jellyfin.media3.ffmpeg.decoder)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            // No `compose.desktop.*` here on purpose: `MainWindow` needs `Window` and
            // `application`, which are already in the desktop variant of `compose.ui` that
            // `commonMain` depends on. Naming `compose.desktop.currentOs` would additionally pin
            // the Skiko binary of whichever machine built the library — that belongs to
            // `:desktopApp`, which resolves it per host.
            implementation(libs.kotlinx.coroutines.swing)
            // libVLC, through JNA. The binaries are *not* in this dependency: vlcj binds to a VLC
            // installed on the machine, which is why `VlcjPlayerEngine` has a message for the case
            // where there isn't one. Bundling libVLC with the app is a packaging decision — PLAN §6.5.
            implementation(libs.vlcj)
            // The same engine Android uses. It is a plain JVM library, so there is nothing
            // Android-specific about it, and one engine across both JVM targets means one set of
            // TLS and proxy behaviours to reason about.
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.json)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)

            implementation(libs.androidx.navigation.compose)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            // The Skiko binary for this machine. `desktopMain` deliberately does without it — the
            // app module supplies it at runtime — but a test that turns a video frame into an
            // `ImageBitmap` is calling into Skia for real, and without this it fails to initialize.
            implementation(compose.desktop.currentOs)
            // Somewhere for vlcj's and Ktor's SLF4J logging to go, for the same reason `:desktopApp`
            // declares one: without a provider SLF4J prints its "No SLF4J providers were found"
            // banner and then drops every message. `desktopMain` still has none — a library must not
            // pick the binding for whoever depends on it. `simplelogger.properties` sets the level.
            runtimeOnly(libs.slf4j.simple)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Generated into our own package rather than the default derived from the module name
// ("jellyfin.shared.generated.resources"), so call sites import `org.jellyfin.mobile.resources.Res`
// alongside the rest of the app. Left internal (`publicResClass` defaults to false) — `shared` is
// the only module that reads strings; androidApp and iosApp only call `App()`.
compose.resources {
    packageOfResClass = "org.jellyfin.mobile.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

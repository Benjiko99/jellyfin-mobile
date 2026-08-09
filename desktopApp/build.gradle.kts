import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

compose.desktop {
    application {
        mainClass = "org.jellyfin.mobile.MainKt"

        nativeDistributions {
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

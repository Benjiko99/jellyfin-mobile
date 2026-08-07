import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlint)
}

// ktlint runs over every module, including this one (for the build scripts themselves). Rules live
// in .editorconfig so that the IDE and the CLI agree; only wiring lives here.
allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    configure<KtlintExtension> {
        version = rootProject.libs.versions.ktlint
        // Fail the build rather than silently emitting a report nobody reads.
        ignoreFailures = false
        // Compose Multiplatform generates resource accessors and the KMP plugin generates iOS
        // interop stubs; neither is ours to format.
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
    }

    dependencies {
        // Compose-specific rules (modifier conventions, state hoisting, unstable params, ...).
        // https://mrmans0n.github.io/compose-rules/rules/
        add("ktlintRuleset", rootProject.libs.ktlint.compose.rules)
    }
}

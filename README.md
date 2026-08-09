<h1 align="center">Jellyfin for Mobile</h1>
<h3 align="center">Part of the <a href="https://jellyfin.org">Jellyfin Project</a></h3>

---

<p align="center">
<img alt="Logo Banner" src="https://raw.githubusercontent.com/jellyfin/jellyfin-ux/master/branding/SVG/banner-logo-solid.svg?sanitize=true"/>
<br/>
<br/>
<img alt="GPL 2.0 License" src="https://img.shields.io/badge/license-GPL--2.0-blue.svg"/>
<br/>
<a href="https://opencollective.com/jellyfin">
<img alt="Donate" src="https://img.shields.io/opencollective/all/jellyfin.svg?label=backers"/>
</a>
<a href="https://features.jellyfin.org">
<img alt="Feature Requests" src="https://img.shields.io/badge/fider-vote%20on%20features-success.svg"/>
</a>
<a href="https://matrix.to/#/+jellyfin:matrix.org">
<img alt="Chat on Matrix" src="https://img.shields.io/matrix/jellyfin:matrix.org.svg?logo=matrix"/>
</a>
<a href="https://www.reddit.com/r/jellyfin/">
<img alt="Join our Subreddit" src="https://img.shields.io/badge/reddit-r%2Fjellyfin-%23FF5700.svg"/>
</a>
</p>

A native Kotlin Multiplatform client for [Jellyfin](https://jellyfin.org), targeting **Android, iOS and
desktop** (Windows, macOS, Linux) with a shared Compose Multiplatform UI. It talks to Jellyfin servers
directly over the [Jellyfin HTTP API](https://api.jellyfin.org/) — there is no embedded web client.

Video playback currently works on Android only; the iOS and desktop builds browse and sign in while
their player engines are written.

This project is a ground-up rewrite of [jellyfin-android](https://github.com/jellyfin/jellyfin-android),
which wrapped the [official web client](https://github.com/jellyfin/jellyfin-web) in a WebView.
Selected native components from that project (device profile / codec detection, media source resolution,
playback queue handling, translations) are carried over — see [PLAN.md](./PLAN.md) for the full
migration inventory and roadmap.

## Project layout

| Path         | Contents                                                                       |
|--------------|--------------------------------------------------------------------------------|
| `shared/`    | Shared Kotlin + Compose Multiplatform code (`commonMain`, `androidMain`, `iosMain`, `desktopMain`) |
| `androidApp/`| Android application entry point                                                |
| `iosApp/`    | iOS application entry point and any SwiftUI glue                               |
| `desktopApp/`| Desktop application entry point and packaging configuration                    |

## Build

### Dependencies

- JDK 21
- Android SDK
- Xcode 16+ (iOS only, macOS host required)

### Android

```sh
./gradlew :androidApp:assembleDebug
```

Deploy to a connected device or emulator:

```sh
./gradlew :androidApp:installDebug
```

Replace `Debug` with `Release` for an optimized binary.

### iOS

Open the [`iosApp`](./iosApp) directory in Xcode and run from there. The shared framework is built
automatically by the Xcode build phase.

### Desktop

```sh
./gradlew :desktopApp:run
```

Installers are built by [jpackage](https://openjdk.org/jeps/392), which only produces the format of
the operating system it runs on — an `.msi` on Windows, a `.dmg` on macOS, a `.deb` on Linux:

```sh
./gradlew :desktopApp:packageDistributionForCurrentOS
```

## Tests

```sh
./gradlew :shared:testAndroidHostTest
```

```sh
./gradlew :shared:iosSimulatorArm64Test
```

```sh
./gradlew :shared:desktopTest
```

## Translations

Strings are seeded from the [jellyfin-android Weblate project](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-android),
and most shared terminology originates in the [web client](https://translate.jellyfin.org/projects/jellyfin/jellyfin-web).

## Contributing

Contributions and pull requests are welcome. If you have a larger feature in mind, please open an issue
first so the implementation can be discussed before you start.

## License

Licensed under the GNU General Public License v2.0 — see [LICENSE.md](./LICENSE.md).
Portions are derived from [jellyfin-android](https://github.com/jellyfin/jellyfin-android), also GPL-2.0.

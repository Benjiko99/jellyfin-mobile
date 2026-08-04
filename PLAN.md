# Rebuild Plan: jellyfin-android (WebView) → jellyfin-mobile (KMP + Compose)

Status: **draft, pre-implementation.** The repo is currently the untouched KMP wizard template.

---

## 1. Goal and constraints

Replace the WebView-wrapper Android client with a native Kotlin Multiplatform app sharing one
Compose Multiplatform UI across **Android and iOS**, talking directly to the Jellyfin HTTP API.

**Decisions already made:**

| Decision | Choice | Rationale |
|---|---|---|
| iOS scope | **First-class target, ship both** | Drives every layering choice below. |
| Data layer | **Own Ktor client in `commonMain`** | `org.jellyfin.sdk:jellyfin-core` is JVM/Android-only. |
| License | **GPL-2.0** | Inherited — we copy code from jellyfin-android. |

### Why we can't use the official Kotlin SDK

[`jellyfin-sdk-kotlin`](https://github.com/jellyfin/jellyfin-sdk-kotlin) is described as "supporting
Android and JVM targets". Its Gradle build is already KMP-structured (`commonMain` / `jvmCommonMain` /
`androidMain`) but only `jvm()` and `androidLibrary` targets are enabled, and the HTTP layer is OkHttp
(`jellyfin-api-okhttp`). `java.util.UUID` is used throughout the public API.
[Issue #208](https://github.com/jellyfin/jellyfin-sdk-kotlin/issues/208) has tracked full KMP support
since 2021 and is still open.

Consequences: the SDK can only live in `androidMain`. Since our Compose UI lives in `commonMain`, using
it would require an `expect`/`actual` seam across the *entire* data layer plus a second, from-scratch
iOS implementation later. Writing one Ktor client in `commonMain` is strictly less work than writing
the data layer twice.

We stay compatible with upstream by using the **same OpenAPI spec** and the **same wire models**, so
migrating to the SDK later (if it goes multiplatform) is a mechanical swap.

### Licensing

jellyfin-android is **GPL-2.0**. Everything we copy or derive from it makes this project GPL-2.0.
`LICENSE.md` has been copied over. Keep the per-file provenance header on ported files:

```kotlin
// Derived from jellyfin-android, GPL-2.0
// https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/org/jellyfin/mobile/player/deviceprofile/DeviceProfileBuilder.kt
```

---

## 2. What's actually in the old app

The framing of jellyfin-android as "just a WebView" is only half right. It ships **~145 Kotlin files
totalling roughly 400 KB of source**, and the native half is substantial: a full ExoPlayer-based video
player, Android Auto browsing, an offline download manager, and codec-capability detection. The WebView
handles browsing UI; nearly everything else is native and worth mining.

### Tier 1 — high-value, port deliberately

| Source | Size | Where it goes | Notes |
|---|---|---|---|
| `player/deviceprofile/DeviceProfileBuilder.kt`<br/>`player/deviceprofile/CodecHelpers.kt`<br/>`player/deviceprofile/DeviceCodec.kt` | ~31 KB | `androidMain` | **The crown jewel.** Probes `MediaCodecList` and maps real device capabilities onto a Jellyfin `DeviceProfile` (containers, direct-play profiles, codec profiles, AVC level ceilings, subtitle delivery). Years of accumulated device-quirk knowledge. Port near-verbatim; only swap SDK model imports for ours. iOS needs an equivalent written from scratch against AVFoundation. |
| `player/source/*` (`JellyfinMediaSource`, `Remote…`, `Local…`, `MediaSourceResolver`, `ExternalSubtitleStream`) | ~14 KB | `commonMain` | Playback-info negotiation + stream/track selection model. Almost pure logic over API models. Note the mediaSourceId dash-stripping workaround in `MediaSourceResolver:48` — that's a real server quirk, keep the comment. |
| `player/queue/QueueManager.kt` | 21 KB | split | Queue, bitrate-fallback ladder, external-subtitle merging, direct-play → transcode fallback. Logic → `commonMain`; ExoPlayer `MediaSource`/`MergingMediaSource` construction → `androidMain`. |
| `player/PlayerViewModel.kt` | 34 KB | split | Extract the **playback reporting** (start/progress/stopped → server, play session lifecycle) into a common `PlaybackReporter`. Audio focus, ExoPlayer wiring, and Android media session stay in `androidMain`. |
| `player/ui/PlayerGestureHelper.kt` | 21 KB | rewrite | Swipe brightness/volume, double-tap seek, pinch-zoom, lock screen. View-based — must be rewritten for Compose, but **mine the thresholds and interaction design** rather than reinventing them. |
| `player/ui/TrickplayHelper.kt` + `utils/coil/SubsetTransformation.kt` | ~10 KB | split | Trickplay scrub-preview: tile index math, x/y offsets, bitmap subsetting. Math → `commonMain`; the Coil transformation → Coil 3 (KMP). |
| `player/TrackSelectionHelper.kt`, `utils/TrackSelectionUtils.kt` | ~10 KB | `androidMain` | ExoPlayer track selection incl. external subtitle tracks. |
| `player/mediasegments/*` | ~5 KB | `commonMain` | Intro / outro / credits skip. Small, high user value, portable. |
| `player/qualityoptions/QualityOptionsProvider.kt` | 2 KB | `commonMain` | Bitrate ladder. Trivial port. |
| `res/values*/strings.xml` | **74 locales × 157 strings** | `commonMain/composeResources` | Community translations from Weblate. Compose Resources uses the *same* XML format (`<string>`, `<plurals>`) — a copy script gets us full i18n nearly free. **Do this early**, it's the cheapest large win in the whole migration. |

### Tier 2 — useful as reference / spec, rewrite the implementation

| Source | Size | Notes |
|---|---|---|
| `ui/screens/connect/ServerSelection.kt`, `ConnectScreen.kt`, `setup/ConnectionHelper.kt` | ~19 KB | **Already Compose.** Server discovery + address-candidate + recommended-server-score flow. Copy-adapt; the discovery calls need reimplementing against our own client. |
| `sessionbrowser/page/*` (16 files) | ~22 KB | Android Auto browse tree. Each file is a small, precise `getItems(...)` query — recent, favorites, genres, artists, albums, playlists, suggested, search, alpha-browse. **Treat this directory as the written spec for our repository query layer**; it encodes the correct parameters for each view. |
| `sessionbrowser/SessionBrowserCallback.kt`, `LibraryService.kt` | ~20 KB | MediaSession browse/play integration → `androidMain` when we do Android Auto. |
| `downloads/*` + `data/` (Room) | ~28 KB + 12 KB | Offline downloads: per-file queue, notifications, storage manager, `ContentRange` resume, Room schema for servers/users/downloads. Design ports; the impl is Android-heavy. Room 2.7+ supports KMP incl. iOS, so the schema can be shared. |
| `player/interaction/PlayerNotificationHelper.kt` + media session bits | ~13 KB | Android playback notification + media button handling. |
| `app/AppPreferences.kt` | 6 KB | Inventory of every setting key and default. Port the *list*, not the SharedPreferences impl. |
| `settings/SettingsFragment.kt` | 15 KB | Uses a third-party preferences lib. Rewrite in Compose, but read it first for the complete settings surface. |
| `bridge/ExternalPlayer.kt` | 17 KB | External player handoff (VLC / MX Player / Just Player) intent protocol + result parsing. Self-contained, Android-only. Port if we want the feature. |
| `utils/Constants.kt`, `utils/extensions/MediaStream.kt`, `MediaSegment.kt` | ~9 KB | Small helpers; cherry-pick. |

### Tier 3 — delete, do not port

- **All of `webapp/`** — `WebViewFragment`, `JellyfinWebViewClient`, `JellyfinWebChromeClient`,
  `WebappFunctionChannel`, and `RemotePlayerService.kt` (23 KB of media session bridged to JavaScript).
  Entirely superseded.
- **All of `bridge/`** except `ExternalPlayer.kt` — `NativeInterface`, `NativePlayer`,
  `JavascriptCallback`, `MediaSegments` are the JS↔Kotlin bridge.
- `events/ActivityEventHandler.kt` — exists to route events out of the WebView.
- `utils/WebViewUtils.kt`, `ui/ComposeFragment.kt`, all Fragment plumbing, all XML layouts.
- `player/cast/IChromecast.kt` + the proprietary/libre flavor split — Chromecast is a separate decision;
  don't carry the build-flavor complexity forward by default.

---

## 3. Target architecture

```
jellyfin-mobile/
├── shared/
│   └── src/
│       ├── commonMain/kotlin/org/jellyfin/mobile/
│       │   ├── core/            # dispatchers, Result types, logging, UUID typealias
│       │   ├── network/
│       │   │   ├── generated/   # OpenAPI-generated models + operations (excluded from lint)
│       │   │   ├── JellyfinClient.kt
│       │   │   ├── AuthorizationHeaderBuilder.kt
│       │   │   ├── ImageUrlBuilder.kt
│       │   │   └── JellyfinSocket.kt        # /socket websocket
│       │   ├── discovery/       # UDP broadcast, address candidates, server scoring
│       │   ├── data/            # repositories, Room DAOs, DataStore settings
│       │   ├── domain/          # our models — NOT BaseItemDto everywhere
│       │   ├── player/          # expect JellyfinPlayer, queue, reporting, segments
│       │   └── ui/              # Compose: designsystem, navigation, feature screens
│       ├── androidMain/         # Media3/ExoPlayer, DeviceProfileBuilder, MediaSession, downloads
│       └── iosMain/             # AVPlayer or VLCKit, device profile, Now Playing
├── androidApp/
└── iosApp/
```

Split into Gradle modules (`:core:network`, `:core:data`, `:feature:player`, …) once `shared/`
gets uncomfortable — not on day one. Premature modularization on KMP is expensive.

### Stack

| Concern | Choice | Note |
|---|---|---|
| HTTP | **Ktor 3.x** client | `OkHttp` engine on Android, `Darwin` on iOS |
| Serialization | kotlinx.serialization | Same as the upstream SDK's wire format |
| DI | **Koin 4.x** | KMP + Compose support; old app already uses Koin, so patterns transfer |
| Images | **Coil 3** | KMP incl. iOS; `coil-network-ktor3` |
| Database | **Room 2.7+ (KMP)** | Shares the ported schema. SQLDelight is the fallback if Room KMP fights us |
| Preferences | **androidx.datastore 1.1+** | KMP preferences DataStore |
| Navigation | **Navigation Compose (JetBrains KMP build)** | Type-safe routes |
| Logging | Kermit | |
| Testing | kotlin.test + Kotest assertions + Turbine + Ktor `MockEngine` | |

### API client generation

Jellyfin's `openapi.json` is large (~600 schemas). Plan:

1. Vendor `openapi.json` pinned to a target server version under `api-spec/`.
   **Done** — `api-spec/jellyfin-openapi-12.0.0.json` (294 paths). See AGENTS.md § *Finding API endpoints*.
2. Prune to the paths we actually consume with a Gradle task (roughly 40 endpoints:
   auth, quick connect, system info, users, views, items, playback info, playback reporting,
   images, sessions, media segments, trickplay, search).
3. Generate models + operations into `network/generated/` via `openapi-generator`
   (`kotlin` generator, `library=multiplatform`). Exclude from detekt/ktlint.
4. Hand-write a thin ergonomic facade over the generated code — the generated API is not what we
   want to call from repositories.
5. A `regenerateApi` Gradle task keeps step 2–3 reproducible; check the output in so builds are hermetic.

Auth is the `Authorization` header, format
`MediaBrowser Client="…", Device="…", DeviceId="…", Version="…", Token="…"` —
port the quoting/escaping rules from the SDK's `AuthorizationHeaderBuilder`.

---

## 4. Phases

Each phase should end with something runnable on **both** platforms.

### Phase 0 — Foundations
Version catalog, Ktor/Koin/Coil/Room/DataStore wiring, Kermit, lint + detekt config, CI (Android
assemble + `iosSimulatorArm64Test`), `LICENSE.md`/`README.md`/`AGENTS.md` (done).
Delete the template's `Greeting`/`Platform`/`GreetingUtil` scaffolding.

### Phase 1 — Network layer
Spec vendoring + generation pipeline. `JellyfinClient`, auth header, error mapping, `ImageUrlBuilder`,
websocket. Tests against `MockEngine`. **No UI.**

### Phase 2 — Connect & authenticate
Port `ConnectionHelper` logic: address candidates → recommended-server scoring → connect.
Reimplement UDP local discovery (Ktor network sockets work on both platforms).
Username/password login + **Quick Connect**. Adapt `ServerSelection.kt` UI. Persist servers/users
(Room). Multi-server, multi-user from the start — retrofitting it later is painful.

### Phase 3 — Browse
Home, libraries, collection/grid, item detail, search. **Mine `sessionbrowser/page/*` for the queries.**
Import the 74-locale string catalog here. Establish the design system + `domain` models
(don't leak `BaseItemDto` into Compose).

### Phase 4 — Player (the hard one)
- `expect class JellyfinPlayer` — Media3/ExoPlayer on Android, AVPlayer (or VLCKit) on iOS.
- Port `DeviceProfileBuilder` to `androidMain`; write the AVFoundation equivalent for iOS.
- Port `MediaSourceResolver` + `QueueManager` + bitrate fallback.
- Compose player UI + gestures (rewrite from `PlayerGestureHelper`).
- Trickplay scrubbing, chapters, media segments (skip intro), track selection, subtitles.
- Playback reporting to the server.

### Phase 5 — Platform integration
Android: MediaSession, playback notification, Android Auto (port `sessionbrowser/`), PiP,
background audio, Bluetooth/headset handling.
iOS: `MPNowPlayingInfoCenter`, `AVAudioSession`, Control Center, background audio, PiP.

### Phase 6 — Offline downloads
Port the Room schema and download queue design. Android: WorkManager + `DocumentFile` storage.
iOS: `URLSession` background transfers. Local playback via `LocalJellyfinMediaSource`.

### Phase 7 — Settings & polish
Full settings surface (read `SettingsFragment.kt` + `AppPreferences.kt` for the inventory),
theming, accessibility, external player handoff, Chromecast decision.

---

## 5. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| **iOS codec support** — AVPlayer won't direct-play MKV/H.265-in-MKV, forcing server transcode where Android direct-plays | High. Affects UX and server load | Decide early: AVPlayer-only (lean on HLS transcode) vs. **VLCKit** for parity. VLCKit is what other iOS Jellyfin clients use. This is a Phase 4 blocker — decide before Phase 4 starts |
| **ASS/SSA subtitle rendering** | Medium | Media3 handles it poorly; the old app leans on server-side burn-in. Expect to do the same initially |
| Generated API surface is large and churns with server versions | Medium | Prune the spec; pin to a server version; hermetic checked-in generation |
| Room KMP / Coil 3 / Navigation KMP maturity on iOS | Medium | Each has a fallback (SQLDelight, custom loader, Decompose). Validate all three in Phase 0 with a spike before committing |
| Losing SDK features we take for granted: local discovery, server scoring, socket reconnect | Medium | Port from jellyfin-android + read the SDK source (GPL-compatible; it's MPL-2.0 — check before copying) |
| Feature-parity expectations vs. the mature web client | High, ongoing | Be explicit that this is a *different* product surface, not a WebView replacement with identical features |

## 6. Open decisions

1. **iOS player engine** — AVPlayer vs. VLCKit. Blocks Phase 4.
2. **Chromecast** — carry over the proprietary/libre flavor split, or drop Cast entirely?
3. **Upstream relationship** — is this intended to replace `jellyfin/jellyfin-android`, or live alongside
   it? Affects package name (`org.jellyfin.mobile` collides with the published app), release channels,
   and whether we should be upstreaming the Ktor client into `jellyfin-sdk-kotlin` instead.
4. **Android minSdk** — template says 26; the old app supports lower. Confirm 26 is acceptable.

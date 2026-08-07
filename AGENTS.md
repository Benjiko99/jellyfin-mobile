# AGENTS.md

`CLAUDE.md` is a symlink to this file. Edit `AGENTS.md`.

## What this project is

A native **Kotlin Multiplatform** Jellyfin client for **Android and iOS**, with a shared
**Compose Multiplatform** UI. It talks to Jellyfin servers directly over the
[Jellyfin HTTP API](https://api.jellyfin.org/).

It replaces [jellyfin-android](https://github.com/jellyfin/jellyfin-android), which wrapped the
Jellyfin web client in a WebView. That project is checked out locally at
`../jellyfin-android` and is the **reference source** for ported components.

Read [PLAN.md](./PLAN.md) before doing architectural work — it holds the migration inventory
(what to port from the old app, what to delete), the phase roadmap, and the open decisions.

The repo is currently close to the untouched KMP wizard template. Treat `Greeting.kt`,
`GreetingUtil.kt`, `Platform.kt` and their tests as scaffolding to delete, not as examples to follow.

## Non-negotiables

- **iOS is a first-class target.** Never put shared logic behind an Android-only dependency.
  If code is in `commonMain`, it must compile for `iosArm64`/`iosSimulatorArm64`. Verify with
  `./gradlew :shared:compileKotlinIosSimulatorArm64`, not just the Android build.
  On a **Windows host** that compile task works (Kotlin/Native cross-compiles the klib) and will
  catch any Kotlin error in `commonMain`/`iosMain` — but `linkDebugFrameworkIosSimulatorArm64` is
  **SKIPPED** and `iosSimulatorArm64Test` cannot run. Linking, running, and iOS tests need macOS,
  so anything beyond type-checking has to happen on a Mac or in CI.
- **Do not add `org.jellyfin.sdk:*`.** The official Kotlin SDK is JVM/Android-only
  ([issue #208](https://github.com/jellyfin/jellyfin-sdk-kotlin/issues/208)). We use our own Ktor
  client in `commonMain`. This is a deliberate decision, not an oversight — see PLAN.md §1.
- **GPL-2.0.** jellyfin-android is GPL-2.0 and we derive from it. Every file ported or adapted from
  it gets a provenance header:
  ```kotlin
  // Derived from jellyfin-android, GPL-2.0
  // https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/<path>
  ```
  Do not paste in code from non-GPL-compatible sources.
- **The API client is hand-written, deliberately.** `network/JellyfinApi.kt` exposes only the
  endpoints we actually use, each with a comment recording why its parameters are what they are.
  There is no generator and no generated source set. Adding an endpoint means looking it up in the
  spec (below) and writing the method.

## Layout

| Path | Contents |
|---|---|
| `shared/src/commonMain` | Shared logic **and** the Compose UI |
| `shared/src/androidMain` | Media3/ExoPlayer, `MediaCodecList` device profiling, MediaSession, WorkManager |
| `shared/src/iosMain` | AVFoundation/VLCKit playback, Now Playing, `URLSession` |
| `androidApp/` | Android entry point only — keep it thin |
| `iosApp/` | iOS entry point + SwiftUI glue only — keep it thin |

Feature code belongs in `shared`, not in `androidApp`/`iosApp`. Split `shared` into Gradle modules
only when it becomes unwieldy; don't pre-modularize.

## Commands

```bash
./gradlew :androidApp:assembleDebug
```

```bash
./gradlew :androidApp:installDebug
```

```bash
./gradlew :shared:testAndroidHostTest
```

```bash
./gradlew :shared:iosSimulatorArm64Test
```

```bash
./gradlew ktlintFormat
```

```bash
./gradlew ktlintCheck
```

iOS app builds run from Xcode against `iosApp/`.

## Conventions

- Package root is `org.jellyfin.mobile`. The template's doubled `org.jellyfin.mobile.jellyfin` and
  the `applicationId` are placeholders — fix them, and note that `org.jellyfin.mobile` is the
  published jellyfin-android app id (see PLAN.md §6 open decision 3 before shipping).
- **Don't leak wire models into the UI.** Generated DTOs (`BaseItemDto` and friends) stop at the
  repository boundary; Compose consumes our own `domain` models.
- `expect`/`actual` is for genuine platform capability (player, codecs, filesystem, notifications).
  It is not a workaround for a library that "doesn't work on iOS" — find a KMP library instead.
- Prefer `Flow` over callbacks; suspend functions over blocking calls. No `LiveData` (the old app
  uses it heavily — convert on port).
- Strings live in `shared/src/commonMain/composeResources/values*/strings.xml`. The 74-locale
  catalog is imported from jellyfin-android's Weblate output; keep the format compatible so
  re-syncing stays a copy.
- No `println` — use Kermit.
- **Run `./gradlew ktlintFormat` before committing.** ktlint runs with the
  [compose-rules](https://mrmans0n.github.io/compose-rules/rules/) ruleset, so it checks Compose
  conventions (modifier parameter, parameter order, state hoisting) as well as formatting. Rules
  live in `.editorconfig`, which the IDE reads too — every deviation from the defaults there carries
  a comment explaining why, so add one if you need another. Prefer a targeted
  `@Suppress("ktlint:compose:<rule>")` with a rationale over disabling a rule for the whole repo.
- Match the surrounding code's style. When porting, keep the original's comments explaining
  server quirks; those comments are the valuable part.

## Finding API endpoints

**Never guess an endpoint path or parameter name.** Look it up in the vendored spec:

    api-spec/jellyfin-openapi-12.0.0.json

This is the pinned OpenAPI 3.0.4 spec (`x-jellyfin-version: 12.0.0`, 294 paths). Sources, all
byte-identical at time of pinning:

- <https://api.jellyfin.org/openapi/jellyfin-openapi-stable.json>
- <https://api.jellyfin.org/openapi/jellyfin-openapi-unstable.json>
- `jellyfin-sdk-kotlin/openapi.json` — **stored via Git LFS**; a plain `raw.githubusercontent.com`
  fetch returns a 132-byte LFS pointer, not the spec. Use the `media.githubusercontent.com/media/…`
  URL instead.

Browsable rendering: <https://api.jellyfin.org/>. A live server also serves its own spec at
`/api-docs/openapi.json` — useful for checking what a *specific* server version supports.

To inspect the vendored spec, query it rather than reading it (it's 1.9 MB):

```bash
python -c "import json;s=json.load(open('api-spec/jellyfin-openapi-12.0.0.json',encoding='utf-8'));print('\n'.join(p for p in s['paths'] if 'Resume' in p))"
```

Gotchas found so far. Each of these cost real debugging time — check the list before assuming an
endpoint behaves like its neighbours.

**Shapes and routes**

- Legacy `/Users/{userId}/Items/...` routes are **gone**. Current equivalents take `userId` as a
  *query* parameter: `/UserItems/Resume`, `/Items/Latest`, `/UserViews`, `/Items`.
- `/Items/Latest` returns a **bare JSON array** of `BaseItemDto`, not a `BaseItemDtoQueryResult`
  like almost every other list endpoint.
- `/Items/Latest` takes **no `startIndex`**, so it cannot be paged at all. Paging "recently added"
  needs `/Items` with `sortBy=DateCreated&sortOrder=Descending` instead — but note that route does
  *not* group episodes under their series the way `/Items/Latest` does, so constrain
  `includeItemTypes` or a TV row and its full list will disagree about what they contain.
- **People are not reachable through `/Items`.** They are `BaseItemKind.Person` but do not live in a
  library folder, so a recursive query never returns them however it is filtered. `/Persons` is the
  only route that does. It also does not reliably set `Type` on its results — assert the kind at the
  call site rather than inferring it.
- `/Items/Suggestions` names its item-type filter **`type`**, not the `includeItemTypes` every
  neighbouring route takes, and accepts no `fields`, `enableImageTypes` or `imageTypeLimit` at all.
  It is built from the user's viewing history, so an empty result is the normal answer for a fresh
  account rather than a fault.
- **`/Items` cannot filter a search to box sets.** `searchTerm` together with
  `includeItemTypes=BoxSet` returns an empty body that is not even JSON — Ktor surfaces it as
  `NoTransformationFoundException`, not as an HTTP error — on a library where the term plainly
  matches a box set. It is that exact pair: the filter works without a term, the term works without
  the filter, and both work if a *second* item type rides along in the same `includeItemTypes`.
  Find box sets by scanning an untyped search instead. Note this makes them unpageable, since
  `startIndex` then counts the unfiltered list.
- Unrecognised `includeItemTypes` values are **silently ignored** rather than rejected, so a typo'd
  item type returns everything instead of failing. Check names against `BaseItemKind` in the spec.

**Counts and paging**

- With `enableTotalRecordCount=false`, the server fills `TotalRecordCount` with the size of the page
  it just returned, not zero. Trusting it on a later page silently replaces a real total.
  Only ask for a count on the first page, and only use it there.
- `/Persons` has no `enableTotalRecordCount` at all. Detect the end of the list from a short page.
- To answer "is there more than N?" without making the server count every match, request `N + 1` and
  compare sizes. `enableTotalRecordCount` costs a full scan for a yes/no question.

**Serialization**

- `JsonNamingStrategy` applies to property names only, **never to enum entries**. Any enum whose
  wire form differs from its Kotlin name needs an explicit `@SerialName` — `MediaStreamProtocol` is
  lowercase (`http`, `hls`) and would not round-trip otherwise.
- Ktor 3 defaults `expectSuccess` to `false`, so an error response reaches the JSON decoder and
  surfaces as a deserialization failure rather than an HTTP error. `HttpClientFactory` sets it true.

**Playback**

- `PlaybackInfo` matches media source ids with the dashes stripped, and silently ignores the stream
  indices you send if you omit the id entirely.
- Stream URLs are fetched by the playback engine, not by our `HttpClient`, so they carry no
  `Authorization` header by default. `StreamAuthorizer` adds it — and only when host and port match
  the signed-in server, because a media source can point at a tuner, a remote share, or a CDN.

## Working with the old app

`../jellyfin-android` is a reference, not a dependency. When porting:

1. Read the original in full before rewriting — the value is usually in edge cases and comments,
   not the happy path.
2. `player/deviceprofile/` is the highest-value asset in that repo. Port it faithfully; resist
   "cleaning it up". Its apparent weirdness is device-quirk handling.
3. `sessionbrowser/page/*` is effectively the written specification for our library queries —
   each file encodes the correct API parameters for one browse view.
4. Anything in `webapp/` or `bridge/` (except `ExternalPlayer.kt`) is WebView glue. Never port it.

## When unsure

The open decisions in PLAN.md §6 (iOS player engine, Chromecast, upstream relationship, minSdk)
are unresolved. Don't silently pick one — surface it.

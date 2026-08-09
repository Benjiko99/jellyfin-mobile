# AGENTS.md

`CLAUDE.md` is a symlink to this file. Edit `AGENTS.md`.

## What this project is

A native **Kotlin Multiplatform** Jellyfin client for **Android, iOS and desktop** (Windows, macOS
and Linux on the JVM), with a shared **Compose Multiplatform** UI. It talks to Jellyfin servers
directly over the [Jellyfin HTTP API](https://api.jellyfin.org/).

It replaces [jellyfin-android](https://github.com/jellyfin/jellyfin-android), which wrapped the
Jellyfin web client in a WebView. That project is checked out locally at
`../jellyfin-android` and is the **reference source** for ported components.

Read [PLAN.md](./PLAN.md) before doing architectural work — it holds the migration inventory
(what to port from the old app, what to delete), the phase roadmap, and the open decisions.

The repo is currently close to the untouched KMP wizard template. Treat `Greeting.kt`,
`GreetingUtil.kt`, `Platform.kt` and their tests as scaffolding to delete, not as examples to follow.

## Non-negotiables

- **iOS is a first-class target.** Never put shared logic behind an Android-only dependency.
  If code is in `commonMain`, it must compile for `iosArm64`/`iosSimulatorArm64` **and** for
  `desktop`. Verify with `./gradlew :shared:compileKotlinIosSimulatorArm64` and
  `:shared:compileKotlinDesktop`, not just the Android build. iOS is the constraint that bites: a
  JVM-only library still compiles for desktop and fails only there.
  On a **Windows host** that compile task works (Kotlin/Native cross-compiles the klib) and will
  catch any Kotlin error in `commonMain`/`iosMain` — but `linkDebugFrameworkIosSimulatorArm64` is
  **SKIPPED** and `iosSimulatorArm64Test` cannot run. Linking, running, and iOS tests need macOS,
  so anything beyond type-checking has to happen on a Mac or in CI.
- **Desktop is a target, not a port.** `jvm("desktop")` builds the same `commonMain` into a Compose
  Desktop app; nothing about it is a separate codebase. Unlike iOS it builds, links, runs and tests
  on this host, so `./gradlew :desktopApp:run` is the fastest way to see a shared-UI change at all —
  and `:shared:desktopTest` runs `commonTest` in seconds where the Android host test needs a
  Robolectric-shaped build. It is also the only place libVLC can be exercised: `VlcjPlayerEngineTest`
  plays a real file and checks the frames, which is the closest thing we have to a test of the iOS
  engine's design. libVLC is a native library and never on the classpath: on Windows the build
  downloads it and packages it into the app (`:desktopApp:bundleVlc`, ~104 MB), and macOS and Linux
  still fall back to the machine's own VLC — PLAN.md §6.5. **The tests always use the machine's
  VLC**, because a test JVM has no packaged app resources to look in, so `VlcjPlayerEngineTest`
  skips itself where VLC is not installed.
  `KeepScreenOn`, `PlaybackHardware` and `SystemBarAppearance` remain documented no-ops there.
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
| `shared/src/desktopMain` | The desktop window, AWT/JVM actuals, per-OS data directories |
| `androidApp/` | Android entry point only — keep it thin |
| `iosApp/` | iOS entry point + SwiftUI glue only — keep it thin |
| `desktopApp/` | `main()` and the packaging config only — keep it thin |

Feature code belongs in `shared`, not in `androidApp`/`iosApp`/`desktopApp`. The window itself is in
`shared` too, for the reason `MainViewController` is: the entry-point modules cannot read `Res`, so
anything with a translated string in it has to live here. Split `shared` into Gradle modules only
when it becomes unwieldy; don't pre-modularize.

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
./gradlew :desktopApp:run
```

```bash
./gradlew :shared:desktopTest
```

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
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
- **No user-facing string literals in Kotlin.** Everything the user reads lives in
  `shared/src/commonMain/composeResources/values*/strings.xml`, reached through the generated
  `org.jellyfin.mobile.resources.Res`. Only English exists so far; the 74-locale catalog will be
  imported from jellyfin-android's Weblate output, so keep the file's format compatible — that
  import should stay a copy rather than a conversion.
  - Placeholders are **always** indexed and always `%1$s`, `%2$s`, … and arguments are passed as
    strings (`count.toString()`). Compose Resources formats these itself on iOS rather than going
    through the platform, and the indexed string form is the only one reliable on both targets.
  - Anything whose wording depends on a count is a `<plurals>`, read with `pluralStringResource`.
    Do not append an "s" in Kotlin.
- **Text produced outside a composition is a `UiText`, not a `String`.** Repositories, mappers and
  view models cannot call `stringResource`, so they name the string and the UI resolves it with
  `UiText.resolve()`. `UiText.Raw` is for text the *server* wrote — a genre, a library name, a media
  stream's label, an exception message — and `UiText.Resource`/`Plural` for ours. An exception that
  reaches the screen implements `LocalizedError` so the user gets a sentence and the log keeps the
  diagnostic. This is why `domain` imports `components-resources`: `StringResource` is a resource
  *identifier*, readable outside a composition, not a Compose UI type — an `ImageVector` is the
  other thing, which is why library icons still resolve up in `NavigationDrawer`.
- Navigation routes must serialize, so a `UiText` cannot ride in one. Carry what the heading is
  built *from* and rebuild it at the destination — `SectionRoute` carries `kind` and `libraryName`,
  and `SectionKind.title()` is the one place either end reads.
- No `println` — use Kermit.
- **Colour, type and shape come from the theme**, never from a literal at the call site.
  `ui/theme/AppTheme.kt` passes Material all three, so `MaterialTheme.colorScheme`,
  `.typography` and `.shapes` are the only sources — a `Color(0xFF…)` or a
  `RoundedCornerShape(8.dp)` in a screen is a bug. The colour schemes in `ColorSchemes.kt` are
  **generated by `tools/palette.py`**, which also checks every foreground clears 4.5:1 on the
  surface behind it: change a hue there and re-run it, don't edit a hex by hand.
  Two deliberate exceptions, both commented where they live: the player is white-on-black in either
  scheme because it sits over video, and the card badges are fixed because they sit on artwork.
- **Icons come from Material Icons**, reached through the named aliases in `ui/components/Icons.kt`
  rather than imported at the call site. Two things to know before touching that file:
  `material-icons-*` is deprecated upstream and is no longer a transitive dependency of Material 3,
  and JetBrains **stopped publishing the multiplatform artifact after 1.7.3** (December 2024). So
  `composeMaterialIcons` is pinned there while `composeMultiplatform` moves on. It resolves and
  compiles today, but it will not gain icons or fixes, and a future Compose release may break it —
  at which point the aliases are the only file that needs to change. `material-icons-extended`
  carries thousands of icons and relies on dead-code elimination to not ship them all; check the APK
  and framework size if that ever stops being true.
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
- Use **`/Items/Filters`**, the legacy route, for a library's filter options — not its successor
  `/Items/Filters2`. Filters2 returns genres with ids and adds audio/subtitle languages but **drops
  `OfficialRatings` and `Years`**. The legacy route also returns genres as plain names, which is the
  form `/Items?genres=` wants them back in.
- The A–Z picker is two parameters, not one: a letter is `nameStartsWith`, and the `#` bucket is
  `nameLessThan=a` (everything sorting before "a" — digits, brackets). From jellyfin-android's
  `AlphaBrowser`, which is the reference for this.
- `/Movies/Recommendations` returns a **bare JSON array** of `RecommendationDto`, like
  `/Items/Latest`. Its rows have no heading — the client builds one from `RecommendationType` and
  `BaselineItemName`. There is **no TV equivalent**; jellyfin-web substitutes Next Up.
- `/Genres` takes `sortBy`; **`/Studios` does not**. Both take `includeItemTypes`, and on both it
  describes the items *carrying* the genre or studio, not the genre or studio itself. Like
  `/Persons`, neither is reachable through `/Items`.
- `/Shows/Upcoming` returns a flat list in air-date order, so grouping by day is the client's job —
  and a day can straddle a page boundary.
- **`adjacentTo` includes the item you asked about.** `/Shows/{seriesId}/Episodes?adjacentTo={id}`
  answers with up to three episodes — the one before, the one asked for, and the one after — and
  only two at either end of a series. Find the item in the list and take what is either side of it;
  `first()` and `last()` offer the episode playing as its own neighbour on the first and last
  episodes. It is the only route that crosses a season boundary for you. `/Playlists/{id}/Items`
  takes no `adjacentTo` at all, and `/Items` takes one.
- **Box sets are not in the library they group.** They live in their own `boxsets` view, so a
  Collections tab scoped with `parentId` of the movie library returns nothing.
- **A playlist's order only comes from `/Playlists/{id}/Items`.** `/Items?parentId=<playlistId>`
  returns the same entries sorted by `sortBy`, which defaults to name — the one thing a playlist is
  not. That route also carries `PlaylistItemId`, which is what tells two appearances of the same
  item apart; without it a repeated entry can only resolve to its first appearance.
- **A playlist is not itself playable.** It has no media source, so `PlaybackInfo` on one fails.
  Playing a playlist means playing its first entry.

**Not in the spec**

- The navigation drawer's custom links come from `/web/config.json`, a **static file of the web
  client** rather than an API route — jellyfin-web reads its own `menuLinks` out of it, and that is
  the only place an administrator can put a link to a companion service (Jellyseerr, Ombi). Nothing
  versions it with the API, `--nowebclient` omits it entirely, and a proxy can answer it with a 401
  that must not be mistaken for an expired session. See `MenuLinksRepository`.
- **A library's tabs are a client decision.** jellyfin-web keeps a table of them per collection type
  in `src/apps/modern/features/libraries/constants/views/{movies,tvshows}.ts`, along with hardcoded
  per-tab capability flags (`isAlphabetPickerEnabled`, `isBtnFilterEnabled`). Nothing in the API
  describes them. `LibraryTab` is our copy of that table.
- **The streaming-quality ladder is a client decision too.** `PlaybackInfo` takes one
  `maxStreamingBitrate` integer and returns no menu of choices, so there is nothing to ask the
  server for — jellyfin-web and jellyfin-android each hardcode the same list of bitrates and filter
  it by the source's own resolution. `QualityOption` is our copy, ported from
  `player/qualityoptions/`. "Auto" is the *absence* of the parameter, not a value in the list. Note
  the device profile carries a `MaxStreamingBitrate` of its own, so the request body contains that
  key either way.
- **`CollectionType` is a fixed enum; libraries are not.** An administrator decides how many exist,
  what each is called and what type it is — two movie libraries, or one named "Films", are both
  normal. Only `playlists` and `boxsets` are made by the server itself. Never hardcode a library
  list; read `/UserViews`.

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
- A naming strategy **also rewrites names set by `@SerialName`**, so annotating a field to keep it
  camelCase does nothing under `JellyfinJson` — it looks like it worked and the field silently
  decodes to its default. Anything not in the API's PascalCase needs its own `Json`; `WebConfigJson`
  is that, and `JellyfinJsonTest` pins the behaviour.
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

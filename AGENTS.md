# IPTV project guide

## Project overview

IPTV is a single-module Android application for playing user-provided M3U/M3U8 playlists. Users can import playlists from HTTP(S) URLs or local files, browse channel groups, search, mark favorites, refresh sources, and watch HLS, DASH, and progressive HTTP(S) streams. Playlists, channels, favorites, the last selected channel, and optional XMLTV programs persist across launches. During import, users choose whether to use or ignore the EPG URL declared by the playlist; enabled guides display current and next programs.

The application ID and Kotlin root package are `ru.alod.iptv`. `MainActivity` is the launcher activity. Navigation contains `Main` and `Player(channelId)` destinations. Playback is foreground-only; Android TV, Picture-in-Picture, background playback, DRM, Ace Stream, UDP multicast, scheduled background EPG updates, and provider-specific APIs are not implemented.

## Technology stack and configuration

- **UI:** Jetpack Compose Material 3, Compose BOM `2026.03.01`; Media3 `PlayerView` is embedded with `AndroidView`.
- **Architecture:** Compose UI, Android `ViewModel`, Kotlin `StateFlow`, repository interface, and manual dependency wiring through `AppContainer`/`IptvApplication`.
- **Navigation:** Navigation 3 `1.0.1` with serializable `NavKey` destinations.
- **Playlist loading:** OkHttp `4.12.0`, Storage Access Framework, and a custom line-oriented M3U parser.
- **Persistence:** Room `2.8.5` using KSP `2.3.6`; KSP is required because AGP 9 built-in Kotlin is incompatible with `kapt`.
- **Playback:** Media3 `1.9.0` ExoPlayer with HLS and DASH modules.
- **Images:** Coil 3 `3.3.0` with the OkHttp network module.
- **Build:** Gradle `9.1.0`, Android Gradle Plugin `9.0.1`, Kotlin `2.3.20`, Java 17.
- **Android:** `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`.
- **App version:** `versionCode = 1`, `versionName = "1.0"`.

Versions belong in `gradle/libs.versions.toml`. Repositories are centralized in `settings.gradle.kts`; module build files must not declare repositories. AGP 9.0.1 emits a warning because it was tested through compile SDK 36.1 while the project uses 37; this does not currently block builds.

## Source organization

Production code is under `app/src/main/kotlin/ru/alod/iptv/`:

- `AppContainer.kt` constructs OkHttp, Room, the source loader, parser, and repository. `IptvApplication.kt` exposes the container.
- `domain/model/` contains playlist/channel models and typed import errors.
- `domain/repository/PlaylistRepository.kt` defines the data contract used by ViewModels and test fakes.
- `data/m3u/M3uParser.kt` parses `#EXTINF`, `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `#EXTGRP`, HTTP(S) channel URLs, and EPG header attributes `url-tvg`, `x-tvg-url`, `tvg-url`. Unknown directives are ignored and malformed entries are counted rather than failing the whole import.
- `data/epg/` downloads XMLTV/XMLTV.gz with size limits and parses programs using SAX with external entities disabled.
- `data/source/` loads URL/file sources with limits and typed errors. `PlaylistDecoder` supports UTF-8/BOM and Windows-1251 fallback. Maximum playlist size is 10 MiB.
- `data/local/` contains Room entities, DAO, and database. Only the current schema (version 1, including EPG settings and `programs`) is exported to `app/schemas/`. No migrations or destructive fallback are configured for the first publication.
- `data/DefaultPlaylistRepository.kt` imports and transactionally refreshes playlists. Failed refreshes retain cached channels; favorites and last channel are restored by stable keys. EPG failures do not cancel channel import or delete cached programs. Programs map to channels by `tvg-id`; SQL selects only the current and next program for each channel, and these values refresh every minute without loading the full guide.
- `ui/main/` contains the playlist/channel screen and its ViewModel. The UI supports multiple saved playlists, groups, search, favorites, import with an EPG opt-in checkbox, refresh, deletion, and current/next program labels.
- `ui/player/` owns ExoPlayer lifecycle and playback UI. The ViewModel retains the player across configuration changes, pauses on stop, and releases it when cleared.
- `Navigation.kt` registers both routes; `NavigationKeys.kt` defines their serializable keys.
- `ui/theme/` contains the Material 3 theme. Dynamic color remains available but disabled by default.

Visible strings are in `app/src/main/res/values/strings.xml`. There is no DI framework, background playback service, or separate analytics/network module.

## Build and run commands

Run from the repository root with Java 17 and Android SDK API 37. `local.properties` supplies the machine-local SDK path.

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:assembleRelease
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`. Devices must run API 24 or newer. Release shrinking is disabled and no project-specific signing or publishing setup exists. `app/proguard-rules.pro` is referenced but is not required by the current non-minified release configuration.

## Development conventions

- Match surrounding Kotlin style: two-space indentation in application code, trailing commas in multiline calls, and English identifiers/comments.
- Keep feature UI under `ui/<feature>/`, domain contracts under `domain/`, data implementations under `data/`, and theme code under `ui/theme/`.
- Expose UI state as `StateFlow` and collect it with `collectAsStateWithLifecycle()`.
- Pass repository dependencies through ViewModel constructors. Use `IptvApplication.container` only at the composition/factory boundary.
- Register every `NavKey` destination in `Navigation.kt` and keep navigation keys serializable.
- Use Material 3 and `IPTVTheme`; do not introduce another UI toolkit.
- Do not log playlist or stream URLs: they can contain credentials or access tokens.
- Preserve the old Room cache when a playlist refresh fails. Keep only the final Room schema in `app/schemas/`; do not add migrations unless project requirements change.

## Testing

Local JVM tests cover M3U/EPG metadata parsing, XMLTV dates and gzip loading, character decoding, ViewModel state, search/filtering, and import errors. Instrumented tests cover Compose interactions and EPG choices, current/next labels and the program-start boundary, limited SQL program selection, transactional playlist and EPG refresh with `MockWebServer`, and real ExoPlayer readiness using a local HLS server and the small fixture `app/src/androidTest/assets/hls/segment.ts`.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

`connectedDebugAndroidTest` requires a connected device or emulator. Do not infer playback verification from JVM tests or successful test-APK compilation. There is no CI workflow, screenshot suite, or coverage configuration.

## Security and data handling

- The manifest requests `INTERNET` and `ACCESS_NETWORK_STATE`; local files use Storage Access Framework and persistable read grants, with no broad storage permission.
- `usesCleartextTraffic="true"` is intentional because many user-provided IPTV sources still use HTTP. HTTPS is preferred. No application credentials are collected.
- Playlist, stream, and enabled EPG URLs, including possible provider tokens, are stored locally in Room to support refresh and playback. `allowBackup` remains enabled.
- Only user-provided playlists are supported; no channel list is bundled.
- Release obfuscation and release signing are not configured.

## Verification status and limitations

- `./gradlew :app:testDebugUnitTest` passes: 18 tests.
- `./gradlew :app:connectedDebugAndroidTest` passes on `Pixel_10_Pro` emulator: 9 tests, including real local HLS playback, EPG import/ignore behavior, and limited current/next SQL selection at a program-start boundary.
- `./gradlew :app:lintDebug`, `assembleDebug`, and `assembleRelease` pass.
- Manual emulator verification covered URL import, Storage Access Framework file import, search, favorites, process-restart persistence, actual video rendering, landscape configuration change, and background/resume. Additional EPG verification used local M3U and XMLTV.gz files: enabled import displayed current/next programs, while ignored import made no EPG request.
- Release output is `app/build/outputs/apk/release/app-release-unsigned.apk`; debug output is `app/build/outputs/apk/debug/app-debug.apk`.
- The compile SDK 37 compatibility warning from AGP 9.0.1 remains non-fatal. Dependency-update warnings reported by lint were left unchanged to avoid unrelated upgrades.

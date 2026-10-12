# AniTV status

As of 2026-10-12. First implementation: commit `dbf3002`.

## Implemented

- Android TV search, results, episode list, and subtitled HLS player in the existing `app` module.
- Loading, empty-result, source-error, and playback-error messages.
- Compose for TV buttons and lists, a focused search field, Back navigation, and Media3 `PlayerView` controls.
- HiAnime requests and parsing isolated in `HiAnimeSource`. Only the ZokoAnime subtitle server is supported.
- Android 14 minimum (`minSdk = 34`); `targetSdk` and `compileSdk` remain 37.

## Verified

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed. All 3 parsing tests passed.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (35 MB at the last build).
- Live HiAnime search, episode-list, server, and embed responses were inspected on 2026-10-12. A sampled HLS playlist and VTT subtitle returned HTTP 200 with the player `Referer`.
- A connected Beko Google TV reports API 34. The installed app launched and a search for `one piece` displayed results.

## Open verification and limits

- The complete result → episode → playback path has **not** been exercised on the TV. Video decoding, subtitle rendering, audio, seeking, Select, and Back during playback remain unverified.
- During the TV probe, D-pad Down did not visibly move focus away from the search field. Keyboard and D-pad focus behavior needs a focused follow-up test and possibly a fix.
- Search reads one result page. Playback supports one HiAnime subtitle server and requires an HTTPS subtitle track. There is no server fallback.
- HiAnime is an unofficial, changeable source. Markup, player encoding, blocks, and expiring links can break this flow. The app reports these failures but cannot repair the source automatically.
- No login, downloads, history, recommendations, other sources, or publishing are included.

Do not describe the APK as fully working on TV until the complete remote and playback flow passes there. Content rights remain with their owners; personal installation does not grant redistribution rights.

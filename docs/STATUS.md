# AniTV status

As of 2026-10-12.

## Implemented

- TV home screen with Search, Currently watching, Favorites, and Completed sections.
- Local favorites, last episode and playback position, and manually controlled title completion survive app restarts via `SharedPreferences`.
- Playing records progress every five seconds and when leaving playback. Currently watching contains titles with saved progress unless marked completed. Playback starting or ending never marks a title completed; use the detail-screen button.
- Full-viewport video with Compose remote controls for Back, 10-second seeking, play/pause, captions, and quality when the HLS stream exposes multiple supported video tracks. Controls hide after six seconds of play or on D-pad Down; D-pad Up or Select shows them again.
- Anime detail screen shows a portrait poster at the top left, with the title, description, and facts to its right. Metadata and poster bytes are cached locally for 24 hours. Old wide-cover cache entries refresh on first open. A stale entry remains visible during refresh; missing artwork triggers a new fetch. Episode loading continues if details fail.
- Search field routes D-pad Down to the Search button. Back unwinds playback, detail, section, then home.
- Search, results, episodes, source errors, and the subtitled ZokoAnime path remain in place, including the player `Referer` header.
- Home buttons use 10 dp corners and labeled Material icons. Episode buttons change background color on focus without growing.
- The supplied AniTV logo appears in the app header and launcher icon; a 320×180 crop serves as the Android TV banner.
- Android Studio Compose previews are defined for sample home and episode screens without network requests or a TV.

## Verified

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed: 9 unit tests, including detail parsing, cache expiry, and saved detail links.
- Current debug APK: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `bd044ed0157cacc2f148e53e1ea4a9b3334c34768ec8e9092780c8b81a70d827`).
- The 320×180 TV banner was visually inspected. Both `@Preview` functions compiled, but their Android Studio renders were not inspected.
- Live HiAnime search and One Piece detail responses were inspected on 2026-10-12. The page exposed a description, facts, and HTTPS portrait poster. A sampled 385×600 poster returned HTTP 200 as a WebP image.
- Previous APK (SHA-256 `b16b051bdc92ef158d201dc3394c3c755c46f5c5e8545f094843f94178bb1bd9`) was manually installed and tested by the user on a Beko Google TV (API 34). The user reported search, focus, result → episode → playback, video, audio, subtitles, controls, seeking, Back, favorites, progress after restart, and completed archive working.

## Open verification and limits

- **Current APK device status: NOT TESTED.** Home-button focus and icon layout, episode focus without scaling, and launcher artwork await TV verification. Portrait poster placement, auto-hiding controls, D-pad Down/Up behavior, quality selection, and cache refresh also remain unverified on this build. No ADB device test or installation was done, per the repository instructions.
- Quality options appear only when Media3 reports multiple supported HLS video tracks. A single-variant stream stays on Auto.
- Search reads one result page. Playback supports one HiAnime subtitle server and requires an HTTPS subtitle track. There is no server fallback.
- HiAnime is unofficial and changeable. Markup, player encoding, blocks, and expiring links can break this flow; the app reports those failures.
- No login, downloads, recommendations, other sources, or publishing are included.

Content rights remain with their owners; personal installation does not grant redistribution rights.

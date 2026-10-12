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
- English and Turkish app text follows the TV language by default. Settings offers System default, English, and Türkçe; Android stores an explicit choice. Source and playback errors have localized messages. English and Turkish settings previews are defined.

## Verified

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed: 9 unit tests, including detail parsing, cache expiry, and saved detail links.
- Current debug APK: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `0645c9e983a736b066f524adee925bbce46c821285e0cbe1758eda5d904be7b4`).
- All 84 English and Turkish string keys and format arguments match. The APK contains Turkish strings and declares its locale configuration.
- The 320×180 TV banner was visually inspected. All four `@Preview` functions compiled, but their Android Studio renders were not inspected.
- Live HiAnime search and One Piece detail responses were inspected on 2026-10-12. The page exposed a description, facts, and HTTPS portrait poster. A sampled 385×600 poster returned HTTP 200 as a WebP image.
- Previous APK (SHA-256 `b16b051bdc92ef158d201dc3394c3c755c46f5c5e8545f094843f94178bb1bd9`) was manually installed and tested by the user on a Beko Google TV (API 34). The user reported search, focus, result → episode → playback, video, audio, subtitles, controls, seeking, Back, favorites, progress after restart, and completed archive working.

## Open verification and limits

- **Current APK device status: NOT TESTED.** System-language selection, manual language changes, Settings focus and Back, home-button layout, episode focus, launcher artwork, playback, and cache behavior await TV verification. Settings previews have not been rendered. No ADB device test or installation was done, per the repository instructions.
- HiAnime titles, descriptions, metadata values, most episode names, and subtitle tracks remain in the source language. Generic “Episode N” labels are localized. The source was not contacted for this build; its site behavior remains unverified.
- Quality options appear only when Media3 reports multiple supported HLS video tracks. A single-variant stream stays on Auto.
- Search reads one result page. Playback supports one HiAnime subtitle server and requires an HTTPS subtitle track. There is no server fallback.
- HiAnime is unofficial and changeable. Markup, player encoding, blocks, and expiring links can break this flow; the app reports those failures.
- No login, downloads, recommendations, other sources, or publishing are included.

Content rights remain with their owners; personal installation does not grant redistribution rights.

# AniTV status

As of 2026-10-12.

## Implemented

- TV home screen with Search, Currently watching, Favorites, and Completed sections.
- Local favorites, last episode and playback position, and manually controlled title completion survive app restarts via `SharedPreferences`.
- Playing records progress every five seconds and when leaving playback. Currently watching contains titles with saved progress unless marked completed. Playback starting or ending never marks a title completed; use the detail-screen button.
- Full-viewport video with Compose remote controls for Back, 10-second seeking, play/pause, and captions. The player still sends `Referer` for HLS and VTT.
- Search field routes D-pad Down to the Search button. Back unwinds playback, detail, section, then home.
- Search, results, episodes, source errors, and the subtitled ZokoAnime path remain in place.

## Verified

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed, including 3 library tests and 3 source parsing tests.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `b16b051bdc92ef158d201dc3394c3c755c46f5c5e8545f094843f94178bb1bd9`).
- On the connected Beko Google TV (API 34), a prior build of this milestone displayed the home screen and search results for `one piece`. D-pad Down moved from the search field to the Search button after keyboard dismissal. Selecting One Piece loaded episodes. Selecting Episode 1 rendered full-viewport video with English subtitles.
- The prior build's standard `PlayerView` controls were not visible under D-pad/Select. The current APK replaces them with Compose controls. These controls have not yet been device-tested.

## Open verification and limits

- The user requested no further ADB testing. Installing the current APK over wireless ADB stalled twice and was stopped; installation is unverified. Current APK controls, video, audio, subtitle toggle, seeking, Back, and persistence across restart require manual TV checks. Audio was not audibly verified during the earlier probe.
- Search reads one result page. Playback supports one HiAnime subtitle server and requires an HTTPS subtitle track. There is no server fallback.
- HiAnime is unofficial and changeable. Markup, player encoding, blocks, and expiring links can break this flow; the app reports those failures.
- No login, downloads, recommendations, other sources, or publishing are included.

Content rights remain with their owners; personal installation does not grant redistribution rights.

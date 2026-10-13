# AniTV status

As of 2026-10-13.

## Implemented

- TV home screen with Search, Currently watching, Favorites, and Completed sections.
- Local favorites, last episode and playback position, and manually controlled title completion survive app restarts via `SharedPreferences`.
- Playing records progress every five seconds and when leaving playback. Currently watching contains titles with saved progress unless marked completed. Playback starting or ending never marks a title completed; use the detail-screen button.
- Full-viewport video with Compose remote controls for Back, 10-second seeking, play/pause, captions, and quality when the HLS stream exposes multiple supported video tracks. The caption menu selects Off, Original, or translation, and saves a target language from 15 choices. Controls hide after six seconds of play or on D-pad Down; D-pad Up or Select shows them again.
- Anime detail screen shows a portrait poster at the top left, with the title, description, and facts to its right. Metadata and poster bytes are cached locally for 24 hours. Old wide-cover cache entries refresh on first open. A stale entry remains visible during refresh; missing artwork triggers a new fetch. Episode loading continues if details fail.
- Search field routes D-pad Down to the Search button. Back unwinds playback, detail, section, then home.
- Search, results, episodes, source errors, and the subtitled ZokoAnime path remain in place, including the player `Referer` header.
- Home buttons use 10 dp corners and labeled Material icons. Episode buttons change background color on focus without growing.
- The updated AniTV SVG paths render as a scalable vector in the app header. The new 1024×1024 PNG is the launcher icon; a 1280×720 SVG-rendered crop is the Android TV banner. Unused `ic_launcher` mipmap icons were removed.
- The repository has the upstream GPL-3.0 `LICENSE` and a minimal README with a centered transparent SVG logo and `AniTV-dot` below it. Separate dark/light SVG variants keep the logo readable in both GitHub themes.
- Android Studio Compose previews are defined for sample home and episode screens without network requests or a TV.
- English and Turkish app text follows the TV language by default. Settings offers System default, English, and Türkçe; Android stores an explicit choice. Source and playback errors have localized messages. English and Turkish settings previews are defined.
- Settings supports keyed Google Cloud Translation and DeepL caption translation. Provider keys are encrypted at rest with Android Keystore. Playback fetches timed VTT cues and translates rolling batches of four ahead of the current cue; original Media3 captions return if translation fails. English and Portuguese DeepL targets use supported regional codes.

## Verified

- `./gradlew :app:assembleDebug --offline` passed for the logo update. The previous `:app:testDebugUnitTest` run passed 14 unit tests; tests were not rerun for this artwork-only change.
- The local `LICENSE` matches the `origin/main:LICENSE` Git blob exactly. Both README SVG variants were rendered and visually inspected against their target backgrounds. README layout on GitHub remains unverified until pushed.
- Current debug APK: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `acaef3a6e5828b87914fb52e6a98ba82493f5ea5bf637a21a44f4e8a7fcfa3e8`).
- All 98 English and Turkish string keys match. The APK contains Turkish strings and declares its locale configuration. VTT parser and four-cue lookahead tests pass.
- The 1280×720 TV banner was visually inspected. The APK packages the new vector, icon, and banner; no `ic_launcher` resources remain. All four `@Preview` functions compiled, but their Android Studio renders were not inspected.
- Live HiAnime search and One Piece detail responses were inspected on 2026-10-12. The page exposed a description, facts, and HTTPS portrait poster. A sampled 385×600 poster returned HTTP 200 as a WebP image.
- Previous APK (SHA-256 `b16b051bdc92ef158d201dc3394c3c755c46f5c5e8545f094843f94178bb1bd9`) was manually installed and tested by the user on a Beko Google TV (API 34). The user reported search, focus, result → episode → playback, video, audio, subtitles, controls, seeking, Back, favorites, progress after restart, and completed archive working.

## Open verification and limits

- **Current APK device status: NOT TESTED.** In-app vector rendering, launcher icon and TV banner appearance, caption menu focus and Back, language selection and persistence, translation provider/key entry, translated captions and rolling lookahead, translation failure fallback, Settings focus and Back, system-language selection, playback, and cache behavior await TV verification. Settings previews have not been rendered. No ADB device test or installation was done, per the repository instructions.
- HiAnime titles, descriptions, metadata values, most episode names, and subtitle tracks remain in the source language. Generic “Episode N” labels are localized. The source was not contacted for this build; its site behavior remains unverified.
- Quality options appear only when Media3 reports multiple supported HLS video tracks. A single-variant stream stays on Auto.
- Translation API requests were not exercised with real provider keys. Google Cloud Translation Basic v2 requires an API key. Google ML Kit is keyless, but its terms prohibit TV app use without prior written permission, so it is not integrated. DeepL documents advise against using API keys in client-side code; this direct TV client sends the user's key to DeepL over HTTPS, so use a personal key and account for runtime extraction risk on a compromised device. Android Keystore protects the saved key at rest.
- Search reads one result page. Playback supports one HiAnime subtitle server and requires an HTTPS subtitle track. There is no server fallback.
- HiAnime is unofficial and changeable. Markup, player encoding, blocks, and expiring links can break this flow; the app reports those failures.
- No login, downloads, recommendations, other sources, or publishing are included.

Content rights remain with their owners; personal installation does not grant redistribution rights.

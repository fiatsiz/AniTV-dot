# AniTV status

As of 2026-10-13.

## Implemented

- Continue watching appears below the main home buttons as one large, focusable artwork card for the most recently watched non-completed title. It shows title, description, saved episode, timestamp, duration when known, and SUB/DUB. Home scrolls vertically with the D-pad; an empty state appears before any progress is saved.
- The title detail screen offers a play-icon Continue watching button whenever that title has saved progress. Both resume actions request a fresh stream for the saved episode and seek to the saved millisecond position. They restore saved SUB/DUB when available; a missing saved audio version shows an error and available choices. They do not advance to the next episode or change manual completion.
- Progress now saves optional audio version and watch time. Watch time determines the featured home title independently of favorite/completion edits. Existing records retain their episode, position, favorites, and completion; missing/unknown audio defaults to SUB and missing watch time to zero, preserving the existing list order for older saves.
- Home artwork reads the existing detail cache off the main thread and refreshes missing/stale data through `HiAnimeSource`. Cached artwork stays visible during refresh. A missing image has a fixed-size placeholder, and artwork failures leave resume usable.

- Episode audio selection offers Subtitled and Dubbed when both supported versions exist. A single available version starts directly. Dubs use the source audio language with no language picker. Dub streams can play without subtitles; caption controls and translation are disabled when no caption track exists. Existing captions still work when supplied. English and Turkish labels/errors cover both playback versions.

- Foreground-only player lifecycle: Activity Stop pauses, saves progress, and releases Media3 synchronously. Returning restores the position and prior play/pause state. Stream responses arriving while stopped cannot start background playback.

- Modular Compose UI: `MainActivity` only starts the themed app; `ui/AniTVApp.kt` coordinates state and navigation; home, search, library, detail, Settings, and previews live in separate files. Playback and caption settings live under `playback/`.

- TV home screen with Search, Currently watching, Favorites, and Completed sections.
- Local favorites, last episode and playback position, and manually controlled title completion survive app restarts via `SharedPreferences`.
- Playing records progress every five seconds and when leaving playback. Currently watching contains titles with saved progress unless marked completed. Playback starting or ending never marks a title completed; use the detail-screen button.
- Full-viewport video with Compose remote controls for Back, 10-second seeking, play/pause, captions, and quality when the HLS stream exposes multiple supported video tracks. The caption menu selects Off, Original, or translation, and saves a target language from 15 choices. Controls hide after six seconds of play or on D-pad Down; D-pad Up or Select shows them again.
- Anime detail screen shows a portrait poster at the top left, with the title, description, and facts to its right. Metadata and poster bytes are cached locally for 24 hours. Old wide-cover cache entries refresh on first open. A stale entry remains visible during refresh; missing artwork triggers a new fetch. Episode loading continues if details fail.
- Search field routes D-pad Down to the Search button. Back unwinds playback, detail, section, then home.
- Search, results, episodes, source errors, and the subtitled ZokoAnime path remain in place, including the player `Referer` header.
- HiAnime and stream requests use the TV's default WebView User-Agent. If WebView is unavailable, they use the TV's Android HTTP User-Agent, then a device-labeled fallback.
- Home buttons use 10 dp corners and labeled Material icons. Episode buttons change background color on focus without growing.
- The updated AniTV SVG paths render as a scalable vector in the app header. The new 1024×1024 PNG is the launcher icon; a 1280×720 SVG-rendered crop is the Android TV banner. Unused `ic_launcher` mipmap icons were removed.
- The repository has the upstream GPL-3.0 `LICENSE` and a minimal README with a centered transparent SVG logo and `AniTV-dot` below it. Separate dark/light SVG variants keep the logo readable in both GitHub themes.
- The README disclaimer identifies third-party content and services, content rights, translation data sent to the selected provider, and the GPL-3.0 warranty terms.
- Android Studio Compose previews are defined for sample home and episode screens without network requests or a TV.
- English and Turkish app text follows the TV language by default. Settings offers System default, English, and Türkçe; Android stores an explicit choice. Source and playback errors have localized messages. English and Turkish settings previews are defined.
- Settings supports keyed Google Cloud Translation and DeepL caption translation. Provider keys are encrypted at rest with Android Keystore. Playback fetches timed VTT cues and translates rolling batches of four ahead of the current cue; original Media3 captions return if translation fails. English and Portuguese DeepL targets use supported regional codes.
- Original and translated captions use the same Media3 subtitle renderer and saved style. Default captions use white sans serif text at 100% size on an 80% opaque black rectangular background. The player caption menu offers font, 50%/75%/100%/150%/200% size, background color, and text color choices.

## Verified

- Continue watching passed `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`; all 27 unit tests passed. New tests cover older save compatibility, audio/watch-time persistence, most-recent selection, favorite-edit independence, completed/unstarted exclusion, exact episode targeting without advancing, and timestamp formatting. The new Continue watching preview compiles; Android Studio rendering and TV behavior remain unverified.
- English and Turkish resources have 125 matching string keys. No dependencies, Gradle/SDK versions, or source selectors/endpoints changed. No live source request was made for this milestone; the previously observed local search HTTP 403 remains an open source-access limit.

- Dub milestone passed `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`; all 21 unit tests passed. New sanitized source fixtures cover SUB+DUB, SUB-only with an unsupported DUB provider, DUB-only, malformed/insecure embeds, and DUB without captions while preserving SUB caption validation. The audio choice preview compiles; its render and D-pad behavior remain unverified.
- Live probes on 2026-10-13: episode 1 server metadata and both ZokoAnime SUB/DUB embed pages returned HTTP 200. Both decoded players exposed HTTPS stream URLs and one caption track. A separate local `search?keyword=takopi` probe returned HTTP 403. No video, audio, subtitle rendering, or TV source flow was exercised.

- Foreground lifecycle unit tests cover immediate Stop release, saved progress, return/resume, paused-state preservation, late stream handling, and idempotent disposal. `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed with 17 tests. Physical Home/return behavior is not yet verified on TV.

- UI extraction passed `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`; all 15 existing unit tests passed. Screen layout, callbacks, source behavior, and storage formats were preserved. Device behavior remains unverified for this build.

- The README disclaimer was checked against the source and translation data flow and the local GPL-3.0 license. `./gradlew :app:assembleDebug --offline` passed. No app source or resource changed; this APK has not been tested on the TV.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed for caption styling. All 15 unit tests passed, including default caption style and size validation.
- Current debug APK: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `83ac7b681db126dd64b217f740c6d034746ea2225b0325881eec668dc1568b90`).
- `./gradlew :app:assembleDebug --offline` passed for the User-Agent update. The previous `:app:testDebugUnitTest` run passed 14 unit tests; tests were not rerun for this request-header change.
- The local `LICENSE` matches the original GitHub `LICENSE` blob exactly. Both README SVG variants were rendered and visually inspected against their target backgrounds. Browser rendering on GitHub was not inspected.
- GitHub reports `master` as the default and only remote branch. The former `main` branch was deleted after confirming `master` contains the project and license.
- All 125 English and Turkish string keys match. The APK contains Turkish strings and declares its locale configuration. VTT parser and four-cue lookahead tests pass.
- The 1280×720 TV banner was visually inspected. The APK packages the new vector, icon, and banner; no `ic_launcher` resources remain. The four original `@Preview` functions compiled, but their Android Studio renders were not inspected.
- Live HiAnime search and One Piece detail responses were inspected on 2026-10-12. The page exposed a description, facts, and HTTPS portrait poster. A sampled 385×600 poster returned HTTP 200 as a WebP image.
- Previous APK (SHA-256 `b16b051bdc92ef158d201dc3394c3c755c46f5c5e8545f094843f94178bb1bd9`) was manually installed and tested by the user on a Beko Google TV (API 34). The user reported search, focus, result → episode → playback, video, audio, subtitles, controls, seeking, Back, favorites, progress after restart, and completed archive working.

## Open verification and limits

- **Current APK device status: NOT TESTED.** Install manually and test detail/home Continue watching, exact episode and seek position after app restart, saved SUB/DUB, missing-audio choices, main-button-to-card D-pad scrolling and return, empty/completed states, thumbnail/cache fallback, and video/audio/subtitles/seek/Back. Test both audio choices on a dubbed title and direct playback on a title without a supported dub. Dub audio, optional captions, translation, audio-choice focus/Select/Back, retry, and video/audio/seek require TV verification. Physical Home during playback must stop all sound; return/resume, paused-state restoration, late stream responses after Home, and Back cleanup await TV testing. Caption style appearance, each style choice, persistence, D-pad focus and Back, and matching original/translated rendering await TV verification. Actual User-Agent selection and HiAnime/stream response on the TV, in-app vector rendering, launcher icon and TV banner appearance, language selection and persistence, translation provider/key entry, translated captions and rolling lookahead, translation failure fallback, Settings focus and Back, system-language selection, playback, and cache behavior also await TV verification. Settings previews have not been rendered. No ADB device test or installation was done, per the repository instructions.
- HiAnime titles, descriptions, metadata values, most episode names, and subtitle tracks remain in the source language. Generic “Episode N” labels are localized. Server and embed metadata were probed for this build; a local search request was blocked with HTTP 403. End-to-end site behavior on TV remains unverified.
- Quality options appear only when Media3 reports multiple supported HLS video tracks. A single-variant stream stays on Auto.
- Translation API requests were not exercised with real provider keys. Google Cloud Translation Basic v2 requires an API key. Google ML Kit is keyless, but its terms prohibit TV app use without prior written permission, so it is not integrated. DeepL documents advise against using API keys in client-side code; this direct TV client sends the user's key to DeepL over HTTPS, so use a personal key and account for runtime extraction risk on a compromised device. Android Keystore protects the saved key at rest.
- Search reads one result page. Playback supports ZokoAnime SUB and DUB entries only. SUB requires an HTTPS subtitle track; DUB captions are optional. Other providers are ignored and there is no server fallback. Audio selection is per episode; library progress remains shared between audio versions.
- HiAnime is unofficial and changeable. Markup, player encoding, blocks, and expiring links can break this flow; the app reports those failures.
- No login, downloads, recommendations, other sources, or publishing are included.

Content rights remain with their owners; personal installation does not grant redistribution rights.

# AniTV

AniTV is a personal Android TV app for searching anime on `https://hianime.at`, selecting an episode, and watching it with a TV remote. Extend this existing Android Studio project; do not create a second project or replace the starter files wholesale.

## Current project

- The project has one `app` module and Kotlin DSL Gradle files.
- The app package and application ID are `indie.fiatsiz.anitv`.
- `app/build.gradle.kts` sets `minSdk = 34`, `targetSdk = 37`, and `compileSdk = 37`.
- `gradle/libs.versions.toml` contains Compose for TV, Media3, and parser dependencies. Add dependencies only when needed.
- `MainActivity.kt` contains the search, episode, and playback UI. `source/HiAnimeSource.kt` contains site requests and parsing.
- `app/src/main/AndroidManifest.xml` declares a Leanback launcher entry and Internet permission. Change it only when a feature needs it.

## Rules

- Read `docs/STATUS.md` and `docs/ARCHITECTURE.md` before editing. Keep `docs/` accurate: update status after each verified milestone and architecture when ownership or data flow changes. Record unverified device behavior as unverified.
- For UI work, use the installed `ui-ux-pro-max` skill. Run a relevant design-system search and focused UX searches; apply only guidance that fits Android TV, Compose for TV, and D-pad navigation. Check the skill's pre-delivery guidance before calling the UI complete.
- Use the Git CLI for version control. Check `git status` before edits and review `git diff` before each commit.
- Commit each completed, verified milestone with a clear message. Stage only files related to that milestone; do not commit unrelated user changes.
- Do not use `git reset --hard`, force pushes, or history rewrites. Do not create a remote or push unless the user asks.

## Current baseline

- Target Android TV / Google TV, installed locally as an APK.
- The user's TV runs Android TV OS 14. Android 14 corresponds to API level 34; keep `minSdk = 34`. API level 14 means Android 4.0 and is not the intended minimum.
- Search, results, episode selection, subtitled playback, and loading/error states already exist. Preserve them.
- Every screen and playback control must work with D-pad, Select, and Back. Show visible focus.
- Do not claim the APK works until it builds and the relevant flow has been exercised. State what could not be tested.

## Next milestone

- Make a TV-friendly home screen with clear access to search, favorites, currently watching, and a completed-anime archive.
- Store favorites, watch progress, and completed status locally so they survive app restarts. Make progress and completion rules explicit; do not mark a title completed merely because playback started.
- Let playback fill the screen, with remote-accessible controls and a reliable path back to the episode list.
- Fix focus or navigation defects found while building these screens. Keep focused items visible and make Back behavior predictable.
- Optimize player setup or playback only where a measured problem or concrete code issue warrants it. Preserve working streams, subtitles, and `Referer` handling.
- Keep login, downloads, recommendations, multiple sources, and publishing outside this milestone.

## Implementation

- Use the existing Kotlin, Compose for TV, Media3 ExoPlayer, and version catalog setup. Avoid changing Gradle or SDK versions without a concrete need.
- Keep HiAnime requests and parsing in one source layer so site changes do not spread through the UI.
- Inspect the site's current responses before choosing selectors or endpoints. Use `ani-cli` as a behavioral reference, not code to copy. Explain the GPLv3 licensing impact before copying any of its code.
- Do not assume HiAnime has a stable, official API. Show useful errors for missing results, changed markup, blocked requests, expired links, and playback failures.
- Pass required HTTP headers, including `Referer` when needed, to the player. Never log full stream URLs, cookies, or tokens.
- Use HTTPS. Do not disable certificate validation, embed secrets, or add anti-bot evasion services.
- Add only dependencies needed for this flow. Keep source handling, TV navigation, and playback responsibilities separate.

## Workflow

1. Inspect the repository and available Android tooling before editing.
2. Preserve the existing search → result → episode → playback path while adding the next milestone.
3. Add focused tests for local library/progress rules and any nontrivial parsing change.
4. Build a debug APK. On the connected TV, test D-pad navigation, full-screen playback, video, audio, subtitles, seek, and Back where possible.
5. Update `docs/STATUS.md` and `docs/ARCHITECTURE.md` to match verified results. Report the APK path, tested and untested steps, and source breakage.

Content rights belong to their respective owners. Personal installation does not grant redistribution rights.

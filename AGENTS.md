# AniTV

AniTV is a personal Android TV app for searching anime on `https://hianime.at`, selecting an episode, and watching it with a TV remote. Extend this existing Android Studio project; do not create a second project or replace the starter files wholesale.

Read `docs/STATUS.md` for verified behavior and open gaps, and `docs/ARCHITECTURE.md` for code boundaries. Update these documents when behavior or verification status changes.

## Current project

- The project has one `app` module and Kotlin DSL Gradle files.
- The app package and application ID are `indie.fiatsiz.anitv`.
- `app/build.gradle.kts` sets `minSdk = 34`, `targetSdk = 37`, and `compileSdk = 37`.
- `gradle/libs.versions.toml` contains Compose for TV, Media3, and parser dependencies. Add dependencies only when needed.
- `MainActivity.kt` contains the search, episode, and playback UI. `source/HiAnimeSource.kt` contains site requests and parsing.
- `app/src/main/AndroidManifest.xml` declares a Leanback launcher entry and Internet permission. Change it only when a feature needs it.

## Rules

- Use the Git CLI for version control. Check `git status` before edits and review `git diff` before each commit.
- Commit each completed, verified milestone with a clear message. Stage only files related to that milestone; do not commit unrelated user changes.
- Do not use `git reset --hard`, force pushes, or history rewrites. Do not create a remote or push unless the user asks.

## First release

- Target Android TV / Google TV, installed locally as an APK.
- The user's TV runs Android TV OS 14. Android 14 corresponds to API level 34; keep `minSdk = 34`. API level 14 means Android 4.0 and is not the intended minimum.
- Include search, results, an episode list, subtitled playback, and clear loading and error states.
- Every screen and playback control must work with D-pad, Select, and Back. Show visible focus.
- Exclude login, downloads, watch history, recommendations, multiple sources, and publishing.
- Do not claim the APK works until it builds and the relevant flow has been exercised. State what could not be tested.

## Implementation

- Use the existing Kotlin, Compose for TV, and version catalog setup. Add Media3 ExoPlayer for playback. Avoid changing Gradle or SDK versions without a concrete need.
- Keep HiAnime requests and parsing in one source layer so site changes do not spread through the UI.
- Inspect the site's current responses before choosing selectors or endpoints. Use `ani-cli` as a behavioral reference, not code to copy. Explain the GPLv3 licensing impact before copying any of its code.
- Do not assume HiAnime has a stable, official API. Show useful errors for missing results, changed markup, blocked requests, expired links, and playback failures.
- Pass required HTTP headers, including `Referer` when needed, to the player. Never log full stream URLs, cookies, or tokens.
- Use HTTPS. Do not disable certificate validation, embed secrets, or add anti-bot evasion services.
- Add only dependencies needed for this flow. Keep source handling, TV navigation, and playback responsibilities separate.

## Workflow

1. Inspect the repository and available Android tooling before editing.
2. Complete one path: query → result → episode → playback.
3. Add a focused test for nontrivial parsing, using a sanitized saved response where possible.
4. Build a debug APK. If an emulator or TV is available, test D-pad navigation and playback there.
5. Report the APK path, tested and untested steps, and source breakage. Do not expand scope to hide a failing path.

Content rights belong to their respective owners. Personal installation does not grant redistribution rights.

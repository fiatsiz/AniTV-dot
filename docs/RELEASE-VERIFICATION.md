# AniTV 1.0.0 preparation verification

Verified on 2026-10-13. Application implementation and rules publication are complete. **An owner-signed release APK is not available: the permanent APK signing credentials are missing. This is not a release-readiness claim.**

## Provenance and artifacts

| Item | Value |
| --- | --- |
| Matching application source commit | `7d0f3db56e5e7b4686dcbcd11dc43a83624e8d9b` (local `master`; later documentation-only commits do not change the APK) |
| Parser implementation commit | `0f8ed6d48d65b5d126f9b227942273016ddb7420` |
| Published rules commit | `e97e669715f09b71e1713ec464b010083d226279` on `origin/rules` |
| Remote application branch | `origin/master` remains `87450f2603453e25a989e5661e06bafd3f282cfd`; application changes were not pushed |
| Available test APK | `app/build/outputs/apk/debug/app-debug.apk` — debug-signed, debuggable; **not the requested owner-signed release** |
| APK SHA-256 | `547c359cafb10446ffb2e88e89d208d25b7f200e23f469be7a55b2b05d5763eb` |
| Checksum file | `app/build/outputs/apk/debug/app-debug.apk.sha256` |
| Debug certificate SHA-256 | `2f23b986e1ddcad860366b24a2a690469ab8ef677302138ef0d3d3bca581e9ed` — Android Debug, not the permanent owner certificate |
| Rules public DER key SHA-256 | `e8f1aada189454a4815011558cec72a623d3ea734b3e2b3b8683772d9fa992d7` |
| Initial signed envelope SHA-256 | `265a58dc674760cb2f652c645268166a3dcb140805b3550d6e0a0d381cd2559c` |
| Release APK / release certificate | Unavailable until owner credentials are supplied; non-debuggable release APK verification remains pending |

Android SDK `aapt` confirmed package `indie.fiatsiz.anitv`, versionName `1.0.0`, versionCode `1`, minSdk `34`, target/compile SDK `37`. SDK `apksigner` verified the available debug APK's v2 signature. `aapt` explicitly reports it as debuggable. No debug key was used to create a release build.

## Automated verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintRelease --offline` passed. Unit suite: 52 tests, 51 passed, one publication-only test skipped in ordinary runs. Release lint: 0 errors, 13 warnings, 5 hints. Existing warnings were retained without disabling lint or adding a baseline.
- The publication command independently ran the skipped publication test successfully. The actual shipping Kotlin parser validated sanitized search, details, episode-list JSON, SUB/DUB server JSON, subtitled player, and captionless dubbed player fixtures against expected models before signing. Initial bundled and published envelopes match byte for byte.
- New JVM tests cover changed selectors, extraction attributes, relative endpoint templates, JSON paths, matching values, player variable/key and Base64/XOR settings, HTML/JSON wrapper handling, numeric ID and HTTPS checks, SUB caption requirements, exact payload identity, tampering/wrong signatures, unsupported schema/minimum app version, missing/invalid fields/selectors/templates, rollback/conflicts, oversized data, HTTP failures, offline defaults/cache, previous-copy recovery, interrupted temporary files, cache write failure, conditional ETags, one startup check, the single deadline, and discarded late responses without cache/ETag writes.
- The same compiled source instance accepted an injected signed revision 2 and parsed changed search/detail/episode/server/player responses. No deliberately broken or test revision was published.
- `./gradlew :app:assembleDebugAndroidTest --offline` passed using existing test dependencies. Manual emulator instrumentation returned `OK (1 test)` in 1.243 seconds. It executed the installed APK's parser with Android JSON/regex/crypto, verified the production bundled signature, and accepted an injected revision 2 with changed selectors, attributes, endpoint and JSON wrapper paths. It parsed the changed fixture without rebuilding/reinstalling the application. Test keys and scratch cache stayed separate from production trust/storage.
- Android startup testing caught a regex incompatibility that JVM tests did not catch; both literal endpoint-placeholder braces are now escaped. Existing release-lint errors were fixed with scoped Media3 opt-ins and Compose-aware resource/configuration reads.
- `:app:assembleRelease --offline` failed explicitly at `validateOwnerReleaseSigning`, listing the four missing owner environment variables. Debug builds/tests/lint still pass. No replacement APK key was generated.
- English/Turkish resources have 138 matching keys. No dependencies, SDK versions, application ID, saved-library format, or release optimization settings changed. `.gitignore` was clean initially and remains untouched.

## Published endpoint

The public [rules branch](https://github.com/fiatsiz/AniTV-dot/tree/rules) contains only editable/signed rules, sanitized fixtures/expected models, instructions, and the GPL-3.0 license. Its separate worktree is `/home/badico/Belgeler/AniTV-rules`.

The production [GitHub Contents endpoint](https://api.github.com/repos/fiatsiz/AniTV-dot/contents/hianime.signed.json?ref=rules) returned HTTP 200 with raw-content media type and 3,431 bytes. Its exact bytes matched the bundled envelope. OpenSSL verified its signature against the bundled public key. The decoded payload is schema 1, revision 1, minimum app versionCode 1. A conditional request with the returned ETag produced HTTP 304. No GitHub token is embedded.

The rules private key is outside Git at `/home/badico/.local/share/anitv-signing/rules-private.pk8`, mode 0600. Back it up securely and independently of the owner APK keystore. Publication instructions and rollback limits are in `docs/RELEASE.md` and the rules branch README.

## Emulator verification

Only `Television_1080p` / `emulator-5554` was controlled; `ro.kernel.qemu=1`, Android API 34. No physical TV was controlled. The existing app was updated with `adb install -r`; no app uninstall or data clear occurred.

- Home rendered immediately. Production rules were accepted and written with their ETag into private current/previous cache records. New process startup displayed revision 1/current. English and Turkish version/revision/outcome labels rendered correctly; language changes retained Settings and the selected process snapshot.
- A 10,000 ms emulator network delay exceeded the startup deadline. Home remained usable; Settings displayed revision 1 and the timeout/local-rules outcome. The snapshot stayed frozen after normal network timing returned and during Activity recreation. Emulator latency was restored to 0 ms. Complete link loss was not reproduced on this wired emulator; offline IOException behavior is covered by injected transport tests.
- Live `takopi` search returned Takopi's Original Sin. D-pad/Select opened details, portrait/facts, six episodes, and the SUB/DUB choice. Earlier desktop probes returned HTTP 200 for current search/detail/episode/server formats with the app-style User-Agent. No live source breakage was reproduced.
- SUB full-screen video and original subtitles rendered. The player advanced, seeking forward worked, and Android reported an active stereo 48 kHz AudioTrack. Audible sound was not verified: this emulator session used `-no-audio`.
- The episode sidebar opened over video, marked/focused the current episode, traversed with D-pad, closed with Back and restored Episodes focus. Selecting another episode loaded its stream with SUB retained. Episode-list retry/error while playing was not forced.
- DUB selected a separate stream and rendered video/captions. Home Continue watching resumed the same episode with saved DUB and a positive saved position. Saved test progress confirmed episode 1 / DUB; the resumed timer began around 0:27 rather than zero.
- Favorites displayed the existing Overgeared artwork/detail card and saved Episode 2. Back navigated out of playback/detail/search and Settings. The original library preferences were backed up before testing and restored byte for byte afterward; Home again showed the original two watching titles and one favorite. Test progress did not replace the user's saved progress.
- Settings passed visual inspection at normal font scale and `font_scale=2.0` with motion scales disabled. Version/revision/outcome stayed readable; D-pad scrolled the focused language choice fully into view. Normal text size and motion defaults were restored. Only Android TV landscape was applicable; phone/portrait layouts were not tested.

## Remaining release gate

The owner must supply the permanent APK keystore and `ANITV_RELEASE_KEYSTORE`, `ANITV_RELEASE_STORE_PASSWORD`, `ANITV_RELEASE_KEY_ALIAS`, and `ANITV_RELEASE_KEY_PASSWORD` to the Gradle process. Follow `docs/RELEASE.md`, rerun signed release assembly, verify package/version/certificate/non-debuggable status, and record the release checksum/fingerprint. Do not uninstall the existing debug install automatically: certificate mismatch can prevent in-place updates, and uninstalling can erase saved data.

Physical-TV operation, audible audio, translation provider requests, caption style variants, quality changes, full offline link loss on-device, forced episode-list failures, large-title sidebar scrolling, and broad site/network compatibility remain unverified for this build. No APK release, APK updater, application push, or history rewrite was performed.

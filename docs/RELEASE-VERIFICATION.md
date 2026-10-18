# AniTV release verification

## Owner-signed 1.2.0 — 2026-10-18

The owner requested a new GitHub release and supplied a signed APK. Its initial packaged identity was still 1.1.0 / code 2, already published. Source was advanced to 1.2.0 / code 3, and the owner rebuilt with the existing key in Android Studio. The rebuilt artifact was staged and published unchanged after the owner renewed GitHub authentication. [AniTV 1.2.0](https://github.com/fiatsiz/AniTV-dot/releases/tag/v1.2.0) is public stable Latest, published at **2026-10-18 14:04:10 UTC**, with draft/prerelease disabled. Tag and source archive commit: `c07d85336ba0fbc8e88c9098e2cce9980fe7131a`. Application source and tag were pushed; the independently versioned rules branch remains unchanged.

| Item | Verified value |
| --- | --- |
| Published release asset | `AniTV-1.2.0.apk` |
| Owner-built APK | `app/build/outputs/apk/release/app-release.apk` |
| Size | 26,835,296 bytes |
| APK SHA-256 | `cc2f4b2619e59d156c59fa083dd613597b5169358fc1d02cad00af051593495a` |
| Owner certificate SHA-256 | `9a65e9f457a546bd65d51146f1e4ab611f44c57be68e089c000d4431fb805854` |
| Signature | APK v1/v2, RSA-3072, CN `fiatsiz`; same certificate as 1.0.0/1.1.0 |
| Package/version | `indie.fiatsiz.anitv`, versionName `1.2.0`, versionCode `3` |
| Minimum / target / compile SDK | 23 / 34 / 36 |
| Debuggable / testOnly | Neither enabled |
| Embedded Git base | `a0718b34ca07da01f03ba38fe032a32642461a4e` |
| Bundled rules / public key | Match tracked source byte for byte |
| Debug fixture | No `UpdateFixtureActivity` in manifest or DEX |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |

The corresponding source includes the version bump to 1.2.0 / code 3, emulator fixture maintenance, and release documentation after the embedded base. No application behavior changed during preparation. SDK `aapt` inspected packaged identity/SDK/manifest; `apksigner` verified signatures and the permanent certificate. Rebuild instructions are in RELEASE.md; no signing password or private key is part of the source assets.

`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m` passed: 85 tests passed, one existing publication-only test skipped; release lint reported zero errors, 17 warnings, five hints. The debug APK contains 1.2.0 / code 3, SDKs 23/34/36, SHA-256 `bfc49e4f2ff0219e1eb39b70b8978aa2883e6c554e4fde6cd0713836cb934e4c`.

No adb devices were connected. Signed-build installation, D-pad navigation, video, audible audio, subtitles, seek, Back, full updater installation, physical-TV behavior, and live source availability were not retested. Earlier feature debug/emulator checks do not establish these signed-build results. No physical device was controlled. GitHub upload size/digest checks and the public latest-release contract passed. Signed-build runtime limits remain unchanged.

### 1.2.0 publication verification

All five uploaded assets have the exact local byte counts and GitHub `sha256:` digests. The source archive, LICENSE, RELEASE-INFO.txt, and SHA256SUMS were downloaded and matched staging byte for byte. The unauthenticated latest-release endpoint returned stable `v1.2.0`, the exact uploaded APK name, 26,835,296-byte size, browser URL, and expected full-file SHA-256 digest. An unauthenticated public APK range request returned HTTP 206 and its first 1,024 bytes matched the supplied owner APK. Full APK re-download was stopped because of slow transfer rates; its full downloaded checksum was not independently recomputed. The full uploaded APK digest was verified through GitHub metadata against the local owner artifact.

Tag `v1.2.0` stays at source archive commit `c07d85336ba0fbc8e88c9098e2cce9980fe7131a`. Later publication-record commits change documentation only. The rules branch remains `e97e669715f09b71e1713ec464b010083d226279`. Release assets and the owner-supplied APK were not replaced after publication. No additional app build or device test was needed for this documentation-only record.

## Owner-signed 1.1.0 — 2026-10-14

The owner supplied the signed APK and requested code/release publication. The APK was copied unchanged into release staging; no replacement signing key or rebuild was used. Published as [public Latest](https://github.com/fiatsiz/AniTV-dot/releases/tag/v1.1.0) at 2026-10-14 18:15:35 UTC, with draft/prerelease disabled. Tag `v1.1.0` points to `36c28f84ae57e61678e43730404a4b17ef6b52c2`; its changes after the source archive commit are verification documentation only.

| Item | Verified value |
| --- | --- |
| Release asset | `AniTV-1.1.0.apk` |
| Owner-built APK | `app/build/outputs/apk/release/app-release.apk` |
| Size | 27,369,152 bytes |
| APK SHA-256 | `e606e4a02f0be45c7b93eb7de144e56fac7d7deac76a9e0b07665a6ddc23b613` |
| Owner certificate SHA-256 | `9a65e9f457a546bd65d51146f1e4ab611f44c57be68e089c000d4431fb805854` |
| Signature | APK v2, RSA-3072, CN `fiatsiz`; same certificate as 1.0.0 |
| Package/version | `indie.fiatsiz.anitv`, versionName `1.1.0`, versionCode `2` |
| Minimum / target / compile SDK | 34 / 37 / 37 |
| Debuggable / testOnly | Neither enabled |
| Embedded Git base | `d5b916fd8e80f05a98828cbf71a1d985bfd80bec` |
| Formatted source archive commit | `55a50bccab909ca5b0ada1a857a383ed24d857df` |
| Rules / updater provider | Bundled rule/public-key bytes match source; FileProvider exposes only `cache/update/` |
| Debug fixture | No `UpdateFixtureActivity` in APK manifest or DEX |

All 26 owner-pending source/manifest/test changes were formatting only. A token comparison against the embedded Git base preserved string/comment contents and every non-whitespace token. The source archive captures the formatted worktree; its commit differs from embedded provenance for this documented reason. No private keystore/credentials or synthetic APK fixtures are included in the tracked archive.

`./gradlew :app:testDebugUnitTest :app:lintRelease --offline` passed again: 67 tests passed, one publication-only test skipped, release lint had no errors. The earlier debug feature verification included five Android tests, synthetic higher-code updater installation with byte-identical library/caption settings, progress/Cancel/background/configuration behavior, permission/installer cancellation, languages/large text, and live SUB playback/subtitles/seek/resume. These are earlier debug results, not repeated claims for every signed-build flow.

Only fresh `AniTV_Release_1_1_0` / `emulator-5554` was controlled (`ro.kernel.qemu=1`, Android API 34). The published 1.0.0 APK first passed its recorded checksum and installed; live `takopi` search, detail/episodes, and saving a Favorite worked. The supplied 1.1.0 then installed with `adb install -r` and the existing Favorite/artwork remained visible. Home/Settings showed version 1.1.0, source rules revision 1/current, and a successful GitHub check that did not offer older 1.0.0. No uninstall or app-data clear occurred. No physical TV was controlled.

The signed-build smoke test did not repeat video, audible sound (`-no-audio`), subtitles/seek, DeepL/Google requests, quality, large text, or installer failure cases. Physical-TV 1.1.0 behavior remains unverified. The first updater-enabled owner-signed APK requires manual installation over 1.0.0; future newer stable releases can use the updater. See [RELEASE.md](RELEASE.md).

### 1.1.0 publication verification

All five assets (APK, source archive, LICENSE, RELEASE-INFO.txt, SHA256SUMS) were downloaded through the release API and matched staging byte-for-byte. GitHub asset `sha256:` digests matched each local file, and downloaded SHA256SUMS passed. The unauthenticated latest-release endpoint returned published stable `v1.1.0` and the exact uploaded APK name, byte count, browser URL, and SHA-256 digest. An unauthenticated public APK download returned HTTP 200 and matched the supplied owner APK. The installed signed 1.1.0 emulator app's manual check then displayed latest 1.1.0 / up to date.

Application changes, owner formatting, and verification documentation were pushed to `origin/master` with the immutable `v1.1.0` tag. The rules branch remains `e97e669715f09b71e1713ec464b010083d226279`. Subsequent publication-record documentation does not change the tag, APK, or source assets.

## Earlier 1.0.0 release verification

## Signed release artifact

Published at 2026-10-13 19:21:52 UTC as the public Latest release, with draft/prerelease disabled. The five uploaded assets were downloaded and verified byte for byte against staging and SHA256SUMS. Unauthenticated GitHub release access returned HTTP 200. Tag `v1.0.0` points to `cfe9659da44603ea900d631693acd1c1badcccab`; post-publication documentation does not move the tag or change the APK.

Verified on 2026-10-13 after the owner supplied a signed APK. Release: [AniTV 1.0.0](https://github.com/fiatsiz/AniTV-dot/releases/tag/v1.0.0). The APK's embedded Git provenance identifies `bf044b8c471c58c41df279cdf757a8d77f72a35e`; the release tag adds documentation only, with no application/build-configuration changes. The source asset is archived from that exact embedded commit.

| Item | Verified value |
| --- | --- |
| Release asset | `AniTV-1.0.0.apk` |
| Local owner-built APK | `app/build/outputs/apk/release/app-release.apk` |
| APK size | 27,261,480 bytes |
| APK SHA-256 | `3c629c2e932b723e94a1de492ffbf06da30f00216b443bcb6f071c6345309969` |
| Owner certificate SHA-256 | `9a65e9f457a546bd65d51146f1e4ab611f44c57be68e089c000d4431fb805854` |
| Signing key | RSA-3072, certificate CN `fiatsiz`; different from the debug certificate |
| Package/version | `indie.fiatsiz.anitv`, versionName `1.0.0`, versionCode `1` |
| Minimum / target / compile SDK | 34 / 37 / 37 |
| Debuggable / testOnly | Neither enabled in the packaged manifest |
| Embedded source commit | `bf044b8c471c58c41df279cdf757a8d77f72a35e` |
| Rules | Initial signed revision 1 and public DER key match tracked source byte for byte |

SDK `apksigner` verified the v2 signature. SDK `aapt` verified version/package/SDKs and the compiled non-debuggable manifest. No private keystore, credentials, or private rules key are included in the APK or source assets. The user requested a rebuild check; the release APK SHA-256 and certificate remained unchanged.

The shared-source unit suite and release lint passed again: 51 unit tests passed, one publication-only test skipped, and lint had no errors. The owner-built APK was copied unchanged into release staging; no substitute signing key or rebuild was used by the agent. A fresh, separate Android TV API 34 emulator (`AniTV_Release_1_0_0`, `emulator-5556`, `ro.kernel.qemu=1`) installed the signed APK, rendered Home/Settings with version 1.0.0 and current rules revision 1, and successfully searched `takopi`, loaded details/episodes, offered SUB/DUB, and rendered SUB full-screen video with original subtitles. The timer advanced, forward seeking worked, and Android reported an active audio track; audible output was not checked (`-no-audio`). Back returned to episode details. Existing `Television_1080p` app data was not touched or uninstalled.

Release assets include the signed APK, matching source archive, GPL-3.0 license, certificate/provenance text, and SHA256SUMS. The existing rules branch remains independently versioned. No APK updater was added. Subsequent owner-reported physical-TV verification is recorded below; the earlier complete debug emulator flow remains separately identified.

## Owner-reported physical-TV verification — 2026-10-14

After receiving manual installation guidance for the signed 1.0.0 release, the owner reported that all checks they could perform from the supplied TV checklist worked on their Beko Google TV (Android API 34). The only identified skipped checks were timestamp-based cache refresh after 24 hours and Google Cloud Translation API requests. The owner explicitly confirmed that normal usage with DeepL translation and caption styling works well.

The supplied checklist covered remote navigation and visible focus, empty libraries, search/details, SUB/DUB playback and audible audio, subtitles, seeking, Back, physical Home/return, episode sidebar, library persistence/resume, artwork, app languages, caption styles, and quality where available. Additional checks covered translation, network loss/retry, episode-list recovery, and available source variants. Results are recorded as the owner's overall manual report, not an independently observed per-case log or a claim of exhaustive title/network/failure coverage.

The earlier agent adb installation attempt failed first on the debug/release certificate mismatch, then stalled after the owner approved uninstalling debug data. The agent's release install did not run. The subsequent manual test report supersedes that installation blocker; no further device action was taken by the agent. APK fingerprints, application code, architecture, and prior automated/emulator results are unchanged. No rebuild or test rerun was needed for this documentation-only update.

## Earlier preparation record

The following record describes the initial debug verification before the owner created the release APK. Its signing blocker is now resolved by the supplied artifact.

## Debug preparation provenance and artifacts

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

## Initial signing gate (resolved)

At the initial preparation stage, the owner still needed to supply the permanent APK keystore and `ANITV_RELEASE_KEYSTORE`, `ANITV_RELEASE_STORE_PASSWORD`, `ANITV_RELEASE_KEY_ALIAS`, and `ANITV_RELEASE_KEY_PASSWORD` to the Gradle process. Follow `docs/RELEASE.md`, rerun signed release assembly, verify package/version/certificate/non-debuggable status, and record the release checksum/fingerprint. Do not uninstall the existing debug install automatically: certificate mismatch can prevent in-place updates, and uninstalling can erase saved data.

At the initial preparation stage, physical-TV operation, audible audio, translation provider requests, caption style variants, quality changes, full offline link loss on-device, forced episode-list failures, large-title sidebar scrolling, and broad site/network compatibility had not been verified. See the 2026-10-14 owner report above for subsequent manual testing and its two identified skipped checks. During initial preparation, no APK release, APK updater, application push, or history rewrite was performed. The owner subsequently authorized GitHub 1.0.0 publication; no APK updater or history rewrite is part of that publication.

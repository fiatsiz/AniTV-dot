# Local APK releases

The next release target is AniTV **1.1.0 / versionCode 2**, application ID `indie.fiatsiz.anitv`, Android 14/API 34 minimum. It adds a stable GitHub release updater. The published [1.0.0 release](https://github.com/fiatsiz/AniTV-dot/releases/tag/v1.0.0) has no APK updater; install the first updater-enabled, owner-signed APK manually. No 1.1.0 tag or release has been published.

## Versioning

Follow [Semantic Versioning](https://semver.org/): PATCH for compatible fixes, MINOR for compatible features, and MAJOR for incompatible changes to documented app behavior or persisted data contracts. Increment Android `versionCode` independently for every future APK release. Rule revisions have a separate increasing integer.

## Owner APK signing key

Supply the permanent owner key; the build never substitutes a generated or debug key. If no key exists, the owner should create and back up a permanent key with Android Studio's Generate Signed APK workflow. Keep the keystore outside all Git worktrees.

Export these variables in the shell that runs Gradle (a previously launched Codex process does not inherit later shell exports):

```bash
export ANITV_RELEASE_KEYSTORE="/absolute/private/path/anitv-release.jks"
export ANITV_RELEASE_KEY_ALIAS="your-key-alias"
read -rsp "Keystore password: " ANITV_RELEASE_STORE_PASSWORD; echo
export ANITV_RELEASE_STORE_PASSWORD
read -rsp "Key password: " ANITV_RELEASE_KEY_PASSWORD; echo
export ANITV_RELEASE_KEY_PASSWORD
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Never put passwords in tracked files, shell arguments, chat, or logs. Restrict private-file access to the owner. Back up the keystore, alias, passwords, and signing-certificate fingerprint securely in separate offline locations. Losing the permanent key can prevent future updates. Follow [Android signing guidance](https://developer.android.com/studio/publish/app-signing).

Missing credentials fail release packaging clearly while debug builds, tests, and lint remain available. Release optimization remains disabled as in the baseline. Output: `app/build/outputs/apk/release/app-release.apk`.

## Installation and existing data

A release signed with a different certificate normally cannot update an installed debug build in place. Never automatically uninstall, clear app data, or replace a user's signing key. Uninstalling can erase Favorites, progress, metadata, and translation settings. Keep the existing install until the owner has chosen a data-preserving transition or accepted manual data loss. There is currently no supported library export/import feature.

Before distribution, verify with SDK `apksigner verify --verbose --print-certs`, `aapt dump badging`, and `apkanalyzer manifest debuggable`; record SHA-256, certificate SHA-256, source commit, and tested/untested device flows. Ship the matching GPL-3.0 source and license with any redistributed APK. The owner subsequently authorized GitHub 1.0.0 publication. Future publication still requires an explicit release request; rule publication remains a separate workflow.

## Signed source rules

The app automatically checks the public [rules branch](https://github.com/fiatsiz/AniTV-dot/tree/rules) once per process startup. It renders Home immediately and waits at most three seconds total before source operations use a frozen validated snapshot. This signed-rules check has no manual action; the separate APK updater described below has its own timing, storage, and controls. Settings shows version/revision/outcome in English or Turkish.

Rules schema 1 supports current HTML and JSON-wrapped HTML, named object-property paths, selectors/attributes, three relative endpoint templates, server/audio names, and the current Base64/XOR decoding switches/key. HiAnime origin, HTTPS, numeric ID validation, response limits, subtitle requirements/selection, and VTT remain compiled. New domains, providers, formats, or decoding algorithms can require a new APK. Correctly signed rules can still be wrong for the live site; fixture tests and live response inspection remain required.

The independently generated RSA-3072 rules key is external, owner-only PKCS#8 DER. On this workstation it is `/home/badico/.local/share/anitv-signing/rules-private.pk8`. Back it up separately from the APK keystore using encrypted offline storage. Never commit it or copy it into either worktree. Losing it requires a new APK to trust a replacement public key.

Create the separate worktree from the existing branch on another workstation with `git worktree add ../AniTV-rules origin/rules -b rules` (if the local branch already exists, use it). Read the rules branch README, pull with `--ff-only`, inspect live responses, edit `hianime.json`, increment revision, and update sanitized fixtures plus expected models. Then run:

```bash
ANITV_RULES_KEY=/absolute/private/path/rules-private.pk8 tools/publish-rules /absolute/path/to/AniTV-rules
```

This command validates configuration and runs the actual Kotlin parser against fixtures before signing exact payload bytes. It verifies the envelope against the bundled public key, rejects revision rollback/conflicts, and atomically writes the envelope. Review/commit the rules worktree, then push only `git push origin rules`. Do not push application changes as part of a rules-only update. Initial publication additionally uses `ANITV_RULES_INITIAL=1` to enforce identical bundled/published payload/signature and envelope bytes.

Rollback restores previous known-good rule content with a revision higher than all published revisions, followed by the same validation/signing/publication. Never rewrite history or decrease revision. Private cache stores accepted content and its ETag atomically and retains a validated previous copy. Bad signatures, malformed/incompatible data, unavailable GitHub, rate limits, and late downloads retain local rules. Key rotation has no remote mechanism; ship a new APK if trust must change.

Production endpoint: `https://api.github.com/repos/fiatsiz/AniTV-dot/contents/hianime.signed.json?ref=rules`. Requests use `Accept: application/vnd.github.raw+json`, a descriptive User-Agent, HTTPS, a 256 KiB envelope limit, and conditional ETags. No token is embedded. See [GitHub Contents API](https://docs.github.com/en/rest/repos/contents#get-repository-content).

## Verification record and Android smoke test

See [RELEASE-VERIFICATION.md](RELEASE-VERIFICATION.md) for matching source/rules commits, debug artifact metadata, verified emulator flows, and owner-certificate verification.

To repeat the focused Android parser proof, build `:app:assembleDebugAndroidTest`. On an emulator only, verify `ro.kernel.qemu=1`, install the debug application and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` with `adb -s <emulator-serial> install -r`, then run:

```bash
adb -s <emulator-serial> shell am instrument -w -r \
  -e class indie.fiatsiz.anitv.source.SignedRulesInstrumentedTest \
  indie.fiatsiz.anitv.test/androidx.test.runner.AndroidJUnitRunner
```

This test uses the installed application parser with synthetic keys/transport and a separate scratch cache. It does not publish test rules, replace production trust, or clear library data. Do not run device-control automation on a physical TV; leave those checks to the owner. No automatic uninstall is part of this manual test workflow.

## Stable APK release contract

The updater requests unauthenticated `GET https://api.github.com/repos/fiatsiz/AniTV-dot/releases/latest`. Publish a full, non-draft, non-prerelease release tagged exactly `vMAJOR.MINOR.PATCH` (numeric components without leading zeroes). Attach exactly one uploaded `AniTV-MAJOR.MINOR.PATCH.apk` with positive size no greater than 100 MiB and GitHub asset `digest` equal to `sha256:<64 hex digits>`. Missing/unsupported assets or digests produce an error rather than an installation offer. The current `v1.0.0` asset meets this contract.

The APK must have package `indie.fiatsiz.anitv`, matching versionName, a strictly increasing versionCode, compatible minimum SDK, and the same current signing certificate(s) as the installed application. Keep the permanent owner key. The verified 1.0.0 owner certificate SHA-256 is `9a65e9f457a546bd65d51146f1e4ab611f44c57be68e089c000d4431fb805854`. Debug APKs cannot update that owner-signed installation. Never uninstall automatically to resolve a certificate mismatch. Ship matching GPL-3.0 source, license, provenance, and checksums with each distributed APK.

Automatic checks run only at launch/resume when 24 hours have elapsed since the last attempt, including failures. The first check runs immediately; clock rollback makes a check due. Settings offers manual checks with a persisted 60-second debounce. ETags reuse cached responses; failures retain previous information with an error. Server retry deadlines override both check modes. GitHub unauthenticated limits are shared per public IP; see [GitHub rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api) and [release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).

Home shows a focusable notice without moving focus. Settings shows installed/latest versions, last successful check, status, expandable plain-text notes, and Update/Retry/Cancel/Install controls. Downloads start only after selection, survive Activity recreation, and cancel with partial-file cleanup when AniTV enters the background. SHA-256, size, package, version, SDK, and certificate checks precede readiness and run again before installation. Verified APKs remain cached for retry until replacement, installation, or cache eviction.

If needed, Install opens AniTV’s Android unknown-app installation settings. Returning rechecks permission and requires another explicit Install selection. A restricted FileProvider shares only `cache/update` through a temporary read grant. Android then asks for installation confirmation. Back/cancellation retains the verified APK and never implies success. On return/startup, the installed version is read again; obsolete downloads are removed. See [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider) and [installation permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()). There is no scheduler, background check while closed, automatic download, silent installation, prerelease channel, or automatic uninstall.

### Owner’s physical-TV updater checklist

Do these manually on the Beko TV; agent automation must control only an emulator.

1. Manually install owner-signed 1.1.0 over owner-signed 1.0.0. Confirm Favorites, completion, progress, translation settings, and caption style remain intact.
2. Open Settings. Confirm installed/latest versions, successful check time, and useful errors when offline. Repeated manual checks must show the debounce; rate-limit deadlines must remain effective after restarting.
3. When a real newer stable release exists, navigate to the Home notice using D-pad and Select. Confirm focus does not move when the notice appears. Check English/Turkish, large text, notes scrolling, and Back.
4. Select Update. Check visible progress and Cancel. Start again, press physical Home, then return: the unfinished transfer must be canceled. Repeat with an Activity/language recreation: the active transfer must continue.
5. Complete download. Check permission denial and permission grant followed by a separate Install selection. Cancel Android’s installer, return, and retry without downloading again.
6. Confirm Android’s update prompt, install, reopen AniTV, and verify the new version plus unchanged library/settings. Never uninstall to bypass a signing error.
7. Regression-check live search, episodes, SUB/DUB video and audible audio, subtitles, seeking, Back, Home/resume, DeepL, and caption styling. Record skipped checks; no newer public release currently exists for end-to-end production updater testing.

### Local emulator fixture workflow

`UpdateFixtureActivity` and its injected local transport exist only in `src/debug`. Release builds contain neither that Activity nor its manifest entry. Use a fresh emulator and verify `adb -s <serial> shell getprop ro.kernel.qemu` prints `1` before any device control. Do not publish synthetic releases or use an owner key for synthetic APKs.

1. Build/install the ordinary debug APK (1.1.0 / code 2) on the emulator.
2. Create a detached temporary worktree from this milestone. In that worktree only, change versionName to `1.2.0` and versionCode to `3`, copy the machine’s ignored `local.properties`, and run `:app:assembleDebug --offline`. It uses the same development debug certificate; never change the owner release signing configuration.
3. Copy the candidate into the installed app’s private fixture directory. Generate a JSON response with `tag_name: "v1.2.0"`, `draft: false`, `prerelease: false`, plain `body`, and an `assets` array containing the exact name, `state: "uploaded"`, actual byte size, actual `sha256:` digest, and URL `https://github.com/fiatsiz/AniTV-dot/releases/download/v1.2.0/AniTV-1.2.0.apk`. This URL is fixture metadata only; the debug transport reads local bytes.

```bash
adb -s <serial> shell run-as indie.fiatsiz.anitv mkdir -p files/updater-fixture
adb -s <serial> exec-in run-as indie.fiatsiz.anitv sh -c 'cat > files/updater-fixture/candidate.apk' < /path/to/candidate.apk
adb -s <serial> exec-in run-as indie.fiatsiz.anitv sh -c 'cat > files/updater-fixture/release.json' < /path/to/release.json
adb -s <serial> shell am start -n indie.fiatsiz.anitv/.update.UpdateFixtureActivity
```

The harness uses a separate private metadata store and `cache/update/fixture` directory, with the real inspector, UI, permission flow, installer, and restricted FileProvider. Production updater cleanup cannot delete the fixture cache. It deliberately streams the local APK slowly enough to exercise progress, Cancel, and background cancellation. It preserves the existing library and translation stores. Restarting the process revalidates the ready file. Use normal `MainActivity` for live GitHub/source regression checks.

Build `:app:assembleDebugAndroidTest --offline`, install its APK, and run the following **before** installing the higher-version candidate:

```bash
adb -s <serial> shell am instrument -w -r \
  -e class indie.fiatsiz.anitv.update.UpdateInstrumentedTest,indie.fiatsiz.anitv.update.UpdateUiInstrumentedTest,indie.fiatsiz.anitv.source.SignedRulesInstrumentedTest \
  indie.fiatsiz.anitv.test/androidx.test.runner.AndroidJUnitRunner
```

APK tests require the local fixture above. They inspect its real Android package/signing identity, reject mismatches/invalid APKs, prove FileProvider scope, verify Home notice focus/Select, preserve download-button focus, and retain the existing signed-rules parser proof. UI checks use D-pad Select semantics rather than assuming TV controls accept touchscreen clicks. Compare private library preferences before and after the actual Android-confirmed update; do not clear app data.

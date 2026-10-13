# Local APK releases

The release target is AniTV 1.0.0, application ID `indie.fiatsiz.anitv`, versionCode 1, Android 14/API 34 minimum. No APK updater or GitHub APK release is included.

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

Before distribution, verify with SDK `apksigner verify --verbose --print-certs`, `aapt dump badging`, and `apkanalyzer manifest debuggable`; record SHA-256, certificate SHA-256, source commit, and tested/untested device flows. Ship the matching GPL-3.0 source and license with any redistributed APK. Do not publish application commits or an APK release as part of this preparation.

## Signed source rules

The app automatically checks the public [rules branch](https://github.com/fiatsiz/AniTV-dot/tree/rules) once per process startup. It renders Home immediately and waits at most three seconds total before source operations use a frozen validated snapshot. No manual button or APK update check/download/install exists. Settings shows version/revision/outcome in English or Turkish.

Rules schema 1 supports current HTML and JSON-wrapped HTML, named object-property paths, selectors/attributes, three relative endpoint templates, server/audio names, and the current Base64/XOR decoding switches/key. HiAnime origin, HTTPS, numeric ID validation, response limits, subtitle requirements/selection, and VTT remain compiled. New domains, providers, formats, or decoding algorithms can require a new APK. Correctly signed rules can still be wrong for the live site; fixture tests and live response inspection remain required.

The independently generated RSA-3072 rules key is external, owner-only PKCS#8 DER. On this workstation it is `/home/badico/.local/share/anitv-signing/rules-private.pk8`. Back it up separately from the APK keystore using encrypted offline storage. Never commit it or copy it into either worktree. Losing it requires a new APK to trust a replacement public key.

Create the separate worktree from the existing branch on another workstation with `git worktree add ../AniTV-rules origin/rules -b rules` (if the local branch already exists, use it). Read the rules branch README, pull with `--ff-only`, inspect live responses, edit `hianime.json`, increment revision, and update sanitized fixtures plus expected models. Then run:

```bash
ANITV_RULES_KEY=/absolute/private/path/rules-private.pk8 tools/publish-rules /absolute/path/to/AniTV-rules
```

This command validates configuration and runs the actual Kotlin parser against fixtures before signing exact payload bytes. It verifies the envelope against the bundled public key, rejects revision rollback/conflicts, and atomically writes the envelope. Review/commit the rules worktree, then push only `git push origin rules`. Application changes remain local. Initial publication additionally uses `ANITV_RULES_INITIAL=1` to enforce identical bundled/published payload/signature and envelope bytes.

Rollback restores previous known-good rule content with a revision higher than all published revisions, followed by the same validation/signing/publication. Never rewrite history or decrease revision. Private cache stores accepted content and its ETag atomically and retains a validated previous copy. Bad signatures, malformed/incompatible data, unavailable GitHub, rate limits, and late downloads retain local rules. Key rotation has no remote mechanism; ship a new APK if trust must change.

Production endpoint: `https://api.github.com/repos/fiatsiz/AniTV-dot/contents/hianime.signed.json?ref=rules`. Requests use `Accept: application/vnd.github.raw+json`, a descriptive User-Agent, HTTPS, a 256 KiB envelope limit, and conditional ETags. No token is embedded. See [GitHub Contents API](https://docs.github.com/en/rest/repos/contents#get-repository-content).

## Verification record and Android smoke test

See [RELEASE-VERIFICATION.md](RELEASE-VERIFICATION.md) for matching source/rules commits, debug artifact metadata, verified emulator flows, and the missing owner-signing gate.

To repeat the focused Android parser proof, build `:app:assembleDebugAndroidTest`. On an emulator only, verify `ro.kernel.qemu=1`, install the debug application and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` with `adb -s <emulator-serial> install -r`, then run:

```bash
adb -s <emulator-serial> shell am instrument -w -r \
  -e class indie.fiatsiz.anitv.source.SignedRulesInstrumentedTest \
  indie.fiatsiz.anitv.test/androidx.test.runner.AndroidJUnitRunner
```

This test uses the installed application parser with synthetic keys/transport and a separate scratch cache. It does not publish test rules, replace production trust, or clear library data. Do not run device-control automation on a physical TV; leave those checks to the owner. No automatic uninstall is part of this manual test workflow.

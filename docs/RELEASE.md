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

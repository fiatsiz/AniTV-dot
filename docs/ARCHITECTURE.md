# AniTV architecture

AniTV is one Kotlin Android `app` module. Compose for TV owns navigation and screens; `LibraryStore` owns local favorites and progress; `AnimeDetailCache` owns cached title metadata and poster files; `HiAnimeSource` owns site requests and parsing; Media3 owns playback.

```text
Home → Search/results or local library → detail + episodes → HLS + VTT player
        MainActivity                       HiAnimeSource       Media3
                         LibraryStore ← progress/favorite/completed
                         AnimeDetailCache ← description/facts/poster/timestamp
Home → Settings → Android LocaleManager → English or Turkish resources
```

## Files and responsibilities

| File | Responsibility |
| --- | --- |
| `app/src/main/java/indie/fiatsiz/anitv/MainActivity.kt` | Home, search, collections, Settings, detail and playback UI; transient state, focus, Back, errors, player lifecycle and remote controls. Home, episode, and Settings composables support sample `@Preview` functions. |
| `app/src/main/java/indie/fiatsiz/anitv/LocalizedSourceError.kt` | Maps source failure codes to localized Android string resources. |
| `app/src/main/java/indie/fiatsiz/anitv/LibraryStore.kt` | `SharedPreferences` JSON encoding, decoding, and library rules, including saved detail links. |
| `app/src/main/java/indie/fiatsiz/anitv/AnimeDetailCache.kt` | Timestamped detail metadata in private preferences and poster bytes in the app cache directory. |
| `app/src/main/java/indie/fiatsiz/anitv/source/HiAnimeSource.kt` | HTTPS requests, HiAnime selectors, detail/episode/server parsing, poster download, player metadata decoding, and language-neutral source failure codes. |
| `app/src/main/java/indie/fiatsiz/anitv/ui/theme/` | Existing TV theme. |
| `app/src/test/` | Library and cache rule tests, sanitized response fragments, and parser tests. |
| `gradle/libs.versions.toml` | Dependency versions and aliases. |
| `app/src/main/AndroidManifest.xml` | Leanback launcher, Internet permission, and Android per-app locale configuration. |
| `app/src/main/res/values*/strings.xml`, `res/xml/locales_config.xml` | English defaults, Turkish translations, and advertised app languages. |
| `app/src/main/res/drawable-nodpi/` | Supplied AniTV launcher logo and a 320×180 Android TV banner crop. |

## Flow

1. `MainActivity` sends a query to `HiAnimeSource.search()` on `Dispatchers.IO`. The source reads `https://hianime.at/search?keyword=...` and parses `.film_list-wrap .flw-item` cards into `Anime` records, including each result's detail link.
2. Selecting a result or saved title loads episodes and details independently. `episodes()` reads `/api/theme/episode/list/{animeId}` and extracts `a.ep-item[data-id]`. `details()` reads the title page, parses `#ani_detail` for description and facts plus `.anisc-poster img[src]` for the portrait poster URL, and falls back to a search by title for older saved entries without a detail link.
3. `AnimeDetailCache` returns stored details and image bytes immediately. Entries under 24 hours old skip a network request. Older entries stay visible while `HiAnimeSource` fetches new metadata and poster bytes; a missing cached image also triggers refresh. The changed cache metadata key invalidates earlier wide-cover entries, so they refresh on first open. Android may evict files from the cache directory, which results in a new fetch.
4. Selecting an episode calls `stream()`. The source reads `/api/theme/episode/servers?episodeId=...`, selects the `sub` ZokoAnime server, decodes its embed link, and reads the embed page. `parsePlayer()` decodes `window.__P` into an HTTPS HLS URL and VTT subtitle URL.
5. `Playback` builds a Media3 `MediaItem` with an external VTT track. `DefaultHttpDataSource` sends the embed origin as `Referer` for stream and subtitle requests. `PlayerView` renders video; Compose buttons handle the remote. Media3 track groups populate the optional quality selector. Controls hide on D-pad Down or after six seconds of play, and show on D-pad Up or Select. `DisposableEffect` saves progress and releases `ExoPlayer` when playback leaves composition.
6. `LibraryStore` saves favorites, completion, and one last episode/position per title as JSON in private `SharedPreferences`. Playback saves progress every five seconds and on exit. The completed archive changes only through the detail-screen button. Currently watching filters saved progress to non-completed titles.
7. Settings reads and sets Android `LocaleManager.applicationLocales`. An empty locale list follows the system; explicit `en` or `tr` choices persist in Android and cause a configuration change. Android string resources localize app controls and source errors. `HiAnimeSource` emits typed failure codes without depending on Android resources.

The UI uses `remember` state for query, results, selection, loading, and errors. The page uses `rememberSaveable` so Settings remains open after a language change. A request counter ignores late responses after another selection or Back. Search query and results are not persisted across process death; library and detail cache state are.

## Boundaries and failure handling

- Source calls use `HttpURLConnection` with 15-second connect and read timeouts, HTTPS validation, `User-Agent`, and `Referer`. Responses are limited to 8 MB. Jsoup parses HTML; `org.json` parses response bodies.
- The source reports blocked requests, non-success HTTP responses, missing markup, missing subtitle servers, malformed player data, insecure links, and oversized responses. Player errors show a retry instruction. Full stream URLs, cookies, and tokens are not logged.
- There is no official or stable HiAnime API contract. Update selectors and endpoints only after inspecting current responses, then update saved fixtures and tests.
- `ani-cli` was used only as a behavior reference. No GPLv3 code was copied.

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` after source or UI changes. Test D-pad focus, Select, Back, video, audio, seek, subtitles, detail caching, and quality selection on an Android TV before claiming full device support for this build.

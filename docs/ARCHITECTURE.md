# AniTV architecture

AniTV is one Kotlin Android `app` module. Compose for TV owns navigation and library screens; `LibraryStore` owns local state; `HiAnimeSource` owns site requests and parsing; Media3 owns playback.

```text
Home → Search/results or local library → episode list → HLS + VTT player
        MainActivity                         HiAnimeSource  Media3
                         LibraryStore ← progress/favorite/completed
```

## Files and responsibilities

| File | Responsibility |
| --- | --- |
| `app/src/main/java/indie/fiatsiz/anitv/MainActivity.kt` | Home, search, collections, detail and playback UI; transient state, focus, Back, errors, player lifecycle and remote controls. |
| `app/src/main/java/indie/fiatsiz/anitv/LibraryStore.kt` | `SharedPreferences` JSON encoding, decoding, and library rules. |
| `app/src/main/java/indie/fiatsiz/anitv/source/HiAnimeSource.kt` | HTTPS requests, HiAnime selectors, episode/server parsing, player metadata decoding, and source errors. |
| `app/src/main/java/indie/fiatsiz/anitv/ui/theme/` | Existing TV theme. |
| `app/src/test/` | Library rule tests, sanitized response fragments, and parser tests. |
| `gradle/libs.versions.toml` | Dependency versions and aliases. |
| `app/src/main/AndroidManifest.xml` | Leanback launcher and Internet permission. |

## Flow

1. `MainActivity` sends a query to `HiAnimeSource.search()` on `Dispatchers.IO`. The source reads `https://hianime.at/search?keyword=...` and parses `.film_list-wrap .flw-item` cards into `Anime` records.
2. Selecting a result or saved title calls `episodes()`. The source reads `/api/theme/episode/list/{animeId}`, parses its JSON `html`, and extracts `a.ep-item[data-id]` into `Episode` records.
3. Selecting an episode calls `stream()`. The source reads `/api/theme/episode/servers?episodeId=...`, selects the `sub` ZokoAnime server, decodes its embed link, and reads the embed page. `parsePlayer()` decodes `window.__P` into an HTTPS HLS URL and VTT subtitle URL.
4. `Playback` builds a Media3 `MediaItem` with an external VTT track. `DefaultHttpDataSource` sends the embed origin as `Referer` for stream and subtitle requests. `PlayerView` renders video; Compose buttons handle the remote. `DisposableEffect` saves progress and releases `ExoPlayer` when playback leaves composition.
5. `LibraryStore` saves favorites, completion, and one last episode/position per title as JSON in private `SharedPreferences`. Playback saves progress every five seconds and on exit. The completed archive changes only through the detail-screen button. Currently watching filters saved progress to non-completed titles.

The UI uses `remember` state for query, results, selection, loading, and errors. A request counter ignores late responses after another selection or Back. Search and navigation state are not persisted across process death; library state is.

## Boundaries and failure handling

- Source calls use `HttpURLConnection` with 15-second connect and read timeouts, HTTPS validation, `User-Agent`, and `Referer`. Jsoup parses HTML; `org.json` parses response bodies.
- The source reports blocked requests, non-success HTTP responses, missing markup, missing subtitle servers, malformed player data, and insecure links. Player errors show a retry instruction. Full stream URLs, cookies, and tokens are not logged.
- There is no official or stable HiAnime API contract. Update selectors and endpoints only after inspecting current responses, then update saved fixtures and tests.
- `ani-cli` was used only as a behavior reference. No GPLv3 code was copied.

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` after source or UI changes. Test D-pad focus, Select, Back, video, audio, seek, and subtitles on an Android TV before claiming full device support.

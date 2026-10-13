# Signed HiAnime rules for AniTV

This branch publishes declarative parsing data, not application binaries or executable code. Schema 1, initial revision 1, minimum app versionCode 1. HiAnime origin is fixed in the APK. Unsupported formats, new domains, providers, or algorithms may require an APK update.

`hianime.json` is editable UTF-8. `hianime.signed.json` contains Base64 payload bytes and the RSA-3072 / SHA256withRSA signature over exactly those bytes. Never reserialize the payload after signing. Only the public key ships in the application. No credentials, real stream links, cookies, or tokens belong in this branch. Fixtures use public metadata and example playback domains.

Initial matching application parser commit: `0f8ed6d48d65b5d126f9b227942273016ddb7420` (local application history; application pushes are outside this publication).

## Edit, validate, sign, publish

1. Use an application checkout containing the matching Kotlin parser and `tools/publish-rules`. Keep this separate `rules` worktree up to date with `git pull --ff-only origin rules` before editing. Never overwrite another publisher's revision.
2. Inspect current HiAnime responses. Change only supported configuration fields. Increment `revision` for every changed payload, including whitespace changes. Keep schema 1 and set `minAppVersionCode` to the oldest compatible APK. The 1.0.0 publisher validates against app versionCode 1.
3. Update sanitized `fixtures/` and `fixtures/expected.json` to match. Episode/server fixtures include the full JSON wrapper when configured; search/detail may be HTML or JSON. Include both subtitled and dubbed player cases. Never commit expiring playback URLs or user data.
4. From the application checkout, run this single local command:

   ```bash
   ANITV_RULES_KEY=/absolute/private/path/rules-private.pk8 tools/publish-rules /absolute/path/to/rules-worktree
   ```

   The command runs the actual Kotlin parser against every expected model, checks the schema and endpoint safety, signs the exact editable file bytes, verifies with the APK's public key, rejects revision rollback/conflicts against the existing envelope, and atomically writes `hianime.signed.json`. It does not push. The private key is unencrypted PKCS#8 DER, owner-only permissions, outside both worktrees. Use encrypted offline backups and restricted local storage. No APK signing key is used here.
5. Review `git diff --check` and `git diff`, then commit only rules, fixtures, and instructions with a Conventional Commit. Run the command again after any payload or fixture edit. Push only `git push origin rules`. Verify the production endpoint below and its signature before announcing publication. Do not publish deliberately broken rules for testing.

Initial publication additionally sets `ANITV_RULES_INITIAL=1` on that command; it proves bundled and published payload/signature match and preserves the bundled envelope byte for byte.

## Rollback and recovery

Restore previous known-good parsing content, increase `revision` above every published revision, rerun fixture validation/signing, then commit and push. Never decrease revision or rewrite branch history. Unchanged revision must have identical payload bytes. Offline, rejected, incompatible, or late updates retain validated cached/bundled rules. A process selects rules once, with a three-second startup deadline; restart the process to check again.

Back up the rules private key separately from the APK keystore. The original private key must match the bundled public key for every future rule update. If lost or compromised, replace the trusted public key through a new APK; there is no downloaded key-rotation mechanism.

## Download

`https://api.github.com/repos/fiatsiz/AniTV-dot/contents/hianime.signed.json?ref=rules`

Use `Accept: application/vnd.github.raw+json`, a descriptive User-Agent, and `If-None-Match` from an accepted response. No token is required for this public repository. The envelope limit is 256 KiB. GitHub availability, rate limits, and site access can still fail independently.

## Schema 1

- `search`: one `{query}` relative endpoint; response format, container/items selectors, ID/title/link fields. A field has a selector and `attribute`; empty attribute extracts text.
- `detail`: response format, root, description/poster fields, fact-item/name selectors, and allowed fact labels.
- `episodes`: one `{animeId}` endpoint, response, item selector, ID/number/title attributes.
- `servers`: one `{episodeId}` endpoint, response, item selector, audio/name/embed attributes, exact SUB/DUB/name values, and Base64 embed flag.
- `player`: JavaScript payload variable name, Base64/XOR flags and public obfuscation key; stream, subtitles-array, subtitle-URL, and default-track JSON paths.
- Each `response` has `format` (`html` or `json`), `htmlPath`, and `statusPath`. Paths are arrays of named object properties. HTML requires empty paths; JSON requires an HTML path and can omit status checking with an empty status path. A configured status must be boolean true. Array traversal, subtitle selection, numeric ID checks, HTTPS, size limits, SUB caption requirement, and VTT parsing remain compiled Kotlin.

Content rights belong to their respective owners. Rules support an unofficial, changing site and carry no guarantee of continued access. Application source is GPL-3.0; see the application checkout's LICENSE.

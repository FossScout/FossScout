# S07 — F12 Collections and Sharing

| Field | Value |
| --- | --- |
| Status | **Done** (S07 scope) |
| Features | F12 |
| Depends on | S06 |
| Can run in parallel with | S05 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F12-collections-sharing.md`](../specs/features/F12-collections-sharing.md) |

## Goal

Let users organise favourites into collections and share/import them offline,
without accounts, a server, or silent data changes.

## Scope

### Collections

- Add a schema version to `AppCollection` and decide/record that items reference
  canonical `AppKey`s, not separate favourite IDs.
- Implement a collection repository and persistence:
  - create, rename, edit description, reorder items, delete;
  - ordered items survive restart;
  - deleting a collection does not delete favourites.
- Add collection UI and navigation under favourites.

### Sharing and import

- Define a versioned `ShareEnvelope` with:
  - schema version;
  - type (single favourite, collection, all favourites);
  - display name/description;
  - app keys, display names, preferred stores, tags;
  - notes only when explicitly included.
- Implement v1 formats:
  - JSON for machine-readable exchange;
  - plain text that is readable without FossScout;
  - file export/import plus Android share sheet.
- Import flow:
  - parse and show a preview before changing local data;
  - merge, replace, or skip conflicts;
  - reject unsupported future schema versions with a clear error;
  - never auto-add stores or install anything from imported links.
- Notes are excluded by default; require an explicit opt-in.
- Reuse the collection schema in the full backup envelope (S09) where practical.

## Out of scope

- Full-profile backup, custom stores, settings, or encryption (S09).
- QR/deep-link sharing (optional SHOULD; can be a follow-up).
- Server sync, accounts, or automatic upload.
- Store descriptor import unless it is cheap and explicitly previewed.

## Key files

- `core/model/src/main/java/com/fossscout/core/model/Collection.kt`
- new collection repository/DataStore codec in `core/data`
- `feature/favourites` or new `feature/sharing` module
- `app/src/main/AndroidManifest.xml` for share/file intents
- `res/xml/file_paths.xml` if a FileProvider is needed
- app navigation/composition root

## Acceptance criteria

- **F12-R1:** sharing works offline and requires no account or service.
- **F12-R2:** notes are excluded by default and included only with consent.
- **F12-R3:** import shows a preview before modifying local data.
- **F12-R4:** merge, replace, and skip are supported and tested.
- **F12-R5:** imported stores/links are untrusted until confirmed; no automatic
  store add or install.
- **F12-R6:** versioned schema rejects unsupported future versions gracefully.
- **F12-R7 (SHOULD):** plain text is readable without the app.
- **F12-R8 (SHOULD):** oversized QR/deep-link payload falls back to a file.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Add codec round-trip tests, default-note-exclusion tests, preview/no-mutation
tests, all three merge modes, and an unsupported-version test. Verify the
FileProvider/export MIME types on an emulator if possible.

## Risks and decisions

- Do not invent a QR dependency unless the SHOULD is implemented. JSON + text +
  file satisfy the v1 MUSTs.
- Define conflict identity clearly: canonical `AppKey` plus collection ID.
- Keep imported data inert until confirmation.
- Coordinate the schema with S09 so backup is not a second incompatible format.

## Completion record

- **Implementation commit:** `7d630e2` — `feat(share): add collections and offline sharing/import` on `feat/s07-collections-sharing`.
- **Docs commit:** `52bdc91` — `docs(s07): record collections and sharing completion`.
- **Merge state:** merged into local `main` by fast-forward (`01d8b30..52bdc91`) on 2026-10-24. Post-merge `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint` passed in the restored toolchain (18m 50s, 613 actionable tasks). `STATUS.md` is updated by the S05 integration pass.
- **Tests:** `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` — BUILD SUCCESSFUL (442 actionable tasks). Added 49 core/data test cases across collection codec/store/repository and share codec/export/import suites. All tests pass.
- **Lint:** `./gradlew lint` — BUILD SUCCESSFUL, 0 errors. The app and `feature:favourites` reports contain 40 and 13 warnings respectively, mostly pre-existing plural-resource and Compose modifier-order warnings.
- **Play manifest:** inspected the merged `playstoreDebug` manifest. It contains only `INTERNET` plus AndroidX's generated dynamic-receiver permission; the non-exported `FileProvider` (`${applicationId}.fileprovider`) is present. No `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, accessibility, or other restricted capability was added.
- **Emulator-only checks not run:** no emulator/device was available, so the Android chooser MIME handling, `FileProvider` read grants, and `OpenDocument` picker flow were not exercised at runtime. Their manifests, provider path, and build wiring were verified statically.

### F12 criteria

- **F12-R1 — complete:** all export/import code is local; no account, upload, or server path exists.
- **F12-R2 — complete:** notes are omitted by default on both export and import, with an explicit opt-in in each dialog.
- **F12-R3 — complete:** import parses into an immutable preview and repository writes happen only after the user confirms.
- **F12-R4 — complete:** merge, replace, and skip are implemented for favourites and collection conflicts and covered by tests.
- **F12-R5 — complete:** imported store references are inert; known stores are copied by id, unknown stores are reported and dropped, and no store registry, install, or link action is triggered.
- **F12-R6 — complete:** JSON, plain text, and persisted collections reject unsupported future schema versions with typed errors and a clear UI message.
- **F12-R7 — complete:** plain text includes a versioned header, readable names, app keys, store references, tags, and notes only when opted in.
- **F12-R8 — deferred SHOULD:** QR/deep-link sharing was not implemented and no QR dependency was added. JSON, plain text, and file export/import cover the v1 MUSTs.
- **F12-R9 — deferred MAY:** sharing a selected subset of a collection is not implemented; collection sharing exports the whole ordered collection.

### Backup schema reuse notes for S09

`ShareEnvelope` documents the intended reuse contract. A full backup should wrap one or more share envelopes in its own outer, versioned envelope rather than defining another collection format. Collection identity remains `collectionId`; ordered items remain canonical `AppKey`s; favourite conflicts remain keyed by `AppKey`; stores remain references, never trusted descriptors; and notes remain opt-in. `ShareEnvelope` deliberately excludes settings, store descriptors, inventory, encryption, and checksums, so S09 must add those concerns at the outer layer rather than extending this v1 sharing payload in place.

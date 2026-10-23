# S07 — F12 Collections and Sharing

| Field | Value |
| --- | --- |
| Status | **Blocked by S06** |
| Features | F12 |
| Depends on | S06 |
| Can run in parallel with | S03 |
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

Update `STATUS.md` after merge:

- commit:
- tests:
- F12 criteria completed:
- deferred SHOULD items:
- backup schema reuse notes:

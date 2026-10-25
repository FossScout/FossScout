# S09 — F13 Backup and Migration

| Field | Value |
| --- | --- |
| Status | **Done** (S09 scope) |
| Features | F13 |
| Depends on | S01, S02, S03 (trust records), S06, S07 |
| Can run in parallel with | S08, S10, S11 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F13-backup-migration.md`](../specs/features/F13-backup-migration.md) |

## Goal

Export and restore the complete local profile so a user can move devices
without an account: favourites, collections, notes, tags, custom stores,
fingerprints/trust settings, and feature preferences.

## Scope

- Define a versioned `BackupEnvelope` with:
  - app version, schema version, creation time;
  - included/excluded section manifest;
  - favourites and notes;
  - collections and ordering;
  - custom store descriptors, fingerprints, and trust settings;
  - feature settings and disabled state;
  - placeholders or explicit exclusions for cached index payloads and future
    match overrides.
- Build a full export UI:
  - show the manifest before writing;
  - write a file through the system document picker/share sheet;
  - never include index payloads or APK files.
- Build import:
  - validate schema and checksum/envelope before changing anything;
  - preview conflicts and changes;
  - support merge/replace/skip consistently with S07;
  - invalid/unsupported files leave local data untouched.
- Add optional passphrase protection:
  - use a standard KDF + authenticated encryption (for example
    PBKDF2WithHmacSHA256 + AES-GCM) with a versioned envelope;
  - wrong passphrase or tampering yields an error, never partial import;
  - record the chosen default in the session notes.
- Preserve store fingerprints and trust state across restore.
- Add round-trip, wrong-passphrase, tamper, invalid-file, and conflict tests.

## Out of scope

- Cloud backup, accounts, or automatic upload.
- Changing Android OS auto-backup semantics beyond documenting them.
- Cached index payload migration.
- F20 match overrides until F20 exists.
- A new backup format that conflicts with S07's share schema.

## Key files

- new backup module or `feature/backup`
- S01 settings snapshot
- S02 store registry codec
- S06 favourites repository/codec
- S07 collection/share codec
- S03 trust records
- manifest/file provider if needed
- app navigation/composition root

## Acceptance criteria

- **F13-R1:** full export includes favourites, collections, notes, custom stores,
  and settings; clean-install restore reproduces them.
- **F13-R2:** the export manifest lists included and excluded data and is shown
  before writing.
- **F13-R3:** import validates schema and shows a preview; invalid files fail
  without changing data.
- **F13-R4:** a passphrase-protected backup cannot import without the correct
  passphrase; wrong passphrase gives an error, not partial data.
- **F13-R5:** store fingerprints and trust settings survive migration.
- **F13-R6 (SHOULD):** per-item conflict resolution.
- **F13-R7 (SHOULD):** Android auto-backup behavior is documented accurately.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Test clean-install round-trip at the codec/repository boundary, encryption
wrong-password and tamper cases, invalid-file no-mutation, and fingerprint
preservation. Manually verify the document picker/share flow if possible.

## Risks and decisions

- Backup is destructive if import is wrong. Validate fully before mutation and
  keep the previous local data until import commits.
- Do not invent cryptography. Use standard platform/JCA primitives, a random
  salt/nonce, authenticated encryption, and versioned parameters.
- Encryption default is an open product decision. Record the chosen default and
  make the UI explicit either way.
- Reuse S07's schema where possible; a second incompatible collection format is a
  bug.

## Completion record

- **Implementation commit:** `8b112b6` — `feat(backup): add S09 full-profile backup and migration` on `feat/s09-backup-migration`.
- **Docs commit:** `PENDING` — `docs(s09): record backup and migration completion`.
- **Merge state:** pending local fast-forward merge into `main` on 2026-10-25.
- **Modules:** new pure-JVM `core:backup` (versioned envelope/manifest, canonical SHA-256 codec, PBKDF2/AES-GCM encryption, merge/replace/skip planner, backup service contract) and Compose `feature:backup` (export manifest, encryption controls, document-picker import, encrypted unlock, restore preview, conflict-mode selection, completion/error states). `core:data` adds `DefaultBackupService` plus snapshot replacement methods on the feature-settings, store-registry, and handoff repositories. Wired through `AppContainer`, the Settings entry, app navigation, share-sheet export, and document-picker import.
- **Encryption/default decision:** unencrypted by default with an explicit opt-in passphrase. PBKDF2-HMAC-SHA256 (210,000 iterations), AES-256-GCM, 16-byte salt, 12-byte nonce, 128-bit tag, versioned outer envelope, and fixed KDF/cipher parameters.
- **Tests:** `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint :app:lintPlaystoreDebug --console=plain` passed on 2026-10-25 (2m 43s, 832 actionable tasks). **669 test executions across 100 suites; 0 failures, 0 errors, 0 skipped**. Lint: 0 errors; warnings were app Play 42, app F-Droid 40, `feature:favourites` 13, and `feature:inventory` 4. The new `core:backup`/`feature:backup` modules and the Settings entry add no lint warnings. The Play merged manifest scan passed with only `INTERNET` and the framework dynamic-receiver permission; F-Droid retains `QUERY_ALL_PACKAGES`. Both merged manifests contain the DataStore-aware backup/data-extraction rules and the non-exported FileProvider.
- **F13 criteria completed:**
  - **F13-R1 — complete:** full-profile export includes favourites, notes, collections, custom stores with fingerprints/trust notes, feature settings/disabled state, and preferred store clients; clean-install restore is covered at the codec/planner/repository boundary.
  - **F13-R2 — complete:** the export and restore preview show the included/excluded manifest before writing or applying.
  - **F13-R3 — complete:** schema, format, checksum, and payload validation run before planning; invalid/unsupported/tampered files leave repositories untouched.
  - **F13-R4 — complete:** encrypted files require the passphrase; wrong passphrase and tampering surface as an error and no repository write occurs.
  - **F13-R5 — complete:** custom-store fingerprints and trust notes survive restore; built-in descriptors are preserved from the current app so a backup cannot downgrade them.
  - **F13-R6 — deferred SHOULD:** restore uses global merge/replace/skip modes with per-item conflict flags in the plan and aggregate counts in the UI. Per-item keep-local/use-backup selection is not implemented.
  - **F13-R7 — complete:** Android backup rules include DataStore files and legacy shared preferences; in-app text and `STATUS.md` describe the actual behavior.
  - **F13-R8 — complete:** export is delivered through the Android share sheet with a non-exported FileProvider URI.
- **Remaining F13 gaps:** per-item conflict resolution (R6); device/emulator verification of the share sheet, document picker, FileProvider reads, and large-profile behavior; cross-DataStore rollback is best-effort rather than transactional.

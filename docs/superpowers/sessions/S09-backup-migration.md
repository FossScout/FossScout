# S09 — F13 Backup and Migration

| Field | Value |
| --- | --- |
| Status | **Blocked by S07** |
| Features | F13 |
| Depends on | S01, S02, S03 (trust records), S06, S07 |
| Can run in parallel with | S05 |
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

Update `STATUS.md` after merge:

- commit:
- encryption/default decision:
- tests:
- F13 criteria completed:
- remaining F13 gaps:

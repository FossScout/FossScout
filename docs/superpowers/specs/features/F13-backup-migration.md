# F13 — Backup and Migration

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `SHARE_IMPORT_EXPORT` |
| Depends on | F11, F12 |
| Related | F04 |

## 1. Problem

With no account and no server, the device is the source of truth. A user who
changes phones must be able to move favourites, notes, custom stores, and
settings without creating a login. Android's own backup is helpful but not
guaranteed across OEMs.

## 2. User stories

- As a user, I want to export everything before I change phones.
- As a user, I want to restore my setup on a new device.
- As a user, I want to protect the backup with a passphrase.
- As a user, I want to know what is and is not included.

## 3. Scope

- Export a full local profile: favourites, collections, notes, custom stores,
  settings, and feature preferences.
- Import with preview and conflict handling.
- Optional passphrase-based encryption.
- Schema version and app version metadata.
- Exclude downloaded APKs and index caches by default.
- Exclude secrets unless explicitly included and encrypted.
- Document Android auto-backup behaviour.

## 4. Non-goals

- Cloud sync or account recovery.
- Backing up another app's data.
- Transferring installed APKs.
- Guaranteeing migration across incompatible schema versions without a migration
  path.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F13-R1 | MUST | A full export includes favourites, collections, notes, custom stores, and settings. | Restore on a clean install reproduces them. |
| F13-R2 | MUST | Export clearly lists what is included and excluded. | The UI shows the manifest before writing the file. |
| F13-R3 | MUST | Import validates schema and shows a preview. | Invalid files fail without changing data. |
| F13-R4 | MUST | A passphrase-protected backup cannot be imported without the passphrase. | Wrong passphrase produces an error, not partial data. |
| F13-R5 | MUST | Store fingerprints and trust settings survive migration. | A restored store retains its pinned fingerprint. |
| F13-R6 | SHOULD | Conflicts (same favourite, different note/store) are shown per item. | User can choose which version to keep. |
| F13-R7 | SHOULD | The app documents whether Android auto-backup includes the data. | Documentation and in-app text match actual behaviour. |
| F13-R8 | MAY | Export can be triggered from a share sheet. | The backup file can be sent to a user-chosen destination. |

## 6. Data

Profile manifest, app version, schema version, favourites, collections, notes,
store descriptors, settings. No device identifier. Secrets are excluded by
default; if included, they require encryption.

## 7. Edge cases

- Restoring an older schema.
- Restoring a backup from a newer app version.
- Duplicate stores with different fingerprints.
- Missing passphrase.
- Truncated file.
- User restores on a device with an existing profile.
- Android backup restores only part of the data.

## 8. Open questions

1. Should the full backup be encrypted by default or only on request?
2. Which settings are safe to include without leaking private information?
3. Should the backup include cached index metadata to speed up first run?
4. Is a device-to-device QR/transfer flow needed, or is file export enough?

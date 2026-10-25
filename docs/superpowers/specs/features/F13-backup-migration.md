# F13 — Backup and Migration

| Field | Value |
| --- | --- |
| Status | Draft (v1 backup implemented in S09; per-item conflict resolution remains deferred) |
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

1. **Resolved in S09:** encryption is on request, not by default; the manifest is
   always visible.
2. **Resolved in S09:** only declared feature settings and disabled state are
   included; unknown/stale settings are dropped on restore. No secrets are
   collected or exported.
3. **Resolved in S09:** cached index metadata is excluded from portable backups.
4. **Resolved for v1:** file export/import is sufficient; QR/device-to-device
   transfer remains future work.

## 9. S09 implementation decisions

- **Encryption default:** unencrypted by default, with an explicit passphrase
  opt-in. Encryption uses PBKDF2-HMAC-SHA256 (210,000 iterations), AES-256-GCM,
  a 16-byte salt, a 12-byte nonce, and a 128-bit authentication tag. The outer
  encryption envelope is versioned and pins its KDF/cipher parameters.
- **Schema:** backup schema v1 includes favourites, notes, collections, custom
  stores with fingerprints/trust notes, feature settings/disabled state, and
  preferred store clients. Index snapshots, cached store data, APK files,
  installed-app inventory, match overrides, and secrets are excluded.
- **Integrity:** a SHA-256 checksum covers canonical metadata, the manifest, and
  the payload. Invalid, unsupported, tampered, or wrong-passphrase input is
  rejected before any repository write.
- **Conflict modes:** `merge` adds missing items, unions tags/collection items,
  keeps existing settings and custom-store descriptors on conflict, and lets
  existing handoff choices win. `replace` uses the backup for all sections.
  `skip` only adds missing favourites, collections, and custom stores and leaves
  settings/handoff unchanged.
- **Built-in stores:** the app preserves its current built-in descriptors during
  restore, so a backup cannot downgrade their endpoints or pinned fingerprints.
  Custom-store merge/skip keeps the existing descriptor on ID conflict.
- **Transient store health:** sync health is stripped from exported custom stores
  and from incoming custom stores during planning; it is not portable profile
  data.
- **Android auto-backup:** the app's backup rules include the DataStore files
  under `files/datastore` and legacy shared preferences. The in-app text says
  Android may include local app data when system backup is enabled, while
  FossScout exports never include index snapshots, cached store data, or APKs.
- **File transfer:** export uses the Android share sheet with a non-exported
  FileProvider URI; import uses the system document picker. No storage
  permission is requested.

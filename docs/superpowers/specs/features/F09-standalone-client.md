# F09 — Standalone Store Client

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **Restricted** |
| Profiles | `fdroid`, `direct`, `custom` (only with `DIRECT_APK_INSTALL`) |
| Required capabilities | `DIRECT_APK_INSTALL`, `STORE_INDEX_NETWORK` |
| Depends on | F05, F14 |
| Related | F07, F08, F15 |

## 1. Problem

Some users do not have F-Droid installed, or do not want to install it. They
still want the APKs from F-Droid-compatible repositories. The repository is a
static file tree, so a store client is not technically required.

## 2. User stories

- As a user without F-Droid, I want to download and install a FOSS app from an
  indexed store.
- As a user, I want the download verified before Android installs it.
- As a user, I want to know when an app I installed this way has an update.

## 3. Scope

- Resolve an APK URL from a verified index or live API.
- Download with resume, progress, cancellation, and storage checks.
- Verify SHA-256, size, package name, version code, and signer.
- Invoke the system package installer with explicit confirmation.
- Support single APKs first; split APKs only if explicitly in scope.
- Track the source and version for later update checks.
- Clean up downloaded files according to a retention policy.

## 4. Non-goals

- Silent installation.
- Bypassing Android signature checks or unknown-sources settings.
- Installing from an unsigned or unverified index by default.
- Acting as a background auto-updater without user opt-in.
- Play Store distribution.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F09-R1 | MUST | Standalone mode requires no store app. | On a device with no store client, a supported app downloads and installs. |
| F09-R2 | MUST | The index is verified before an APK URL is trusted. | A tampered index blocks the download. |
| F09-R3 | MUST | The APK hash and size match the verified index. | A corrupted download is rejected before installation. |
| F09-R4 | MUST | The signer is shown and checked against the installed app when present. | A signer mismatch routes to F07 guidance. |
| F09-R5 | MUST | Installation requires explicit user action. | No background or automatic install path exists. |
| F09-R6 | MUST | Downloads can be cancelled and resumed where supported. | Cancel leaves no partial file presented as complete. |
| F09-R7 | SHOULD | The app records which store/version it installed for update tracking. | The installed source appears in app details. |
| F09-R8 | SHOULD | Storage usage is visible and old downloads can be cleaned. | Cleanup frees space without breaking installed apps. |
| F09-R9 | MAY | Split APKs / app bundles are supported. | A compatible split install succeeds; unsupported cases fail clearly. |

## 6. Data

Download record: store, package, version, APK URL, hash, signer, local path,
state, error, installed source. No account or device identifier.

## 7. Edge cases

- APK removed from the store between match and download.
- Server returns HTML or a redirect to a login page.
- Storage full during download.
- Installer permission not granted.
- User cancels the system installer.
- Downgrade or incompatible SDK/ABI.
- App already installed by another user/profile.
- Split APK set incomplete.

## 8. Open questions

1. Is split APK support required for v1, or can it be a later enhancement?
2. Should FossScout install updates automatically with user opt-in, or always
   require confirmation?
3. How long should downloaded APKs be retained?
4. Should the `direct` profile ship standalone install in v1?

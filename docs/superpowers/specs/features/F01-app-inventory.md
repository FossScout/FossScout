# F01 — Installed App Inventory

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General + restricted enhancement** |
| Profiles | `playstore` (manual/selected), `fdroid` (full), `direct` (full), `custom` |
| Required capabilities | `MANUAL_PACKAGE_SELECTION`; `BROAD_PACKAGE_VISIBILITY` for full scan |
| Depends on | F02 |
| Related | F06, F10, F15 |

## 1. Problem

A privacy-aware user wants to know which installed apps are closed source and
which have FOSS equivalents. Enumerating every installed package is the most
complete way to answer that, but Android and Google Play restrict broad package
visibility. The feature must therefore have a useful baseline without that
permission.

## 2. User stories

- As a user, I want to scan my installed apps and see which ones have FOSS
  alternatives.
- As a Play Store user, I want to check a specific app even if FossScout cannot
  enumerate every package.
- As a user, I want to see what FossScout knows, what it does not know, and when
  the data was last refreshed.

## 3. Scope

- Enumerate installed packages when the profile has broad visibility.
- Provide a manual “check an app” path in every profile.
- Show label, package name, version, installer source, signer, enabled/system
  state, and FOSS classification.
- Refresh on demand and expose the last scan time.
- Filter by classification, installer, system/user app, and store availability.

## 4. Non-goals

- Uploading the installed-app inventory to a server.
- Monitoring app launches or usage time.
- Identifying a person or device.
- Automatically uninstalling or replacing apps.
- Scanning other user profiles without the OS granting access.

## 5. Behaviour

### 5.1 Full scan (`fdroid`, `direct`)

1. Request or verify the broad package visibility capability.
2. Enumerate installed packages and their versions.
3. Read installer package and signing certificate fingerprint where available.
4. Match each app through F02.
5. Persist a local snapshot with a timestamp.
6. Show progress and allow cancellation.

### 5.2 Manual check (`playstore`)

- The user searches for an app by name or package name, picks it from a
  system-provided picker if available, or shares a store link to FossScout.
- FossScout checks only that app.
- The result is clearly labelled “checked one app”, not “scanned the device”.

### 5.3 Results

- **FOSS** — a trusted FOSS source is known.
- **Proprietary** — known closed-source app, with evidence.
- **Unknown** — no reliable license/source information.
- **Unavailable** — package no longer installed or not visible.

Never infer “proprietary” solely from the absence of an F-Droid entry.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F01-R1 | MUST | Inventory data stays local unless the user explicitly exports it. | A scan with networking disabled produces the same local result; no inventory network request is made. |
| F01-R2 | MUST | Full scan is available only when `BROAD_PACKAGE_VISIBILITY` exists. | The `playstore` profile without the capability does not request or imply a full scan. |
| F01-R3 | MUST | Manual check works in every profile. | A user can check one app by name/package/share without broad visibility. |
| F01-R4 | MUST | Results distinguish FOSS, proprietary, unknown, and unavailable. | Each state has a visible label and an explanation. |
| F01-R5 | MUST | The result states when the last scan happened. | A stale snapshot is visibly marked with its timestamp. |
| F01-R6 | SHOULD | System apps, disabled apps, and work-profile apps can be filtered. | Each filter changes the list without re-scanning. |
| F01-R7 | SHOULD | The scan can be cancelled without corrupting the previous snapshot. | Cancelling leaves the last complete snapshot usable. |
| F01-R8 | MAY | The user can hide selected apps from the audit. | Hidden apps remain installed but are excluded from the default list. |

## 7. Data and privacy

Local model: package name, label, version name/code, installer package, signer
SHA-256, system/disabled flags, first/last seen, classification, evidence links.
No device identifier, account, or analytics identifier is required.

## 8. Edge cases

- Work profiles and multiple Android users.
- Split APKs and app bundles.
- Disabled or suspended apps.
- Packages with the same label but different IDs.
- Installer unknown (sideloaded).
- Package visibility returning a partial list.
- OEM-specific package manager behaviour.

## 9. Open questions

1. Is a system-provided app picker reliable enough for the Play baseline?
2. Should the Play profile request `QUERY_ALL_PACKAGES` at all, or ship manual-only
   until approval is certain?
3. How long should local scan history be retained?

# F19 — Permission and Tracker Diff

| Field | Value |
| --- | --- |
| Status | Proposed |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | Installed-app metadata; optional tracker-data source |
| Depends on | F02, F03, F06 |
| Related | F07, F14, F21 |

## 1. Problem

“FOSS” answers who can inspect and reuse the code. It does not answer what an app
can access on the device. Users comparing an installed proprietary app with a
FOSS alternative need to see permission and tracker differences, not just a
replacement name.

## 2. User stories

- As a user, I want to compare permissions before I switch apps.
- As a user, I want to know when an alternative asks for more access than the app
  it replaces.
- As a user, I want to see which tracker signatures are known, with their source.
- As a user, I want to know when data is missing instead of seeing a false
  “clean” result.

## 3. Data sources

- Installed app permissions: Android `PackageManager` (profile-dependent).
- Store metadata: F-Droid-compatible index permissions and anti-features.
- Tracker signatures: a bundled, versioned dataset or a user-selected local
  source. If a network source is used, the source, license, and update time must
  be visible.

No permission or tracker data is uploaded.

## 4. Scope

- Show normal, dangerous, signature, and special permissions in plain language.
- Compare installed app and alternative side by side.
- Mark permissions present only in the alternative and only in the installed app.
- Show tracker matches with confidence and dataset version.
- Link every claim to its source.
- Explain that absence of a tracker match is not proof of no tracking.

## 5. Non-goals

- Assigning a single “privacy score” that hides the underlying evidence.
- Claiming runtime network behaviour from static signatures alone.
- Uploading an app or its permission list for analysis.
- Blocking installation based on this comparison.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F19-R1 | MUST | Show the installed app's requested permissions. | The list matches `PackageManager` output for the selected package. |
| F19-R2 | MUST | Show the alternative's known permissions when store metadata provides them. | Missing metadata is labeled “unknown”, not “none”. |
| F19-R3 | MUST | Highlight permissions that differ between the two apps. | Added and removed permissions are visually distinct. |
| F19-R4 | MUST | Show tracker matches with dataset name, version, and license. | Every tracker badge can reveal its source and version. |
| F19-R5 | MUST | Never present “no tracker match” as proof of no tracking. | The empty state includes the limitation text. |
| F19-R6 | SHOULD | Explain dangerous permissions in user-facing language. | A permission has a short “what this allows” description. |
| F19-R7 | SHOULD | Let the user hide tracker information if no dataset is configured. | The permission diff still works without the tracker dataset. |
| F19-R8 | MAY | Export the comparison as part of the audit report. | Report includes source and timestamp. |

## 7. Edge cases

- Work-profile or cloned apps with the same package ID.
- Split APKs with permission differences.
- Permissions added by a recent app update.
- Alternative metadata older than the installed version.
- Tracker signatures with low confidence or shared libraries.
- Apps with no declared permissions.

## 8. Open questions

1. Which tracker dataset ships first, and under what license?
2. Should the comparison use installed-app permissions only, or also the
   alternative APK's manifest when available?
3. Should the app show a summary count before the detailed diff?

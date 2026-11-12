# F19 — Permission and Tracker Diff

| Field | Value |
| --- | --- |
| Status | **Done** (F19-R8 audit export deferred) |
| Availability | **General** |
| Profiles | `playstore`, `vanilla`, `direct`, `custom` |
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
- Tracker signatures: the bundled Exodus Privacy ETIP snapshot described in
  [`docs/TRACKER_DATASET.md`](../../../TRACKER_DATASET.md). It is versioned
  `2026-11-10` and licensed under ODbL 1.0/DbCL 1.0. The app does not fetch
  tracker data at runtime.
- Tracker evidence: the selected installed app's already-local base and split
  APK files are scanned for `classes*.dex` signatures. The store alternative is
  not downloaded or scanned.

No permission or tracker data is uploaded.

## 4. Scope

- Show normal, dangerous, signature, and special permissions in plain language.
- Compare installed app and alternative side by side.
- Mark permissions present only in the alternative and only in the installed app.
- Show tracker matches with confidence, dataset name, version, license, and
  matched code signature.
- Link every claim to its source.
- Explain that absence of a tracker match is not proof of no tracking.
- Scan only the selected installed app's local DEX files; never download the
  alternative APK for this feature.

## 4a. Implementation status

As of 2026-11-10, the permission and tracker-comparison slices are implemented:

- F19-R1: installed requested permissions come from `PackageManager` with
  `GET_PERMISSIONS`; no new manifest permission is required.
- F19-R2: alternative permissions come from the latest cached store-index
  version. An empty or absent store permission list is shown as unknown, never
  as “none”.
- F19-R3: the comparison screen shows added, removed, and shared counts, then
  groups the detailed diff. The entry point is the primary, other-match, or
  alternative card on the match screen.
- F19-R5/R6: dangerous and special permissions have short explanations where
  known, and the tracker section states that no tracker result does not prove
  the absence of trackers.
- F19-R7: the permission comparison works without tracker data. The tracker
  section reports that no dataset is configured rather than implying a clean
  result.

### Tracker signatures (F19-R4)

- The bundled dataset is the Exodus Privacy ETIP snapshot
  [`2026-11-10`](../../../TRACKER_DATASET.md): 674 trackers and 987 code
  signatures under ODbL 1.0/DbCL 1.0.
- The scan reads only the selected installed app's local base and split APK
  files and only their `classes*.dex` entries. It does not download the
  alternative APK, load classes, or execute app code.
- The scan starts only when the user opens the permission comparison. It runs on
  a background dispatcher and is capped at 64 MiB; hitting the cap is shown as
  incomplete.
- The tracker section shows the dataset name, version, license, source,
  attribution, match confidence, matched code signature, and the limitation
  that a static match is not runtime proof.
- Unreadable APKs and APKs without DEX files are shown as unavailable, never as
  a clean result.
- The snapshot update procedure and APK scan policy are in
  [`docs/TRACKER_DATASET.md`](../../../TRACKER_DATASET.md).

F19-R8 audit export remains deferred. Real-device/UI verification of the tracker
section is still pending.

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
- Apps whose APK files are unreadable or whose scan exceeds the 64 MiB cap.
- Trackers in native code, remote-loaded code, or code hidden by renaming.

## 8. Open questions

1. Resolved: F19-R4 uses the Exodus Privacy ETIP snapshot version `2026-11-10`
   under ODbL 1.0/DbCL 1.0. The licence and attribution are shown in the
   comparison screen.
2. Deferred: analyzing the alternative APK manifest when it is already available
   locally. V1 uses installed-app permissions and cached store metadata, and
   never downloads the alternative APK.
3. Resolved: the comparison shows added, removed, and shared summary counts
   before the detailed diff.
4. F19-R8 audit export remains deferred; it is the only open scope item.

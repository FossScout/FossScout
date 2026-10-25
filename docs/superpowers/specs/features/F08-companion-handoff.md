# F08 — Companion Store Handoff

| Field | Value |
| --- | --- |
| Status | Draft (v1 handoff implemented in S08; other-store deep links remain deferred) |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `MANUAL_PACKAGE_SELECTION`; no install permission required |
| Depends on | F04 |
| Related | F05, F09 |

## 1. Problem

Many users already have F-Droid or another store installed. FossScout should not
duplicate that app's job when a clean handoff is enough. It should also help the
user add a repository to the store they already trust.

## 2. User stories

- As an F-Droid user, I want to open an app in F-Droid from FossScout.
- As an F-Droid user, I want to add a custom compatible repository with one
  action or a QR scan.
- As a user without F-Droid, I want clear instructions instead of a dead button.

## 3. Scope

- Detect known store clients without broad package visibility, using explicit
  package queries for known IDs.
- Open app details via `market://details?id=<package>` or the store's deep link.
- Open the store's repository-add flow via a well-formed URL, QR, or
  `fdroidrepos://` where supported.
- Copy the repository URL and fingerprint.
- Fall back to browser instructions when no store is installed.

## 4. Non-goals

- Installing the store client silently.
- Adding a repository without user confirmation.
- Adding a non-F-Droid-compatible store to F-Droid.
- Replacing the store's own update mechanism.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F08-R1 | MUST | Detect F-Droid with an explicit package query, not broad visibility. | Play profile can detect F-Droid without `QUERY_ALL_PACKAGES`. |
| F08-R2 | MUST | Offer “Open in F-Droid” for an app when F-Droid is installed and the app is known there. | The intent opens the correct app page. |
| F08-R3 | MUST | Offer “Add repository” with URL and fingerprint, QR, and copy actions. | Each path produces the same descriptor. |
| F08-R4 | MUST | If no store is installed, show install/store-independent alternatives. | No dead-end button; user can install F-Droid or use FossScout standalone. |
| F08-R5 | SHOULD | Remember the user's preferred store per app. | The preferred store is preselected next time. |
| F08-R6 | MAY | Support other stores' deep links where a stable scheme exists. | Each scheme is verified and fails gracefully. |

## 6. Data

Installed store package IDs, preferred store per app, last handoff result. No
personal data.

## 7. Edge cases

- Store installed but disabled.
- Multiple F-Droid-compatible clients installed.
- Deep link resolves to the wrong app.
- Repository URL redirects.
- The store does not support `fdroidrepos://`.
- Android work profile.

## 8. Open questions

1. Which store clients should be detected in v1?
2. Should FossScout ask F-Droid to refresh the repo after adding it?
3. How should it behave when both F-Droid and a fork client are installed?

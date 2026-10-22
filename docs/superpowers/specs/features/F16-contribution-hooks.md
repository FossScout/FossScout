# F16 — Contribution Hooks

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None beyond local export |
| Depends on | F04, F06 |
| Related | F04, F12 |

## 1. Problem

The project is open source so others can improve it. Users will find missing
stores, wrong matches, broken adapters, and unsupported formats. Reporting those
findings should be easy and should not leak private data.

## 2. User stories

- As a user, I want to report a missing store without writing a long bug report.
- As a user, I want to include useful diagnostics without exposing my installed
  apps.
- As a contributor, I want a prefilled issue with the store URL and adapter
  details.

## 3. Scope

- “Report a missing store” with URL, adapter guess, and validation error.
- “Report a wrong match” with package, expected store, and observed result.
- “Request an adapter” with format details.
- Copy a diagnostic summary to the clipboard.
- Open the project's issue tracker with a prefilled template where possible.
- Export a debug bundle that excludes personal data by default.

## 4. Non-goals

- Automatically uploading diagnostics.
- Including the installed-app inventory, notes, or account data.
- Creating an issue without user review.
- Promising a fix.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F16-R1 | MUST | Reports are user-reviewed before leaving the device. | A preview is shown before sharing/opening. |
| F16-R2 | MUST | Default diagnostics exclude installed apps, notes, and identifiers. | A generated bundle contains only the reported store/app and error. |
| F16-R3 | MUST | The report includes app version, profile, adapter type, and error. | The issue template is actionable without follow-up basics. |
| F16-R4 | SHOULD | The user can copy the report instead of opening a browser. | Clipboard path works offline. |
| F16-R5 | SHOULD | The report can include a redacted store descriptor. | Credentials and tokens are removed. |
| F16-R6 | MAY | A debug bundle can be exported with explicit opt-in for extra logs. | Extra logs are clearly listed before export. |

## 6. Data

Report payload: app version, profile, feature ID, store descriptor (redacted),
adapter type, error class/message, timestamp. Explicitly no inventory, notes,
or device identifiers.

## 7. Edge cases

- No issue tracker available offline.
- Store URL contains a token.
- Error message contains a local path.
- User attaches a screenshot with private content.
- Report duplicates an existing issue.

## 8. Open questions

1. Which issue tracker is canonical: GitHub, GitLab, Codeberg, or all?
2. Should reports be queued offline and sent later?
3. Is a debug bundle a zip file or plain text?

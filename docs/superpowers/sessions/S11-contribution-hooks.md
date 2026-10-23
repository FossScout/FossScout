# S11 — F16 Contribution Hooks

| Field | Value |
| --- | --- |
| Status | **Blocked by S05** |
| Features | F16 |
| Depends on | S02, S05 |
| Can run in parallel with | S08, S09, S10 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F16-contribution-hooks.md`](../specs/features/F16-contribution-hooks.md) |

## Goal

Let users report a missing store, wrong match, or unsupported format with a
prefilled, user-reviewed report that contains no inventory, notes, or identifiers.

## Scope

- Define a `ReportPayload` containing only:
  - app version/build profile;
  - feature ID and operation;
  - store/adapter type;
  - error class and safe message;
  - redacted store descriptor;
  - timestamp;
  - optional coverage summary (store statuses only, no app inventory).
- Add a report builder with redaction:
  - remove credentials, query tokens/userinfo, local file paths, and notes;
  - never include installed-app lists, package inventories, or device IDs.
- Show a preview before anything leaves the app.
- Provide copy-to-clipboard and Android share; both work offline.
- Add an optional debug bundle that is clearly marked and still redacted.
- Optionally construct a prefilled issue URL for the project tracker; do not
  auto-submit or open without confirmation.

## Out of scope

- Automatic upload, accounts, telemetry, or an offline server queue.
- Full logcat capture or debug bundles with inventory/notes.
- A canonical issue tracker decision if the repository URL changes; use the
  current project URL and make it configurable.

## Key files

- new `feature:contribution` module or a small contribution screen
- S02 store descriptor/redaction helpers
- S05 coverage model
- app navigation/composition root

## Acceptance criteria

- **F16-R1:** reports are previewed and user-reviewed before leaving the device.
- **F16-R2:** default diagnostics exclude installed apps, notes, and identifiers.
- **F16-R3:** the report includes app version, profile, adapter type, and error.
- **F16-R4 (SHOULD):** copy works offline.
- **F16-R5 (SHOULD):** store descriptor is redacted.
- **F16-R6 (SHOULD):** optional debug bundle with extra logs remains redacted.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Add tests that a fixture containing notes, package lists, tokens, and local paths
cannot leak into the payload. Add a preview/no-share-before-confirm test.

## Risks and decisions

- Privacy is the feature. Treat every added field as guilty until proven safe.
- Do not include store fingerprints unless they are public and necessary; they
  usually are public, but keep the default minimal.
- No network permission is required for copy/share.

## Completion record

Update `STATUS.md` after merge:

- commit:
- tests:
- F16 criteria completed:
- remaining tracker/format decisions:

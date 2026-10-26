# S11 — F16 Contribution Hooks

| Field | Value |
| --- | --- |
| Status | **Done** (S11 scope) |
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

- **Implementation commit:** `160db61` — `feat(contribution): add S11 reviewed redacted reports` on `feat/s11-contribution-hooks`.
- **Docs commit:** `1bc04a9` — `docs(s11): record contribution hooks completion`.
- **Merge state:** merged into local `main` by fast-forward (`3401981..1bc04a9`) on 2026-10-26.
- **Modules:** new pure-JVM `core:contribution` (payload schema v1, redactor, context builders, configurable issue URL, report builder) and Compose `feature:contribution` (draft state, preview gate, copy/share/issue/debug actions, Compose screen). Wired through `AppContainer`, Settings, Stores (header and editor), Match, and the app overlay/back flow.
- **Tests:** `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint :app:lintPlaystoreDebug --console=plain` passed on 2026-10-26 (3m 43s, 902 actionable tasks). **729 test executions across 106 suites; 0 failures, 0 errors, 0 skipped.** Both debug assemblies passed. Lint reported **0 errors**; warnings were app Play 42, app F-Droid 40, `feature:favourites` 13, and `feature:inventory` 4. `core:contribution` and `feature:contribution` add no lint warnings.
- **F16 criteria completed:**
  - **F16-R1 — complete:** the ViewModel enforces a preview gate; copy, share, debug export, and issue opening are no-ops until `previewReport()` succeeds. Editing invalidates the preview.
  - **F16-R2 — complete:** the payload has no inventory, notes, account data, device identifiers, or raw error fields. Coverage keeps store status/reason only.
  - **F16-R3 — complete:** app version/code, profile, feature, operation, adapter type (or explicit `unknown`), and error class are in the body and payload.
  - **F16-R4 — complete:** clipboard copy works offline and uses the exact previewed text.
  - **F16-R5 — complete:** store descriptors drop userinfo, query/fragment tokens, trust notes, health, and fingerprints while retaining `hasPinnedFingerprint`.
  - **F16-R6 — complete:** debug details are opt-in, clearly marked, and exported as redacted plain text/JSON with no logcat or inventory.
- **Remaining F16 gaps:** device/emulator verification of the preview, clipboard, share sheet, browser opening, and debug JSON; automatic upload/telemetry, an offline queue, screenshot attachment, and duplicate-issue detection remain out of v1. The GitHub issue URL is the default and is configurable through `ContributionReportBuilder`.

# S08 — F08 Companion Handoff + F07 Guidance-Only Replacement

| Field | Value |
| --- | --- |
| Status | **Blocked by S05** |
| Features | F08, F07 (v1 guidance/handoff scope) |
| Depends on | S02, S05 |
| Can run in parallel with | S09, S10, S11 |
| Profiles | `playstore`, `fdroid` |
| Specs | [`F08-companion-handoff.md`](../specs/features/F08-companion-handoff.md), [`F07-replacement-guidance.md`](../specs/features/F07-replacement-guidance.md) |

## Goal

Give the user a safe next step after a match: hand off to an installed store or
browser, and explain whether the FOSS build is an update, a replacement, or an
alternative. Do not install or uninstall anything.

## Scope

### F08 — companion handoff

- Detect installed store clients with explicit package queries, not broad
  visibility. Add the required `<queries>` entries.
- Open the correct F-Droid app page when possible.
- Offer “Add repository” with URL + fingerprint, QR, and copy actions. Every path
  must produce the same descriptor.
- If no store client is installed, show a store-independent fallback with no
  dead-end button.
- Remember a preferred store per app locally (optional SHOULD).

### F07 — replacement guidance

- Build `ReplacementAssessment` from installed metadata and the matched store
  candidate:
  - same package + same signer + newer version → **update**;
  - same package + different signer → **replace**, with uninstall/data-loss
    warning;
  - different package solving the same job → **alternative**;
  - system app or other-store install → explicit warning.
- Show installed and candidate versions when known.
- Never uninstall or replace without explicit confirmation; there is no silent
  action in v1.
- Offer a reminder/handoff only. Actual install is F09 and remains deferred.
- If signer metadata is unavailable, fail closed to “cannot verify” and do not
  claim a safe update.

## Out of scope

- APK install, uninstall, download, or `REQUEST_INSTALL_PACKAGES` (F09).
- Accessibility prompts (F10).
- Trust badge UI (F21) and full security details (S10).
- Deep links for stores without a stable scheme.

## Key files

- new `feature:companion` / `feature:replacement` modules or a cohesive
  replacement module
- `app/src/main/AndroidManifest.xml` for `<queries>`
- store registry contracts from S02
- matching result UI from S05
- app composition root and navigation

## Acceptance criteria

- **F08-R1:** F-Droid detection uses an explicit package query in every profile.
- **F08-R2:** “Open in F-Droid” opens the correct app page when available.
- **F08-R3:** repository add works via URL/fingerprint, QR, and copy, producing
  the same descriptor.
- **F08-R4:** no-store fallback has a working action and no dead end.
- **F08-R5 (SHOULD):** preferred store per app persists.
- **F07-R1:** signer/package identity is checked when metadata is available;
  mismatch shows an uninstall warning.
- **F07-R2:** data-loss warning appears before any destructive action.
- **F07-R3:** no uninstall/replace path without explicit confirmation.
- **F07-R4:** update/replace/alternative labels follow the decision matrix.
- **F07-R5–R7 (SHOULD):** versions, cross-store key explanation, reminder.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Test intent URI construction, `<queries>` presence, descriptor equivalence across
all add paths, no-store fallback, and each replacement decision. Add a test that
a missing signer is treated as unknown, not safe.

## Risks and decisions

- F07's spec depends on F09, which is deferred. This session intentionally
  implements guidance and handoff only; update the F07 status note accordingly.
- QR generation may add a small dependency (for example ZXing core). Prefer
  generating a bitmap locally; do not add camera permissions.
- Android 11 package visibility can hide store clients. Use explicit `<queries>`
  entries and test on API 30+.
- Do not use broad package visibility for handoff detection.

## Completion record

Update `STATUS.md` after merge:

- commit:
- tests/device checks:
- F07/F08 criteria completed:
- F07 post-v1 install gaps:

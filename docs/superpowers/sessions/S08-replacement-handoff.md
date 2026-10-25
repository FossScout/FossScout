# S08 — F08 Companion Handoff + F07 Guidance-Only Replacement

| Field | Value |
| --- | --- |
| Status | **Done** (S08 scope) |
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

- **Implementation commit:** `b1e36c0` — `feat(handoff): add S08 replacement guidance and companion handoff` on `feat/s08-replacement-handoff`.
- **Docs commit:** `6ca4c50` — `docs(s08): record replacement guidance and companion handoff completion`.
- **Merge state:** merged into local `main` by fast-forward (`3def5dd..6ca4c50`) on 2026-10-25. The full verification run above was executed on the branch before the docs commit.
- **Modules:** new pure-JVM `core:handoff` (replacement assessment, companion store catalog/detector contract, handoff planner, canonical repository descriptor, preferred-store repository) and Compose `feature:companion` (result/handoff screen, QR renderer, UI state, view model). Wired through `AppContainer`, the match “What happens next?” card, app navigation, explicit manifest `<queries>`, clipboard, and store/browser intents. `core:data` adds a versioned preferred-store DataStore codec.
- **Tests:** focused `:core:handoff:test`, `:core:data:test`, `:feature:companion:testDebugUnitTest`, `:core:matching:test`, and `:app:testPlaystoreDebugUnitTest` passed. Full `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint` passed (11m 50s, 753 actionable tasks): **618 test executions across 93 suites, 0 failures, 0 errors, 0 skipped**. Lint: **0 errors**; the app report has 40 pre-existing warnings, `feature:favourites` 13, and `feature:inventory` 4. The new `core:handoff`, `feature:companion`, and matching changes add no warnings.
- **Manifest:** Play contains only `INTERNET` plus AndroidX's dynamic-receiver permission and explicit package queries for `org.fdroid.fdroid`, `com.looker.droidify`, and `com.machiav3lli.fdroid`. No `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, accessibility service, or install/uninstall code was added. F-Droid keeps its existing `QUERY_ALL_PACKAGES` and launcher `<queries>` in addition to the explicit companion queries.
- **Device/emulator checks not run:** real `PackageManager` visibility/disabled-client values, F-Droid/Droid-ify/Neo Store intent resolution, QR scanning, clipboard overlay behavior, browser fallback, and live store-client install/uninstall transitions remain outstanding. The behavior is covered by JVM fakes and static manifest checks.

### F08 criteria

- **F08-R1 — complete:** detection uses explicit package queries in the main manifest for all three supported clients; the Play merged manifest has no broad package visibility.
- **F08-R2 — complete:** “Open in F-Droid” uses `market://details?id=<package>` with the selected client package set explicitly. F-Droid, Droid-ify, and Neo Store are supported for F-Droid candidates; other-store deep links (F08-R6 MAY) remain deferred.
- **F08-R3 — complete:** `RepositoryDescriptor` is the single source for the `fdroidrepos://` add intent, QR payload, combined copy payload, URL copy, and fingerprint copy. The UI also displays the URL and fingerprint.
- **F08-R4 — complete:** missing or disabled clients show a “Get F-Droid” action, a browser package page, and repository details/copy/QR without a dead-end button.
- **F08-R5 — complete:** the preferred client is stored per app key in a versioned local DataStore and restored on the next open.

### F07 criteria

- **F07-R1 — complete:** package and signer identity are compared before any recommendation; different signers produce `REPLACE` with uninstall and data-loss warnings.
- **F07-R2 — complete:** data-loss and uninstall warnings render before the store button is enabled; `REPLACE` also requires an explicit checkbox.
- **F07-R3 — complete:** there is no install, uninstall, or delete path in S08. FossScout only opens a store page, repository URI, or browser page.
- **F07-R4 — complete:** update, replace, alternative, no-update, cannot-verify, and not-installed labels follow the decision matrix.
- **F07-R5 — complete:** installed and candidate version names/codes are shown when known; the store candidate version code is carried from the index.
- **F07-R6 — complete:** Play/sideload-to-F-Droid crossings show the different-signing-key explanation. Known F-Droid-compatible clients are not mislabeled as Play crossings.
- **F07-R7 — deferred MAY:** a persistent reminder/watchlist item was not added. The handoff itself lets the user defer acting in FossScout.

### Post-v1 gaps

- In-app install/uninstall and APK download remain F09 and are excluded from v1.
- APK hash/signer continuity checks remain F14/F09 and security-detail UI is S10.
- Signature-lineage/key rotation, split APKs, ABI/SDK compatibility, and system-installer cancellation are not modeled.
- A persisted last-handoff result/history is not exposed; the local handoff DataStore stores only the preferred client per app.
- Device/emulator verification of package visibility, store intents, and QR/clipboard behavior is still required before release.

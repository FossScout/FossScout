# S05 — F02/F03/F06 Matching, Alternatives, and Coverage

| Field | Value |
| --- | --- |
| Status | **Done** (S05 scope) |
| Features | F02, F03, F06; completes F01-R4 |
| Depends on | S02, S03, S04 |
| Can run in parallel with | S07 |
| Profiles | `playstore`, `fdroid` |
| Specs | [`F02`](../specs/features/F02-foss-matching.md), [`F03`](../specs/features/F03-alternative-suggestions.md), [`F06`](../specs/features/F06-coverage-confidence.md) |

## Goal

Answer “is there a FOSS version of this exact app?” without false negatives,
show evidence, and suggest clearly-labelled alternatives only when no exact or
fork match exists. Every result must carry coverage information.

## Scope

- Define shared matching/coverage contracts:
  - `MatchLevel` L0/L1/L2/L3;
  - `MatchCandidate`, `MatchDecision`, `MatchEvidence`;
  - `CoverageSnapshot` and `StoreCoverage` with checked/skipped/failed/stale/
    unsupported reasons;
  - a confidence representation (label first; avoid a fake numeric score unless
    explainable).
- Implement the matching engine against store snapshots and app keys:
  - package identity first;
  - L1 exact package, L2 fork/upstream variant, L3 functional alternative,
    L0 unknown;
  - only L1 may be called “the same app”;
  - missing license/source metadata is `Unknown`, never FOSS;
  - stale/unsupported stores cannot produce a “no match” conclusion.
- Integrate the inventory classifier from S04 to complete F01-R4.
- Implement alternative suggestions:
  - only when no L1/L2 exists;
  - always labelled as a different app;
  - cite category/tag/description evidence;
  - respect enabled stores and coverage gaps;
  - cap and rank by relevance, not store affiliation;
  - optional hide/undo and side-by-side comparison (SHOULD).
- Add result/detail UI with a “why this match” surface, store/source/license
  evidence, coverage details, and alternative cards.
- Make matching work from cached snapshots with no network.

## Out of scope

- Replacement/install guidance (S08; F07).
- Security verification UI (S10; F14).
- Contribution reports (S11; F16).
- Permission/tracker diff (F19), matching correction (F20), trust badges (F21).
- Update tracking (F15).

## Key files

- new `feature/matching/` and/or `feature/alternatives/` modules
- shared matching/coverage contracts in `core:*`
- S03 snapshot repository
- S04 inventory and classification contracts
- app navigation and composition root

## Acceptance criteria

- **F02-R1:** same-package FOSS listing produces L1 with source evidence.
- **F02-R2:** L1/L2/L3/L0 are distinct in data and UI; only L1 is “same app.”
- **F02-R3:** every match exposes store, package, license, and source URL.
- **F02-R4:** missing license/source metadata yields `Unknown`.
- **F02-R5:** stale/unsupported store produces a coverage gap, not “no match.”
- **F02-R6 (SHOULD):** matching works offline from cached indexes.
- **F02-R7/R8 (SHOULD):** local correction/pinned source hooks, if cheap; full
  correction is F20.
- **F03-R1:** alternatives appear only when no L1/L2 exists.
- **F03-R2:** every alternative is labelled as a different app.
- **F03-R3:** each suggestion states why it was chosen.
- **F03-R4:** disabled stores excluded; unsupported stores show gaps.
- **F03-R5–R7 (SHOULD):** hide/undo, ranking, comparison as feasible.
- **F06-R1:** every result carries checked/unchecked stores.
- **F06-R2:** “not found” only when all enabled supported stores are fresh
  enough; otherwise “incomplete.”
- **F06-R3:** every skipped store has a reason.
- **F01-R4:** inventory results show FOSS/Proprietary/Unknown/Unavailable with
  explanations.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Build fixture snapshots for every match level, missing metadata, stale stores,
unsupported stores, and multiple stores carrying the same package. Add UI-state
tests for “incomplete” versus “not found” and for alternatives being suppressed
by L1/L2.

## Risks and decisions

- The specs contain F02↔F06 and F01↔F02 cycles. This session owns the shared
  `CoverageSnapshot` contract and treats inventory as the input to matching,
  not as a downstream feature.
- Define “fresh enough” explicitly and test it; do not hardcode an unexplained
  threshold.
- Alternative ranking without reliable category/tag data can become misleading.
  Show gaps rather than inventing a reason.
- Do not claim a match is verified until S10 exposes the trust state.

## Completion record

- **Implementation commit:** `b9fa0da` — `feat(matching): add S05 matching alternatives and coverage`.
- **Modules:** new pure-JVM `core:matching` (contracts, query, catalog projection/cache, ranker, engine, repository/classifier) and Compose `feature:matching` (result/detail screen, labels, UI state, view model). Wired through `AppContainer`, inventory classification, and an inventory-to-match overlay/back flow.
- **Tests:** `./gradlew :core:matching:test :feature:matching:test` passed. `core:matching` contributes 45 tests; `feature:matching` contributes 16 tests across debug and release (32 executions). Full `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint` passed (1m 32s, 683 actionable tasks): **563 test executions across 85 suites, 0 failures, 0 errors, 0 skipped**. Lint: **0 errors**; the app report has the 40 pre-existing warnings and `feature:matching` adds none.
- **Manifest:** no permission, service, or provider was added. Play still contains only `INTERNET` plus AndroidX's dynamic-receiver permission; F-Droid keeps its existing `QUERY_ALL_PACKAGES` and launcher `<queries>`.
- **Device/emulator checks not run:** live F-Droid/IzzyOnDroid sync, real `PackageManager` signer values, inventory badge refresh, and runtime behavior with large indexes remain outstanding. Matching is exercised on JVM fixtures and fake catalogs.

### F02 criteria

- **F02-R1 — complete:** package identity is checked before fuzzy signals; an exact package with license and source produces L1 and source evidence.
- **F02-R2 — complete:** L0/L1/L2/L3 are distinct in the data model and UI; only L1 shows “Same app,” while L2 and L3 are explicitly labelled as not the same app.
- **F02-R3 — complete:** the primary evidence card shows store, package, license, source URL, version, and signer; other-store matches show store, package, level, license, and source.
- **F02-R4 — complete:** missing license or source metadata downgrades the exact listing to L0 and inventory classification to `UNKNOWN`.
- **F02-R5 — complete:** stale, failed, never-synced, and unsupported enabled stores remain coverage gaps and block a conclusive “not found.” A match that is found still carries the incomplete coverage instead of hiding the gap.
- **F02-R6 — complete:** matching reads only cached verified snapshots and makes no network request; the catalog cache is keyed by payload path and bounded.
- **F02-R7 — deferred SHOULD:** reporting a wrong match belongs to F20/S12; no local report action was added.
- **F02-R8 — deferred MAY:** pinning a preferred source was not implemented; F11's preferred store remains a display preference only.

### F03 criteria

- **F03-R1 — complete:** alternatives are computed only when no L1/L2 exists and are suppressed when the feature toggle is off.
- **F03-R2 — complete:** every suggestion carries the L3 “Different app” label and the alternatives section states they are not replacements.
- **F03-R3 — complete:** each suggestion lists category, tag, and/or name/description-token reasons.
- **F03-R4 — complete:** disabled stores never contribute; unsupported and stale stores appear as coverage gaps.
- **F03-R5 — complete for the session:** hide and undo work on the current result; hidden suggestions are not persisted.
- **F03-R6 — complete:** ranking uses category (4), tag (3), and description-token (1) evidence, then display name and store id; store affiliation never adds score, and the cap is five.
- **F03-R7 — deferred MAY:** side-by-side comparison was not implemented.

### F06 and F01-R4 criteria

- **F06-R1 — complete:** every `MatchDecision` carries a `CoverageSnapshot`; when coverage is enabled, the UI lists checked, stale, failed, and skipped stores.
- **F06-R2 — complete:** “not found” requires at least one checked store and every enabled non-disabled store to be checked with fresh data. When no match is found, stale/offline/unsupported stores produce `INCOMPLETE`. Disabled stores are user choices and do not block.
- **F06-R3 — complete:** every non-checked store carries a typed reason and visible label; the model enforces the checked/reason invariant.
- **F06-R4 — deferred SHOULD:** coverage is not yet embedded in S07 share/export envelopes; S09 should include it in backup/report payloads.
- **F06-R5 — deferred SHOULD:** targeted “refresh only affected stores” was not added.
- **F06-R6 — complete:** confidence is label-first (`HIGH`/`MEDIUM`/`LOW`/`UNKNOWN`) and explained by match level, freshness, and signer/source evidence.
- **F01-R4 — production integration complete for available evidence:** inventory now calls `MatchingRepository.classify`; exact FOSS evidence yields `FOSS`, missing/partial evidence yields `UNKNOWN`, and missing/not-visible packages yield `UNAVAILABLE`. `PROPRIETARY` is not selected because no proprietary evidence source exists; absence of a FOSS listing is not treated as proof of closed source.

### Decisions and remaining gaps

- Freshness is 24 hours, injected into the engine and shown in the coverage card.
- A signer mismatch or shared upstream source URL produces L2, not L1; only L1 may be called the same app.
- Unsupported enabled stores block a conclusive “not found,” while disabled stores do not.
- Alternatives are capped at five and hidden/undone per result session only.
- Matching, alternatives, and coverage each respect their `FeatureId` toggle; matching itself remains hidden when `FeatureId.MATCHING` is disabled.
- Remaining gaps are the deferred SHOULDs above, `PROPRIETARY` evidence, and real-device/index verification. S09 can reuse the coverage snapshot and `ShareEnvelope`; S10 owns trust details.

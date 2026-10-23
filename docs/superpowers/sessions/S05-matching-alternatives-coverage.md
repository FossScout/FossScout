# S05 — F02/F03/F06 Matching, Alternatives, and Coverage

| Field | Value |
| --- | --- |
| Status | **Ready** (S02, S03, and S04 merged) |
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

Update `STATUS.md` after merge:

- commit:
- tests:
- F02/F03/F06 criteria completed:
- confidence/freshness decisions:
- remaining SHOULD gaps:

# F06 — Coverage and Confidence

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None beyond matching/cache |
| Depends on | F02, F05 |
| Related | F04, F16 |

## 1. Problem

“Not found” is ambiguous. It can mean the app is not in any enabled store, or
that a store was disabled, offline, unsupported, or never synced. Treating those
as the same produces false confidence and bad replacement advice.

## 2. User stories

- As a user, I want to know which stores were actually checked.
- As a user, I want to know when a result is based on stale data.
- As a user, I want to understand why a store could not be checked.

## 3. Scope

- Record a coverage snapshot for every audit or search.
- Show checked, skipped, stale, unsupported, and failed stores.
- Attach coverage to match results and shared reports.
- Distinguish “no match found” from “not enough coverage”.
- Provide a per-store health summary.

## 4. Non-goals

- Guaranteeing that a store contains every FOSS app.
- Hiding coverage gaps to make results look complete.
- Ranking stores by coverage alone.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F06-R1 | MUST | Every result carries a coverage snapshot. | Result detail lists checked and unchecked stores. |
| F06-R2 | MUST | “Not found” is shown only when all enabled, supported stores were checked with fresh-enough data. | A stale/offline/unsupported store changes the message to “incomplete”. |
| F06-R3 | MUST | Each skipped store has a reason. | UI shows disabled, unsupported, offline, stale, or error. |
| F06-R4 | SHOULD | Coverage is included in exported/shared reports. | Recipients can see the same gaps. |
| F06-R5 | SHOULD | The user can refresh only the stores that affected a result. | Refresh action targets those stores. |
| F06-R6 | MAY | A confidence score combines match level and coverage. | Score is explainable, not a black box. |

## 6. Data

`CoverageSnapshot`: timestamp, list of `StoreCoverage` (store ID, status, last
sync, reason), match level, confidence. No personal data.

## 7. Edge cases

- All stores disabled.
- A store syncs while a scan is running.
- Partial package visibility in the Play profile.
- A store is supported but returns zero relevant apps.
- A result comes from a cached API response.

## 8. Open questions

1. Should coverage be shown per app or once per audit?
2. What freshness threshold makes a store “fresh enough”?
3. Should confidence be numeric, labelled, or both?

# F02 — FOSS Matching

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `STORE_INDEX_NETWORK` or cached indexes |
| Depends on | F04, F05 |
| Related | F03, F06, F07 |

## 1. Problem

The user needs an honest answer to “is there a FOSS version of this app?” A
package-name match is strong evidence; a similar name or category is not. The
feature must distinguish an exact build from a fork and from a functional
alternative, and must show its evidence.

## 2. User stories

- As a user, I want to know whether the exact app I have is available as FOSS.
- As a user, I want to understand why FossScout believes a match is exact.
- As a user, I want to see “unknown” instead of a misleading “no” when data is
  incomplete.

## 3. Match levels

| Level | Meaning | Example |
| --- | --- | --- |
| **L1 — Exact package** | Same package ID is available from a FOSS source | The same app published in F-Droid |
| **L2 — Fork / upstream variant** | Different package or signer, but clearly derived from the same upstream project | A maintained fork of the same codebase |
| **L3 — Functional alternative** | Different app that solves the same job | A maps app replacing another maps app |
| **L0 — Unknown** | Not enough data to classify | Missing license or source metadata |

L1 is the only level that may be described as “the same app”. L2 and L3 must be
labelled explicitly.

## 4. Scope

- Match installed apps and favourites against enabled stores.
- Use package ID first, then source repository URL, then app identity signals.
- Record evidence: store, package, license, source URL, version, signer.
- Compute a confidence value and expose the reasons.
- Keep unknown separate from no match.

## 5. Non-goals

- Claiming an app is FOSS without a license/source record.
- Automatically installing a match.
- Ranking alternatives by ideology.
- Treating a similar description as proof of equivalence.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F02-R1 | MUST | Match by package ID before any fuzzy signal. | Same package ID from a FOSS store yields L1 with source evidence. |
| F02-R2 | MUST | Distinguish L1, L2, L3, and L0 in data and UI. | A test fixture for each level renders the correct label. |
| F02-R3 | MUST | Every match shows its evidence and source store. | Tapping “why” reveals store, package, license, and source URL. |
| F02-R4 | MUST | Missing license or source metadata produces unknown, not FOSS. | An app with no license record is classified unknown. |
| F02-R5 | MUST | A stale or unsupported store cannot produce a “no match” conclusion. | Result shows a coverage gap instead of “not available”. |
| F02-R6 | SHOULD | Matching works offline from a cached index. | Airplane mode still returns matches from the last complete snapshot. |
| F02-R7 | SHOULD | The user can report a wrong match. | A report action captures package, store, and expected result locally. |
| F02-R8 | MAY | The user can pin a preferred source for an app. | The pinned source is used for display and install suggestions. |

## 7. Data

`MatchCandidate`: app key, store ID, listing ID, level, confidence, evidence
links, license, version, signer, last seen. `MatchDecision`: chosen candidate,
reason, timestamp, coverage snapshot.

## 8. Edge cases

- Same package ID with a different signing certificate.
- F-Droid build lagging behind the upstream release.
- App renamed but package unchanged.
- Package collision between unrelated apps.
- Fork with a different package ID and no explicit upstream link.
- License changed between versions.
- Anti-features or proprietary dependencies in an otherwise FOSS app.

## 9. Open questions

1. Which signals are strong enough for L2: source URL, upstream repo field,
   signing certificate, or maintainer declaration?
2. Should confidence be a numeric score or an enumerated label?
3. How should an app with mixed licensing be shown?

# F03 — Alternative Suggestions

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `STORE_INDEX_NETWORK` or cached indexes |
| Depends on | F02, F04, F05 |
| Related | F06, F07 |

## 1. Problem

Many proprietary apps have no exact FOSS build. The user still needs a useful
next step: a different app that does the same job. That is a suggestion, not a
replacement, and must never be presented as equivalent.

## 2. User stories

- As a user, when there is no exact FOSS version, I want to see realistic
  alternatives.
- As a user, I want to know what I would lose by switching.
- As a user, I want to dismiss bad suggestions so they do not keep appearing.

## 3. Scope

- Generate candidates only after F02 reports no L1/L2 match.
- Use category, tags, description similarity, permissions, and user feedback.
- Show up to a small, ranked set; explain the basis.
- Label every result “alternative”, not “replacement”.
- Let the user hide or report a suggestion.

## 4. Non-goals

- Guaranteeing feature parity.
- Migrating app data automatically.
- Suggesting apps that are not installable from an enabled source.
- Ranking by popularity alone.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F03-R1 | MUST | Alternatives appear only when no exact/fork match exists. | An app with L1 does not show the alternatives section by default. |
| F03-R2 | MUST | Every alternative is labelled as a different app. | The label is present in list and detail views. |
| F03-R3 | MUST | Each suggestion states why it was chosen. | At least category/tag/description evidence is visible. |
| F03-R4 | MUST | Suggestions respect enabled stores and coverage gaps. | Disabled stores are not used; unsupported stores are shown as gaps. |
| F03-R5 | SHOULD | The user can hide a suggestion and undo it. | Hidden suggestions disappear and can be restored. |
| F03-R6 | SHOULD | The list is capped and ranked by relevance, not store affiliation. | No store is preferred solely because it is FOSS or proprietary. |
| F03-R7 | MAY | The user can compare basic attributes side by side. | Comparison shows category, license, source, and notable differences. |

## 6. Data

Candidate: app key, store, category, tags, license, source, score, reasons,
user feedback. No personal data.

## 7. Edge cases

- No plausible alternative.
- Hundreds of weak candidates.
- An alternative with a restrictive license or proprietary dependencies.
- A discontinued project.
- An app that is itself a fork of the original.

## 8. Open questions

1. Should alternatives be generated from the full catalog or only installed-store
   indexes?
2. How much weight should permissions and offline capability carry?
3. Should the user be able to request “show me more” beyond the initial set?

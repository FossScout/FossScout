# F21 — Trust Badges

| Field | Value |
| --- | --- |
| Status | Proposed |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | Store index metadata; signer verification where available |
| Depends on | F04, F05, F14 |
| Related | F03, F07, F19 |

## 1. Problem

A FOSS label is not enough to decide whether a build is trustworthy or suitable.
Users need compact, explainable signals for anti-features, reproducible builds,
signer continuity, and metadata freshness. Those signals should inform a choice
without pretending to be a single universal score.

## 2. User stories

- As a user, I want to see F-Droid anti-features before choosing an app.
- As a user, I want to know whether a build is reproducibly verified.
- As a user, I want a warning when an app's signing key changes.
- As a user, I want to know how old the metadata is.
- As a user, I want to tap a badge and read what it means.

## 3. Badge types

| Badge | Source | Meaning |
| --- | --- | --- |
| Anti-feature | F-Droid index `antiFeatures` | The app has a declared limitation such as ads or tracking. |
| Reproducible | F-Droid verification metadata, if available | The published APK matches a build from source. |
| Signer changed | Local signer history plus store metadata | The signing certificate differs from the last observed one. |
| Stale metadata | Local cache timestamp | The index has not been refreshed recently. |
| Link-only | Store registry | The store cannot provide structured metadata. |

## 4. Scope

- Show badges on search results, app detail, and comparison views.
- Explain each badge and link to its source.
- Use neutral language; do not imply a badge is a guarantee.
- Keep signer history local by default.
- Distinguish “not verified” from “verification failed”.
- Show the cache age next to store-derived badges.

## 5. Non-goals

- A single opaque trust score.
- Claiming an app is malware-free.
- Uploading installed APKs or signer data.
- Replacing the user's own judgment or the store's security model.
- Showing a badge when the underlying source is missing.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F21-R1 | MUST | Anti-features from a store index are shown with their source. | A badge opens a short explanation and names the store. |
| F21-R2 | MUST | Reproducible-build status is shown only when the store provides evidence. | Missing evidence shows no positive badge. |
| F21-R3 | MUST | A signer change is a warning, not a silent update. | The previous and current certificate fingerprints are available. |
| F21-R4 | MUST | “Not verified” and “verification failed” are distinct states. | The UI never renders both as the same badge. |
| F21-R5 | MUST | Store-derived badges show metadata age. | A stale index is visibly marked. |
| F21-R6 | SHOULD | Link-only stores show a link-only badge and no invented metadata. | No anti-feature or reproducibility claim appears for them. |
| F21-R7 | SHOULD | Badge explanations are accessible to screen readers. | Content descriptions include badge meaning. |
| F21-R8 | MAY | Badges can be filtered in search. | The filter matches only apps with the selected badge. |

## 7. Edge cases

- Anti-feature metadata missing from an older index.
- Reproducible status available for one version but not the current one.
- Signer history absent on first install.
- Multiple signers or certificate rotation.
- A store uses a different package ID for the same app.
- A badge source is removed or its license changes.

## 8. Open questions

1. Which store provides the first reproducible-build signal we can rely on?
2. How long should local signer history be retained?
3. Should badges be enabled by default in the Play profile?

# F21 — Trust Badges

| Field | Value |
| --- | --- |
| Status | **Implemented** (device/UI smoke pending) |
| Availability | **General** |
| Profiles | Enabled in `vanilla`; hidden in `playstore`; `direct`/`custom` remain unspecified |
| Required capabilities | Store index metadata; `Capability.STORE_TRUST_BADGES`; on-demand F-Droid Verification Server access for reproducible checks |
| Depends on | F04, F05, F14 |
| Related | F03, F07, F18, F19 |

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
| Anti-feature | Per-version `antiFeatures` in the F-Droid v2 index | The app version declares a limitation such as ads or tracking. |
| Index status | Store snapshot signature status | The index is verified, unverified, failed, or not checked. |
| Source and license | Store metadata | Both a source URL and a license are present, or one is missing. |
| Reproducible | F-Droid Verification Server report, fetched only on demand | The published APK matches a build from source. |
| Not verified / failed | On-demand verification report | No successful report exists, or a report explicitly failed. These are separate states. |
| Signer changed | Latest and previous version in the cached store index | The signing certificate set differs from the previous cached version. |
| Stale metadata | Local cache timestamp | The cached index has not been refreshed recently. |
| Link-only | Store registry | The store cannot provide structured metadata. |

There is deliberately no aggregate trust score. A positive badge is never shown
when its underlying evidence is missing. Signer and verification evidence remains
local; the app never uploads installed-app inventory, APKs, or signer data.

## 4. Scope

- Show badges on store-search results, the store-details dialog, and match
  evidence cards.
- Explain each badge and link to its source.
- Use neutral language; do not imply a badge is a guarantee.
- Keep signer evidence local. Compare the latest and previous version in the
  cached index; do not persist a separate signer history.
- Distinguish “not verified”, “verification failed”, and “verification
  unavailable”. Only the first two produce a status badge.
- Show the cache age next to store-derived badges.
- Gate badges with `FeatureId.TRUST_BADGES` and
  `Capability.STORE_TRUST_BADGES`. The Play profile has no trust-badge
  capability; the vanilla/F-Droid profile enables the feature by default.
- Fetch F-Droid reproducibility evidence only after an explicit “Check
  reproducibility” action. Do not make background or automatic verification
  requests. Cache verified, failed, and not-verified results for the app session;
  do not cache unavailable results, so the user can retry.
- Disclose verification-server requests in the privacy policy.

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
| F21-R8 | MAY | Badges can be filtered in search. | The filter matches only apps with the selected badge. Deferred; existing store trust filters remain. |
| F21-R9 | MUST | Trust badges are gated by profile. | Play builds expose no trust-badge feature or badge UI; vanilla builds enable badges by default. |
| F21-R10 | MUST | Reproducibility checks are explicit and on demand. | No request is made until the user taps the check action. Results are cached for the session; unavailable results remain retryable. |
| F21-R11 | MUST | Signer evidence stays local. | Signer fingerprints are shown in badge explanations and the match evidence, and are never uploaded. |

## 7. Edge cases

- Older cached payloads may only carry anti-features on package metadata. The
  catalog uses version-level anti-features when present and falls back to package
  metadata only for those older payloads.
- A verification report may exist for one version and not the current one.
  A missing report is “not verified”, not “failed”.
- A network error, non-success HTTP status, or malformed report is
  “unavailable”; it produces no badge and remains retryable.
- Multiple verification reports may exist. Any report with `verified: true` is
  treated as verified evidence.
- The previous signer is absent on the first observed version, so no signer-change
  warning is shown.
- Multiple signers are compared as certificate sets, ignoring order.
- A store uses a different package ID for the same app; badges remain scoped to
  the store hit and version.
- Link-only stores show only the link-only badge and never invent metadata.
- A badge source is removed or its license changes; stale and link-only states
  remain explicit rather than being replaced by a positive claim.

## 8. Resolved decisions (2026-11-19)

1. Reproducible-build evidence comes from the public F-Droid Verification Server
   per-version JSON report, fetched only on demand.
2. Signer changes compare the latest and previous version already present in the
   cached F-Droid index. No additional persistent signer history is created.
3. Badges are disabled by default in Play and enabled by default in
   vanilla/F-Droid.
4. Verified, failed, and not-verified verification results are cached for the session;
   unavailable or malformed results are not cached. No automatic requests are
   made.
5. The live F-Droid v2 index stores anti-features on each version, so the parser
   reads them there.
6. Existing trust filters remain. A badge-specific search filter is deferred.

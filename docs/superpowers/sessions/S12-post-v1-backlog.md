# S12 — Post-v1 and Restricted Backlog

| Field | Value |
| --- | --- |
| Status | **Post-v1 — do not implement in a v1 session** |
| Features | F09, F10, F15, F18, F19, F21 (F20 merged as S18) |
| Depends on | Separate product decisions and prerequisite sessions |
| Profiles | Depends on feature; F09/F10 are restricted builds only |

This is a backlog map, not an executable session. Each item needs its own
approved brief before work starts.

## F09 — Standalone store client

**Why deferred:** v1 does not download or install APKs; Play policy and the
user decision (D21) keep it out.

**Prerequisites:** F04/F05, the F14 APK verification path, a `direct`/restricted
profile with `DIRECT_APK_INSTALL`, download/resume, FileProvider, installer
intents, signer continuity, and cleanup policy.

**Must decide:** split APK support, update manager scope, retention, and whether
`direct` is a release artifact.

**Suggested future session:** S13 — standalone download/install for a restricted
profile. Do not add `REQUEST_INSTALL_PACKAGES` to Play.

## F10 — Install-time suggestion

**Why deferred:** requires an AccessibilityService and a separate policy review;
v1 has no accessibility declaration.

**Prerequisites:** F01/F02/F08, restricted module, disclosure/consent, per-app
suppression, and a decision on overlay vs notification.

**Must decide:** store flows in scope, trigger threshold, dismiss behaviour, and
whether Play can ever ship a non-accessibility equivalent.

**Suggested future session:** S14 — restricted accessibility prompt. Keep the
service out of the Play manifest and test that exclusion.

## F15 — Update tracking

**Why deferred:** v1 does not background-check or install updates.

**Prerequisites:** F01/F05/F06, notification channel, scheduler, off-by-default
settings, and stale-coverage handling.

**Must decide:** notification vs in-app only, check frequency, metered/charging
rules, snooze, and Play handoff behaviour.

**Suggested future session:** S15 — opt-in update notifications.

## F18 — Unified store search

**Recommendation:** S16 is the recommended next feature, subject to approval and
an approved brief.

**Prerequisites:** F04/F05 and a local snapshot query layer.

**Must decide:** grouping preference, anti-feature defaults, source-repository
results, and link-only handoff presentation.

**Suggested future session:** S16 — local unified search over cached indexes.

## F19 — Permission and tracker diff

**Status:** Implemented in S17, the F19 tracker-signature follow-up, the
F19-R8 audit export, and local alternative-APK manifest analysis. The permission
comparison, local tracker matching, local manifest import, and local JSON audit
export are complete.

**Prerequisites:** F01/F03/F06 and installed permission metadata.

**Decisions:** the bundled tracker dataset is the Exodus Privacy ETIP snapshot
`2026-11-10` (674 trackers / 987 signatures) under ODbL 1.0/DbCL 1.0. The app
scans only the selected installed app's local base/split APK `classes*.dex`
files, capped at 64 MiB; it never downloads or scans the store alternative. The
optional manifest import reads only an APK the user selects, through a
temporary cache copy capped at 128 MiB; it rejects package mismatches and does
not install, execute, or signature-verify the APK. It does not infer runtime
tracking, upload scan data, or persist match results. Dataset update and scan
policy are documented in [`docs/TRACKER_DATASET.md`](../../TRACKER_DATASET.md).

**Deferred:** Real-device/UI verification of the tracker section, APK picker,
and audit export.

## F20 — Matching correction

**Status:** Merged as S18 in PR #2 (`9e55acf`); that PR also carried S11 because
local `main` was ahead of `origin/main`.

**Prerequisites:** F02/F06 and F12/F13 for export/backup.

**Decisions:** one override per installed package key; target search over cached
enabled-store catalogs; a no-match is stored as a null target; notes are
private; backup schema 2 carries overrides with v1 checksum migration. Merge
keeps the newer `updatedAtEpochMillis`, replace removes local-only overrides,
and skip adds only missing overrides.

**Deferred:** store-scoped overrides (R8), shareable correction files,
low-confidence prompts, profile-scoped duplicates, and device/UI verification.

## F21 — Trust badges

**Prerequisites:** F04/F05/F14 and signer history.

**Must decide:** reproducible-build evidence source, badge taxonomy, retention of
signer history, and accessibility wording.

**Suggested future session:** S19 — explainable trust badges.

## Profile work not yet scheduled

- `direct` profile: decide whether it is a release artifact or testing profile.
- `custom` profile: define capability sets and release metadata.
- Source-repository adapters: technical feasibility and scope.

## Promotion rule

A post-v1 feature may become a session only when:

1. its product scope is marked **Decided**;
2. its prerequisites are merged and reflected in `STATUS.md`;
3. a new brief defines acceptance criteria, tests, and profile boundaries;
4. the Play manifest remains free of restricted capabilities.

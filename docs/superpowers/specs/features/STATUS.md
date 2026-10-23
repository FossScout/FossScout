# FossScout Implementation Status

Last updated: **2026-10-23**
Baseline: `feat/s01-feature-configuration` at `6949df7` (F17 feature configuration), based on `main` at `a4861f7`
Feature sessions: [`docs/superpowers/sessions/README.md`](../../sessions/README.md)
Requirements: [`README.md`](README.md), [`F01`](F01-app-inventory.md) through [`F21`](F21-trust-badges.md)

This file is the **implementation** tracker. The feature matrix in `README.md`
records planned availability; it does not mean a feature is built. Update this
file after every feature session with the new commit, test result, and status.

## Status legend

| Status | Meaning |
| --- | --- |
| **Done** | Implemented, tested, and matches the v1 scope of its spec. |
| **Partial** | A usable subset exists; at least one MUST or acceptance criterion is still missing. |
| **Registered** | A feature descriptor/profile entry exists, but there is no feature behavior. |
| **Model only** | A data class exists, but there is no repository, UI, or behavior. |
| **Deferred** | Intentionally outside v1 and excluded from the v1 profiles. |
| **Spec only** | Post-v1 design; no feature ID or implementation. |

## Summary

| Area | Status |
| --- | --- |
| Modular foundation | **Done** |
| Build profiles (`playstore`, `fdroid`) | **Done** for these two; `direct`/`custom` not implemented |
| Local favourites and notes | **Partial** |
| Feature configuration | **Done** |
| Store, index, inventory, matching | **Not implemented** |
| Sharing, backup, security, contribution | **Not implemented** |
| Feature completion | **1 of 21 fully done; 1 partial; 12 registered/model-only; 3 deferred; 4 spec-only** |

**Recommended next sessions:** [S02](../../sessions/S02-store-registry.md),
[S04](../../sessions/S04-installed-inventory.md), and
[S06](../../sessions/S06-favourites-completion.md) can run in parallel. S03
waits for S02; S05 waits for S03 and S04; S09 waits for S06/S07.

## What works today

- Bundled project catalog with search and category filters.
- Locally persisted favourites with private notes, duplicate handling, note
  length validation, and visible read/write error states.
- Runtime feature toggles backed by a versioned DataStore envelope, with
  migration from the legacy `disabled_features` preference.
- Descriptor-declared feature settings (boolean and text) with defaults and
  validation, plus basic/advanced sections, per-feature reset, global reset, and
  a read-only versioned snapshot for F13.
- `FeatureRegistry` is the single source of availability and enabled state;
  build profiles supply capabilities only.
- Capability-based `playstore` and `fdroid` profiles with separate application
  IDs (`com.fossscout.app`, `com.fossscout.app.fdroid`).
- Policy-safe “unavailable in this build” explanations and capability labels for
  restricted features.
- Play-safe manifest: no install-packages permission, broad package visibility,
  or accessibility service.
- Material 3 UI, light/dark theme, About screen, and unit tests for the current
  data/domain/settings behaviour.

## Feature status

| ID | Feature | Status | What exists / what is missing | Planned session |
| --- | --- | --- | --- | --- |
| F01 | Installed app inventory | **Registered** | Descriptor and profile capability only. No `PackageManager` source, manual picker, full scan, snapshot, filters, or stale timestamp. | S04, S05 |
| F02 | FOSS matching | **Registered** | Descriptor only. No match levels, evidence, matching engine, or UI. | S05 |
| F03 | Alternative suggestions | **Registered** | Descriptor only. No ranking, reasons, or alternative cards. | S05 |
| F04 | Store registry and adapters | **Registered** | Descriptor only. No store model, adapters, built-in stores, custom-store UI, or descriptor export. | S02, S03 |
| F05 | Index sync and offline cache | **Registered** | Descriptor only. No network layer, snapshots, freshness, cancellation, or storage budget. | S03 |
| F06 | Coverage and confidence | **Registered** | Descriptor only. No coverage snapshot, skipped-store reasons, or incomplete-result state. | S05 |
| F07 | Replacement guidance | **Registered** | Descriptor only. No signer/package comparison, update/replace/alternative labels, or warning UI. | S08 |
| F08 | Companion store handoff | **Registered** | Descriptor only. No store detection, app-page intent, repository-add flow, or no-store fallback. | S08 |
| F09 | Standalone store client | **Deferred** | Excluded from both v1 profiles. Requires F05, F14, and `DIRECT_APK_INSTALL`. | S12 |
| F10 | Install-time suggestion | **Deferred** | Excluded from both v1 profiles. Requires matching, handoff, and the accessibility module. | S12 |
| F11 | Favourites and notes | **Partial** | Add/remove, private notes, persistence, duplicate handling, note limit, and error states work. Tags/preferred store are modeled but not exposed; no favourites search, installed badge, or collection integration. | S06, S07 |
| F12 | Collections and sharing | **Model only** | `AppCollection` exists but has no schema version, persistence, repository, UI, sharing, or import. | S07 |
| F13 | Backup and migration | **Registered** | Descriptor only. No backup envelope, manifest, export/import, conflict handling, or encryption. | S09 |
| F14 | Security verification | **Registered** | Descriptor only. No fingerprint pinning, index signature verification, trust records, or security details UI. | S03, S10 |
| F15 | Update tracking | **Deferred** | Not enabled in either v1 profile. | S12 |
| F16 | Contribution hooks | **Registered** | Descriptor only. No report builder, redaction, preview, or sharing. | S11 |
| F17 | Feature configuration | **Done** | Descriptor settings model (boolean/text with defaults and validation), versioned envelope with legacy migration, registry-authoritative availability, basic/advanced sections, per-feature/global reset, and read-only snapshot for F13. No production feature declares a setting yet; backup import/export UI is S09. | S01 |
| F18 | Unified store search | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F19 | Permission and tracker diff | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F20 | Matching correction | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F21 | Trust badges | **Spec only** | Post-v1. No feature ID or code. | S12 |

## Verification snapshot

Commands run on `feat/s01-feature-configuration` at `6949df7` on 2026-10-23:

| Check | Result |
| --- | --- |
| `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` | **Passed** |
| Unit tests | **117 test executions across 21 suites; 0 failures, 0 errors, 0 skipped** (debug and release variants counted separately) |
| `assemblePlaystoreDebug` | **Passed** |
| `assembleFdroidDebug` | **Passed** |
| Restricted manifest scan | **Passed**: the Play merged manifest contains no `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, `AccessibilityService`, or `BIND_ACCESSIBILITY_SERVICE` |
| `./gradlew lint` | **Passed** (2m 6s): 0 errors, 37 warnings (existing dependency-version and redundant-label warnings). The previously reported Maven resolution block did not recur in this run. |

Debug builds may warn that native symbols for `libandroidx.graphics.path.so`
and `libdatastore_shared_counter.so` cannot be stripped. This is a debug-build
warning, not a failing check.

## Known gaps and blockers

### Product scope

- The master design still marks the v1 feature scope **proposed** and the
  feature files are Draft/Proposed. The session plan records the planning
  assumptions used until the user confirms or changes them.
- The master design's candidate numbering (`F1`–`F24`) does **not** match the
  feature files (`F01`–`F21`). Sessions and code must use the `F01`–`F21` files.
- F07 is listed in v1 but depends on F09, which is deferred. The planned v1
  scope is guidance and handoff only.
- F14 contains MUSTs for APK hash and signer checks that need F09. The planned
  v1 scope is repository/index verification, HTTPS, fingerprint pinning, and
  trust details.
- Play package visibility is not finally decided. The planning assumption is
  manual/selected inventory on Play and no `QUERY_ALL_PACKAGES`; F-Droid may
  perform the full scan.
- Only `playstore` and `fdroid` profiles exist. `direct` and `custom` remain
  future profiles.

### Architecture and data

- F01/F02, F02/F06, F04/F05, and F05/F14 have circular dependencies in the
  specs. Shared contracts must be defined before the dependent features.
- No store, snapshot, sync, inventory, matching, coverage, or verification
  models exist.
- `AppCollection` has no schema version or repository.
- `AppKey` has no documented canonicalization rule for source-only apps.
- No production feature declares a `FeatureSetting` yet. The generated
  basic/advanced UI and reset flow are exercised by descriptor-driven tests; the
  Advanced section appears when a future feature declares a real advanced
  setting.
- The settings envelope stores disabled IDs plus per-feature values. A future
  feature whose `defaultEnabled` is false and that users can opt into will need
  an explicit enabled-override field; no current descriptor uses that case.
- Settings import/export UI remains part of F13/S09. S01 only exposes the
  read-only snapshot.
- F17-R8 development availability overrides (MAY) are not implemented.
- Settings toggles for unimplemented features currently have no observable
  effect. This is expected at this stage but should not be presented as a
  shipped capability.

## Status update protocol

After a feature session is merged:

1. Update the baseline commit and date at the top of this file.
2. Update the affected feature row and the summary counts.
3. Record the exact test/build commands and any new environment caveats.
4. Add or remove blockers.
5. Link the session file if the work was split further.

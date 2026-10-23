# FossScout Implementation Status

Last updated: **2026-10-23**
Baseline: `main` at `ae84373` (S01 feature configuration, S02 store registry, S03 index sync/verification, S04 installed inventory, and S06 favourites completion merged)
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
| Local favourites, tags, search, and installed badge | **Partial** (collections are S07) |
| Feature configuration | **Done** |
| Store registry and installed inventory | **Partial** |
| Index sync and offline cache | **Done** (live network/device verification pending) |
| Matching, coverage, security details | **Not implemented** |
| Sharing, backup, contribution | **Not implemented** |
| Feature completion | **2 of 21 fully done; 4 partial; 8 registered/model-only; 3 deferred; 4 spec-only** |

**Recommended next sessions:** [S05](../../sessions/S05-matching-alternatives-coverage.md)
and [S07](../../sessions/S07-collections-sharing.md) can run in parallel now.
S08/S10/S11 wait for S05; S09 waits for S07.

## What works today

- Bundled project catalog with search and category filters.
- Locally persisted favourites with private notes, duplicate handling, note
  length validation, and visible read/write error states.
- Runtime feature toggles backed by a versioned DataStore envelope, with
  migration from the legacy `disabled_features` preference.
- Descriptor-declared feature settings (boolean, text, and integer) with
  defaults and validation, plus basic/advanced sections, per-feature reset,
  global reset, and a read-only versioned snapshot for F13.
- `FeatureRegistry` is the single source of availability and enabled state;
  build profiles supply capabilities only.
- Capability-based `playstore` and `fdroid` profiles with separate application
  IDs (`com.fossscout.app`, `com.fossscout.app.fdroid`).
- Local store registry with verified F-Droid and IzzyOnDroid descriptors,
  custom-store add/edit/enable/reorder/delete, link-only fallback, explicit
  HTTP opt-in, and versioned descriptor export.
- Verified F-Droid v2 index sync: signed `entry.jar` and signer-fingerprint
  checks, signed index/diff SHA-256 and size checks, HTTPS-by-default, ETag/diff
  reuse, streaming parse into bounded local snapshots, cancellation-safe atomic
  replacement, freshness/last-sync state, and a 16–2048 MB storage-budget
  setting.
- Local installed-app inventory with manual package/label/store-link lookup on
  both profiles, F-Droid-only full scan, persisted complete snapshots, stale
  marking, system/disabled/work-profile/hide filters, and a classification
  contract ready for S05.
- Favourites tags, local search across names/notes/tags, optional preferred
  store, and a read-time installed badge with an explanation.
- Policy-safe “unavailable in this build” explanations and capability labels for
  restricted features.
- Play-safe manifest: no install-packages permission, broad package visibility,
  or accessibility service. Broad package visibility is confined to the F-Droid
  flavor.
- Material 3 UI, light/dark theme, About screen, and unit tests for the current
  data/domain/settings/store/inventory/favourites/index behaviour.

## Feature status

| ID | Feature | Status | What exists / what is missing | Planned session |
| --- | --- | --- | --- | --- |
| F01 | Installed app inventory | **Partial** | Manual package/label/store-link lookup, F-Droid-only full scan, `PackageManager` source, persisted complete snapshots, stale marking, filters, hide list, classification contract, and inventory UI exist. Production classification still returns `UNKNOWN` until S05; real device/emulator `PackageManager`, installer/signer/work-profile, shared-intent, and process-restart checks remain. | S04, S05 |
| F02 | FOSS matching | **Registered** | Descriptor only. No match levels, evidence, matching engine, or UI. | S05 |
| F03 | Alternative suggestions | **Registered** | Descriptor only. No ranking, reasons, or alternative cards. | S05 |
| F04 | Store registry and adapters | **Partial** | Pure-Kotlin store/adapter contracts, verified built-in F-Droid and IzzyOnDroid descriptors, versioned registry persistence, custom-store CRUD, link-only fallback, HTTP opt-in, descriptor export, Stores UI, real cached-index adapter, and F04-R2 post-sync usability exist. Health history and import preview remain. | S02, S03 |
| F05 | Index sync and offline cache | **Done** | Signed F-Droid v2 sync, signer/hash/size verification, HTTPS policy, ETag/diff reuse, streaming parse, atomic snapshot replacement, cancellation safety, freshness/last-sync state, and bounded storage with a real integer setting. Live network and device/UI verification remain outstanding. | S03 |
| F06 | Coverage and confidence | **Registered** | Descriptor only. No coverage snapshot, skipped-store reasons, or incomplete-result state. | S05 |
| F07 | Replacement guidance | **Registered** | Descriptor only. No signer/package comparison, update/replace/alternative labels, or warning UI. | S08 |
| F08 | Companion store handoff | **Registered** | Descriptor only. No store detection, app-page intent, repository-add flow, or no-store fallback. | S08 |
| F09 | Standalone store client | **Deferred** | Excluded from both v1 profiles. Requires F05, F14, and `DIRECT_APK_INSTALL`. | S12 |
| F10 | Install-time suggestion | **Deferred** | Excluded from both v1 profiles. Requires matching, handoff, and the accessibility module. | S12 |
| F11 | Favourites and notes | **Partial** | Add/remove, private notes, persistence, duplicate handling, note limit, tags, local search across names/notes/tags, optional preferred store, installed-state badge, and error states work. Collections integration remains S07; the badge does not refresh live while the screen stays foregrounded. | S06, S07 |
| F12 | Collections and sharing | **Model only** | `AppCollection` exists but has no schema version, persistence, repository, UI, sharing, or import. | S07 |
| F13 | Backup and migration | **Registered** | Descriptor only. No backup envelope, manifest, export/import, conflict handling, or encryption. | S09 |
| F14 | Security verification | **Partial** | Repository fingerprint pinning/change blocking, index JAR signature verification, signed index/diff hash and size checks, HTTPS policy, and local trust records exist. Security-details/trust-audit UI is S10; APK hash/signer continuity (R3/R4) is deferred with F09. | S03, S10 |
| F15 | Update tracking | **Deferred** | Not enabled in either v1 profile. | S12 |
| F16 | Contribution hooks | **Registered** | Descriptor only. No report builder, redaction, preview, or sharing. | S11 |
| F17 | Feature configuration | **Done** | Descriptor settings model (boolean/text/integer with defaults and validation), versioned envelope with legacy migration, registry-authoritative availability, basic/advanced sections, per-feature/global reset, and read-only snapshot for F13. `INDEX_CACHE` declares the real `storage_budget_mb` setting; backup import/export UI is S09. | S01 |
| F18 | Unified store search | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F19 | Permission and tracker diff | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F20 | Matching correction | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F21 | Trust badges | **Spec only** | Post-v1. No feature ID or code. | S12 |

## Verification snapshot

Commands run on `main` at `ae84373` on 2026-10-23 after merging S01, S02, S03, S04, and S06:

| Check | Result |
| --- | --- |
| `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` | **Passed** (4m 34s) |
| Unit tests | **388 test executions across 62 suites; 0 failures, 0 errors, 0 skipped** (debug and release variants counted separately) |
| `assemblePlaystoreDebug` | **Passed** |
| `assembleFdroidDebug` | **Passed** |
| Play restricted manifest scan | **Passed**: only `INTERNET` and the framework dynamic-receiver permission were added; no `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, `AccessibilityService`, or `BIND_ACCESSIBILITY_SERVICE` |
| F-Droid manifest scan | **Expected**: contains `QUERY_ALL_PACKAGES` and a launcher-intent `<queries>` block for the full inventory scan |
| `./gradlew lint` | **Passed** (2m 28s): 0 errors, 40 warnings. The previously reported Maven resolution block did not recur. |

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
- Play package visibility is not finally decided. The implementation now follows
  the planning assumption: manual/selected inventory on Play with no
  `QUERY_ALL_PACKAGES`; F-Droid performs the full scan. A Play policy review and
  device verification are still required before release.
- Only `playstore` and `fdroid` profiles exist. `direct` and `custom` remain
  future profiles.

### Architecture and data

- F01/F02, F02/F06, F04/F05, and F05/F14 have circular dependencies in the
  specs. Shared contracts must be defined before the dependent features.
- Store registry, installed inventory, and verified index sync/cache exist.
  Matching, coverage, and security-details UI do not.
- F01's production classifier still returns `UNKNOWN`; S05 must replace it
  without changing the inventory contract.
- F04's cached-index adapter is real, but health history and import preview
  remain. Live sync against the upstream F-Droid/IzzyOnDroid indexes has not
  been exercised from a device or emulator.
- A valid signed index with no pinned fingerprint can activate as `UNVERIFIED`.
  The Stores UI shows the trust evidence, but custom-store pin review remains a
  product/security decision.
- Android `PackageManager` behaviour is covered by JVM fakes only. Real
  enumeration, package visibility, installer/signer/work-profile values,
  shared-link handling, stale rendering, and hide-list survival across process
  restart still need device/emulator verification.
- F11's installed badge is advisory and does not refresh live while the Saved
  screen stays foregrounded through an external install/uninstall.
- The app now exposes six bottom-navigation destinations. This is functional but
  needs a UX review before release; Inventory or Stores may belong in a
  secondary surface.
- `AppCollection` has no schema version or repository.
- `AppKey` has no documented canonicalization rule for source-only apps.
- `INDEX_CACHE` declares the real integer `storage_budget_mb` setting. Other
  features do not declare settings yet, so the Advanced section is sparse until
  future features add real settings.
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

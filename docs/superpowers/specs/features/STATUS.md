# FossScout Implementation Status

Last updated: **2026-10-27**
Baseline: `main` after S11 docs commit `1bc04a9` (S01 feature configuration, S02 store registry, S03 index sync/verification, S04 installed inventory, S05 matching/alternatives/coverage, S06 favourites completion, S07 collections/sharing, S08 replacement guidance/companion handoff, S09 backup/migration, S10 security details/trust audit, and S11 contribution hooks merged)
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
| Local favourites, tags, search, installed badge, and collections | **Done** (device badge refresh pending) |
| Feature configuration | **Done** |
| Store registry and installed inventory | **Partial** |
| Index sync and offline cache | **Done** (live network/device verification pending) |
| Matching, alternatives, coverage | **Done** (device/index verification pending) |
| Replacement guidance and companion handoff | **Done** (device/store-client verification pending) |
| Security details and trust audit | **Done** (S10; APK checks deferred with F09) |
| Sharing, backup, contribution | **Done** (sharing/import, backup, and reviewed contribution reports; device checks pending) |
| Feature completion | **12 of 21 fully done; 2 partial; 3 deferred; 4 spec-only** |

**Recommended next session:** No v1 implementation session remains. [S12](../../sessions/S12-post-v1-backlog.md) is the post-v1 backlog.

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
  marking, system/disabled/work-profile/hide filters, and production
  FOSS/Unknown/Unavailable classification through the matching engine.
- FOSS matching over cached indexes: exact package L1, signer/source-derived
  L2, functional alternatives L3, missing-metadata L0, evidence cards, offline
  operation, and inventory classification integration.
- Coverage and confidence: per-store checked/stale/failed/skipped reasons,
  24-hour freshness, conclusive “not found” only when every enabled non-disabled
  store was checked, and explicit “incomplete” results otherwise.
- Alternative suggestions with visible category/tag/description reasons, a cap
  of five, relevance ranking, and session-only hide/undo.
- Guidance-only replacement comparison: package/signer/version/installer
  checks, update/replace/alternative/no-update/cannot-verify labels, explicit
  data-loss/uninstall/system/other-store/cross-store warnings, and a required
  confirmation for signer-mismatch replacements.
- Companion handoff: explicit F-Droid/Droid-ify/Neo Store detection, an explicit
  `market://` app page, one canonical repository descriptor for add/QR/copy,
  separate URL and fingerprint copy, per-app preferred-client persistence, and a
  browser fallback with no install or uninstall path.
- Favourites tags, local search across names/notes/tags, optional preferred
  store, a read-time installed badge with an explanation, versioned collections,
  and local JSON/text/file sharing and import with notes excluded by default.
- Versioned full-profile backup and migration: visible included/excluded
  manifest, SHA-256 integrity check, merge/replace/skip restore preview,
  custom-store fingerprint/trust preservation, optional PBKDF2/AES-GCM
  passphrase encryption, share-sheet export, document-picker import, and
  DataStore-aware Android auto-backup rules.
- Store security details and trust audit: distinct verified/unverified/failed/
  rotation/not-checked states, pinned and last-verified fingerprints, index
  signer/signature/SHA-256/version, verification and index sources, HTTP opt-in
  state, last successful sync, failure reason, and a bounded 100-entry local
  verification history. A changed or removed pin, or a signer mismatch, blocks
  sync until the user approves the new fingerprint or removes the store.
  Match/detail evidence shows index trust and explicitly says APK hash and
  signer continuity are not checked in v1.
- Reviewed contribution reports for missing stores, wrong matches, and adapter
  requests: a preview gate, redacted payload and issue URL, offline clipboard
  and share actions, opt-in redacted debug JSON, and no automatic upload or new
  network permission.
- Policy-safe “unavailable in this build” explanations and capability labels for
  restricted features.
- Play-safe manifest: no install-packages permission, broad package visibility,
  or accessibility service. Explicit package queries cover the supported store
  clients; broad package visibility is confined to the F-Droid flavor.
- Material 3 UI, light/dark theme, About screen, and unit tests for the current
  data/domain/settings/store/inventory/favourites/index/matching/share/handoff/
  backup/contribution behaviour.

## Feature status

| ID | Feature | Status | What exists / what is missing | Planned session |
| --- | --- | --- | --- | --- |
| F01 | Installed app inventory | **Partial** | Manual package/label/store-link lookup, F-Droid-only full scan, `PackageManager` source, persisted complete snapshots, stale marking, filters, hide list, and inventory UI exist. Production classification now selects `FOSS` only for an exact package with license and source evidence; everything else stays `UNKNOWN` or `UNAVAILABLE`. `PROPRIETARY` still needs a proprietary evidence source. Real device/emulator `PackageManager`, installer/signer/work-profile, shared-intent, and process-restart checks remain. | S04, S05 |
| F02 | FOSS matching | **Done** | Pure-JVM matching engine with L0–L3, package identity first, source-URL/signer-derived L2, missing-metadata L0, offline cached indexes, evidence cards (store, package, license, source, version, signer), and inventory classification integration. Report-wrong-match (R7) is covered by the S11 contribution action; pinned source (R8) remains deferred to F20. | S05, S11 |
| F03 | Alternative suggestions | **Done** | Alternatives only when no L1/L2 exists, always labelled as a different app, with category/tag/description reasons, enabled-store and coverage awareness, relevance ranking, a cap of five, and session-only hide/undo. Side-by-side comparison (R7 MAY) is deferred. | S05 |
| F04 | Store registry and adapters | **Partial** | Pure-Kotlin store/adapter contracts, verified built-in F-Droid and IzzyOnDroid descriptors, versioned registry persistence, custom-store CRUD, link-only fallback, HTTP opt-in, descriptor export, Stores UI, real cached-index adapter, and F04-R2 post-sync usability exist. Health history and import preview remain. | S02, S03 |
| F05 | Index sync and offline cache | **Done** | Signed F-Droid v2 sync, signer/hash/size verification, HTTPS policy, ETag/diff reuse, streaming parse, atomic snapshot replacement, cancellation safety, freshness/last-sync state, and bounded storage with a real integer setting. Live network and device/UI verification remain outstanding. | S03 |
| F06 | Coverage and confidence | **Done** | Per-store coverage snapshot with checked/stale/failed/skipped reasons, 24-hour freshness, explicit incomplete results, a checked-vs-required summary, and label-first confidence. “Not found” is blocked by any enabled non-disabled store that was not checked. Shared-report coverage (R4) is included in S11 contribution payloads; targeted refresh (R5) remains deferred. | S05, S11 |
| F07 | Replacement guidance | **Done** | Pure-JVM assessment compares package, signer, version code, and installer. UI shows update/replace/alternative/no-update/cannot-verify, installed and candidate versions, installer, data-loss/uninstall/system/other-store/cross-store/work-profile/disabled warnings, and requires explicit confirmation for `REPLACE`. Missing signer/version metadata fails closed. There is no install, uninstall, or delete path. Reminder/watchlist (R7 MAY) remains deferred. | S08 |
| F08 | Companion store handoff | **Done** | Explicit package queries detect F-Droid, Droid-ify, and Neo Store without broad Play visibility. The selected enabled client opens `market://details?id=<package>` with an explicit package; one canonical `fdroidrepos://` descriptor drives add, QR, combined copy, URL copy, and fingerprint copy. Preferred client per app persists locally; missing/disabled clients get install and browser fallbacks. Other-store deep links (R6 MAY) remain deferred. Real intent/package-visibility checks are device-pending. | S08 |
| F09 | Standalone store client | **Deferred** | Excluded from both v1 profiles. Requires F05, F14, and `DIRECT_APK_INSTALL`. | S12 |
| F10 | Install-time suggestion | **Deferred** | Excluded from both v1 profiles. Requires matching, handoff, and the accessibility module. | S12 |
| F11 | Favourites and notes | **Done** | Add/remove, private notes, persistence, duplicate handling, note limit, tags, local search across names/notes/tags, optional preferred store, installed-state badge, error states, and versioned collection integration work. The badge is advisory and does not refresh live while the Saved screen stays foregrounded; real package-manager transitions still need device verification. | S06, S07 |
| F12 | Collections and sharing | **Done** | Versioned collections with create/rename/reorder/edit, local JSON/text/file sharing and import, notes excluded by default, merge/replace/skip conflict handling, inert imported store references, unsupported-version rejection, and a non-exported FileProvider. QR/deep links (R8) and subset sharing (R9) are deferred. | S07 |
| F13 | Backup and migration | **Done** | Versioned full-profile JSON backup with visible included/excluded manifest, SHA-256 checksum, merge/replace/skip restore planner, custom-store fingerprint/trust preservation, optional PBKDF2/AES-GCM passphrase encryption, share-sheet export, document-picker import, and Android auto-backup rules including DataStore. F13-R6 per-item conflict resolution remains SHOULD and is deferred; device share/document-picker checks remain. | S09 |
| F14 | Security verification | **Done** | Repository fingerprint pinning/change blocking, index JAR signature verification, signed index/diff hash and size checks, HTTPS policy, and local trust records exist. S10 adds the security-details panel, distinct verified/unverified/failed/rotation states, a bounded local verification audit, and a fingerprint-rotation approval/removal flow. Match evidence shows index trust without claiming APK integrity. APK hash/signer continuity (R3/R4) is deferred with F09; audit export (R8) and transparency metadata (R9) remain post-v1. | S03, S10 |
| F15 | Update tracking | **Deferred** | Not enabled in either v1 profile. | S12 |
| F16 | Contribution hooks | **Done** | Pure-JVM schema-v1 report builder and redaction boundary, configurable prefilled issue URL, preview-gated ViewModel, Compose contribution screen, and Settings/Stores/Match entry points. Default reports exclude inventory, notes, account data, device identifiers, raw errors, query tokens, and local paths; coverage keeps statuses/reasons only. Copy/share work offline; debug details are opt-in and exported as redacted JSON. The GitHub issue URL is configurable in the builder. Device checks for preview, clipboard, share sheet, browser opening, and debug JSON remain. | S11 |
| F17 | Feature configuration | **Done** | Descriptor settings model (boolean/text/integer with defaults and validation), versioned envelope with legacy migration, registry-authoritative availability, basic/advanced sections, per-feature/global reset, and read-only snapshot used by F13. `INDEX_CACHE` declares the real `storage_budget_mb` setting; S09 adds the backup import/export UI. | S01, S09 |
| F18 | Unified store search | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F19 | Permission and tracker diff | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F20 | Matching correction | **Spec only** | Post-v1. No feature ID or code. | S12 |
| F21 | Trust badges | **Spec only** | Post-v1. No feature ID or code. | S12 |

## Verification snapshot

Commands run on `feat/s11-contribution-hooks` at `160db61` on 2026-10-26 after S01–S11:

| Check | Result |
| --- | --- |
| `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint :app:lintPlaystoreDebug` | **Passed** (3m 43s, 902 actionable tasks) |
| Unit tests | **729 test executions across 106 suites; 0 failures, 0 errors, 0 skipped** (debug and release variants counted separately) |
| `assemblePlaystoreDebug` | **Passed** |
| `assembleFdroidDebug` | **Passed** |
| Play restricted manifest scan | **Unchanged and passing**: only `INTERNET` and the framework dynamic-receiver permission are present; no `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, `AccessibilityService`, or `BIND_ACCESSIBILITY_SERVICE`. `ManifestPolicyTest` passed in both profiles. S11 adds no permission, provider, or manifest capability; contribution uses clipboard, share, and browser intents only. |
| F-Droid manifest scan | **Expected**: contains `QUERY_ALL_PACKAGES`, the explicit companion-store queries, and a launcher-intent `<queries>` block for the full inventory scan |
| Share and backup providers | **Unchanged**: non-exported `FileProvider` at `${applicationId}.fileprovider`; contribution adds no provider or permission. Both merged manifests declare `allowBackup`, `fullBackupContent`, and `dataExtractionRules`; the rules include `sharedpref` and `file/datastore/`. |
| `./gradlew lint` | **Passed**: 0 errors. Warnings: app Play 42, app F-Droid 40, `feature:favourites` 13, `feature:inventory` 4. `core:contribution`, `feature:contribution`, `feature:stores`, and `feature:matching` report no issues. Play has two extra `QueryPermissionsNeeded` warnings because broad package visibility is intentionally confined to F-Droid. |

### Headless screenshot smoke set (2026-10-27)

A temporary Compose preview screenshot harness rendered **24 screens** from the
Play debug source set. The standard capture is 411 x 891 dp (1079 x 2339 px),
matching the earlier set; handoff, backup, contribution, and settings captures
use taller canvases where the content requires it. The set covers Discover
(light, search, category, dark), inventory overview and lookup, exact match and
coverage-gap match, companion handoff with QR, store overview/security
details/editor, saved favourites and note editor, collections and collection
detail, collection sharing, backup export and restore preview, contribution
form and preview, basic/advanced settings, and About.

All 24 previews rendered without layoutlib or Compose crashes. This is a
composition smoke check with representative fake state, **not device or
emulator verification**. Images are kept in the ignored `screenshots/`
directory and are not committed.

Visual review notes:

- At 360 dp the six bottom-navigation labels wrap; the 411 dp capture width
  matches the earlier screenshots and keeps them on one line.
- Long package names wrap onto two lines in the Saved-project cards at 411 dp.
  This is a layout issue to review before release, not a functional failure.
- The restore-preview capture is intentionally tall so the preview and conflict
  mode controls below the export/import cards are visible.

Post-screenshot cleanup re-run on 2026-10-27 after restoring the temporary
build files and removing the preview harness: `./gradlew test
assemblePlaystoreDebug assembleFdroidDebug` passed with **729 tests across 106
suites; 0 failures, 0 errors, 0 skipped**. Both debug APKs assembled.

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
- F07 is listed in v1 but depends on F09, which is deferred. S08 implements the
  planned v1 scope: guidance and handoff only, with no in-app install or
  uninstall.
- F14-R3/R4 (APK hash/size and signer-continuity enforcement) need F09 and
  remain post-v1. The v1 scope — repository/index verification, HTTPS,
  fingerprint pinning, trust details, bounded audit, and rotation review — is
  implemented in S10.
- Play package visibility is not finally decided. The implementation now follows
  the planning assumption: manual/selected inventory on Play with no
  `QUERY_ALL_PACKAGES`; F-Droid performs the full scan. A Play policy review and
  device verification are still required before release.
- Only `playstore` and `fdroid` profiles exist. `direct` and `custom` remain
  future profiles.

### Architecture and data

- F01/F02, F02/F06, F04/F05, and F05/F14 have circular dependencies in the
  specs. Shared contracts must be defined before the dependent features.
- Store registry, installed inventory, verified index sync/cache, matching,
  coverage, alternatives, replacement guidance, companion handoff, collections,
  local sharing, backup/migration, security details/trust audit, and
  contribution reports/redaction exist.
- F01's production classifier now calls the matching engine and selects `FOSS`
  only for an exact package with license and source evidence. It still never
  selects `PROPRIETARY` because no proprietary evidence source is implemented;
  absence of a FOSS listing remains `UNKNOWN`, not proof of closed source.
- Matching uses a 24-hour freshness window. An L1 result from a stale snapshot is
  provisional; a stale or unsupported enabled store blocks a conclusive
  “not found.”
- Alternative hide/undo is session-only and is not persisted. Matching catalogs
  are projected from cached snapshots and cached in memory (bounded to four
  payloads); no network request is made during matching.
- F02-R8 pinned source, F06-R5 targeted refresh, and F03-R7 comparison are not
  implemented. F02-R7 report-wrong-match and F06-R4 shared-report coverage are
  covered by the S11 contribution flow.
- F07-R7 reminder/watchlist and F08-R6 other-store deep links are not
  implemented. F07's signature-lineage/key-rotation, split-APK, and ABI/SDK
  compatibility checks remain post-v1. F08's last-handoff history is not
  persisted; only the preferred client per app is stored.
- F04's cached-index adapter is real, but health history and import preview
  remain. Live sync against the upstream F-Droid/IzzyOnDroid indexes has not
  been exercised from a device or emulator.
- A valid signed index with no pinned fingerprint can activate as `UNVERIFIED`.
  The Stores UI marks it unverified and match evidence carries that state; v1
  has no one-tap install. A changed/removed pin or signer mismatch now requires
  explicit approval or store removal before sync resumes.
- Android `PackageManager` behaviour is covered by JVM fakes only. Real
  enumeration, package visibility, installer/signer/work-profile values,
  shared-link handling, stale rendering, and hide-list survival across process
  restart still need device/emulator verification.
- Companion handoff has not been exercised on a device: real store-client
  intent resolution, explicit package visibility, disabled-client behavior,
  `fdroidrepos://` handling, QR scanning, clipboard overlays, and browser
  fallback remain to be checked.
- Backup export/import has not been exercised on a device: system share-sheet
  handling, document-picker URI access, FileProvider reads by receiving apps,
  and large-profile behavior remain to be checked. The share/import path is
  covered by JVM fakes and static FileProvider checks.
- Security details and rotation approval have not been exercised on a device.
  The trust-state mapping, audit bounding, rotation approval/clearing, and
  ViewModel behavior are covered by JVM tests; the Compose dialog and long
  fingerprint/URL wrapping still need device/emulator checks.
- Contribution preview, clipboard, share-sheet, browser opening, and debug JSON
  have not been exercised on a device. Redaction, preview gating, issue-URL
  construction, and ViewModel behavior are covered by JVM tests. The issue
  tracker URL is configurable in `ContributionReportBuilder`, not exposed as an
  in-app setting.
- F14-R8 audit export is not implemented; the local 100-entry audit is visible
  in the security details panel. F14-R9 binary-transparency/reproducibility
  metadata is not displayed. F14-R3/R4 remain deferred with F09.
- F13-R6 per-item conflict resolution is not implemented; restore uses global
  merge/replace/skip modes with aggregate preview counts and conflict flags in
  the restore plan. Per-item keep-local/use-backup selection is missing.
  F13-R7 is satisfied by matching in-app text and Android backup XML rules.
- Backup rollback is best-effort across independent DataStores. A failure after
  an earlier write attempts to restore the captured previous state, but there is
  no cross-DataStore transaction.
- F11's installed badge is advisory and does not refresh live while the Saved
  screen stays foregrounded through an external install/uninstall.
- The app now exposes six bottom-navigation destinations. Matching and
  contribution open as overlays/back flows rather than adding more tabs. This is
  functional but needs a UX review before release.
- `AppKey` has no documented canonicalization rule for source-only apps.
- `INDEX_CACHE` declares the real integer `storage_budget_mb` setting. Other
  features do not declare settings yet, so the Advanced section is sparse until
  future features add real settings.
- The settings envelope stores disabled IDs plus per-feature values. A future
  feature whose `defaultEnabled` is false and that users can opt into will need
  an explicit enabled-override field; no current descriptor uses that case.
- Settings import/export is provided by the S09 backup screen; S01 exposes the
  read-only snapshot used to build it.
- F17-R8 development availability overrides (MAY) are not implemented.
- The contribution toggle gates the real contribution UI. The security toggle
  does not disable mandatory index verification; verification and trust details
  remain enforced. The matching, alternatives, coverage, replacement-guidance,
  companion-handoff, and backup toggles gate their real UI behavior.

## Status update protocol

After a feature session is merged:

1. Update the baseline commit and date at the top of this file.
2. Update the affected feature row and the summary counts.
3. Record the exact test/build commands and any new environment caveats.
4. Add or remove blockers.
5. Link the session file if the work was split further.

# S02 — F04 Store Registry and Adapter Contracts

| Field | Value |
| --- | --- |
| Status | **Done** (S02 scope; index sync lands in S03) |
| Features | F04 (contracts/registry portion) |
| Depends on | Foundation; S01 recommended for descriptor changes |
| Can run in parallel with | S04, S06 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F04-store-registry.md`](../specs/features/F04-store-registry.md) |

## Goal

Create the store domain and local registry so the rest of the app can talk to
stores through one contract. This session does **not** fetch or parse real
indexes; S03 adds sync and verification.

## Scope

- Add a `core:store-api` module (or the equivalent shared core module) containing
  pure-Kotlin contracts:
  - `StoreDescriptor` with ID, name, homepage, adapter type, index URL, API URL,
    repository URL, fingerprint, enabled, priority, trust note, health, schema
    version;
  - `AdapterType`: F-Droid v2, F-Droid v1, live API, generic JSON, source
    repository, deep link;
  - `StoreAdapter` capability contract for search, listing, versions, artifact
    resolution, sync, and verification;
  - `StoreHealth` and adapter capability/unsupported reasons.
- Ship predefined F-Droid and IzzyOnDroid descriptors. Verify current official
  URLs and fingerprints during the session; do not guess values.
- Persist the registry with a versioned codec (DataStore Preferences + JSON is
  acceptable, consistent with the current data layer).
- Implement custom-store CRUD: add, edit, enable/disable, reorder, delete.
- Save unsupported stores as link-only with a visible reason; open their
  homepage and exclude them from matching with a coverage gap later.
- Require explicit opt-in for HTTP-only stores.
- Export a portable, versioned store descriptor.
- Add a Store settings screen under `feature:stores` and wire it into the app
  behind `FeatureId.STORES`.
- Use fake adapters in tests; no network calls in this session.

## Out of scope

- Index download, parsing, caching, freshness, or storage budgets (S03).
- Signature verification and trust UI (S03/S10).
- APK download/install (F09, deferred).
- Deep-link handoff intents (S08).

## Key files

- `settings.gradle.kts`, `gradle/libs.versions.toml`
- new `core/store-api/` module
- new `feature/stores/` module
- `core/data/` if the registry store lives there
- `app/src/main/java/com/fossscout/app/AppContainer.kt`
- `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`
- both `app/src/*/java/com/fossscout/app/profile/ProfileFeatures.kt`

## Acceptance criteria

- **F04-R1:** fresh install lists F-Droid and IzzyOnDroid with current data.
- **F04-R2:** contract supports adding a valid F-Droid-compatible repository;
  actual “usable after sync” completes in S03.
- **F04-R3:** unsupported store persists as link-only, opens its homepage, is
  excluded from matching, and shows a reason.
- **F04-R4:** enabled/disabled/reordered/edited/deleted store changes affect the
  next query without restart.
- **F04-R5:** descriptor export/import is portable and versioned.
- **F04-R6:** HTTP requires an explicit warning and opt-in.
- Registry codec rejects malformed/future unsupported data without corrupting
  the active registry.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Add fake-adapter tests for CRUD, priority, link-only fallback, HTTP policy, and
descriptor round-trip. Confirm no new network dependency is required yet.

## Risks and decisions

- F04 and F05 are circular in the specs. This session defines the contracts;
  S03 owns sync. Do not wait for a full F-Droid parser.
- v1 store scope assumption: F-Droid + IzzyOnDroid via the F-Droid v2 adapter;
  other stores are link-only.
- Store fingerprints are security-sensitive. Never ship a guessed fingerprint;
  verify the official source and record where it came from.

## Completion record

Implemented on `feat/s02-store-registry` on 2026-10-23.

- commit: `461ead9` (implementation); this completion record is a follow-up
  commit on the same branch.
- modules: new `core:store-api` (pure-Kotlin descriptor, adapter, health, and
  capability contracts), new `feature:stores` (Store screen and view model),
  and registry codec/store/repository additions under `core:data`. App wiring
  adds the DataStore-backed repository and a Stores destination gated by
  `FeatureId.STORES`.
- tests: **184 test executions across 31 suites; 0 failures, 0 errors, 0
  skipped** (`./gradlew test`, debug and release variants counted separately).
  New coverage includes CRUD, priority normalization/reorder, link-only
  fallback, HTTP opt-in, codec/descriptor export round-trips, malformed and
  future schema rejection, and fake-adapter capability handling.
- build: `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` **passed**.
- lint: `./gradlew lint` **passed** (0 errors; 37 existing app warnings). The new
  `feature:stores` module reports no lint issues.
- Play merged manifest scan: **passed**. No `REQUEST_INSTALL_PACKAGES`,
  `QUERY_ALL_PACKAGES`, `AccessibilityService`, or
  `BIND_ACCESSIBILITY_SERVICE` entries were added.
- F04 criteria completed: R1 (fresh install ships F-Droid and IzzyOnDroid), R2
  (custom F-Droid-compatible stores add, persist, and become matchable), R3
  (unsupported adapters persist as link-only with a reason, open their homepage,
  and are excluded from matching), R4 (enable/disable/reorder/edit/delete feed
  the next registry read without restart), R5 (versioned JSON descriptor export
  and share), R6 (HTTP requires explicit opt-in). The registry codec rejects
  malformed and future data without overwriting the active registry.
- remaining F04 criteria (expected: R2 sync): R2's actual index usability waits
  for S03 sync; R7 fingerprint-change security events, richer R8 health history
  (last-sync/latency display), and MAY R9 import preview remain for later
  sessions.
- verified store sources (checked 2026-10-23; not guessed):
  - F-Droid: homepage <https://f-droid.org>, index
    <https://f-droid.org/repo>, fingerprint
    `43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab`.
    Source: <https://f-droid.org/docs/Release_Channels_and_Signing_Keys/>,
    listed as the APK signing certificate SHA-256 fingerprint.
  - IzzyOnDroid: homepage <https://apt.izzysoft.de/fdroid/>, index
    <https://apt.izzysoft.de/fdroid/repo>, fingerprint
    `3bf0d6abfeae2f401707b6d966be743bf0eee49c2561b9ba39073711f628937a`.
    Source: <https://apt.izzysoft.de/fdroid/>, whose repository link publishes
    the value in its `fingerprint` query parameter.
- known gaps: no real adapter/network implementation, index parsing, sync
  cache/freshness, signature-verification UI, APK install, import preview, or
  deep-link handoff was added (all intentionally out of S02 scope).

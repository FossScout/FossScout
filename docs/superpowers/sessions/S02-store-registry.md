# S02 — F04 Store Registry and Adapter Contracts

| Field | Value |
| --- | --- |
| Status | **Ready** |
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

Update `STATUS.md` after merge:

- commit:
- tests:
- F04 criteria completed:
- remaining F04 criteria (expected: R2 sync):

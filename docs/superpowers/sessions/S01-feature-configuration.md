# S01 — F17 Feature Configuration Completion

| Field | Value |
| --- | --- |
| Status | **Ready** |
| Features | F17 |
| Depends on | Foundation only |
| Can run in parallel with | S02, S04, S06 after this session lands |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F17-feature-configuration.md`](../specs/features/F17-feature-configuration.md) |

## Goal

Make feature declarations the single source of truth for availability, defaults,
and settings, and finish the reset/advanced-settings behaviour required by F17.

This session is deliberately first because every later feature should declare
its settings through the same model instead of retrofitting them.

## Current state

- `FeatureDescriptor` has no settings.
- `FeatureSettingsStore` persists only a `Set<String>` of disabled feature IDs.
- `ProfileFeatures.enabledFeatures` filters the catalog in the app composition,
  while `FeatureRegistry` computes availability separately.
- Settings has toggles and unavailable reasons, but no per-feature/global reset,
  no basic/advanced sections, and no settings snapshot for backup.

## Scope

- Add a small `FeatureSetting` model in `core:feature`:
  - stable key, display section (basic/advanced), default value, value type, and
    validation;
  - support only the value types needed now (start with boolean and text; add a
    numeric type only when a real setting needs it).
- Add `settings: List<FeatureSetting>` to `FeatureDescriptor` with an empty
  default so existing descriptors keep compiling.
- Replace the disabled-ID-only store with a versioned settings envelope:
  - schema version;
  - disabled feature IDs;
  - per-feature values;
  - migration from the legacy `disabled_features` preference.
- Make `FeatureRegistry` the authoritative availability/enabled calculation.
  Remove the duplicate `enabledFeatures` filtering path or reduce it to profile
  capability data only.
- Add basic/advanced sections to the settings screen.
- Add individual and global “reset to default” actions.
- Keep the existing “unavailable in this build” explanation and policy-safe
  capability labels.
- Expose a read-only settings snapshot that F13 can include in a full backup.
- Add tests for migration, reset, preservation across disable/re-enable,
  restricted-feature rejection, and profile drift.

## Out of scope

- Implementing settings for features that do not exist yet. Declare none or
  only real settings.
- Backup/export UI (S09).
- Changing feature behavior or adding F01–F16 implementations.
- Development-only availability overrides unless needed for tests; they must
  never ship enabled in release.

## Key files

- `core/feature/src/main/java/com/fossscout/core/feature/FeatureDescriptor.kt`
- `core/feature/src/main/java/com/fossscout/core/feature/FeatureCatalog.kt`
- `core/feature/src/main/java/com/fossscout/core/feature/FeatureRegistry.kt`
- `core/data/src/main/java/com/fossscout/core/data/FeatureSettingsStore.kt`
- `core/data/src/main/java/com/fossscout/core/data/FeatureSettingsRepository.kt`
- `feature/settings/src/main/java/com/fossscout/feature/settings/*`
- `app/src/main/java/com/fossscout/app/AppContainer.kt`
- `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`
- `app/src/playstore/java/com/fossscout/app/profile/ProfileFeatures.kt`
- `app/src/fdroid/java/com/fossscout/app/profile/ProfileFeatures.kt`

## Acceptance criteria

- **F17-R1:** every optional feature can declare settings and defaults; the
  screen is generated from descriptors.
- **F17-R2:** disabling then re-enabling a feature preserves its data and
  settings.
- **F17-R3:** a feature unavailable for the profile cannot be enabled at runtime.
- **F17-R4:** unavailable features show a policy-safe reason.
- **F17-R5:** individual and global reset restore declared defaults.
- **F17-R6:** basic and advanced sections are separated.
- **F17-R7:** settings can be read as a versioned snapshot for backup.
- Existing legacy disabled-feature data migrates without loss.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Add repository/codec tests for the new envelope and a settings ViewModel test
for reset/preservation. Verify the Play merged manifest is unchanged.

## Risks and decisions

- Do not expose a settings maze. Only declare settings that affect real
  behaviour.
- Keep `FeatureDescriptor` data-only and free of Android resources.
- If a feature is not implemented, its toggle may remain present but must not
  imply working behavior. Consider a “not implemented in this build” state in a
  later session rather than adding fake settings.
- If the profile model changes, update both flavors and `FeatureRegistryTest`.

## Completion record

Update `STATUS.md` after merge:

- commit:
- tests:
- remaining F17 gaps:

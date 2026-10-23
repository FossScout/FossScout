# S04 — F01 Installed App Inventory

| Field | Value |
| --- | --- |
| Status | **Ready** |
| Features | F01 (inventory; full classification integration in S05) |
| Depends on | Foundation; S01 recommended |
| Can run in parallel with | S02, S06 |
| Profiles | `playstore` manual/selected; `fdroid` full scan |
| Spec | [`F01-app-inventory.md`](../specs/features/F01-app-inventory.md) |

## Goal

Build a local inventory of installed apps that works without networking and
respects the profile boundary: manual/selected on Play, full scan on F-Droid.
Real FOSS classification is completed in S05; this session provides the
classification contract and a fixture/stub classifier.

## Scope

- Add inventory models and a source abstraction:
  - installed package, label, version name/code, installer, signer, system and
    enabled flags, profile/user identity where available;
  - scan mode (`MANUAL`, `FULL`), classification (`FOSS`, `PROPRIETARY`,
    `UNKNOWN`, `UNAVAILABLE`), snapshot time, coverage/status;
  - `InstalledAppSource` so Play can use manual/selected lookup and F-Droid can
    enumerate.
- Implement the Android `PackageManager` source.
- Manual path for every profile:
  - search/type a package or app name;
  - picker where practical;
  - accept a shared store link/package identity.
- Full scan path only when `BROAD_PACKAGE_VISIBILITY` is present.
  - Add the F-Droid-only manifest permission/query needed for enumeration.
  - Add a test proving the Play merged manifest does not contain it.
- Persist the last complete snapshot locally with its timestamp.
- Mark stale data visibly; cancellation must preserve the last complete
  snapshot.
- Add filters for system/disabled/work-profile apps and the optional hide list.
- Add `feature:inventory` UI and wire it into navigation behind
  `FeatureId.INVENTORY`.
- Add a `ClassificationSource` interface returning `UNKNOWN` until S05.

## Out of scope

- FOSS matching, alternatives, or coverage (S05).
- Update tracking (F15, deferred).
- Install-time suggestions (F10, deferred).
- Any network request for inventory data.
- Hiding apps from the system; “hide” only means hide from FossScout results.

## Key files

- new `feature/inventory/` module
- new shared inventory contract in `core:*`
- `app/src/main/AndroidManifest.xml`
- new F-Droid-specific manifest if the broad visibility declaration is added
- `app/src/main/java/com/fossscout/app/AppContainer.kt`
- `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`
- both `ProfileFeatures.kt` files

## Acceptance criteria

- **F01-R1:** inventory works with networking disabled and makes no inventory
  network request.
- **F01-R2:** full scan is impossible in Play; the Play manifest has no broad
  package visibility.
- **F01-R3:** manual “check one app” works in every profile.
- **F01-R4:** classification contract and UI can show all four labels with
  explanations; the real classifier is wired in S05.
- **F01-R5:** every result/snapshot shows scan time and visibly marks stale data.
- **F01-R6:** system/disabled/work-profile filters work where data is available.
- **F01-R7:** cancellation preserves the last complete snapshot.
- **F01-R8 (optional):** hidden apps stay hidden across restart.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Use a fake `InstalledAppSource` for JVM tests. Add a manifest test or inspection
for the Play/F-Droid boundary. Device-level `PackageManager` behavior needs
manual verification; record what was tested on a device/emulator.

## Risks and decisions

- Planning assumption: Play is manual/selected only and does not request
  `QUERY_ALL_PACKAGES`. If that changes, this session needs a policy review.
- Android package visibility and work-profile APIs vary by API level. Keep the
  source abstraction and degrade to `UNKNOWN`/unavailable rather than crashing.
- Do not store signer or inventory data remotely. Local only.
- Do not call matching from the UI; S05 owns classification.

## Completion record

Update `STATUS.md` after merge:

- commit:
- tests/device checks:
- F01 criteria completed:
- remaining F01-R4 classification integration:

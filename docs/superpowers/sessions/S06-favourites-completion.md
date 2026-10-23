# S06 — F11 Favourites Completion

| Field | Value |
| --- | --- |
| Status | **Done** (F11 scope; collections/sharing land in S07) |
| Features | F11 (F12 collections/sharing are S07) |
| Depends on | Foundation; S02 store registry and S04 installed inventory are merged |
| Can run in parallel with | S03 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F11-favourites-notes.md`](../specs/features/F11-favourites-notes.md) |

## Goal

Finish the local favourites experience: make tags and preferred stores usable,
add search/filter across names, notes, and tags, and show installed state as a
derived badge without changing the favourite itself.

The core add/remove/note/persistence workflow already exists. Do not rewrite it.

## Scope

- Expose the existing `tags` field:
  - add/remove tags in the note editor or a separate editor;
  - normalise/limit tags and test duplicates/blank values.
- Add favourites search and filtering across display name, notes, and tags.
- Add a preferred-store selector using S02's store registry when available;
  keep the field optional and do not block saving if no store is available.
- Add an installed-state provider:
  - derive INSTALLED / NOT_INSTALLED / UNAVAILABLE / UNKNOWN at read time;
  - show a badge with an explanation;
  - never store installed state in the favourite.
- Validate canonical `AppKey` identity and keep duplicate resolution intact.
- Add UI/state tests for empty search, tag editing, note search, installed badge,
  and read/write failures.
- Ensure disabling/re-enabling favourites preserves data (works with S01).

## Out of scope

- Collections, share/import, QR, backup (S07/S09).
- Storing installation state or package metadata in `Favourite`.
- Matching/classification (S05); use the installed-state contract only.
- Changing the note privacy model.

## Key files

- `core/model/src/main/java/com/fossscout/core/model/Favourite.kt`
- `core/data/src/main/java/com/fossscout/core/data/FavouritesRepository.kt`
- `core/data/src/main/java/com/fossscout/core/data/FavouritesCodec.kt`
- `feature/favourites/src/main/java/com/fossscout/feature/favourites/*`
- `feature/catalog` favourite action wiring
- `app/src/main/java/com/fossscout/app/AppContainer.kt`
- `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`

## Acceptance criteria

- **F11-R1:** a favourite can be created for an app that is not installed.
- **F11-R2:** favourites, notes, and tags survive repository/app recreation.
- **F11-R3:** installed state is derived, displayed separately, and changing it
  does not modify the favourite.
- **F11-R4:** notes remain private by default and are not included in any share
  preview (sharing itself is S07).
- **F11-R5:** duplicate favourites resolve to one canonical app entry.
- **F11-R7 (SHOULD):** search covers favourites and notes; tags are filterable.
- **F11-R8 (SHOULD):** preferred store can be set and is preserved.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Use a fake installed-state provider for badge tests. Add repository tests that
show tags and preferred store survive encode/decode and that duplicate adds do
not clear notes.

## Risks and decisions

- F11's spec mentions collections, but F12 owns collections and sharing. Keep
  this session's collection surface out; S07 adds it.
- If S02/S04 are not merged yet, hide the preferred-store selector and installed
  badge behind their contracts rather than inventing temporary data.
- Search must be local and must not introduce a network dependency.
- Installed badge is advisory; do not turn a missing package into an automatic
  removal.

## Completion record

- commit: `649bd9f` (`feat(favourites): complete S06 search tags and installed badges`); docs record in a follow-up commit. `STATUS.md` is updated by the parent after merge.
- tests:
  - `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` — **passed**.
  - `test`: **324 test executions across 54 suites; 0 failures, 0 errors, 0 skipped** (debug/release variants and JVM modules counted separately).
  - New coverage: canonical `AppKey`, tag normalisation/limits (`FavouriteTagsTest`), search and tag filtering (`FavouriteSearchTest`), repository detail updates and duplicate preservation (`FavouritesRepositoryTest`), encode/decode and persistence across store recreation (`FavouritesCodecTest`, `DataStoreFavouritesStoreTest`), installed-state mapping for installed/missing/unsupported/unknown sources (`InstalledAppStateProviderTest`), and favourites UI/state for empty search, tag editing, note search, installed badges, preferred stores, and read/write failures (`FavouritesUiStateTest`, `FavouritesViewModelTest`).
  - `./gradlew lint` — **passed**; warnings only (new `PluralsCandidate` warnings for tag/note limit strings are non-failing).
  - Play merged manifest inspection (`app/build/intermediates/merged_manifest/playstoreDebug/processPlaystoreDebugMainManifest/AndroidManifest.xml`): no `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, accessibility service, or other restricted capability.
  - Device/emulator-only checks **not run**: real `PackageManager` install/uninstall transitions and badge refresh on process resume with live data. The derivation logic is exercised on JVM with fake installed-state providers.
- F11 criteria completed:
  - **F11-R1** — a favourite can be created and saved for an app that is not installed; saving does not require a store or installed package.
  - **F11-R2** — favourites, notes, tags, and preferred store survive encode/decode and repository/store recreation; disabling and re-enabling favourites does not clear the backing data.
  - **F11-R3** — installed state is derived at read time via `InstalledStateProvider` as INSTALLED / NOT_INSTALLED / UNAVAILABLE / UNKNOWN, shown as an advisory badge with an explanation; it is never written into `Favourite`, and a missing package never removes the favourite.
  - **F11-R4** — notes remain local and private by default; the note privacy model is unchanged and no share/export surface was added.
  - **F11-R5** — duplicate adds resolve to one canonical `AppKey` entry; canonical keys reject blank/whitespace variants, and duplicate adds preserve existing notes, tags, and preferred store.
  - **F11-R7 (SHOULD)** — local search covers display name, notes, and tags; tag chips filter the list, with a distinct empty-search state.
  - **F11-R8 (SHOULD)** — preferred store can be selected from the S02 registry (optional and nullable when unavailable) and is preserved through encode/decode and recreation.
- remaining F11 gaps:
  - **F11-R6 collections** remain out of scope for this session; F12/S07 owns collections and sharing (share/import, QR, backup).
  - The installed badge is advisory only and refreshes on list changes and Saved-screen re-entry; real-time refresh while the app stays foregrounded through an external install/uninstall is not implemented.
  - Device/emulator verification of live package-manager transitions is still outstanding.

# S06 — F11 Favourites Completion

| Field | Value |
| --- | --- |
| Status | **Ready** |
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

Update `STATUS.md` after merge:

- commit:
- tests:
- F11 criteria completed:
- remaining F11 gaps:

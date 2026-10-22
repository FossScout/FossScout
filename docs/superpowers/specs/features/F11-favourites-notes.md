# F11 — Favourites and Notes

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None beyond local storage |
| Depends on | F02 |
| Related | F12, F13 |

## 1. Problem

A favourite is not necessarily installed. Users want a place to keep apps they
are considering, apps they use elsewhere, and notes about why they saved them.
The current scaffold's “saved projects” are in-memory and installed-state
agnostic, but the model needs to be explicit and persistent.

## 2. User stories

- As a user, I want to favourite an app I have not installed.
- As a user, I want to write a private note about a favourite.
- As a user, I want to see whether a favourite is installed, without that
  changing the favourite itself.
- As a user, I want to organise favourites into collections.

## 3. Data model

`Favourite`:

- canonical app key (package name where known; otherwise a stable source key);
- display name;
- optional preferred store/source;
- optional note (private by default);
- optional tags;
- createdAt / updatedAt;
- schema version.

Installed state is **derived at read time**, never stored as part of the
favourite. A favourite may be installed, not installed, unavailable, or unknown.

`Collection`: id, name, description, ordered favourite IDs, createdAt,
updatedAt, schema version.

## 4. Scope

- Add a favourite from a search result, app detail, store listing, share sheet,
  or import.
- Add/edit/delete a note per favourite.
- Create, rename, reorder, and delete collections.
- Show installed state as a badge, not as a requirement.
- Persist locally across restarts and upgrades.
- Search and filter favourites and notes.

## 5. Non-goals

- Requiring an account or login.
- Syncing favourites to a server.
- Treating a favourite as proof of installation.
- Publishing notes automatically.
- Automatically installing a favourite.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F11-R1 | MUST | A favourite can be created for an app that is not installed. | A catalog app can be favourited without installation. |
| F11-R2 | MUST | Favourites and notes persist locally across restarts. | Kill and reopen the app; data is intact. |
| F11-R3 | MUST | Installed state is derived and displayed separately. | Uninstalling an app changes the badge but not the favourite. |
| F11-R4 | MUST | Notes are private by default and excluded from sharing unless explicitly included. | Default share preview omits notes. |
| F11-R5 | MUST | Duplicate favourites resolve to one canonical app entry. | Favouriting the same package twice does not create duplicates. |
| F11-R6 | SHOULD | Collections can be named, reordered, and edited. | Order persists after restart. |
| F11-R7 | SHOULD | Favourites and notes are searchable. | Query matches app name and note text. |
| F11-R8 | MAY | A favourite can have a preferred store/source. | The preference is shown and used for install suggestions. |

## 7. Edge cases

- Same package ID from multiple stores.
- App renamed while package stays the same.
- App removed from all stores.
- Source-only app with no package name.
- Very long notes.
- Imported favourite with an unknown store.
- Work-profile installation.

## 8. Open questions

1. Should notes be included in a shared collection only when the user opts in per
   share, or per note?
2. Should tags be a first-class feature or just part of the note?
3. What is the maximum note length?
4. Should favourites survive an app uninstall by default? (Current assumption:
   yes.)

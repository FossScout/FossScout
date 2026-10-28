# F20 — Matching Correction

| Field | Value |
| --- | --- |
| Status | **Done** (F20-R8 deferred) |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | None beyond local storage |
| Depends on | F02 |
| Related | F03, F12, F13, F18 |

## 1. Problem

Package matching will be wrong sometimes: a proprietary app may share a name with
an unrelated FOSS app, a fork may look like its upstream, or a store may use a
different package ID. Users need a way to correct a match, and the correction
must survive index refreshes and device migration.

## 2. User stories

- As a user, I want to mark “this is not the app I meant”.
- As a user, I want to choose a different store/package for the match.
- As a user, I want my correction to persist after the index updates.
- As a user, I want to remove a correction if I change my mind.
- As a user, I want to know which matches were corrected by me.

## 3. Data model

`MatchOverride`:

- canonical installed app key;
- chosen target (app key, display name, store id, and store name), or an explicit
  no-match (`target = null`);
- optional private note (maximum 4,000 characters);
- createdAt / updatedAt;
- schema version.

One override exists per installed app key. Overrides are local, per device, and
applied before display. They do not change the store index or the upstream
catalog. A null target means “no suitable match”; it is not the same as a missing
override.

## 4. Scope

- Correct an automatic match from the match screen. The app has no separate
  app-detail screen, so the existing match result is the correction entry point.
- Choose “no suitable match”.
- Pick a specific package from cached enabled-store search results.
- Add an optional private note.
- Show a “you corrected this” badge.
- Keep the automatic result visible for inspection.
- Undo a correction and return to the automatic result.
- Include overrides and notes in local profile backup/export when the user
  chooses.

## 5. Non-goals

- Editing the shared F-Droid or store catalog.
- Sending corrections to a server or other users automatically.
- Treating a correction as a security verification.
- Hiding the original automatic match entirely; it should remain inspectable.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F20-R1 | MUST | A user can replace an automatic match with another app key. | The corrected match is shown after returning to the screen. |
| F20-R2 | MUST | A user can mark an installed app as having no suitable match. | Future scans do not suggest the rejected app unless the override is removed. |
| F20-R3 | MUST | Overrides persist across restarts and index refreshes. | Refresh the index; the override still applies. |
| F20-R4 | MUST | Corrected matches are visibly labeled as user corrections. | A badge or label distinguishes them from automatic matches. |
| F20-R5 | MUST | A correction can be undone without losing the original automatic result. | Undo restores the previous match. |
| F20-R6 | SHOULD | The correction reason is optional and private by default. | The reason is absent from shared collections unless explicitly included. |
| F20-R7 | SHOULD | Overrides can be exported and imported with the profile backup. | Import preview lists override changes before applying. |
| F20-R8 | MAY | A correction can be scoped to a store. | The same package from another store can still appear separately. |

### Implementation notes (2026-10-28)

- Merged in PR #2 (`9e55acf`). The PR also carried S11 because the local `main`
  used as its base was ahead of `origin/main`.
- The match screen hosts the correction card and editor. Search is limited to
  cached catalogs from enabled stores and works offline.
- A correction replaces the displayed result, while the automatic result stays
  visible under an “automatic result” heading. A corrected badge identifies the
  user override.
- A no-match correction makes inventory classification `UNKNOWN` and suppresses
  the automatic suggestion without deleting it.
- If the target disappears from the cached index, the override is retained and
  the UI warns that the target is currently unavailable.
- Undo removes the override and restores the automatic result.
- Notes are private, excluded from sharing, and included in backup only as part
  of the profile export.
- Backup schema 2 adds match overrides. Version 1 backups are checksum-verified
  with the legacy manifest, then migrated. Merge keeps the newer
  `updatedAtEpochMillis`, replace removes local-only overrides, and skip adds
  only missing overrides.
- One override is stored per installed package key. Work-profile duplicates that
  share a package key therefore share an override; profile-scoped overrides are
  not implemented.
- Deferred: store-scoped overrides (F20-R8), shareable correction files,
  low-confidence correction prompts, and multiple overrides per installed app.

## 7. Edge cases

- Correcting a match to an app that later disappears from the index.
- Two installed profiles with the same package ID.
- An override that points to the same package as the installed app.
- An imported override with an unknown feature/schema version.
- A store refresh changes the target's display name.

## 8. Resolved decisions

1. Corrections are not shareable as a separate “match fixes” file in this
   version. They travel only through profile backup.
2. The app does not prompt for a correction when confidence is low. The user
   starts a correction from the match screen.
3. Multiple overrides per installed app are not allowed. Store-scoped overrides
   remain deferred under F20-R8.

# F20 — Matching Correction

| Field | Value |
| --- | --- |
| Status | Proposed |
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
- chosen target app key, or explicit `NO_MATCH`;
- optional preferred store;
- reason/note (private by default);
- createdAt / updatedAt;
- schema version.

Overrides are local, per device, and applied before display. They do not change
the store index or the upstream catalog.

## 4. Scope

- Correct an automatic match from the app detail screen.
- Choose “no suitable match”.
- Pick a specific package from search results.
- Show a “you corrected this” badge.
- Undo a correction and return to the automatic result.
- Include overrides in local backup/export when the user chooses.

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

## 7. Edge cases

- Correcting a match to an app that later disappears from the index.
- Two installed profiles with the same package ID.
- An override that points to the same package as the installed app.
- An imported override with an unknown feature/schema version.
- A store refresh changes the target's display name.

## 8. Open questions

1. Should corrections be shareable as a separate “match fixes” file?
2. Should the app suggest a correction when confidence is low?
3. Should multiple overrides be allowed per installed app (per store)?

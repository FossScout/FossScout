# F18 — Unified Store Search

| Field | Value |
| --- | --- |
| Status | Proposed |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | Store index/network capability for indexed stores; deep links for link-only stores |
| Depends on | F04, F05 |
| Related | F02, F03, F08, F20 |

## 1. Problem

FossScout currently starts from installed apps. Users also need to discover a FOSS
app by name when they do not know its package ID or which store carries it.
Searching each store separately is slow and hides the fact that the same app may
exist in several stores with different versions or build status.

## 2. User stories

- As a user, I want one search box that searches every enabled store.
- As a user, I want to see which store each result comes from.
- As a user, I want to open a result in its store or detail page.
- As a user, I want a clear explanation when a store can only be searched by
  opening its website.

## 3. Scope

- Search indexed stores locally from the cached index.
- Search link-only stores by opening the store's own search/deep link.
- Merge duplicate package results across stores without hiding store-specific
  metadata.
- Show package ID, app name, summary, store, version, and trust indicators.
- Filter by store, FOSS status, and availability.
- Keep search history local and optional.

## 4. Non-goals

- Scraping stores that do not expose an index or documented search handoff.
- Installing an APK from a search result.
- Replacing the store client's own search UI.
- Sending the query to FossScout servers (there are none).

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F18-R1 | MUST | Search runs across every enabled indexed store. | A query returns results from at least two enabled indexed stores when both contain matches. |
| F18-R2 | MUST | Each result identifies its source store. | Store name/icon is visible on every result row. |
| F18-R3 | MUST | Duplicate package results are grouped, not silently discarded. | Selecting a grouped result shows every store that carries the package. |
| F18-R4 | MUST | Link-only stores are searched through an explicit handoff, not parsed. | The UI offers “Search in <store>” and explains why results are not inline. |
| F18-R5 | MUST | Search works offline against cached indexes and states the cache age. | Airplane mode search returns cached results with an “as of” timestamp. |
| F18-R6 | SHOULD | A result can be favourited without installing it. | Favourite action appears before any install action. |
| F18-R7 | SHOULD | Search can be filtered by store and trust state. | Filters combine and persist for the session. |
| F18-R8 | MAY | Search history is stored locally and can be cleared. | Clearing history removes it from device storage. |

## 6. Edge cases

- Same package name from a fork and its upstream.
- A store index contains an app removed from the store's live catalog.
- A link-only store requires a locale or region.
- A query matches only anti-feature text or permissions.
- An index is stale or partially downloaded.
- No enabled store can search.

## 7. Privacy

Queries stay on the device. Opening a link-only store hands the query to that
store under the store's own privacy policy; the UI must say so before opening.

## 8. Open questions

1. Should grouping prefer the newest version or the user's preferred store?
2. Should search include apps with anti-features by default, or require a filter?
3. Should search scan source-code repositories as a separate result type?

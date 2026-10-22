# F04 — Store Registry and Adapters

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `CUSTOM_STORE_ADAPTERS`, `STORE_INDEX_NETWORK`; `DIRECT_APK_INSTALL` only for the install step |
| Depends on | F05 |
| Related | F06, F08, F09, F14, F16 |

## 1. Problem

F-Droid is not the only FOSS source. Users need predefined stores, the ability to
add a missing store, and the ability to define their own. Stores differ in what
they expose: a signed index, a live API, release pages, or only a website.

## 2. User stories

- As a user, I want F-Droid, IzzyOnDroid, and other FOSS stores available without
  manual setup.
- As a user, I want to add a store that FossScout does not know.
- As a user, I want to disable a store or change its priority.
- As a user, I want to know when a store is only partially supported.

## 3. Adapter types

| Adapter | Reads | Can match offline? | Can download APK? |
| --- | --- | --- | --- |
| F-Droid index v2 | Signed index, entry/diffs, v1 fallback | Yes | Yes |
| Live API (e.g. Accrescent gRPC) | Listings, package info, download info | Cached results only | Yes, where API provides it |
| Generic JSON | User-defined mapping | If cached | Only if URLs are present |
| Source repository | GitHub/GitLab/Codeberg/SourceHut releases, direct APK, HTML | Per app only | Yes, if resolved |
| Deep link / search template | Store page or search URL | No | No |

## 4. Scope

- Ship predefined descriptors for supported FOSS stores.
- Allow add, edit, disable, delete, reorder, import, and export.
- Detect adapter type from the URL where possible; let the user override.
- Validate URL, schema, signature, and reachability.
- Keep a per-store health record: last sync, item count, error, latency.
- Accept an unsupported store as **link-only** and explain the limitation.
- Never prefer a store based on FOSS/proprietary ideology.

## 5. Non-goals

- Implementing a general web scraper for every store.
- Bypassing a store's authentication or terms.
- Shipping credentials for a store.
- Treating a search URL as a catalog index.
- Adding a store to the F-Droid client if it is not F-Droid-compatible.

## 6. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F04-R1 | MUST | At least F-Droid and IzzyOnDroid ship as predefined F-Droid-compatible stores. | Fresh install lists both with current URLs and fingerprints. |
| F04-R2 | MUST | A user can add a custom store with name, URL, and adapter type. | A valid F-Droid repo URL is usable after adding. |
| F04-R3 | MUST | An unsupported store is saved as link-only with a reason. | The entry persists, opens the homepage, and is excluded from matching with a visible gap. |
| F04-R4 | MUST | Stores can be enabled, disabled, reordered, edited, and deleted. | Changes affect matching after the next query without app restart. |
| F04-R5 | MUST | A store's descriptor can be exported and shared. | Export produces a portable, versioned descriptor. |
| F04-R6 | MUST | HTTP-only stores require an explicit warning and opt-in. | An HTTP URL is not silently accepted as trusted. |
| F04-R7 | SHOULD | Fingerprint changes are surfaced as a security event. | A changed fingerprint blocks sync until the user approves or removes the store. |
| F04-R8 | SHOULD | Store health is visible per store. | Last sync, count, and error are shown in store details. |
| F04-R9 | MAY | The user can import a store descriptor from a shared collection. | Import previews the store and requires confirmation. |

## 7. Data

`StoreDescriptor`: id, display name, homepage, adapter type, index URL, API URL,
repository URL, fingerprint, enabled, priority, trust note, last sync, health,
schema version. Secrets, if ever supported, belong in encrypted storage and are
never exported in a shared collection.

## 8. Edge cases

- Store URL ends with or without `/repo`.
- Redirects, mirrors, and CDN URLs.
- Repository key rotation.
- A store that serves a valid index but no APKs.
- A store that serves APKs but no index.
- Duplicate store URLs with different names.
- A store that is temporarily offline.
- A custom store requiring headers or a token.

## 9. Open questions

1. Which predefined FOSS stores ship in v1?
2. Is a generic JSON adapter worth the configuration burden, or should v1 ship
   only F-Droid-compatible, Accrescent, source, and deep-link adapters?
3. Should store priority affect match selection, install suggestions, or both?
4. How are per-store headers/tokens stored if enterprise users need them?

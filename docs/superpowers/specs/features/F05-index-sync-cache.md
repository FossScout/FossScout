# F05 — Index Sync and Offline Cache

| Field | Value |
| --- | --- |
| Status | Draft |
| Availability | **General** |
| Profiles | `playstore`, `fdroid`, `direct`, `custom` |
| Required capabilities | `STORE_INDEX_NETWORK`; local storage |
| Depends on | F04 |
| Related | F02, F06, F14 |

## 1. Problem

Checks should be fast and work semi-offline. Full store indexes are large
(F-Droid’s v2 index was ~66.5 MB and IzzyOnDroid’s ~14.4 MB on 2026-10-22), so
downloading them repeatedly is wasteful. At the same time, stale data must never
be presented as current.

## 2. User stories

- As a user, I want matches to work without a network after a sync.
- As a user, I want to know how old the data is.
- As a user, I want to cancel a slow or costly sync.
- As a user, I want a failed sync to leave the previous data usable.

## 3. Scope

- Fetch F-Droid `entry.json` and use diffs where possible; fall back to
  `index-v2.json` or `index-v1.jar`.
- Use HTTP conditional requests (`ETag`, `Last-Modified`) and byte ranges where
  supported.
- Support live APIs with a separate cache policy.
- Store freshness, ETag, version, package count, size, and last error per store.
- Sync on demand and on an optional schedule with network/charging constraints.
- Make updates atomic and rollback-safe.
- Expose storage usage and a cleanup action.

## 4. Non-goals

- Downloading every store index in the background by default.
- Keeping unbounded historical snapshots.
- Using an unsigned index for security-sensitive decisions.
- Presenting cached data without a timestamp.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F05-R1 | MUST | A full index is verified before it replaces the active snapshot. | A corrupt or unsigned index leaves the old snapshot active. |
| F05-R2 | MUST | Diffs/conditional requests are used when the store supports them. | A second sync transfers substantially less than a full index. |
| F05-R3 | MUST | Every cached store shows last successful sync and freshness. | UI distinguishes fresh, stale, and never-synced. |
| F05-R4 | MUST | Sync can be cancelled without corrupting data. | Cancel mid-download leaves the previous snapshot usable. |
| F05-R5 | MUST | Storage usage is visible and bounded by a user setting. | Exceeding the limit triggers cleanup or a clear warning. |
| F05-R6 | SHOULD | Scheduled sync is opt-in and respects metered/charging settings. | Default install performs no background index download. |
| F05-R7 | SHOULD | A live-API cache has its own TTL and is never treated as a full offline catalog. | API results are marked “live/cached” separately from indexed stores. |
| F05-R8 | MAY | The user can pin a snapshot for reproducibility. | A pinned snapshot is not replaced until unpinned. |

## 6. Data

`StoreSnapshot`: store ID, schema version, fetched at, ETag, index version,
package count, payload path, signature status, size, source URL. `SyncJob`:
state, progress, bytes, error, started/finished.

## 7. Edge cases

- Server ignores range requests.
- Diff chain is incomplete; force a full download.
- Index changes during download.
- Clock skew and future timestamps.
- Low storage.
- Network drops after headers.
- Store returns HTML instead of JSON.
- Multiple mirrors with different freshness.

## 8. Open questions

1. What is the default storage budget for indexes?
2. Should the app ship a small bundled starter index for first-run offline use?
3. How often should scheduled sync run by default, if at all?
4. Should snapshots be shared between profiles or isolated per app?

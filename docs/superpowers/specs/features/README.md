# Feature Index

Status: **Draft**
Date: 2026-10-22
Master design: `../2026-10-22-fossscout-feature-scope-design.md`
Build profiles: `../2026-10-22-build-profiles-and-optional-features.md`
Implementation status: [`STATUS.md`](STATUS.md)
Feature sessions: [`../../sessions/README.md`](../../sessions/README.md)

Each feature has its own file with availability, required capabilities,
requirements, acceptance criteria, data/privacy notes, and open questions.

## Availability legend

| Value | Meaning |
| --- | --- |
| **General** | Play-safe baseline; included in the `playstore` profile. |
| **Restricted** | Requires a restricted capability; excluded from `playstore`. |
| **General + restricted enhancement** | Play-safe baseline plus extra behaviour in `fdroid`/`direct`. |

## Feature matrix

| ID | Feature | Availability | Play | F-Droid | Direct | Depends on |
| --- | --- | --- | --- | --- | --- | --- |
| F01 | Installed app inventory | General + restricted enhancement | Manual/selected | Full scan | Full scan | F02 |
| F02 | FOSS matching | General | Yes | Yes | Yes | F04, F05 |
| F03 | Alternative suggestions | General | Yes | Yes | Yes | F02 |
| F04 | Store registry and adapters | General | Yes | Yes | Yes | F05 |
| F05 | Index sync and offline cache | General | Yes | Yes | Yes | F04 |
| F06 | Coverage and confidence | General | Yes | Yes | Yes | F02, F05 |
| F07 | Replacement guidance | General | Yes | Yes | Yes | F02, F09, F14 |
| F08 | Companion store handoff | General | Yes | Yes | Yes | F04 |
| F09 | Standalone store client | Restricted | No | Yes | Yes | F05, F14 |
| F10 | Install-time suggestion | Restricted | No | Yes | Yes | F01, F02, F08 |
| F11 | Favourites and notes | General | Yes | Yes | Yes | F02 |
| F12 | Collections and sharing | General | Yes | Yes | Yes | F11 |
| F13 | Backup and migration | General | Yes | Yes | Yes | F11, F12 |
| F14 | Security verification | General | Verify only | Verify + install | Verify + install | F04, F05 |
| F15 | Update tracking | General + restricted enhancement | Notify + handoff | Notify + install | Notify + install | F01, F05, F09 |
| F16 | Contribution hooks | General | Yes | Yes | Yes | F04, F06 |
| F17 | Feature configuration | General | Yes | Yes | Yes | Build profiles |
| F18 | Unified store search | General | Yes | Yes | Yes | F04, F05 |
| F19 | Permission and tracker diff | General | Yes | Yes | Yes | F02, F03, F06 |
| F20 | Matching correction | General | Yes | Yes | Yes | F02 |
| F21 | Trust badges | General | Yes | Yes | Yes | F04, F05, F14 |

## Notes

- “General” describes the feature’s policy posture, not its implementation
  location. A general feature can have restricted enhancements when a capability
  exists.
- A profile may expose the same feature with a smaller scope. For example, F01 is
  a manual check in `playstore` and a full inventory in `fdroid`.
- Every feature document must state whether its requirements are **MUST**,
  **SHOULD**, or **MAY**, and must include acceptance criteria.
- F18–F21 were added after the first feature library as quality/completeness
  candidates: unified store search, permission/tracker comparison, local
  matching correction, and trust badges. They are proposed for the post-v1
  roadmap, not the first integrated foundation.

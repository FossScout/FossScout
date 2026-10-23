# S03 — F05 Index Sync/Cache + F14 Verification Core

| Field | Value |
| --- | --- |
| Status | **Ready** (S01 and S02 merged) |
| Features | F05, F14 (verification core; trust UI is S10) |
| Depends on | S01, S02 |
| Can run in parallel with | S06 |
| Profiles | `playstore`, `fdroid` |
| Specs | [`F05-index-sync-cache.md`](../specs/features/F05-index-sync-cache.md), [`F14-security-verification.md`](../specs/features/F14-security-verification.md) |

## Goal

Fetch, verify, and cache a real F-Droid-compatible index, then expose freshness
and sync state to the rest of the app. No index may become active before it
passes verification.

This is the largest infrastructure session. Start with a short technical spike
on the current F-Droid v2 index format and signature flow before designing the
parser.

## Scope

- Define shared contracts:
  - `StoreSnapshot` (store, schema, fetched time, ETag/index version, package
    count, payload location, signature status, size, source URL);
  - `SyncJob` (state, progress, bytes, error, start/finish);
  - `IndexVerifier` and `VerificationResult`;
  - `TrustRecord` for local verification history.
- Implement the F-Droid v2 adapter:
  - fetch the index over HTTPS;
  - support conditional requests/ETag and diffs where the format supports them;
  - stream/large-file handling suitable for the current F-Droid index size;
  - parse package/listing metadata needed by F02/F03/F06/F18;
  - verify the signed index before activation;
  - replace snapshots atomically and keep the previous snapshot on failure;
  - support cancellation without corrupting the active snapshot.
- Add snapshot metadata persistence and payload storage with a bounded storage
  budget and cleanup/warning behaviour.
- Enforce HTTPS by default; HTTP only after the S02 opt-in.
- Pin and check repository fingerprints; a changed fingerprint blocks sync until
  review.
- Show last successful sync and `fresh` / `stale` / `never synced` state.
- Complete F04-R2 by making a valid added F-Droid repository usable after sync.
- Add fixture-based tests, including signed/tampered indexes and cancellation.

## Out of scope

- APK download, APK hash/size checks, and signer continuity checks (F09 is
  deferred; keep the contract extensible).
- Security details UI and trust audit screen (S10).
- Live API, generic JSON, and source-repository adapters.
- Scheduled/background sync unless it falls out naturally; it is a SHOULD.
- Matching, alternatives, and coverage presentation (S05).

## Key files

- S02's `core/store-api` contracts
- new index/cache module or `core/data` implementation
- `feature:stores` for sync status/freshness UI
- `app/src/main/java/com/fossscout/app/AppContainer.kt`
- manifest/network security config if HTTPS policy requires it

## Acceptance criteria

- **F05-R1:** corrupt/tampered/unsigned index never replaces the active snapshot.
- **F05-R2:** a second sync uses a conditional/diff path and transfers less than
  a full index where the upstream format supports it.
- **F05-R3:** every cached store shows last successful sync and freshness.
- **F05-R4:** cancellation leaves the previous snapshot intact.
- **F05-R5:** storage use is visible and bounded by a setting; cleanup or a clear
  warning occurs at the limit.
- **F04-R2:** a valid added F-Droid repository becomes usable after sync.
- **F14-R1:** a repository without a pinned fingerprint is marked unverified.
- **F14-R2:** index signatures are verified before metadata is trusted.
- **F14-R5:** HTTPS is required unless the user explicitly opted into HTTP.
- **F14-R6:** trust evidence (fingerprint, signature, source) is available to the
  UI layer.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Use local fixtures and a fake HTTP client. Do not make live network calls in
unit tests. Record the exact upstream index format/version validated by the
spike in the completion note.

## Risks and decisions

- The documented index is large (~66.5 MB in the spec). Do not load it fully into
  memory; design for streaming and bounded storage.
- Signature verification is security-critical. If the full parser + signed
  verification does not fit in one session, split into “sync/parse” and
  “activation/verification” without activating unverified data.
- Do not add APK verification claims to v1. F14-R3/R4 remain post-F09.
- The F-Droid v2 format and signature process must be checked against current
  upstream documentation at implementation time, not assumed from this brief.

## Completion record

Update `STATUS.md` after merge:

- commit:
- upstream format/version verified:
- tests:
- F05/F14 criteria completed:
- remaining gaps:

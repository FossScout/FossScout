# S03 — F05 Index Sync/Cache + F14 Verification Core

| Field | Value |
| --- | --- |
| Status | **Done** (F05 and F14 verification core; trust audit UI remains in S10) |
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

Implemented on `feat/s03-index-sync-verification` on 2026-10-23.

- commit: `6ee4e4ad71be09e012ebb62472c0e0e1593b6908`
  (`feat(index): add S03 verified index sync and cache`); this completion record
  is a follow-up commit on the same branch. `STATUS.md` was not edited by this
  session.
- modules: new pure-Kotlin/JVM `core:index` module for the shared snapshot, job,
  verifier, trust, parser, payload, and F-Droid v2 sync/repository code. The
  existing `core:data` module owns versioned DataStore metadata persistence.
  `feature:stores` renders sync/freshness/trust/storage state, and `app` wires
  the repository, `INTERNET` permission, and the real integer storage-budget
  setting.
- upstream format/version verified: the spike checked the current signed F-Droid
  v2 repository flow. The adapter fetches `entry.jar`, verifies its JAR
  signature and signer-certificate SHA-256, then verifies the full index/diff
  SHA-256 and size declared by the signed `entry.json`. The observed
  `entry.json` schema `version` was **30000**; it carries `timestamp`, `maxAge`,
  `index {name, sha256, size, numPackages}`, and `diffs {oldTimestamp: ...}`.
  The full `index-v2.json` contains `repo` plus `packages` with each package's
  `metadata` and `versions`. The parser consumes that metadata for F02/F03/F06/
  F18 and records upstream timestamps and diff versions. No live network is used
  by tests; fixtures create real signed JARs with the JDK signing tools.
- security/activation boundary: downloads go to temporary files first. Only a
  cryptographically signed entry whose signer matches the pinned fingerprint can
  become `VERIFIED`; a valid signed entry without a pin can become active but is
  `UNVERIFIED`. Unsigned, invalid, tampered, hash/size-mismatched, corrupt, and
  parse-failed indexes are never activatable. A changed or removed pin blocks
  sync before any request. Cancellation, I/O failure, and storage-limit failures
  leave the previous snapshot and payload untouched. Payload replacement is
  atomic, and same-timestamp/304 reuse also requires the signed index hash and
  active payload identity to match; otherwise the adapter retries unconditionally.
- sync/cache implementation: HTTPS is the default and HTTP is allowed only for
  descriptors that carry the S02 opt-in. The adapter sends `ETag`/
  `If-Modified-Since`, reuses an unchanged snapshot, and uses signed diffs when
  the previous timestamp is available and smaller. Full and diff indexes stream
  through a custom JSON reader into normalized JSON Lines instead of loading the
  whole upstream file; diffs load only changed packages and preserve unaffected
  packages. Non-storage diff failures fall back to a verified full index.
- persistence/storage: `IndexStateCodec` version-checks snapshot, job, and trust
  records before writing. Payloads are stored under the app-private snapshot
  directory with a byte budget. `INDEX_CACHE` now declares a real integer
  `storage_budget_mb` feature setting (16–2048 MB, default 256 MB), not a text
  setting. Storage use is visible in Stores, new writes over budget are rejected
  with a clear error, and cleanup removes unreferenced payloads while preserving
  active snapshots.
- Stores/F04-R2: every cached F-Droid v2 store card shows last successful sync,
  `fresh`/`stale`/`never synced`, signature/trust evidence, sync progress,
  cancellation, and mapped failure text; raw domain exceptions are not shown.
  A newly added, valid F-Droid-compatible repository becomes usable through the
  cached-index adapter after sync.
- tests/build: `./gradlew test assemblePlaystoreDebug assembleFdroidDebug lint`
  **passed** on the committed source. `test`: **328 test executions across 55
  suites; 0 failures, 0 errors, 0 skipped** (JVM and Android debug/release
  variants counted separately). The new `core:index` module contributes 37
  fixture-based tests covering valid signed activation, tampered/corrupt/
  unsigned rejection, no-pin `UNVERIFIED`, fingerprint change, HTTP policy,
  conditional/ETag reuse, diff transfer, stale-validator retry, cancellation,
  storage budget/cleanup, payload-path safety, malformed metadata, and the
  F04-R2 adapter path.
- Play merged manifest inspection
  (`app/build/intermediates/merged_manifests/playstoreDebug/processPlaystoreDebugManifest/AndroidManifest.xml`)
  **passed**: only `INTERNET` plus the framework dynamic-receiver permission were
  added. No `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`,
  `BIND_ACCESSIBILITY_SERVICE`, accessibility service, `SYSTEM_ALERT_WINDOW`, or
  `MANAGE_EXTERNAL_STORAGE` was added.
- F05 criteria completed: **R1** corrupt/tampered/unsigned data never replaces
  the active snapshot; **R2** a repeat sync uses conditional/diff transfer and
  moves less than a full index; **R3** cached stores show last sync and
  freshness; **R4** cancellation preserves the active snapshot; **R5** storage
  use is visible, bounded by the integer setting, and cleanup/warning behaviour
  is present.
- F04-R2 completed: a valid added F-Droid repository is usable after sync.
- F14 criteria completed: **R1** no pinned fingerprint ⇒ `UNVERIFIED`; **R2**
  signatures and signed hashes are checked before metadata is trusted or
  activated; **R5** HTTPS is required without the explicit S02 opt-in; **R6**
  expected/actual fingerprint, signature status, source URL, index version/
  timestamp, and failure are retained in `TrustRecord` and exposed to the UI.
- remaining gaps: F14-R3/R4 (APK hash/size and signer continuity) remain
  deferred with F09, so this session makes **no APK verification claim**. The
  security-details/trust-audit screen (S10), scheduled/background sync, live API,
  generic JSON/source-repository adapters, matching/alternatives/coverage (S05),
  and real-device UI/network verification were not added. Trust for a valid
  index with no pinned fingerprint stays explicitly `UNVERIFIED` until a pin is
  reviewed.

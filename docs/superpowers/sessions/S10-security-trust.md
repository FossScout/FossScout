# S10 — F14 Security Trust UI and Audit

| Field | Value |
| --- | --- |
| Status | **Blocked by S05** |
| Features | F14 (verification UI/audit; core is S03) |
| Depends on | S03, S05 |
| Can run in parallel with | S08, S09, S11 |
| Profiles | `playstore`, `fdroid` |
| Spec | [`F14-security-verification.md`](../specs/features/F14-security-verification.md) |

## Goal

Make verification state visible and understandable: why a store is trusted or
not, what was verified, when, and what failed. Do not imply APK verification that
v1 does not perform.

## Scope

- Add a security details surface for store/index trust:
  - pinned fingerprint and source;
  - index signature status;
  - HTTPS/HTTP opt-in state;
  - last successful verification/sync;
  - failure reason and local verification history.
- Distinguish clearly between:
  - not verified / no pinned fingerprint;
  - verification failed;
  - verified.
- Integrate trust state into match/detail surfaces without inventing a single
  opaque score.
- Add a key/fingerprint rotation review flow where cheap (SHOULD): block sync
  and require approval/removal.
- Keep the audit trail local and bounded.
- Expose enough state for F16 reports and F21 badges later.

## Out of scope

- APK hash/size and signer-continuity enforcement (F09 post-v1).
- Trust badges (F21), permission/tracker diff (F19).
- A general-purpose security score.
- Uploading verification results or store URLs to a server.

## Key files

- S03 `IndexVerifier`, `VerificationResult`, `TrustRecord`
- S02 store registry and fingerprint model
- `feature:security` or `feature:stores` security panel
- S05 match/detail integration
- app navigation/composition root

## Acceptance criteria

- **F14-R1 (UI):** an unpinned repository is visibly marked unverified.
- **F14-R2 (UI):** index signature status and verification time are inspectable.
- **F14-R5 (UI):** HTTP is visibly opt-in and not presented as trusted by default.
- **F14-R6:** the user can inspect fingerprint, signature, source, and failure
  reason for each store.
- **F14-R7 (SHOULD):** fingerprint change requires review before sync resumes.
- “Not verified” and “verification failed” are distinct states.
- No UI claims APK integrity or signer continuity in v1.

## Test and build

```sh
./gradlew test
./gradlew assemblePlaystoreDebug assembleFdroidDebug
```

Add UI-state tests for unpinned, verified, failed, HTTP-opted-in, and rotation
states. Add a test that the audit trail is local and bounded.

## Risks and decisions

- Security UI must not overstate. If a check was not performed, say so.
- Do not expose sensitive tokens or local paths in the details.
- F14-R3/R4 remain explicitly deferred until F09 exists. Record this in the
  feature note/status.

## Completion record

Update `STATUS.md` after merge:

- commit:
- tests:
- F14 UI criteria completed:
- post-F09 gaps:

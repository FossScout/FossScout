# F14 — Security Verification

| Field | Value |
| --- | --- |
| Status | Draft (v1 index trust UI/audit implemented; post-v1 APK checks deferred) |
| Availability | **General** for verification; **Restricted** for direct install |
| Profiles | `playstore` (verify/handoff), `fdroid` (verify/install), `direct` (verify/install), `custom` |
| Required capabilities | `STORE_INDEX_NETWORK`; `DIRECT_APK_INSTALL` only for install |
| Depends on | F04, F05 |
| Related | F07, F09, F10 |

## 1. Problem

A privacy tool that recommends installs becomes a malware vector if it trusts
the wrong index or APK. F-Droid-compatible stores sign their indexes; users can
pin a repository fingerprint; APK hashes and signers can be checked. FossScout
must make those checks visible and must fail closed.

## 2. User stories

- As a user, I want to know that the index came from the store I trust.
- As a user, I want to know that the APK matches what the index says.
- As a user, I want a clear warning when a key changes.
- As a user, I want to understand why an install is blocked.

## 3. Scope

- Pin repository fingerprints.
- Verify signed indexes and entry/diff metadata.
- Verify APK SHA-256 and size against the signed index.
- Show APK signer and check continuity with the installed app.
- Require HTTPS by default; HTTP requires explicit, warned opt-in.
- Detect fingerprint/key rotation and block until the user decides.
- Provide a per-item “security details” panel.
- Keep verification results in the local audit trail.

## 4. Non-goals

- Proving that an app is free of vulnerabilities.
- Replacing Android's signature verification.
- Bypassing certificate pinning or warnings.
- Trusting a store because it is labelled FOSS.
- Verifying a store with no available public key.

## 5. Requirements

| ID | Priority | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| F14-R1 | MUST | A repository without a pinned fingerprint is marked unverified. | Its results cannot be used for one-tap install. |
| F14-R2 | MUST | Index signatures are verified before metadata is trusted. | A tampered index fails verification and is not activated. |
| F14-R3 | MUST | APK hash and size are checked against the signed index. | A modified APK is rejected. |
| F14-R4 | MUST | A signer change blocks install and routes to F07. | A different signer shows the uninstall/data-loss path. |
| F14-R5 | MUST | HTTPS is required by default. | An HTTP store is unusable until the user explicitly opts in. |
| F14-R6 | MUST | The user can inspect why a source is trusted or not. | Security details list fingerprint, signature, hash, and signer. |
| F14-R7 | SHOULD | Key rotation has a review flow rather than silent acceptance. | Old and new fingerprints are shown; user approves or removes. |
| F14-R8 | SHOULD | Verification failures are logged locally with a timestamp. | The audit trail can be exported without personal data. |
| F14-R9 | MAY | F-Droid binary transparency / reproducibility metadata is displayed. | Available evidence is linked, not overstated. |

## 5a. v1 implementation status (S10)

| Requirement | v1 status |
| --- | --- |
| F14-R1 | **Done.** Unpinned repositories activate only as `UNVERIFIED` and are visibly marked unverified. |
| F14-R2 | **Done.** Signed entry/index verification, signer-fingerprint matching, and index hash/size checks run before activation. Verification time and signature status are inspectable. |
| F14-R3 | **Deferred with F09.** v1 does not download APKs, so APK hash/size checks are not performed. |
| F14-R4 | **Deferred with F09.** v1 has no install path; signer continuity against installed apps is not enforced. |
| F14-R5 | **Done.** HTTPS is required by default; plain HTTP is blocked unless the user explicitly opts in, and the opt-in state is visible. |
| F14-R6 | **Done.** Security details show the pinned/expected fingerprint, index signer, signature status, verification source, index SHA-256/version, HTTP state, last successful sync, and failure reason. |
| F14-R7 | **Done.** A changed or removed pin, or a signer mismatch, requires review. Old and new fingerprints are shown; the user can approve the new fingerprint (which clears old active trust and re-syncs) or remove the store. |
| F14-R8 | **Partial.** Verification results are kept in a local, timestamped, bounded audit trail (100 entries) visible in the security details panel. Export is deferred to F16. |
| F14-R9 | **Not implemented.** Binary-transparency/reproducibility metadata is not displayed. |

The UI explicitly states that APK hash and signer continuity are not checked in
v1. There is no single opaque security score.

## 6. Data

Store fingerprint, index signature status, APK hash, signer certificate,
verification time, failure reason. No personal data.

## 7. Edge cases

- Repository key rotation.
- Signature lineage accepted by Android but not described by the index.
- Expired certificate.
- Index signed with an unexpected key.
- APK served from a mirror with a different hash.
- Network interception / captive portal.
- Clock skew affecting certificate validity.
- A store that publishes hashes but not signatures.

## 8. Open questions

1. Are unverified third-party repos allowed in a “trust anyway” mode?
2. How much verification detail belongs in the main UI versus an advanced panel?
3. Should FossScout support PGP verification in addition to JAR signatures?

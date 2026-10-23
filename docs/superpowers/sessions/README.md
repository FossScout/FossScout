# Feature Implementation Sessions

This directory turns the remaining v1 feature work into **independent session
briefs**. Each file is written so it can be pasted into a new agent session with
minimal extra context.

Start here:

1. Read [`STATUS.md`](../specs/features/STATUS.md) for current implementation
   state and the verification baseline.
2. Read the session brief and the feature specs it names.
3. Check `git status` and create a branch for the session.
4. Do not start a blocked session before its dependency lands.

## Session map

| Session | Scope | Status | Depends on | Can run in parallel with | Wave |
| --- | --- | --- | --- | --- | --- |
| [S01](S01-feature-configuration.md) | F17 feature configuration completion | **Ready** | Foundation | S02, S04, S06 after S01 | 0 |
| [S02](S02-store-registry.md) | F04 store contracts, registry, custom stores | **Ready** | Foundation | S04, S06 | 1 |
| [S03](S03-index-sync-verification.md) | F05 index sync/cache + F14 verification core | **Blocked by S02** | S01, S02 | S07 | 2 |
| [S04](S04-installed-inventory.md) | F01 installed inventory, manual/selected + full scan | **Ready** | Foundation | S02, S06 | 1 |
| [S05](S05-matching-alternatives-coverage.md) | F02/F03/F06 matching, alternatives, coverage | **Blocked by S03, S04** | S02, S03, S04 | S09 | 3 |
| [S06](S06-favourites-completion.md) | F11 favourites completion | **Ready** | Foundation; installed badge finalization uses S04 | S02, S04 | 1 |
| [S07](S07-collections-sharing.md) | F12 collections, sharing, import | **Blocked by S06** | S06 | S03 | 2 |
| [S08](S08-replacement-handoff.md) | F08 handoff + F07 guidance-only replacement | **Blocked by S05** | S02, S05 | S09, S10, S11 | 4 |
| [S09](S09-backup-migration.md) | F13 backup and migration | **Blocked by S07** | S01, S02, S03, S06, S07 | S05 | 3 |
| [S10](S10-security-trust.md) | F14 security details and trust audit UI | **Blocked by S05** | S03, S05 | S08, S09, S11 | 4 |
| [S11](S11-contribution-hooks.md) | F16 contribution reports and redaction | **Blocked by S05** | S02, S05 | S08, S09, S10 | 4 |
| [S12](S12-post-v1-backlog.md) | F09/F10/F15/F18–F21 | **Post-v1** | Separate decisions | None | 5 |

## Recommended order

```text
S01
  ├── S02 ── S03 ──┐
  ├── S04 ─────────┼── S05 ──┬── S08
  └── S06 ── S07 ──┘         ├── S10
        └── S09 ─────────────┘
                            └── S11
S12 remains post-v1.
```

The single-agent order is **S01 → S02 → S03 → S04 → S05 → S06 → S07 → S09 →
S08 → S10 → S11**. If multiple sessions run in parallel, use the wave table and
avoid editing the same app wiring files without coordinating.

## Planning assumptions

These are the least-risk assumptions that make the sessions executable. They do
not create new product decisions; confirm or override them before the affected
session starts.

1. **v1 ships `playstore` and `fdroid` only.** `direct` and `custom` are future
   profiles.
2. **Play inventory is manual/selected only.** Do not request
   `QUERY_ALL_PACKAGES` in the Play profile. The F-Droid profile may declare the
   broad visibility it needs, but only in the F-Droid manifest.
3. **v1 still does not install APKs and has no AccessibilityService.** F09 and
   F10 stay deferred.
4. **F07 is guidance and handoff only in v1.** No install or uninstall action.
5. **F14 v1 verifies repositories, index signatures, fingerprints, and HTTPS.**
   APK hash/signer enforcement waits for F09.
6. **v1 stores are F-Droid and IzzyOnDroid through the F-Droid index adapter.**
   Unsupported custom stores are link-only with a visible reason.
7. **F17's descriptor is the single source of truth** for availability and
   settings. Profile code supplies capabilities, not a duplicate enabled-feature
   list.
8. **Collections reference canonical `AppKey`s**, not separate favourite IDs.
   Persisted schemas are versioned.
9. **Sharing v1 is JSON + human-readable text + file.** QR/deep links are
   optional later. Notes are excluded unless explicitly included.
10. **Backup encryption is optional, passphrase-protected when enabled.** The
    default is a product decision still to confirm; do not silently encrypt or
    silently omit a manifest.

## Scope guardrails

- Use the `F01`–`F21` feature files, not the older `F1`–`F24` candidate table in
  the master design.
- Do not add F09/F10/F15/F18–F21 behavior to a v1 session.
- Do not add accounts, analytics, device identifiers, or a server.
- Keep feature modules independent; put shared contracts in `core:*`.
- All user-facing text belongs in `res/values/strings.xml`.
- No restricted permission or service may enter the Play merged manifest.
- Do not disturb the untracked `screenshots/` or `SCREENSHOT_CHECKLIST.md`.

## Definition of done for every session

- [ ] The session's MUST acceptance criteria are implemented or explicitly
      recorded as deferred with a reason.
- [ ] New behavior has unit tests; persistence has codec/repository tests.
- [ ] `./gradlew test assemblePlaystoreDebug assembleFdroidDebug` passes.
- [ ] Play merged manifest contains no restricted capability added by the work.
- [ ] `STATUS.md` is updated with the baseline commit, status, tests, and gaps.
- [ ] The session brief is updated if scope was split or changed.
- [ ] No unrelated feature work is mixed into the branch.

Full lint may be unavailable in this sandbox because some Android test
dependencies cannot be resolved. Record that caveat; do not treat it as a code
failure.

## Starting prompt for a new session

Use this template and fill in the session:

```text
Implement session <Sxx> for FossScout.

Read, in order:
- docs/superpowers/specs/features/STATUS.md
- docs/superpowers/sessions/<Sxx-file>.md
- the feature specs named in that brief

Start from the current main branch. Confirm the dependency session(s) are
already merged. Follow the brief's scope, acceptance criteria, tests, and
guardrails. Do not implement deferred features. At the end, run the checks in
the brief and update STATUS.md.
```

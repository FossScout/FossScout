# F04-R8 Store Health Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each store's current health — last successful sync, cached item count, sync duration, and error — in the store list.

**Architecture:** Keep the existing `StoreHealth` model. Capture elapsed sync time in `StoresViewModel`, derive health through a small pure function, and render every available health field together in `StoresScreen`. No persistent sync timeline and no schema change.

**Tech Stack:** Kotlin, coroutines, Jetpack Compose Material 3, JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/features/F04-store-registry.md` (F04-R8, scope: last sync, item count, error, latency)

## Global Constraints

- No new dependencies or Gradle changes.
- No backup, export, registry, or `StoreHealth` schema change.
- Health remains local-only and is not transmitted.
- Reuse existing `StoreHealth` fields and existing relative-time formatting.
- Keep the existing F-Droid sync block and its last-sync/freshness display.
- Follow the existing native execution style and Conventional Commits.

## Review Focus

- A failed sync must keep the previous last sync and item count while adding the error and duration.
- An unsynced store must not show a fabricated last sync or duration.
- F-Droid must not show the same last-sync line twice; its existing sync block already owns that line.
- Negative or zero elapsed time must not produce a negative duration.
- A store with only an error, or only an item count, must still render a coherent summary.

---

### Task 1: Pure sync-health mapping

**Files:**
- Create: `feature/stores/src/main/java/com/fossscout/feature/stores/StoreHealthMapper.kt`
- Test: `feature/stores/src/test/java/com/fossscout/feature/stores/StoresViewModelTest.kt`

**Interfaces:**
- Produces: `internal fun syncHealth(previous: StoreHealth, state: SyncState, snapshot: StoreSnapshot?, error: String?, latencyMillis: Long): StoreHealth`
- Produces: `internal fun formatSyncLatency(millis: Long): String`

- [ ] **Step 1: Write failing mapper tests**

Add tests covering:
- successful sync: status `OK`, snapshot timestamp/count, latency, no error;
- failed sync: status `ERROR`, previous timestamp/count retained, error and latency set;
- negative latency: clamped to `0`;
- formatting: `0 ms`, `999 ms`, `1.0 s`, `1.2 s`.

- [ ] **Step 2: Run the tests and verify failure**

Run: `./gradlew :feature:stores:testDebugUnitTest --tests "*StoresViewModelTest"`

Expected: compilation fails because `syncHealth` and `formatSyncLatency` do not exist.

- [ ] **Step 3: Implement the mapper**

`syncHealth` returns a new `StoreHealth` on success. On failure it copies `previous`, changes status/error/latency, and preserves timestamp/count. Clamp latency with `coerceAtLeast(0L)`. `formatSyncLatency` uses `< 1000 ms` as milliseconds and otherwise one decimal second with `Locale.ROOT`.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew :feature:stores:testDebugUnitTest --tests "*StoresViewModelTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/stores/src/main/java/com/fossscout/feature/stores/StoreHealthMapper.kt feature/stores/src/test/java/com/fossscout/feature/stores/StoresViewModelTest.kt
git commit -m "feat(stores): map sync health with latency"
```

### Task 2: Record sync duration in the ViewModel

**Files:**
- Modify: `feature/stores/src/main/java/com/fossscout/feature/stores/StoresViewModel.kt` (sync path)

**Interfaces:**
- Consumes: `syncHealth(...)` from Task 1.
- Produces: `StoreHealth.latencyMillis` populated for every completed sync result.

- [ ] **Step 1: Measure elapsed time around `syncRepository.sync(descriptor)`**

Use `System.nanoTime()` before the call and after it returns; convert to milliseconds. Keep the existing cancellation and exception handling.

- [ ] **Step 2: Replace the inline success/failure health construction with `syncHealth(...)`**

Pass `descriptor.health`, `result.job.state`, `result.snapshot`, the existing error fallback (`result.job.error?.kind?.name ?: result.job.state.name`), and elapsed milliseconds.

- [ ] **Step 3: Run the store tests**

Run: `./gradlew :feature:stores:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/stores/src/main/java/com/fossscout/feature/stores/StoresViewModel.kt
git commit -m "feat(stores): record sync latency in store health"
```

### Task 3: Render the health summary

**Files:**
- Modify: `feature/stores/src/main/java/com/fossscout/feature/stores/StoresScreen.kt`
- Modify: `feature/stores/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `StoreHealth` fields and `formatSyncLatency(...)`.
- Produces: visible last sync, item count, sync duration, and error lines.

- [ ] **Step 1: Add the latency string**

Add `stores_health_latency` as `Sync duration: %1$s`.

- [ ] **Step 2: Replace the single-value health `when` with a compact column**

- For non-F-Droid stores, show `stores_sync_last` with `formatRelativeTime(...)` when a last sync exists.
- For non-F-Droid stores with no sync yet, show `stores_health_unknown`.
- For F-Droid, skip the last-sync/never line because `StoreSyncBlock` already shows it.
- Show item count with the existing plural and latency with `formatSyncLatency`.
- Show the error line when status is `ERROR` and an error exists.
- Keep the 4 dp top spacing and small body typography.

- [ ] **Step 3: Build and lint both flavors**

Run: `./gradlew assemblePlaystoreDebug assembleVanillaDebug :app:lintPlaystoreDebug :app:lintVanillaDebug`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/stores/src/main/java/com/fossscout/feature/stores/StoresScreen.kt feature/stores/src/main/res/values/strings.xml
git commit -m "feat(stores): show complete store health details"
```

### Task 4: Update documentation and verify

**Files:**
- Modify: `docs/superpowers/specs/features/F04-store-registry.md`
- Modify: `docs/superpowers/specs/features/STATUS.md`
- Modify: `docs/superpowers/sessions/S02-store-registry.md`
- Modify: `docs/DEVICE_SMOKE_TEST.md`

- [ ] **Step 1: Mark F04-R8 implemented**

State that F04 shows current health (last successful sync, item count, sync duration, error), not a per-sync timeline. Update F04 status, STATUS row/bullet, S02 notes, and the device checklist.

- [ ] **Step 2: Run the full verification**

```bash
./gradlew test --rerun-tasks
./gradlew assemblePlaystoreDebug assembleVanillaDebug :app:lintPlaystoreDebug :app:lintVanillaDebug
```

Expected: all tests pass, both APKs assemble, both lint variants pass.

- [ ] **Step 3: Commit documentation**

```bash
git add docs/superpowers/specs/features/F04-store-registry.md docs/superpowers/specs/features/STATUS.md docs/superpowers/sessions/S02-store-registry.md docs/DEVICE_SMOKE_TEST.md
git commit -m "docs(stores): record F04-R8 health display"
```

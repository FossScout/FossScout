package com.fossscout.core.inventory

import com.fossscout.core.model.InstalledState
import kotlinx.coroutines.CancellationException

/**
 * Derives installation state for a saved app. Implementations must not persist
 * the result into the favourite: state belongs to the device, not the note.
 */
interface InstalledStateProvider {
    suspend fun stateFor(packageName: String): InstalledState
}

/**
 * Read-time badge provider backed by S04's [InstalledAppSource].
 *
 * A visible package is [InstalledState.INSTALLED]. A missing package is only
 * reported as [InstalledState.NOT_INSTALLED] when the source can enumerate every
 * package; without broad visibility the honest answer is
 * [InstalledState.UNAVAILABLE]. Non-package keys (source-only apps) and
 * unexpected failures stay [InstalledState.UNKNOWN].
 */
class InstalledAppStateProvider(
    private val source: InstalledAppSource,
    private val reliableAbsence: Boolean = source.supportsFullScan,
) : InstalledStateProvider {
    override suspend fun stateFor(packageName: String): InstalledState {
        val identity = packageName.trim()
        if (!PackageIdentityParser.isPackageName(identity)) {
            return InstalledState.UNKNOWN
        }
        return try {
            when {
                source.find(identity) != null -> InstalledState.INSTALLED
                reliableAbsence -> InstalledState.NOT_INSTALLED
                else -> InstalledState.UNAVAILABLE
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            InstalledState.UNAVAILABLE
        } catch (_: Exception) {
            InstalledState.UNKNOWN
        }
    }
}

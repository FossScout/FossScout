package com.fossscout.app.inventory

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import android.os.UserManager
import com.fossscout.core.inventory.AppProfile
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.inventory.InventoryEnumeration
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [PackageManager] inventory source.
 *
 * The Play profile constructs this with [supportsFullScan] false. Lookups that
 * package visibility blocks return null, which the repository records as
 * UNAVAILABLE instead of crashing.
 */
class PackageManagerInstalledAppSource(
    context: Context,
    override val supportsFullScan: Boolean,
) : InstalledAppSource {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager

    override suspend fun find(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        val info = packageInfo(packageName) ?: return@withContext null
        toInstalledApp(info)
    }

    override suspend fun search(query: String, limit: Int): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val needle = query.trim()
            if (needle.isEmpty()) return@withContext emptyList()
            val context = currentCoroutineContext()
            val apps = installedPackages().asSequence()
                .mapNotNull { info ->
                    context.ensureActive()
                    toInstalledApp(info)
                }
                .filter { app ->
                    app.packageName.contains(needle, ignoreCase = true) ||
                        app.label.contains(needle, ignoreCase = true)
                }
                .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
                .take(limit)
                .toList()
            apps
        }

    override suspend fun enumerate(): InventoryEnumeration = withContext(Dispatchers.IO) {
        check(supportsFullScan) {
            "Full scan requires broad package visibility"
        }
        val apps = mutableListOf<InstalledApp>()
        var inaccessible = 0
        val context = currentCoroutineContext()
        installedPackages().forEach { info ->
            context.ensureActive()
            val app = toInstalledApp(info)
            if (app == null) {
                inaccessible += 1
            } else {
                apps += app
            }
        }
        InventoryEnumeration(
            apps = apps.sortedWith(
                compareBy({ it.label.lowercase() }, { it.packageName }),
            ),
            inaccessibleApps = inaccessible,
        )
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> {
        val flags = packageInfoFlags()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                packageManager.getInstalledPackages(flags)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo? {
        val flags = packageInfoFlags()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                packageManager.getPackageInfo(packageName, flags)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfoFlags(): Int {
        val signerFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return signerFlag or PackageManager.MATCH_DISABLED_COMPONENTS
    }

    private fun toInstalledApp(info: PackageInfo): InstalledApp? {
        val appInfo = info.applicationInfo ?: return null
        val packageName = info.packageName ?: return null
        val label = runCatching { appInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: packageName

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

        return InstalledApp(
            packageName = packageName,
            label = label,
            versionName = info.versionName,
            versionCode = versionCode,
            installerPackage = installerPackage(packageName),
            signerSha256 = signerSha256(info),
            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isEnabled = appInfo.enabled,
            profile = currentProfile(),
        )
    }

    @Suppress("DEPRECATION")
    private fun installerPackage(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = packageManager.getInstallSourceInfo(packageName)
            source.installingPackageName ?: source.initiatingPackageName
        } else {
            packageManager.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun signerSha256(info: PackageInfo): String? {
        val signatures: Array<Signature>? = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        val signature = signatures?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun currentProfile(): AppProfile {
        // getUserId() is a hidden API; the per-user UID range is stable.
        val userId = Process.myUid() / PER_USER_UID_RANGE
        val isWorkProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { userManager?.isManagedProfile == true }.getOrDefault(false)
        } else {
            false
        }
        return AppProfile(userId = userId, isWorkProfile = isWorkProfile)
    }

    private companion object {
        const val PER_USER_UID_RANGE = 100_000
    }
}

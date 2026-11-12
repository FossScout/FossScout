package com.fossscout.app.inventory

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.fossscout.core.matching.AlternativeApkManifest
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LocalApkManifestFailure {
    UNREADABLE,
    TOO_LARGE,
    INVALID_APK,
}

sealed interface LocalApkManifestReadResult {
    data class Success(val manifest: AlternativeApkManifest) : LocalApkManifestReadResult

    data class Failure(val reason: LocalApkManifestFailure) : LocalApkManifestReadResult
}

/**
 * Reads a user-selected APK's manifest through a temporary local copy. The APK
 * is never installed, executed, or kept after the manifest is read.
 */
class LocalApkManifestReader(
    context: Context,
    private val maxApkBytes: Long = DEFAULT_MAX_APK_BYTES,
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    init {
        require(maxApkBytes > 0L) { "maxApkBytes must be positive" }
    }

    suspend fun read(uri: Uri): LocalApkManifestReadResult = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, CACHE_DIRECTORY)
        val tempFile = try {
            if (!directory.isDirectory && !directory.mkdirs()) {
                return@withContext LocalApkManifestReadResult.Failure(
                    LocalApkManifestFailure.UNREADABLE,
                )
            }
            File.createTempFile(TEMP_PREFIX, APK_SUFFIX, directory)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withContext LocalApkManifestReadResult.Failure(
                LocalApkManifestFailure.UNREADABLE,
            )
        }

        try {
            when (val copy = copyToFile(uri, tempFile)) {
                CopyResult.TOO_LARGE -> LocalApkManifestReadResult.Failure(
                    LocalApkManifestFailure.TOO_LARGE,
                )
                CopyResult.FAILED -> LocalApkManifestReadResult.Failure(
                    LocalApkManifestFailure.UNREADABLE,
                )
                CopyResult.COPIED -> parse(tempFile)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            LocalApkManifestReadResult.Failure(LocalApkManifestFailure.INVALID_APK)
        } finally {
            tempFile.delete()
        }
    }

    private fun copyToFile(uri: Uri, target: File): CopyResult = try {
        val input = appContext.contentResolver.openInputStream(uri) ?: return CopyResult.FAILED
        input.use {
            target.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxApkBytes) {
                        return CopyResult.TOO_LARGE
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        CopyResult.COPIED
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CopyResult.FAILED
    }

    @Suppress("DEPRECATION")
    private fun parse(file: File): LocalApkManifestReadResult {
        val info = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_PERMISSIONS,
        ) ?: return LocalApkManifestReadResult.Failure(LocalApkManifestFailure.INVALID_APK)
        val packageName = info.packageName
        if (packageName.isBlank()) {
            return LocalApkManifestReadResult.Failure(LocalApkManifestFailure.INVALID_APK)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return LocalApkManifestReadResult.Success(
            AlternativeApkManifest(
                packageName = packageName,
                versionName = info.versionName,
                versionCode = versionCode,
                permissions = info.requestedPermissions
                    .orEmpty()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct(),
            ),
        )
    }

    private enum class CopyResult {
        COPIED,
        TOO_LARGE,
        FAILED,
    }

    companion object {
        const val DEFAULT_MAX_APK_BYTES: Long = 128L * 1024L * 1024L

        private const val CACHE_DIRECTORY = "apk-manifest"
        private const val TEMP_PREFIX = "selected-"
        private const val APK_SUFFIX = ".apk"
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

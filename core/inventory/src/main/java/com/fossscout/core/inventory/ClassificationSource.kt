package com.fossscout.core.inventory

/** Classification plus optional machine-readable evidence from S05. */
data class ClassificationResult(
    val classification: Classification,
    val explanation: String? = null,
)

/** Matching contract owned by F02/S05. */
interface ClassificationSource {
    suspend fun classify(app: InstalledApp): ClassificationResult
}

/**
 * S04 stub. It deliberately never guesses: without a matching source every
 * visible app is [Classification.UNKNOWN].
 */
object UnknownClassificationSource : ClassificationSource {
    override suspend fun classify(app: InstalledApp): ClassificationResult =
        ClassificationResult(Classification.UNKNOWN)
}

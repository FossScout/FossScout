package com.fossscout.core.matching

import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.model.AppKey

private val PACKAGE_NAME_PATTERN =
    Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

/** True when the value has the shape of an Android package name. */
fun looksLikePackageName(value: String): Boolean =
    PACKAGE_NAME_PATTERN.matches(value.trim())

/**
 * Builds a match query for an installed app. The installed signer is passed as
 * evidence only; it is never assumed to match a store listing.
 */
fun MatchQuery.Companion.from(app: InstalledApp): MatchQuery = MatchQuery(
    appKey = AppKey.from(app.packageName).value,
    label = app.label,
    packageName = app.packageName,
    installedSignerSha256 = app.signerSha256?.trim()?.lowercase()?.takeIf(String::isNotEmpty),
)

/** Builds a query from a user-entered package name or free-text label. */
fun MatchQuery.Companion.forInput(input: String): MatchQuery {
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "Match query is blank" }
    return MatchQuery(
        appKey = AppKey.from(trimmed).value,
        label = trimmed,
        packageName = trimmed.takeIf(::looksLikePackageName),
    )
}

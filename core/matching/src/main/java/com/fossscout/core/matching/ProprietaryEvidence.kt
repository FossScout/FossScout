package com.fossscout.core.matching

/**
 * Manually curated evidence for an app whose vendor publishes proprietary
 * terms instead of source code. Absence from [KnownProprietaryApps] is not
 * evidence of either FOSS or proprietary status.
 */
data class ProprietaryEvidence(
    val packageName: String,
    val displayName: String,
    val evidenceUrl: String,
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
        require(displayName.isNotBlank()) { "Display name is blank" }
        require(evidenceUrl.startsWith("https://")) { "Evidence URL must use HTTPS" }
    }
}

/** Package-level evidence used after the FOSS catalogs have been consulted. */
fun interface ProprietaryEvidenceSource {
    fun evidenceFor(packageName: String): ProprietaryEvidence?
}

/** Default for builds or tests that do not ship closed-source evidence. */
object NoProprietaryEvidence : ProprietaryEvidenceSource {
    override fun evidenceFor(packageName: String): ProprietaryEvidence? = null
}

/**
 * Seed catalog for common proprietary apps. Each URL points to the vendor's
 * current terms or end-user agreement; entries are reviewed manually.
 */
object KnownProprietaryApps : ProprietaryEvidenceSource {
    internal val entries = listOf(
        ProprietaryEvidence("com.Slack", "Slack", "https://slack.com/terms-of-service"),
        ProprietaryEvidence(
            "com.facebook.katana",
            "Facebook",
            "https://www.facebook.com/legal/terms",
        ),
        ProprietaryEvidence(
            "com.facebook.lite",
            "Facebook Lite",
            "https://www.facebook.com/legal/terms",
        ),
        ProprietaryEvidence(
            "com.facebook.orca",
            "Messenger",
            "https://www.facebook.com/legal/terms",
        ),
        ProprietaryEvidence(
            "com.instagram.android",
            "Instagram",
            "https://help.instagram.com/581066165581870",
        ),
        ProprietaryEvidence(
            "com.whatsapp",
            "WhatsApp",
            "https://www.whatsapp.com/legal/terms-of-service",
        ),
        ProprietaryEvidence(
            "com.whatsapp.w4b",
            "WhatsApp Business",
            "https://www.whatsapp.com/legal/business-terms",
        ),
        ProprietaryEvidence("com.twitter.android", "X", "https://x.com/en/tos"),
        ProprietaryEvidence(
            "com.zhiliaoapp.musically",
            "TikTok",
            "https://www.tiktok.com/legal/page/us/terms-of-service/en",
        ),
        ProprietaryEvidence("com.discord", "Discord", "https://discord.com/terms"),
        ProprietaryEvidence(
            "com.linkedin.android",
            "LinkedIn",
            "https://www.linkedin.com/legal/user-agreement",
        ),
        ProprietaryEvidence(
            "com.snapchat.android",
            "Snapchat",
            "https://www.snapchat.com/terms",
        ),
        ProprietaryEvidence(
            "com.reddit.frontpage",
            "Reddit",
            "https://www.redditinc.com/policies/user-agreement",
        ),
        ProprietaryEvidence(
            "com.pinterest",
            "Pinterest",
            "https://policy.pinterest.com/en/terms-of-service",
        ),
        ProprietaryEvidence(
            "com.spotify.music",
            "Spotify",
            "https://www.spotify.com/legal/end-user-agreement/",
        ),
        ProprietaryEvidence(
            "com.netflix.mediaclient",
            "Netflix",
            "https://help.netflix.com/legal/termsofuse",
        ),
        ProprietaryEvidence(
            "com.google.android.youtube",
            "YouTube",
            "https://www.youtube.com/t/terms",
        ),
        ProprietaryEvidence(
            "com.android.chrome",
            "Google Chrome",
            "https://www.google.com/chrome/terms/",
        ),
        ProprietaryEvidence(
            "com.google.android.apps.maps",
            "Google Maps",
            "https://www.google.com/intl/en/help/terms_maps/",
        ),
        ProprietaryEvidence(
            "com.google.android.gm",
            "Gmail",
            "https://policies.google.com/terms",
        ),
        ProprietaryEvidence(
            "com.microsoft.teams",
            "Microsoft Teams",
            "https://www.microsoft.com/en-us/servicesagreement",
        ),
        ProprietaryEvidence(
            "us.zoom.videomeetings",
            "Zoom",
            "https://explore.zoom.us/en/terms/",
        ),
    )

    private val byPackageName = entries.associateBy(ProprietaryEvidence::packageName)

    override fun evidenceFor(packageName: String): ProprietaryEvidence? =
        byPackageName[packageName]
}

package com.fossscout.core.matching

import com.fossscout.core.index.ReproducibilityCheck
import com.fossscout.core.index.ReproducibilityStatus
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.store.LinkOnlyReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustBadgesTest {
    @Test
    fun `verified evidence produces positive badges`() {
        val badges = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            hasLicense = true,
            hasSource = true,
            reproducibility = check(ReproducibilityStatus.VERIFIED),
        ).toTrustBadges()

        assertEquals(
            listOf(
                TrustBadgeKind.INDEX_VERIFIED,
                TrustBadgeKind.SOURCE_AND_LICENSE,
                TrustBadgeKind.REPRODUCIBLE,
            ),
            badges.map(TrustBadge::kind),
        )
        assertTrue(badges.all { it.tone == TrustBadgeTone.POSITIVE })
    }

    @Test
    fun `missing evidence never produces a positive badge`() {
        val badges = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.UNVERIFIED,
            hasLicense = false,
            hasSource = false,
            antiFeatures = listOf("Ads"),
            freshness = SnapshotFreshness.STALE,
            cacheFetchedAtEpochMillis = 1_000L,
            previousSignerSha256 = listOf("old"),
            currentSignerSha256 = listOf("new"),
        ).toTrustBadges()

        assertFalse(badges.any { it.tone == TrustBadgeTone.POSITIVE })
        assertEquals(
            listOf(
                TrustBadgeKind.INDEX_UNVERIFIED,
                TrustBadgeKind.SOURCE_OR_LICENSE_MISSING,
                TrustBadgeKind.ANTI_FEATURE,
                TrustBadgeKind.SIGNER_CHANGED,
                TrustBadgeKind.STALE_METADATA,
            ),
            badges.map(TrustBadge::kind),
        )
        assertEquals(listOf("old"), badges[3].previousSignerSha256)
        assertEquals(listOf("new"), badges[3].currentSignerSha256)
    }

    @Test
    fun `missing reproducibility stays distinct from failed verification`() {
        val notVerified = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            hasLicense = true,
            hasSource = true,
            reproducibility = check(ReproducibilityStatus.NOT_VERIFIED),
        ).toTrustBadges()
        val failed = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            hasLicense = true,
            hasSource = true,
            reproducibility = check(ReproducibilityStatus.FAILED),
        ).toTrustBadges()
        val unavailable = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            hasLicense = true,
            hasSource = true,
            reproducibility = check(ReproducibilityStatus.UNAVAILABLE),
        ).toTrustBadges()

        assertEquals(
            TrustBadgeKind.REPRODUCIBILITY_NOT_VERIFIED,
            notVerified.last().kind,
        )
        assertEquals(TrustBadgeTone.NEUTRAL, notVerified.last().tone)
        assertEquals(TrustBadgeKind.REPRODUCIBILITY_FAILED, failed.last().kind)
        assertEquals(TrustBadgeTone.NEGATIVE, failed.last().tone)
        assertFalse(unavailable.any { it.kind == TrustBadgeKind.REPRODUCIBILITY_FAILED })
        assertFalse(unavailable.any { it.kind == TrustBadgeKind.REPRODUCIBLE })
    }

    @Test
    fun `signer changes compare certificate sets`() {
        val reordered = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            previousSignerSha256 = listOf("a", "b"),
            currentSignerSha256 = listOf("b", "a"),
        ).toTrustBadges()
        val changed = TrustBadgeInput(
            storeName = "F-Droid",
            signatureStatus = SignatureStatus.VERIFIED,
            previousSignerSha256 = listOf("a", "b"),
            currentSignerSha256 = listOf("b", "c"),
        ).toTrustBadges()

        assertFalse(reordered.any { it.kind == TrustBadgeKind.SIGNER_CHANGED })
        assertTrue(changed.any { it.kind == TrustBadgeKind.SIGNER_CHANGED })
    }

    @Test
    fun `link only stores get a neutral badge without trust claims`() {
        val badge = StoreSearchLinkOnly(
            storeId = "vendor",
            storeName = "Vendor",
            homepage = "https://vendor.example",
            reason = LinkOnlyReason.ADAPTER_UNSUPPORTED,
        ).toTrustBadge()

        assertEquals(TrustBadgeKind.LINK_ONLY, badge.kind)
        assertEquals(TrustBadgeTone.NEUTRAL, badge.tone)
        assertEquals("Vendor", badge.storeName)
        assertEquals("https://vendor.example", badge.sourceUrl)
    }

    private fun check(status: ReproducibilityStatus) = ReproducibilityCheck(
        packageName = "org.example.app",
        versionCode = 7L,
        status = status,
        sourceUrl = "https://verification.f-droid.org/packages/org.example.app/",
        checkedAtEpochMillis = 1_000L,
        versionName = "1.0",
    )
}

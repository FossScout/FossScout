package com.fossscout.core.matching

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerSignatureMatcherTest {
    @Test
    fun `slash descriptor matches a dotted class signature`() {
        val matcher = TrackerSignatureMatcher(
            dataset(tracker("Analytics", "com.example.tracker.", TrackerConfidence.KNOWN)),
        )

        val matches = matcher.toMatches(
            matcher.scan(
                input("Lcom/example/tracker/Analytics;"),
                MAX_BYTES,
            ).found,
        )

        assertEquals("Analytics", matches.single().name)
        assertEquals(TrackerConfidence.KNOWN, matches.single().confidence)
        assertEquals(listOf("com.example.tracker."), matches.single().matchedSignatures)
    }

    @Test
    fun `dotted package text does not count as a DEX descriptor`() {
        val matcher = TrackerSignatureMatcher(
            dataset(tracker("Analytics", "com.example.tracker.", TrackerConfidence.KNOWN)),
        )

        val result = matcher.scan(input("com.example.tracker.Analytics"), MAX_BYTES)

        assertTrue(result.found.isEmpty())
    }

    @Test
    fun `leading-dot signature matches a package segment`() {
        val matcher = TrackerSignatureMatcher(
            dataset(tracker("WeChat", ".wxapi.", TrackerConfidence.CANDIDATE)),
        )

        val matches = matcher.toMatches(
            matcher.scan(
                input("Lcom/tencent/wxapi/WXEntryActivity;"),
                MAX_BYTES,
            ).found,
        )

        assertEquals("WeChat", matches.single().name)
        assertEquals(TrackerConfidence.CANDIDATE, matches.single().confidence)
    }

    @Test
    fun `all matched signatures for one tracker are reported once`() {
        val matcher = TrackerSignatureMatcher(
            dataset(
                TrackerDefinition(
                    name = "Analytics",
                    signatures = listOf(
                        TrackerSignature("com.example.tracker.", TrackerConfidence.KNOWN),
                        TrackerSignature(".tracker.", TrackerConfidence.CANDIDATE),
                    ),
                ),
            ),
        )

        val matches = matcher.toMatches(
            matcher.scan(
                input("Lcom/example/tracker/Analytics;"),
                MAX_BYTES,
            ).found,
        )

        assertEquals(1, matches.size)
        assertEquals(TrackerConfidence.KNOWN, matches.single().confidence)
        assertEquals(
            listOf(".tracker.", "com.example.tracker."),
            matches.single().matchedSignatures,
        )
    }

    @Test
    fun `matches are sorted and distinct trackers stay separate`() {
        val matcher = TrackerSignatureMatcher(
            dataset(
                tracker("Zeta", "com.example.zeta.", TrackerConfidence.CANDIDATE),
                tracker("Alpha", "com.example.alpha.", TrackerConfidence.KNOWN),
            ),
        )

        val matches = matcher.toMatches(
            matcher.scan(
                input("Lcom/example/zeta/Class;Lcom/example/alpha/Class;"),
                MAX_BYTES,
            ).found,
        )

        assertEquals(listOf("Alpha", "Zeta"), matches.map(TrackerMatch::name))
    }

    @Test
    fun `invalid signatures are ignored`() {
        val matcher = TrackerSignatureMatcher(
            dataset(tracker("Invalid", "com.example:coordinate", TrackerConfidence.KNOWN)),
        )

        val result = matcher.scan(input("Lcom/example:coordinate/Class;"), MAX_BYTES)

        assertTrue(result.found.isEmpty())
    }

    @Test
    fun `scan reports truncation at the byte limit`() {
        val matcher = TrackerSignatureMatcher(
            dataset(tracker("Analytics", "com.example.tracker.", TrackerConfidence.KNOWN)),
        )

        val result = matcher.scan(input("x".repeat(100)), maxBytes = 10)

        assertTrue(result.truncated)
        assertEquals(10, result.consumedBytes.toInt())
        assertTrue(result.found.isEmpty())
    }

    private fun dataset(vararg trackers: TrackerDefinition): TrackerDataset = TrackerDataset(
        dataset = TrackerDatasetInfo(
            name = "Test dataset",
            version = "1",
            licenseName = "MIT",
            licenseUrl = "https://example.org/license",
            sourceUrl = "https://example.org/source",
        ),
        trackers = trackers.toList(),
    )

    private fun tracker(
        name: String,
        signature: String,
        confidence: TrackerConfidence,
    ): TrackerDefinition = TrackerDefinition(
        name = name,
        signatures = listOf(TrackerSignature(signature, confidence)),
    )

    private fun input(value: String): ByteArrayInputStream =
        ByteArrayInputStream(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val MAX_BYTES = 1024L
    }
}

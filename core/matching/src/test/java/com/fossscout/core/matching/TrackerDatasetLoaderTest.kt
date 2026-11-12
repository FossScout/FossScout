package com.fossscout.core.matching

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerDatasetLoaderTest {
    @Test
    fun `bundled dataset loads with version and license metadata`() {
        val dataset = TrackerDatasetLoader.loadBundled()

        assertNotNull(dataset)
        requireNotNull(dataset)
        assertEquals(TRACKER_DATASET_SCHEMA_VERSION, dataset.schemaVersion)
        assertEquals("2026-11-10", dataset.dataset.version)
        assertTrue(dataset.dataset.licenseName.contains("ODbL"))
        assertTrue(dataset.dataset.sourceUrl.startsWith("https://"))
        assertTrue(dataset.trackers.any { tracker -> tracker.name == "Pangle" })
        assertTrue(dataset.trackers.any { tracker ->
            tracker.signatures.any { signature -> signature.confidence == TrackerConfidence.KNOWN }
        })
    }

    @Test
    fun `decoder keeps known fields and ignores future fields`() {
        val dataset = TrackerDatasetLoader.decode(
            """
            {
              "schemaVersion": 1,
              "dataset": {
                "name": "Test dataset",
                "version": "1",
                "licenseName": "MIT",
                "licenseUrl": "https://example.org/license",
                "sourceUrl": "https://example.org/source"
              },
              "trackers": [
                {
                  "name": "Example tracker",
                  "categories": ["Analytics"],
                  "website": "https://tracker.example",
                  "signatures": [
                    {"value": "com.example.tracker.", "confidence": "KNOWN"}
                  ]
                }
              ],
              "futureField": "ignored"
            }
            """.trimIndent(),
        )

        assertEquals("Test dataset", dataset.dataset.name)
        assertEquals("Example tracker", dataset.trackers.single().name)
        assertEquals(
            TrackerConfidence.KNOWN,
            dataset.trackers.single().signatures.single().confidence,
        )
    }

    @Test
    fun `bundled dataset matches a realistic DEX descriptor`() {
        val dataset = requireNotNull(TrackerDatasetLoader.loadBundled())
        val matcher = TrackerSignatureMatcher(dataset)
        val result = matcher.scan(
            ByteArrayInputStream(
                "Lcom/google/android/gms/analytics/AnalyticsReceiver;"
                    .toByteArray(Charsets.UTF_8),
            ),
            maxBytes = 1024,
        )

        val matches = matcher.toMatches(result.found)

        assertTrue(matches.any { match -> match.name == "Google Analytics" })
    }
}

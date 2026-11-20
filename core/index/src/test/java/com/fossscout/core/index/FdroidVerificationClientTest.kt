package com.fossscout.core.index

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FdroidVerificationClientTest {
    @Test
    fun `verified report exposes build evidence`() = runTest {
        val fetcher = FakeHttpFetcher { request ->
            fakeResponse(
                request.url,
                """
                {
                  "1790000000.0": {
                    "verified": true,
                    "local": {
                      "sha256": "local-hash",
                      "versionName": "1.0",
                      "timestamp": 1790000000.0
                    },
                    "remote": {"sha256": "remote-hash"}
                  }
                }
                """.trimIndent().toByteArray(),
            )
        }
        val client = FdroidVerificationClient(fetcher, now = { 9_999L })

        val result = client.check("org.example.app", 7L)

        assertEquals(ReproducibilityStatus.VERIFIED, result.status)
        assertEquals("local-hash", result.localSha256)
        assertEquals("remote-hash", result.remoteSha256)
        assertEquals("1.0", result.versionName)
        assertEquals(1_790_000_000_000L, result.reportTimestampEpochMillis)
        assertEquals(
            "https://verification.f-droid.org/unsigned/org.example.app_7.apk.json",
            fetcher.requests.single().url,
        )
        assertEquals(
            "https://verification.f-droid.org/packages/org.example.app/",
            result.sourceUrl,
        )
    }

    @Test
    fun `one verified report wins over failed reports`() = runTest {
        val fetcher = FakeHttpFetcher { request ->
            fakeResponse(
                request.url,
                """
                {
                  "1.0": {"verified": false, "local": {"sha256": "failed"}},
                  "2.0": {
                    "verified": true,
                    "local": {"sha256": "verified", "versionName": "1.0"}
                  }
                }
                """.trimIndent().toByteArray(),
            )
        }

        val result = FdroidVerificationClient(fetcher).check("org.example.app", 7L)

        assertEquals(ReproducibilityStatus.VERIFIED, result.status)
        assertEquals("verified", result.localSha256)
        assertEquals("1.0", result.versionName)
    }

    @Test
    fun `missing version is not verified rather than failed`() = runTest {
        val fetcher = FakeHttpFetcher { request ->
            fakeResponse(request.url, ByteArray(0), status = 404)
        }

        val result = FdroidVerificationClient(fetcher).check("org.example.app", 7L)

        assertEquals(ReproducibilityStatus.NOT_VERIFIED, result.status)
        assertNull(result.localSha256)
    }

    @Test
    fun `failed reports are reported as failed`() = runTest {
        val fetcher = FakeHttpFetcher { request ->
            fakeResponse(
                request.url,
                """{"1.0":{"verified":false}}""".toByteArray(),
            )
        }

        val result = FdroidVerificationClient(fetcher).check("org.example.app", 7L)

        assertEquals(ReproducibilityStatus.FAILED, result.status)
    }

    @Test
    fun `network failures and malformed reports are unavailable`() = runTest {
        val offline = FdroidVerificationClient(
            FakeHttpFetcher { throw IOException("offline") },
        ).check("org.example.app", 7L)
        val malformed = FdroidVerificationClient(
            FakeHttpFetcher { request ->
                fakeResponse(request.url, "not json".toByteArray())
            },
        ).check("org.example.app", 7L)

        assertEquals(ReproducibilityStatus.UNAVAILABLE, offline.status)
        assertEquals(ReproducibilityStatus.UNAVAILABLE, malformed.status)
    }

    @Test
    fun `successful and missing results are cached for the session`() = runTest {
        val fetcher = FakeHttpFetcher { request ->
            fakeResponse(
                request.url,
                """{"1.0":{"verified":true}}""".toByteArray(),
            )
        }
        val client = FdroidVerificationClient(fetcher)

        client.check("org.example.app", 7L)
        client.check("org.example.app", 7L)

        assertEquals(1, fetcher.requests.size)
    }

    @Test
    fun `unavailable results are not cached so users can retry`() = runTest {
        val fetcher = FakeHttpFetcher { throw IOException("offline") }
        val client = FdroidVerificationClient(fetcher)

        client.check("org.example.app", 7L)
        client.check("org.example.app", 7L)

        assertEquals(2, fetcher.requests.size)
        assertEquals(
            ReproducibilityStatus.UNAVAILABLE,
            client.check("org.example.app", 7L).status,
        )
        assertEquals(3, fetcher.requests.size)
    }
}

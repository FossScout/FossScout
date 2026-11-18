package com.fossscout.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreSearchHistoryCodecTest {
    private val codec = StoreSearchHistoryCodec()

    @Test
    fun `queries round trip`() {
        val queries = listOf("example", "org.example.app")

        assertEquals(queries, codec.decode(codec.encode(queries)))
    }

    @Test
    fun `malformed data is rejected`() {
        assertThrows(StoreSearchHistoryFormatException::class.java) {
            codec.decode("not json")
        }
    }

    @Test
    fun `an unknown schema version is rejected`() {
        assertThrows(StoreSearchHistoryFormatException::class.java) {
            codec.decode("""{"schemaVersion":2,"queries":["example"]}""")
        }
    }
}

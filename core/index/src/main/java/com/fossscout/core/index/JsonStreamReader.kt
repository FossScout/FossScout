package com.fossscout.core.index

import java.io.Reader

/**
 * Minimal streaming JSON tokenizer for the very large F-Droid v2 index. It
 * never materialises the whole document: the caller walks top-level keys and
 * captures one package value at a time.
 */
internal class JsonStreamReader(private val reader: Reader) : AutoCloseable {
    private var lookahead: Int = UNREAD

    fun beginObject() = expect('{')

    fun nextObjectKey(): String? {
        skipWhitespace()
        var next = peek()
        if (next == '}'.code) {
            read()
            return null
        }
        if (next == ','.code) {
            read()
            skipWhitespace()
            next = peek()
        }
        require(next == '"'.code) { "Expected an object key" }
        val key = readString()
        skipWhitespace()
        expect(':')
        return key
    }

    fun beginArray() = expect('[')

    /** Returns false (and consumes `]`) after the last array element. */
    fun hasNextArrayElement(): Boolean {
        skipWhitespace()
        when (peek()) {
            ']'.code -> {
                read()
                return false
            }

            ','.code -> {
                read()
                skipWhitespace()
            }
        }
        return true
    }

    fun readString(): String {
        skipWhitespace()
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val next = read()
            require(next >= 0) { "Unterminated string" }
            when (next) {
                '"'.code -> return builder.toString()
                '\\'.code -> {
                    val escaped = read()
                    require(escaped >= 0) { "Unterminated escape" }
                    when (escaped) {
                        '"'.code -> builder.append('"')
                        '\\'.code -> builder.append('\\')
                        '/'.code -> builder.append('/')
                        'b'.code -> builder.append('\b')
                        'f'.code -> builder.append('\u000C')
                        'n'.code -> builder.append('\n')
                        'r'.code -> builder.append('\r')
                        't'.code -> builder.append('\t')
                        'u'.code -> {
                            var value = 0
                            repeat(4) {
                                val digit = Character.digit(read(), 16)
                                require(digit >= 0) { "Invalid unicode escape" }
                                value = (value shl 4) or digit
                            }
                            builder.append(value.toChar())
                        }

                        else -> throw IllegalArgumentException("Invalid escape \\$escaped")
                    }
                }

                else -> builder.append(next.toChar())
            }
        }
    }

    /**
     * Captures the next JSON value verbatim. Strings keep their escapes so the
     * captured fragment can be handed to a normal JSON decoder.
     */
    fun captureValue(): String {
        skipWhitespace()
        val start = peek()
        require(start >= 0) { "Expected a value" }
        val builder = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false

        while (true) {
            val next = peek()
            if (next < 0) {
                require(depth == 0 && !inString) { "Unterminated value" }
                break
            }
            if (inString) {
                val consumed = read()
                builder.append(consumed.toChar())
                when {
                    escaped -> escaped = false
                    consumed == '\\'.code -> escaped = true
                    consumed == '"'.code -> {
                        inString = false
                        if (depth == 0) break
                    }
                }
                continue
            }

            when (next) {
                '{'.code, '['.code -> {
                    depth++
                    builder.append(read().toChar())
                }

                '}'.code, ']'.code -> {
                    if (depth == 0) break
                    depth--
                    builder.append(read().toChar())
                    if (depth == 0) break
                }

                ','.code -> {
                    if (depth == 0) break
                    builder.append(read().toChar())
                }

                ' '.code, '\n'.code, '\r'.code, '\t'.code -> {
                    if (depth == 0) break
                    builder.append(read().toChar())
                }

                '"'.code -> {
                    inString = true
                    builder.append(read().toChar())
                }

                else -> builder.append(read().toChar())
            }
        }

        val captured = builder.toString()
        require(captured.isNotEmpty()) { "Expected a value" }
        return captured
    }

    fun skipValue() {
        captureValue()
    }

    fun expectEnd() {
        skipWhitespace()
        require(read() < 0) { "Trailing data after JSON value" }
    }

    override fun close() = reader.close()

    private fun expect(expected: Char) {
        skipWhitespace()
        require(read() == expected.code) { "Expected '$expected'" }
    }

    private fun skipWhitespace() {
        while (true) {
            val next = peek()
            when (next) {
                ' '.code, '\n'.code, '\r'.code, '\t'.code -> read()
                else -> return
            }
        }
    }

    private fun peek(): Int {
        if (lookahead == UNREAD) {
            lookahead = reader.read()
        }
        return lookahead
    }

    private fun read(): Int {
        val value = peek()
        lookahead = UNREAD
        return value
    }

    private companion object {
        const val UNREAD = -2
    }
}

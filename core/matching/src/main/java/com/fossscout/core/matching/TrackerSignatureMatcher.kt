package com.fossscout.core.matching

import java.io.InputStream

/**
 * Byte-level multi-pattern matcher for tracker class signatures.
 *
 * DEX type descriptors store package names with slashes, so a dataset signature
 * such as `com.example.tracker.` is matched as `com/example/tracker/`.
 */
internal class TrackerSignatureMatcher(
    private val dataset: TrackerDataset,
) {
    private data class Pattern(
        val trackerIndex: Int,
        val signature: String,
        val confidence: TrackerConfidence,
        val bytes: ByteArray,
    )

    private class Automaton(
        val offsets: IntArray,
        val edgeBytes: ByteArray,
        val edgeTargets: IntArray,
        val failure: IntArray,
        val outputs: List<List<Int>>,
    ) {
        fun child(state: Int, byte: Int): Int {
            var index = offsets[state]
            val end = offsets[state + 1]
            while (index < end) {
                if ((edgeBytes[index].toInt() and 0xff) == byte) {
                    return edgeTargets[index]
                }
                index += 1
            }
            return NO_CHILD
        }

        companion object {
            const val NO_CHILD: Int = -1
        }
    }

    private val patterns: List<Pattern>
    private val automaton: Automaton

    init {
        patterns = buildList {
            dataset.trackers.forEachIndexed { trackerIndex, tracker ->
                tracker.signatures.forEach { signature ->
                    dexPattern(signature.value)?.let { bytes ->
                        add(
                            Pattern(
                                trackerIndex = trackerIndex,
                                signature = signature.value,
                                confidence = signature.confidence,
                                bytes = bytes,
                            ),
                        )
                    }
                }
            }
        }
        automaton = buildAutomaton(patterns)
    }

    fun scan(input: InputStream, maxBytes: Long): TrackerSignatureScan {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        val found = mutableMapOf<Int, MutableSet<String>>()
        var state = 0
        var consumed = 0L
        var truncated = false
        val buffer = ByteArray(BUFFER_SIZE)

        while (consumed < maxBytes) {
            val remaining = maxBytes - consumed
            val limit = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, limit)
            if (read < 0) {
                break
            }
            if (read == 0) {
                continue
            }
            for (index in 0 until read) {
                val byte = buffer[index].toInt() and 0xff
                while (state != 0 && automaton.child(state, byte) == Automaton.NO_CHILD) {
                    state = automaton.failure[state]
                }
                val next = automaton.child(state, byte)
                state = if (next == Automaton.NO_CHILD) 0 else next
                val output = automaton.outputs[state]
                if (output.isNotEmpty()) {
                    output.forEach { patternIndex ->
                        val pattern = patterns[patternIndex]
                        found.getOrPut(pattern.trackerIndex) { mutableSetOf() }
                            .add(pattern.signature)
                    }
                }
            }
            consumed += read
        }

        if (consumed >= maxBytes) {
            truncated = true
        }

        return TrackerSignatureScan(
            found = found.mapValues { (_, signatures) -> signatures.toSet() },
            consumedBytes = consumed,
            truncated = truncated,
        )
    }

    fun toMatches(found: Map<Int, Set<String>>): List<TrackerMatch> = found.mapNotNull { (index, values) ->
        val tracker = dataset.trackers.getOrNull(index) ?: return@mapNotNull null
        val matched = tracker.signatures.filter { it.value in values }
        if (matched.isEmpty()) {
            return@mapNotNull null
        }
        TrackerMatch(
            name = tracker.name,
            categories = tracker.categories,
            website = tracker.website,
            confidence = if (matched.any { it.confidence == TrackerConfidence.KNOWN }) {
                TrackerConfidence.KNOWN
            } else {
                TrackerConfidence.CANDIDATE
            },
            matchedSignatures = matched.map(TrackerSignature::value).sorted(),
        )
    }.sortedWith(compareBy({ it.name.lowercase() }, { it.name }))

    private fun buildAutomaton(patterns: List<Pattern>): Automaton {
        val children = mutableListOf<MutableMap<Int, Int>>(mutableMapOf())
        val outputs = mutableListOf<MutableList<Int>>(mutableListOf())

        patterns.forEachIndexed { patternIndex, pattern ->
            var state = 0
            pattern.bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                val existing = children[state][value]
                if (existing != null) {
                    state = existing
                } else {
                    val next = children.size
                    children.add(mutableMapOf())
                    outputs.add(mutableListOf())
                    children[state][value] = next
                    state = next
                }
            }
            outputs[state].add(patternIndex)
        }

        val failure = IntArray(children.size)
        val queue = ArrayDeque<Int>()
        children[0].values.forEach { child ->
            failure[child] = 0
            queue.add(child)
        }

        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            children[state].forEach { (byte, child) ->
                var fallback = failure[state]
                var next = childOf(children, fallback, byte)
                while (fallback != 0 && next == Automaton.NO_CHILD) {
                    fallback = failure[fallback]
                    next = childOf(children, fallback, byte)
                }
                failure[child] = if (next == Automaton.NO_CHILD) 0 else next
                outputs[child].addAll(outputs[failure[child]])
                queue.add(child)
            }
        }

        val offsets = IntArray(children.size + 1)
        var edgeCount = 0
        children.forEachIndexed { state, edges ->
            offsets[state] = edgeCount
            edgeCount += edges.size
        }
        offsets[children.size] = edgeCount
        val edgeBytes = ByteArray(edgeCount)
        val edgeTargets = IntArray(edgeCount)
        var edgeIndex = 0
        children.forEach { edges ->
            edges.forEach { (byte, target) ->
                edgeBytes[edgeIndex] = byte.toByte()
                edgeTargets[edgeIndex] = target
                edgeIndex += 1
            }
        }

        return Automaton(
            offsets = offsets,
            edgeBytes = edgeBytes,
            edgeTargets = edgeTargets,
            failure = failure,
            outputs = outputs.map { it.toList() },
        )
    }

    private fun childOf(
        children: List<MutableMap<Int, Int>>,
        state: Int,
        byte: Int,
    ): Int = children[state][byte] ?: Automaton.NO_CHILD

    private fun dexPattern(signature: String): ByteArray? {
        val trimmed = signature.trim()
        if (trimmed.length < MIN_PATTERN_LENGTH) {
            return null
        }
        if (trimmed.any { character ->
                !character.isLetterOrDigit() &&
                    character != '.' &&
                    character != '/' &&
                    character != '_' &&
                    character != '$'
            }
        ) {
            return null
        }
        return trimmed.replace('.', '/').encodeToByteArray()
    }

    internal data class TrackerSignatureScan(
        val found: Map<Int, Set<String>>,
        val consumedBytes: Long,
        val truncated: Boolean,
    )

    private companion object {
        const val BUFFER_SIZE: Int = 8 * 1024
        const val MIN_PATTERN_LENGTH: Int = 4
    }
}

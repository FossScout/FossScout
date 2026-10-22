package com.fossscout.feature.favourites

import com.fossscout.core.model.MAX_NOTE_LENGTH

/**
 * Trims a private note before it is stored. A blank note is stored as an empty
 * string, and notes longer than [MAX_NOTE_LENGTH] are rejected.
 */
fun normalizeNote(raw: String): String {
    val trimmed = raw.trim()
    require(trimmed.length <= MAX_NOTE_LENGTH) { "Note is too long" }
    return trimmed
}

package com.nanobot.client.hotword

import androidx.compose.runtime.Composable

/**
 * Starts/stops in-app wake-word detection. Implementations must only listen
 * while the app is visible and must stop when [active] becomes false.
 */
@Composable
expect fun HotwordEffect(
    enabled: Boolean,
    phrase: String,
    active: Boolean,
    onDetected: () -> Unit,
)

/** Whether the current platform supports in-app wake-word detection. */
expect fun hotwordSupported(): Boolean

/** Lowercase and keep only letters/digits, collapsing everything else to spaces. */
fun normalizeHotwordPhrase(raw: String): String =
    raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

/**
 * Vosk accepts a JSON array of allowed phrases. [unk] lets the decoder emit
 * anything else without trying to match the target phrase.
 */
fun buildVoskGrammar(phrase: String): String {
    val normalized = normalizeHotwordPhrase(phrase)
    if (normalized.isEmpty()) return "[\"[unk]\"]"
    val escaped = normalized.replace("\\", "\\\\").replace("\"", "\\\"")
    return "[\"$escaped\", \"[unk]\"]"
}

/** True when [transcript] contains [phrase] as a whole word sequence. */
fun transcriptMatchesPhrase(transcript: String, phrase: String): Boolean {
    val target = normalizeHotwordPhrase(phrase).split(' ').filter { it.isNotEmpty() }
    if (target.isEmpty()) return false
    val words = normalizeHotwordPhrase(transcript).split(' ').filter { it.isNotEmpty() }
    if (words.size < target.size) return false
    return words.windowed(target.size).any { it == target }
}

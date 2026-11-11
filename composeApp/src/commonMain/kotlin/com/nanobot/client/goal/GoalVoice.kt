package com.nanobot.client.goal

/**
 * Turns a spoken transcript into a /goal command when it starts with the
 * configured trigger phrase. An optional leading wake word is ignored.
 *
 * Examples (trigger = "задача"):
 *  - "задача подготовить отчёт" -> "/goal подготовить отчёт"
 *  - "клод, задача проверить сервер" -> "/goal проверить сервер"
 *  - "у меня задача посложнее" -> null
 */
fun goalCommandFromVoice(
    transcript: String,
    triggerPhrase: String,
    wakeWord: String? = null,
): String? {
    val words = normalizeWords(transcript)
    val trigger = normalizeWords(triggerPhrase)
    if (words.isEmpty() || trigger.isEmpty()) return null

    var start = 0
    val wake = normalizeWords(wakeWord.orEmpty())
    if (wake.isNotEmpty() && words.size >= wake.size && words.subList(0, wake.size) == wake) {
        start = wake.size
    }
    if (words.size < start + trigger.size) return null
    if (words.subList(start, start + trigger.size) != trigger) return null

    val objective = words.drop(start + trigger.size)
    if (objective.isEmpty()) return null
    return "/goal ${objective.joinToString(" ")}"
}

fun normalizeWords(value: String): List<String> =
    value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotEmpty() }

package com.nanobot.client.audio

/**
 * Cleans raw ASR output from models like Qwen-Audio / Qwen3-ASR / Whisper.
 *
 * Handles:
 * - "language Russian<asr_text>Распознаный текст" -> "Распознаный текст"
 * - "language Russian<asr_text>Привет мир</asr_text>" -> "Привет мир"
 * - "<asr_text>Тест</asr_text>" -> "Тест"
 * - "<|startoftranscript|><|ru|>Привет<|endoftranscript|>" -> "Привет"
 * - "language English: Hello world" -> "Hello world"
 */
fun cleanAsrText(raw: String): String {
    var text = raw.trim()

    val asrTag = "<asr_text>"
    val asrTagClose = "</asr_text>"
    val lower = text.lowercase()
    if (lower.contains(asrTag)) {
        val start = lower.indexOf(asrTag) + asrTag.length
        val end = lower.indexOf(asrTagClose, start)
        text = if (end != -1) {
            text.substring(start, end)
        } else {
            text.substring(start)
        }
    }

    text = text.replace(Regex("""^(language\s*[:=]?\s*[A-Za-zА-Яа-я_-]+[:\s]*)""", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("""<\|[^>]*\|>"""), "")
    text = text.replace(Regex("""</?asr_text>""", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("""\[/?[A-Za-z0-9_]+\]"""), "")

    return text.trim()
}

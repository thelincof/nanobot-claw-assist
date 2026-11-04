package com.nanobot.client

import com.nanobot.client.audio.cleanAsrText
import com.nanobot.client.audio.encodeWav
import com.nanobot.client.core.gateway.UrlResult
import com.nanobot.client.core.gateway.buildWebSocketUrl
import com.nanobot.client.core.gateway.normalizeBaseUrl
import com.nanobot.client.core.model.BootstrapResponse
import com.nanobot.client.core.model.InboundEvent
import com.nanobot.client.core.model.parseInboundEvent
import com.nanobot.client.hotword.buildVoskGrammar
import com.nanobot.client.hotword.normalizeHotwordPhrase
import com.nanobot.client.hotword.transcriptMatchesPhrase
import com.nanobot.client.tts.chunkForSpeech
import com.nanobot.client.tts.parseWav
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreTest {
    @Test
    fun normalizesGatewayUrl() {
        val result = normalizeBaseUrl("  http://192.168.1.10:8765/  ")
        assertIs<UrlResult.Valid>(result)
        assertEquals("http://192.168.1.10:8765", result.url)
    }

    @Test
    fun rejectsUrlWithoutScheme() {
        assertIs<UrlResult.Invalid>(normalizeBaseUrl("192.168.1.10:8765"))
    }

    @Test
    fun buildsWebSocketUrlForNanobot() {
        val bootstrap = BootstrapResponse(
            token = "abc",
            wsUrl = null,
            wsPath = "/ws",
        )
        val url = buildWebSocketUrl("http://192.168.0.250:8765", bootstrap)
        assertTrue(url.startsWith("ws://192.168.0.250:8765/ws"))
        assertTrue(url.contains("token=abc"))
    }

    @Test
    fun prefersExplicitWsUrlFromBootstrap() {
        val bootstrap = BootstrapResponse(
            token = "abc",
            wsUrl = "ws://192.168.0.250:8765/",
            wsPath = "/",
        )
        val url = buildWebSocketUrl("http://192.168.0.250:8765", bootstrap)
        assertTrue(url.startsWith("ws://192.168.0.250:8765/"))
        assertTrue(url.contains("token=abc"))
    }

    @Test
    fun wavRoundTrip() {
        val samples = shortArrayOf(0, 100, -100, 32767, -32768)
        val wav = encodeWav(samples, 16_000)
        val parsed = parseWav(wav)
        assertEquals(16_000, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertTrue(samples.contentEquals(parsed.samples))
    }

    @Test
    fun parsesDeltaAndTurnEnd() {
        val delta = parseInboundEvent(
            """{"event":"delta","chat_id":"c1","text":"hello","turn_id":"t1"}""",
        )
        assertIs<InboundEvent.Delta>(delta)
        assertEquals("hello", delta.value.text)

        val end = parseInboundEvent(
            """{"event":"turn_end","chat_id":"c1","turn_id":"t1","latency_ms":42}""",
        )
        assertIs<InboundEvent.TurnEnd>(end)
        assertEquals(42L, end.value.latencyMs)
    }

    @Test
    fun parsesTranscriptionResult() {
        val event = parseInboundEvent(
            """{"event":"transcription_result","request_id":"r1","text":"привет"}""",
        )
        assertIs<InboundEvent.TranscriptionResult>(event)
        assertEquals("привет", event.value.text)
    }

    @Test
    fun chunksLongSpeech() {
        val text = (1..300).joinToString(" ") { "word$it" }
        val chunks = chunkForSpeech(text, maxChars = 120)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 120 })
        assertNotNull(chunks.firstOrNull())
    }

    @Test
    fun normalizesHotwordPhrase() {
        assertEquals("клод", normalizeHotwordPhrase("  Клод!  "))
        assertEquals("эй клод", normalizeHotwordPhrase("Эй, Клод..."))
        assertEquals("", normalizeHotwordPhrase("!!!"))
    }

    @Test
    fun buildsVoskHotwordGrammar() {
        assertEquals("[\"клод\", \"[unk]\"]", buildVoskGrammar("Клод!"))
        assertEquals("[\"[unk]\"]", buildVoskGrammar("  "))
    }

    @Test
    fun matchesHotwordAsWholeWords() {
        assertTrue(transcriptMatchesPhrase("привет, клод", "клод"))
        assertTrue(transcriptMatchesPhrase("эй клод как дела", "эй клод"))
        assertFalse(transcriptMatchesPhrase("клад", "клод"))
        assertFalse(transcriptMatchesPhrase("клодовый", "клод"))
    }

    @Test
    fun cleansAsrOutputWithQwenTags() {
        assertEquals("Распознаный текст", cleanAsrText("language Russian<asr_text>Распознаный текст"))
        assertEquals("Привет мир", cleanAsrText("language Russian<asr_text>Привет мир</asr_text>"))
        assertEquals("Тест", cleanAsrText("<asr_text>Тест</asr_text>"))
        assertEquals("Привет", cleanAsrText("<|startoftranscript|><|ru|>Привет<|endoftranscript|>"))
        assertEquals("Hello world", cleanAsrText("language English: Hello world"))
        assertEquals("Обычный текст", cleanAsrText("   Обычный текст   "))
    }
}

package com.example.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiTest {
    @Test
    fun ttsBodyNestsVoiceConfig() {
        val m = Json.parseObject(GeminiApi.ttsBody("For the Emperor!", "Charon", "Say it like a gruff sergeant:"))
        val gc = m.obj("generationConfig")!!
        assertEquals(listOf("AUDIO"), gc.list("responseModalities"))
        assertEquals("Charon", gc.obj("speechConfig")!!.obj("voiceConfig")!!.obj("prebuiltVoiceConfig")!!.str("voiceName"))
        val text = m.objList("contents")[0].objList("parts")[0].str("text")
        assertEquals("Say it like a gruff sergeant: For the Emperor!", text)
    }

    @Test
    fun parsesPcmIntoWav() {
        val pcm = byteArrayOf(1, 0, -1, -1)
        val json = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"${B64.encode(pcm)}"}}]}}]}"""
        val media = GeminiApi.parseMedia(json).single()
        val (ext, wav) = GeminiApi.toAudioFile(media)
        assertEquals("wav", ext)
        assertEquals(44 + 4, wav.size)
        assertArrayEquals(pcm, wav.copyOfRange(44, 48))
    }

    @Test
    fun parsesMp3AndLyrics() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"[Verse] Waaagh"},{"inline_data":{"mime_type":"audio/mpeg","data":"SUQz"}}]}}]}"""
        val media = GeminiApi.parseMedia(json).single()
        assertEquals("[Verse] Waaagh", media.text)
        assertEquals("mp3", GeminiApi.toAudioFile(media).first)
    }

    @Test
    fun explainsErrorsAndEmptyResults() {
        val e = runCatching { GeminiApi.parseMedia("""{"error":{"message":"API key not valid"}}""") }.exceptionOrNull()
        assertEquals("API key not valid", e?.message)
        val textOnly = runCatching { GeminiApi.parseMedia("""{"candidates":[{"content":{"parts":[{"text":"I can't"}]},"finishReason":"STOP"}]}""") }.exceptionOrNull()
        assertTrue(textOnly!!.message!!.contains("text only"))
        assertTrue(GeminiApi.errorMessage(429, "{}").contains("Quota"))
    }

    @Test
    fun validatesModelIds() {
        assertTrue(GeminiApi.isValidModel("lyria-3-clip-preview"))
        assertFalse(GeminiApi.isValidModel("../evil"))
        assertTrue(GeminiApi.endpoint("gemini-3.8-flash-tts").endsWith("/gemini-3.8-flash-tts:generateContent"))
        assertEquals(2, GeminiApi.musicBodies("epic orchestral").size)
    }
}

package com.example.core

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreesoundApiTest {
    private val sample = """{"count":2,"next":"https://freesound.org/apiv2/search/text/?page=2","previous":null,"results":[
      {"id":123,"name":"Battle Cry 01.wav","tags":["voice","shout"],"license":"http://creativecommons.org/publicdomain/zero/1.0/",
       "username":"sgt","duration":1.4,"previews":{"preview-hq-mp3":"https://cdn.freesound.org/previews/0/123_1-hq.mp3","preview-lq-mp3":"https://cdn.freesound.org/previews/0/123_1-lq.mp3"},
       "url":"https://freesound.org/people/sgt/sounds/123/"},
      {"id":456,"name":"grunt","tags":[],"license":"https://creativecommons.org/licenses/by/4.0/","username":"orc","duration":0.6,
       "previews":{"preview-lq-ogg":"https://cdn.freesound.org/previews/0/456-lq.ogg"},"url":"https://freesound.org/people/orc/sounds/456/"}
    ]}"""

    @Test
    fun parsesResults() {
        val page = FreesoundApi.parse(sample)
        assertEquals(2, page.count)
        assertTrue(page.hasNext)
        val a = page.results[0]
        assertEquals(SoundLicense.CC0, a.license)
        assertEquals("https://cdn.freesound.org/previews/0/123_1-hq.mp3", a.previewUrl)
        assertEquals("123_battle_cry_01.mp3", a.fileName)
        val b = page.results[1]
        assertEquals(SoundLicense.BY, b.license)
        assertTrue(b.license.needsCredit)
        assertEquals("https://cdn.freesound.org/previews/0/456-lq.ogg", b.previewUrl)
    }

    @Test
    fun mapsLicenses() {
        assertEquals(SoundLicense.BY_NC, SoundLicense.fromApi("http://creativecommons.org/licenses/by-nc/3.0/"))
        assertEquals(SoundLicense.SAMPLING_PLUS, SoundLicense.fromApi("http://creativecommons.org/licenses/sampling+/1.0/"))
        assertEquals(SoundLicense.CC0, SoundLicense.fromApi("Creative Commons 0"))
        assertEquals(SoundLicense.UNKNOWN, SoundLicense.fromApi("weird"))
    }

    @Test
    fun buildsSearchUrlWithoutKey() {
        val url = FreesoundApi.searchUrl("battle cry", setOf(SoundLicense.CC0, SoundLicense.BY), 5.0, page = 2)
        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue(url.startsWith("https://freesound.org/apiv2/search/text/?query=battle+cry"))
        assertTrue(decoded.contains("license:(\"Creative Commons 0\" OR \"Attribution\") duration:[0 TO 5.0]"))
        assertTrue(decoded.contains("page=2"))
        assertFalse(url.contains("token"))
        // empty selection falls back to CC0
        assertTrue(FreesoundApi.filter(emptySet(), 3.0).contains("Creative Commons 0"))
    }

    @Test
    fun creditsAreAddedOnce() {
        val s = FreesoundApi.parse(sample).results[1]
        val once = FreesoundApi.withCredit("", s)
        assertTrue(once.startsWith("# Sound credits"))
        assertTrue(once.contains("\"grunt\" by orc"))
        assertEquals(once, FreesoundApi.withCredit(once, s))
    }

    @Test
    fun errors() {
        val e = runCatching { FreesoundApi.parse("""{"detail":"Invalid token."}""") }.exceptionOrNull()
        assertEquals("Invalid token.", e?.message)
        assertTrue(FreesoundApi.errorMessage(401, """{"detail":"Invalid token."}""").contains("API key"))
        assertNull(FreesoundApi.parse("""{"count":0,"results":[]}""").results.firstOrNull())
    }
}

package com.example.core

import java.security.KeyPairGenerator
import java.util.Date
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfSignedCertTest {
    @Test
    fun createsVerifiableV3Certificate() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = Date()
        val cert = SelfSignedCert.create(kp, "My Game Signer", notBefore = now)
        cert.verify(kp.public)
        cert.checkValidity()
        assertEquals(3, cert.version)
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertTrue(cert.subjectX500Principal.name.contains("CN=My Game Signer"))
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertArrayEquals(kp.public.encoded, cert.publicKey.encoded)
        // 30 years: crosses 2050, so GeneralizedTime must be handled
        assertTrue(cert.notAfter.time - now.time > 29L * 365 * 24 * 3600 * 1000)
    }

    @Test
    fun encodesOidsAndLongLengths() {
        assertArrayEquals(byteArrayOf(0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b),
            SelfSignedCert.oid(intArrayOf(1, 2, 840, 113549, 1, 1, 11)))
        val big = SelfSignedCert.tlv(0x04, ByteArray(300))
        assertEquals(0x82.toByte(), big[1])
        assertEquals(304, big.size)
    }
}

package com.example.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds a self-signed X.509 v3 certificate (SHA256withRSA) with plain DER encoding,
 * so the on-device APK signer needs no extra crypto library.
 */
object SelfSignedCert {

    private val SHA256_WITH_RSA = intArrayOf(1, 2, 840, 113549, 1, 1, 11)
    private val COMMON_NAME = intArrayOf(2, 5, 4, 3)
    private val ORGANIZATION = intArrayOf(2, 5, 4, 10)

    fun create(
        keyPair: KeyPair,
        commonName: String,
        organization: String = "PackFlow Studio",
        notBefore: Date = Date(),
        validYears: Int = 30,
        serial: BigInteger = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
    ): X509Certificate {
        val notAfter = Date(notBefore.time + validYears * 365L * 24 * 3600 * 1000)
        val algorithm = seq(oid(SHA256_WITH_RSA), byteArrayOf(0x05, 0x00))
        val name = seq(
            set(seq(oid(COMMON_NAME), utf8(commonName))),
            set(seq(oid(ORGANIZATION), utf8(organization)))
        )
        val tbs = seq(
            tlv(0xA0, int(BigInteger.valueOf(2))),   // [0] version v3
            int(serial),
            algorithm,
            name,
            seq(time(notBefore), time(notAfter)),
            name,
            keyPair.public.encoded                    // SubjectPublicKeyInfo, already DER
        )
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val der = seq(tbs, algorithm, tlv(0x03, byteArrayOf(0) + sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    // --- minimal DER encoding ---

    fun tlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = value.size
        when {
            len < 0x80 -> out.write(len)
            len < 0x100 -> { out.write(0x81); out.write(len) }
            len < 0x10000 -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xff) }
            else -> { out.write(0x83); out.write(len shr 16); out.write(len shr 8 and 0xff); out.write(len and 0xff) }
        }
        out.write(value)
        return out.toByteArray()
    }

    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))

    fun oid(parts: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayList<Int>()
            stack.add(v and 0x7f)
            v = v ushr 7
            while (v > 0) { stack.add((v and 0x7f) or 0x80); v = v ushr 7 }
            stack.reversed().forEach { out.write(it) }
        }
        return tlv(0x06, out.toByteArray())
    }

    /** UTCTime before 2050, GeneralizedTime from 2050 (RFC 5280). */
    private fun time(d: Date): ByteArray {
        val year = SimpleDateFormat("yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d).toInt()
        return if (year < 2050) {
            tlv(0x17, fmt("yyMMddHHmmss'Z'", d).toByteArray(Charsets.US_ASCII))
        } else {
            tlv(0x18, fmt("yyyyMMddHHmmss'Z'", d).toByteArray(Charsets.US_ASCII))
        }
    }

    private fun fmt(p: String, d: Date) =
        SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d)
}

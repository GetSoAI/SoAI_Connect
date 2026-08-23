// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    private val client = OkHttpClient()

    @Test
    fun tlsFailure_walksCauseChain() {
        assertTrue(isTlsVerificationFailure(IOException("outer", SSLHandshakeException("bad cert"))))
        assertFalse(isTlsVerificationFailure(IOException("timeout")))
    }

    @Test
    fun healthIdentity_requiresProductInstanceAndVersion() {
        val identity = DiscoveryPayloadParser.parse(
            """{"product":"soai","instance_id":"abc","instance_name":"Home","version":"1.0","scheme":"https","port":5090,"preferred_port":5090,"fallback_active":false}"""
        )
        assertEquals("abc", identity?.instanceId)
        assertEquals("Home", identity?.instanceName)
        assertNull(DiscoveryPayloadParser.parse("""{"product":"other","instance_id":"abc","version":"1"}"""))
        assertNull(DiscoveryPayloadParser.parse("not-json"))
        assertNull(DiscoveryPayloadParser.parse("""{"product":"soai","instance_id":1,"version":"1"}"""))
    }

    @Test
    fun discoveryPayload_prefersPortAndFiltersScheme() {
        val service = DiscoveryService(client, client)
        assertEquals(listOf("https", "http"), service.buildPreferredSchemeList("HTTPS"))
        assertEquals(listOf("http", "https"), service.buildPreferredSchemeList("ftp"))
        assertEquals(listOf("http", "https"), service.buildPreferredSchemeList("http"))
        assertEquals(listOf("http", "https"), service.buildPreferredSchemeList(null))
        assertEquals(5090, DiscoveryPayloadParser.parse(
            """{"product":"soai","version":"1","instance_id":"abc","instance_name":null,"port":5090,"preferred_port":5090,"fallback_active":false,"scheme":"https"}"""
        )?.port)
        assertNull(DiscoveryPayloadParser.parse("""{"product":"other","port":5090}"""))
        assertNull(DiscoveryPayloadParser.parse("""{"product":"soai","port":"5090"}"""))
        assertNull(DiscoveryPayloadParser.parse("""{"product":"soai","port":65536}"""))
    }

    @Test
    fun certificateFormatting_normalizesSeparators() {
        assertEquals("AB:CD:EF", CertificateUtils.formatHexFingerprint("ab cd:ef"))
    }

    @Test
    fun serverOrigin_normalizesDefaultsCaseAndIpv6() {
        assertEquals("https://soai.local:443", ServerOrigin.normalize("HTTPS", "SoAI.Local", -1))
        assertEquals("http://soai.local:8080", ServerOrigin.normalize("http", "soai.local", 8080))
        assertEquals("https://[2001:db8::1]:443", ServerOrigin.normalize("https", "[2001:DB8::1]", -1))
        assertNull(ServerOrigin.normalize("ftp", "soai.local", -1))
        assertNull(ServerOrigin.normalize("https", "", 443))
        assertNull(ServerOrigin.normalize("https", "soai.local", 0))
    }
}

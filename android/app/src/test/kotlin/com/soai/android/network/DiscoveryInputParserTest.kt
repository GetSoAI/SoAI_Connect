// SPDX-License-Identifier: MIT

package com.soai.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryInputParserTest {

    @Test
    fun parseHttpsWithoutPort_usesHttpsDefaultPort() {
        val parsed = DiscoveryInputParser.parse("https://soai.local")
        assertEquals("soai.local", parsed.hostname)
        assertEquals(443, parsed.explicitPort)
        assertEquals("https", parsed.preferredScheme)
    }

    @Test
    fun parseHostWithExplicitPort_extractsPort() {
        val parsed = DiscoveryInputParser.parse("192.168.1.10:5090")
        assertEquals("192.168.1.10", parsed.hostname)
        assertEquals(5090, parsed.explicitPort)
        assertNull(parsed.preferredScheme)
    }

    @Test
    fun parseBracketedIpv6WithPort_extractsHostAndPort() {
        val parsed = DiscoveryInputParser.parse("[2001:db8::1]:5090")
        assertEquals("2001:db8::1", parsed.hostname)
        assertEquals(5090, parsed.explicitPort)
        assertNull(parsed.preferredScheme)
    }

    @Test
    fun parseUppercaseHttpsScheme_recognizedAndLowercased() {
        val parsed = DiscoveryInputParser.parse("HTTPS://soai.local:5090/path")
        assertEquals("soai.local", parsed.hostname)
        assertEquals(5090, parsed.explicitPort)
        assertEquals("https", parsed.preferredScheme)
    }

    @Test
    fun parseMixedCaseHttpScheme_recognizedAndLowercased() {
        val parsed = DiscoveryInputParser.parse("Http://192.168.1.10")
        assertEquals("192.168.1.10", parsed.hostname)
        assertEquals(80, parsed.explicitPort)
        assertEquals("http", parsed.preferredScheme)
    }

    @Test
    fun parseMalformedSchemeUri_isRejected() {
        val parsed = DiscoveryInputParser.parse("https://host name:5090/path?x#y")
        assertEquals("", parsed.hostname)
        assertNull(parsed.explicitPort)
        assertNull(parsed.preferredScheme)
    }

    @Test
    fun parseBareIpv6AndStripSuffixes() {
        val parsed = DiscoveryInputParser.parse("2001:db8::1/path?query#fragment")
        assertEquals("2001:db8::1", parsed.hostname)
        assertNull(parsed.explicitPort)
        assertEquals("[2001:db8::1]", DiscoveryInputParser.formatHost(parsed.hostname))
    }

    @Test
    fun detectIpv6ZoneIdentifiers_onlyInAuthority() {
        assertEquals(true, DiscoveryInputParser.hasIpv6ZoneIdentifier("[fe80::1%wlan0]:5090"))
        assertEquals(true, DiscoveryInputParser.hasIpv6ZoneIdentifier("https://[fe80::1%25wlan0]:5090"))
        assertEquals(false, DiscoveryInputParser.hasIpv6ZoneIdentifier("https://soai.local/path?q=10%25"))
        assertEquals(false, DiscoveryInputParser.hasIpv6ZoneIdentifier("https://user%name@soai.local:5090"))
        assertEquals(false, DiscoveryInputParser.hasIpv6ZoneIdentifier("soai%local:5090"))
        assertEquals(false, DiscoveryInputParser.hasIpv6ZoneIdentifier("host[name%value]:5090"))
    }

    @Test
    fun parseUnsupportedSchemeAndUserInfo_areRejected() {
        assertEquals("", DiscoveryInputParser.parse("ftp://soai.local").hostname)
        assertEquals("", DiscoveryInputParser.parse("https://user@soai.local").hostname)
    }

    @Test
    fun parseMalformedPortsAndBrackets_areRejected() {
        assertEquals("", DiscoveryInputParser.parse("soai.local:not-a-port").hostname)
        assertEquals("", DiscoveryInputParser.parse("[2001:db8::1:5090").hostname)
    }
}

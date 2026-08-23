// SPDX-License-Identifier: MIT

package com.soai.android.network

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    private val unresolvable: (String) -> List<InetAddress> = { host ->
        throw UnknownHostException(host)
    }

    private fun resolvingTo(vararg addresses: String): (String) -> List<InetAddress> {
        return { _ -> addresses.map { address -> InetAddress.getByName(address) } }
    }

    @Test
    fun privateIpv4Ranges_allowCleartext() {
        assertTrue(CleartextPolicy.allowsCleartext("10.0.0.1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("172.16.4.9", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("172.31.255.254", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("192.168.1.50", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("127.0.0.1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("169.254.10.10", unresolvable))
    }

    @Test
    fun sharedAddressSpace_isTreatedAsPrivate() {
        assertTrue(CleartextPolicy.allowsCleartext("100.64.0.1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("100.101.102.103", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("100.127.255.254", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("fd7a:115c:a1e0::1", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("100.63.255.255", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("100.128.0.1", unresolvable))
    }

    @Test
    fun publicIpv4_deniesCleartext() {
        assertFalse(CleartextPolicy.allowsCleartext("203.0.113.10", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("8.8.8.8", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("172.32.0.1", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("11.0.0.1", unresolvable))
    }

    @Test
    fun privateIpv6_allowsLoopbackLinkLocalAndUniqueLocal() {
        assertTrue(CleartextPolicy.allowsCleartext("::1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("[::1]", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("fe80::1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("fd00::1", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("fc00::1", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("2001:db8::1", unresolvable))
    }

    @Test
    fun privateHostnameSuffixes_allowCleartextWithoutResolving() {
        assertTrue(CleartextPolicy.allowsCleartext("localhost", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("soai.local", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("SoAI.Local", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("box.lan", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("server.home.arpa", unresolvable))
        assertTrue(CleartextPolicy.allowsCleartext("host.internal", unresolvable))
    }

    @Test
    fun bareHostname_resolvingToPrivateAddress_allowsCleartext() {
        assertTrue(CleartextPolicy.allowsCleartext("soai-box", resolvingTo("192.168.1.50")))
        assertTrue(CleartextPolicy.allowsCleartext("nas", resolvingTo("10.1.2.3")))
        assertTrue(CleartextPolicy.allowsCleartext("homeserver", resolvingTo("172.16.0.9")))
        assertTrue(CleartextPolicy.allowsCleartext("router.myisp.net", resolvingTo("192.168.0.1")))
    }

    @Test
    fun bareHostname_resolvingToPublicAddress_deniesCleartext() {
        assertFalse(CleartextPolicy.allowsCleartext("example.com", resolvingTo("93.184.216.34")))
        assertFalse(CleartextPolicy.allowsCleartext("soai-box", resolvingTo("203.0.113.7")))
    }

    @Test
    fun hostnameResolvingToMixedAddresses_deniesCleartext() {
        assertFalse(
            CleartextPolicy.allowsCleartext("mixed", resolvingTo("192.168.1.10", "203.0.113.7"))
        )
    }

    @Test
    fun unresolvableOrEmptyHostname_deniesCleartext() {
        assertFalse(CleartextPolicy.allowsCleartext("notlocal", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("   ", unresolvable))
        assertFalse(CleartextPolicy.allowsCleartext("nothing", { _ -> emptyList() }))
    }
}

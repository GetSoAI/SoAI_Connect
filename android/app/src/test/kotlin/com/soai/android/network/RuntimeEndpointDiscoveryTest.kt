// SPDX-License-Identifier: MIT

package com.soai.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEndpointDiscoveryTest {

    @Test
    fun `payload parser validates fallback invariants`() {
        val payload = DiscoveryPayloadParser.parse(
            """
            {
              "product": "soai",
              "version": "1.0.0",
              "instance_id": "instance-a",
              "instance_name": "Rack A",
              "scheme": "https",
              "port": 5091,
              "preferred_port": 5090,
              "fallback_active": true
            }
            """.trimIndent()
        )
        assertEquals(5091, payload?.port)
        assertEquals(5090, payload?.preferredPort)
        assertEquals(true, payload?.fallbackActive)

        val invalid = DiscoveryPayloadParser.parse(
            """
            {
              "product": "soai",
              "version": "1.0.0",
              "instance_id": "instance-a",
              "instance_name": null,
              "scheme": "http",
              "port": 5090,
              "preferred_port": 5090,
              "fallback_active": true
            }
            """.trimIndent()
        )
        assertNull(invalid)
    }

    @Test
    fun `selection prefers saved identity and reports ambiguity`() {
        val first = discoveredEndpoint("instance-a", 5090)
        val second = discoveredEndpoint("instance-b", 5091)
        val selected = DiscoveryEndpointSelector.select(listOf(second, first), "instance-a")
        assertTrue(selected is DiscoverySelection.Selected)
        assertEquals(first, (selected as DiscoverySelection.Selected).endpoint)
        assertTrue(
            DiscoveryEndpointSelector.select(listOf(first, second), null) is
                DiscoverySelection.Ambiguous
        )
    }

    private fun discoveredEndpoint(instanceId: String, port: Int): DiscoveryResult {
        return DiscoveryResult(
            serverUrl = "http://host:$port",
            port = port,
            scheme = "http",
            tlsStatus = TlsStatus.NONE,
            instanceId = instanceId,
            instanceName = null,
            version = "1.0.0",
            preferredPort = 5090,
            fallbackActive = port != 5090,
            cleartextToPublicHost = false
        )
    }
}

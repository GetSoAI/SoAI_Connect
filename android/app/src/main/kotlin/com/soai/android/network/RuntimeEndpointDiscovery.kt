// SPDX-License-Identifier: MIT

package com.soai.android.network

import org.json.JSONObject

internal data class RuntimeEndpointPayload(
    val instanceId: String,
    val instanceName: String?,
    val version: String,
    val scheme: String,
    val port: Int,
    val preferredPort: Int,
    val fallbackActive: Boolean
)

internal object DiscoveryPayloadParser {
    fun parse(body: String?): RuntimeEndpointPayload? {
        if (body.isNullOrBlank()) return null
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        if ((json.opt("product") as? String)?.trim() != SOAI_PRODUCT_MARKER) return null
        val version = requiredText(json, "version") ?: return null
        val instanceId = requiredText(json, "instance_id") ?: return null
        val instanceName = nullableText(json, "instance_name") ?: return null
        val scheme = requiredText(json, "scheme")?.lowercase()
            ?.takeIf { it == "http" || it == "https" } ?: return null
        val port = readPort(json, "port") ?: return null
        val preferredPort = readPort(json, "preferred_port") ?: return null
        val fallbackActive = json.opt("fallback_active") as? Boolean ?: return null
        if (fallbackActive != (port != preferredPort)) return null
        return RuntimeEndpointPayload(
            instanceId = instanceId,
            instanceName = instanceName.ifBlank { null },
            version = version,
            scheme = scheme,
            port = port,
            preferredPort = preferredPort,
            fallbackActive = fallbackActive
        )
    }

    private fun requiredText(json: JSONObject, name: String): String? {
        return (json.opt(name) as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun nullableText(json: JSONObject, name: String): String? {
        if (json.isNull(name)) return ""
        return (json.opt(name) as? String)?.trim()
    }

    private fun readPort(json: JSONObject, name: String): Int? {
        val value = json.opt(name)
        if (value !is Number) return null
        val port = value.toInt()
        if (value.toDouble() != port.toDouble()) return null
        return port.takeIf { it in 1..65535 }
    }
}

internal sealed interface DiscoverySelection {
    data class Selected(val endpoint: DiscoveryResult) : DiscoverySelection
    data class Ambiguous(val endpoints: List<DiscoveryResult>) : DiscoverySelection
    data object NotFound : DiscoverySelection
}

internal object DiscoveryEndpointSelector {
    fun select(
        candidates: List<DiscoveryResult>,
        targetInstanceId: String?
    ): DiscoverySelection {
        val unique = candidates.distinctBy {
            Triple(it.instanceId, it.scheme, it.serverUrl)
        }
        val matching = if (targetInstanceId.isNullOrBlank()) {
            unique
        } else {
            unique.filter { it.instanceId == targetInstanceId }
        }
        return when (matching.size) {
            0 -> DiscoverySelection.NotFound
            1 -> DiscoverySelection.Selected(matching.first())
            else -> DiscoverySelection.Ambiguous(matching)
        }
    }
}

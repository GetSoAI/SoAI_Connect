// SPDX-License-Identifier: MIT

package com.soai.android.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

enum class WakeOnLanField {
    MAC,
    BROADCAST,
    PORT
}

class WakeOnLanException(val field: WakeOnLanField) : Exception("Invalid ${field.name}")

class WakeOnLanDeliveryException(cause: Throwable) : Exception("Wake-on-LAN delivery failed", cause)

object WakeOnLanSender {

    private const val MAGIC_PREFIX_LENGTH = 6
    private const val MAC_LENGTH_BYTES = 6
    private const val MAGIC_REPETITIONS = 16

    fun buildMagicPacket(macAddress: String): ByteArray {
        val macBytes = parseMacAddress(macAddress)
        val packet = ByteArray(MAGIC_PREFIX_LENGTH + MAC_LENGTH_BYTES * MAGIC_REPETITIONS)

        for (index in 0 until MAGIC_PREFIX_LENGTH) {
            packet[index] = 0xFF.toByte()
        }

        var offset = MAGIC_PREFIX_LENGTH
        repeat(MAGIC_REPETITIONS) {
            for (macIndex in 0 until MAC_LENGTH_BYTES) {
                packet[offset] = macBytes[macIndex]
                offset += 1
            }
        }

        return packet
    }

    fun parseMacAddress(macAddress: String): ByteArray {
        val normalized = macAddress
            .trim()
            .replace("-", ":")
            .lowercase()

        val parts = normalized.split(":").filter { it.isNotBlank() }
        val hexPairs = if (parts.size == MAC_LENGTH_BYTES) {
            parts
        } else {
            val raw = normalized.replace(":", "")
            if (raw.length != MAC_LENGTH_BYTES * 2) {
                throw WakeOnLanException(WakeOnLanField.MAC)
            }
            raw.chunked(2)
        }

        if (hexPairs.size != MAC_LENGTH_BYTES) {
            throw WakeOnLanException(WakeOnLanField.MAC)
        }

        val result = ByteArray(MAC_LENGTH_BYTES)
        for (index in 0 until MAC_LENGTH_BYTES) {
            val pair = hexPairs[index]
            if (pair.length != 2) {
                throw WakeOnLanException(WakeOnLanField.MAC)
            }
            val value = pair.toIntOrNull(16) ?: throw WakeOnLanException(WakeOnLanField.MAC)
            result[index] = value.toByte()
        }
        return result
    }

    fun resolveBroadcastAddresses(explicitBroadcastAddress: String?): List<InetAddress> {
        val trimmed = explicitBroadcastAddress?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            val address = try {
                InetAddress.getByName(trimmed)
            } catch (_: Exception) {
                throw WakeOnLanException(WakeOnLanField.BROADCAST)
            }
            if (address !is Inet4Address) {
                throw WakeOnLanException(WakeOnLanField.BROADCAST)
            }
            return listOf(address)
        }

        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to enumerate network interfaces", exception)
            null
        }
        val candidates = LinkedHashSet<InetAddress>()
        interfaces?.let { networkInterfaces ->
            for (networkInterface in networkInterfaces) {
                if (!networkInterface.isUp) continue
                if (networkInterface.isLoopback) continue
                for (address in networkInterface.interfaceAddresses) {
                    val broadcast = address.broadcast ?: continue
                    if (broadcast is Inet4Address) {
                        candidates.add(broadcast)
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(InetAddress.getByName(LIMITED_BROADCAST_ADDRESS))
        }
        return candidates.toList()
    }

    fun sendWakeSignal(
        macAddress: String,
        broadcastAddress: String?,
        port: Int
    ) {
        if (port !in 1..65535) {
            throw WakeOnLanException(WakeOnLanField.PORT)
        }

        val packetBytes = buildMagicPacket(macAddress)
        val addresses = resolveBroadcastAddresses(broadcastAddress)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            var delivered = false
            var lastFailure: Exception? = null
            for (address in addresses) {
                try {
                    socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))
                    delivered = true
                } catch (exception: Exception) {
                    lastFailure = exception
                }
            }
            if (!delivered) {
                throw WakeOnLanDeliveryException(checkNotNull(lastFailure))
            }
        }
    }

    private const val LIMITED_BROADCAST_ADDRESS = "255.255.255.255"
    private const val TAG = "WakeOnLanSender"
}

// SPDX-License-Identifier: MIT

package com.soai.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOnLanSenderTest {

    @Test
    fun parseMacAddress_colonSeparated_parsesBytes() {
        val bytes = WakeOnLanSender.parseMacAddress("AA:BB:CC:DD:EE:FF")
        assertEquals(6, bytes.size)
        assertEquals(0xAA.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[5])
    }

    @Test
    fun parseMacAddress_dashSeparated_parsesBytes() {
        val bytes = WakeOnLanSender.parseMacAddress("aa-bb-cc-dd-ee-ff")
        assertEquals(6, bytes.size)
        assertEquals(0xBB.toByte(), bytes[1])
    }

    @Test
    fun parseMacAddress_plainHex_parsesBytes() {
        val bytes = WakeOnLanSender.parseMacAddress("aabbccddeeff")
        assertEquals(6, bytes.size)
        assertEquals(0xCC.toByte(), bytes[2])
    }

    @Test
    fun buildMagicPacket_hasExpectedLengthAndPrefix() {
        val packet = WakeOnLanSender.buildMagicPacket("00:11:22:33:44:55")
        assertEquals(102, packet.size)
        for (index in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[index])
        }
    }

    @Test
    fun buildMagicPacket_repeatsMacAddress() {
        val packet = WakeOnLanSender.buildMagicPacket("01:02:03:04:05:06")
        val firstMacOffset = 6
        assertEquals(0x01.toByte(), packet[firstMacOffset])
        assertEquals(0x06.toByte(), packet[firstMacOffset + 5])

        val secondMacOffset = 12
        assertEquals(0x01.toByte(), packet[secondMacOffset])
        assertEquals(0x06.toByte(), packet[secondMacOffset + 5])

        val lastMacOffset = 6 + 15 * 6
        assertEquals(0x01.toByte(), packet[lastMacOffset])
        assertEquals(0x06.toByte(), packet[lastMacOffset + 5])

        assertTrue(packet.any { it != 0.toByte() })
    }
}


// SPDX-License-Identifier: MIT

package com.soai.android.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceIdentityTest {
    @Test
    fun metadataCookie_isBoundedPathScopedHttpOnlyAndHostOnly() {
        val identity = AndroidDeviceIdentity(
            deviceId = "8c3fb825-a0d7-4f56-97ef-74370d4fb8f0",
            deviceLabel = "My Phone"
        )

        val cookie = AndroidDeviceIdentityCookie.build("https://soai.local:5090", identity)

        assertTrue(cookie.contains("soai-android-device="))
        assertTrue(cookie.contains("Path=/api/v1/webui/auth/login"))
        assertTrue(cookie.contains("HttpOnly"))
        assertTrue(cookie.contains("SameSite=Strict"))
        assertTrue(cookie.contains("Secure"))
        assertFalse(cookie.contains("Domain="))
        assertTrue(cookie.length <= 768)
    }

    @Test
    fun metadataCookie_omitsSecureForPlainHttpDevelopmentServers() {
        val identity = AndroidDeviceIdentity(
            deviceId = "8c3fb825-a0d7-4f56-97ef-74370d4fb8f0",
            deviceLabel = "Phone"
        )

        val cookie = AndroidDeviceIdentityCookie.build("http://192.168.1.10:5090", identity)

        assertFalse(cookie.contains("; Secure"))
    }

    @Test
    fun deviceLabel_isTrimmedAndBounded() {
        assertEquals("My Phone", AndroidDeviceIdentity.normalizeLabel("  My Phone  "))
        assertEquals(80, AndroidDeviceIdentity.normalizeLabel("x".repeat(100)).length)
        val emojiLabel = AndroidDeviceIdentity.normalizeLabel("😀".repeat(100))
        assertEquals(80, emojiLabel.codePointCount(0, emojiLabel.length))
        assertTrue(Character.isSurrogatePair(emojiLabel[emojiLabel.length - 2], emojiLabel.last()))
    }

    @Test
    fun metadataValueStaysWithinBoundedCookieContractForMaximumUnicodeLabel() {
        val identity = AndroidDeviceIdentity(
            deviceId = "8c3fb825-a0d7-4f56-97ef-74370d4fb8f0",
            deviceLabel = "😀".repeat(80)
        )

        val cookie = AndroidDeviceIdentityCookie.build("https://soai.local", identity)
        val encodedValue = cookie.substringAfter('=').substringBefore(';')

        assertTrue(encodedValue.length <= 2_048)
    }
}

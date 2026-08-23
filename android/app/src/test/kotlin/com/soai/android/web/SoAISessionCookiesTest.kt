// SPDX-License-Identifier: MIT

package com.soai.android.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoAISessionCookiesTest {
    @Test
    fun authentication_requiresExactNonBlankCookieName() {
        assertTrue(SoAISessionCookies.hasAuthentication("http://soai.local", "soai-http-token=abc; soai-http-csrf=def"))
        assertTrue(SoAISessionCookies.hasAuthentication("https://soai.local", "__Host-soai-token=abc; __Host-soai-csrf=def"))
        assertFalse(SoAISessionCookies.hasAuthentication("http://soai.local", "__Host-soai-token=abc"))
        assertFalse(SoAISessionCookies.hasAuthentication("https://soai.local", "soai-http-token=abc"))
        assertFalse(SoAISessionCookies.hasAuthentication("http://soai.local", "soai-http-token="))
        assertFalse(SoAISessionCookies.hasAuthentication("http://soai.local", null))
    }

    @Test
    fun csrfToken_handlesSpacingAndEqualsInValue() {
        assertEquals("token=value", SoAISessionCookies.csrfToken("http://soai.local", "a=b; soai-http-csrf=token=value"))
        assertNull(SoAISessionCookies.csrfToken("https://soai.local", "soai-http-csrf=token"))
    }
}

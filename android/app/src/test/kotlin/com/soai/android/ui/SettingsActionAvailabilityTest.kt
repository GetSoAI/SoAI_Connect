// SPDX-License-Identifier: MIT

package com.soai.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsActionAvailabilityTest {

    @Test
    fun `new installation exposes no unavailable data actions`() {
        val availability = SettingsActionAvailability.resolve(
            hasServer = false,
            hasAuthentication = false,
            hasCache = false,
            hasTrustedCertificate = false
        )

        assertEquals(
            SettingsActionAvailability(
                logout = false,
                clearCache = false,
                changeServer = false,
                dataSection = false,
                resetCertificate = false
            ),
            availability
        )
    }

    @Test
    fun `each action follows the state that gives it an effect`() {
        val availability = SettingsActionAvailability.resolve(
            hasServer = true,
            hasAuthentication = true,
            hasCache = true,
            hasTrustedCertificate = true
        )

        assertEquals(
            SettingsActionAvailability(
                logout = true,
                clearCache = true,
                changeServer = true,
                dataSection = true,
                resetCertificate = true
            ),
            availability
        )
    }

    @Test
    fun `cached data alone keeps only cache action and section visible`() {
        val availability = SettingsActionAvailability.resolve(
            hasServer = false,
            hasAuthentication = false,
            hasCache = true,
            hasTrustedCertificate = false
        )

        assertEquals(
            SettingsActionAvailability(
                logout = false,
                clearCache = true,
                changeServer = false,
                dataSection = true,
                resetCertificate = false
            ),
            availability
        )
    }
}

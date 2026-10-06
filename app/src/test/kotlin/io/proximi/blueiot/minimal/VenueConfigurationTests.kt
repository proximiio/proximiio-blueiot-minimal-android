//
//  VenueConfigurationTests.kt
//  BlueiotMinimalTests
//
//  The configuration sentence and the binding configuration built from the build-time
//  values. A key that is empty or does not parse must be named on screen; otherwise the
//  symptom is a Connect button that does nothing.
//
package io.proximi.blueiot.minimal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VenueConfigurationTests {
    /** Every empty key is named, in one sentence. */
    @Test
    fun missingNamesEveryEmptyKey() {
        assertEquals(
            "Set PROXIMIIO_APPLICATION_TOKEN, BLUEIOT_RELAY_APP_TOKEN in secrets.properties, then rebuild.",
            VenueConfiguration.missing(null, "https://relay-api-sandbox.proximi.fi", null),
        )
        assertNull(VenueConfiguration.missing("t", "https://relay-api-sandbox.proximi.fi", "a"))
    }

    /** A URL that does not parse is named, and no binding is configured. */
    @Test
    fun invalidRelayURLIsNamed() {
        assertEquals(
            "Set a valid BLUEIOT_RELAY_URL in secrets.properties, then rebuild.",
            VenueConfiguration.missing("t", "ftp://relay.example.com", "a"),
        )
        assertNull(VenueConfiguration.binding("ftp://relay.example.com", "a"))
    }

    /** No app token, no binding: Connect stays disabled. A bare host becomes https. */
    @Test
    fun bindingNeedsURLAndAppToken() {
        assertNull(VenueConfiguration.binding("https://relay-api-sandbox.proximi.fi", null))
        assertNull(VenueConfiguration.binding(null, "a"))
        val configuration = requireNotNull(VenueConfiguration.binding("relay-api-sandbox.proximi.fi", "a"))
        assertEquals("https://relay-api-sandbox.proximi.fi", configuration.relayURL.baseUrl.toString())
    }
}

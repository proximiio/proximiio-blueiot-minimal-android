//
//  BackgroundPositioningTests.kt
//  BlueiotMinimalTests
//
//  Positioning in a pocket needs four things (see `Venue.kt`). These are the two
//  that fail silently: forget either and the dot stops within minutes of the screen
//  locking, with nothing on screen to say why. The other two are the manifest
//  permissions and the visitor's own grant, and a build without the permissions is
//  one the SDK warns about in the log.
//
//  There is no `DiagnosticsTests` here. Its iOS twin proves that the diagnostics log
//  never carries a configured secret verbatim; the Android SDK records no log yet
//  (README, "The diagnostics log"), so there is nothing to prove and the test is
//  deferred with the gap rather than stubbed.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayConfiguration
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPositioningTests {
    /**
     * Asked once: while Android has never been asked, and never after an answer — a
     * refusal included, because nagging is how a refusal becomes an uninstall.
     */
    @Test
    fun locationIsAskedForOnlyWhileNeverAsked() {
        assertTrue(LocationPrompt.isOwed(hasBeenAsked = false))
        assertFalse(LocationPrompt.isOwed(hasBeenAsked = true))
    }

    /**
     * The first of the two switches: the foreground service is what keeps the process
     * alive off screen, and `relayOnly` leaves `serviceOptions` at `null` — a default
     * is the easiest thing to fall back to unnoticed.
     */
    @Test
    fun theConfigurationKeepsRunningInTheBackground() {
        val options = requireNotNull(Venue.configuration("t").serviceOptions)
        assertTrue("the socket is read on the CPU, and Doze stops it", options.holdsWakeLock)
        // A relay-only app scans nothing, so `location` is the only foreground-service
        // type it can hold — and asking for `connectedDevice` would make
        // `startForeground` throw on API 34+.
        assertFalse(options.includesConnectedDeviceType)
    }

    /**
     * The second switch, and the one with no symptom of its own: without it the
     * facade pauses the relay provider a second after the screen locks, however
     * healthy the service looks.
     */
    @Test
    fun theRelayConfigurationRunsInTheBackground() {
        val endpoint = requireNotNull(BlueiotCloudRelayEndpoint.fromText("blueiot.proximi.fi"))
        val configuration =
            BlueiotCloudRelayConfiguration(
                endpoint = endpoint,
                tagId = "7001",
                runsInBackground = true,
            )
        assertTrue(configuration.runsInBackground)
        // And no floor table: passing one switches the SDK's own derivation off.
        assertTrue(configuration.floorNoMap.isEmpty())
    }
}

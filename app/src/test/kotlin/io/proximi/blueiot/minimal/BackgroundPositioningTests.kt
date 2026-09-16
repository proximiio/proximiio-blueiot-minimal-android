//
//  BackgroundPositioningTests.kt
//  BlueiotMinimalTests
//
//  Background positioning requires four things (see `Venue.kt`). These tests cover the
//  two that fail silently: with either missing, position updates stop within minutes of
//  the screen locking and nothing on screen says why. The other two are the manifest
//  permissions and the visitor's grant; a build missing the permissions is reported in
//  the SDK log.
//
//  There is no `DiagnosticsTests`. The Android SDK records no diagnostics log (README,
//  "The diagnostics log"), so its iOS twin has nothing to assert against.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayConfiguration
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPositioningTests {
    /**
     * The prompt is owed only while Android has never been asked. A refusal counts as an
     * answer.
     */
    @Test
    fun locationIsAskedForOnlyWhileNeverAsked() {
        assertTrue(LocationPrompt.isOwed(hasBeenAsked = false))
        assertFalse(LocationPrompt.isOwed(hasBeenAsked = true))
    }

    /**
     * The foreground service is what keeps the process alive off screen. `relayOnly`
     * leaves `serviceOptions` at `null`, which is foreground-only positioning.
     */
    @Test
    fun theConfigurationKeepsRunningInTheBackground() {
        val options = requireNotNull(Venue.configuration("t").serviceOptions)
        assertTrue("the socket is read on the CPU, and Doze stops it", options.holdsWakeLock)
        // A relay-only app scans nothing, so the `connectedDevice` foreground-service
        // type is never warranted and the service's type mask stays `location` only.
        assertFalse(options.includesConnectedDeviceType)
    }

    /**
     * Without `runsInBackground` the SDK pauses the relay provider whenever the app is
     * backgrounded, however healthy the foreground service is. The default is `false`.
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

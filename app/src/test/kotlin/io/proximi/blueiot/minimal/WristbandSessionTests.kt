//
//  WristbandSessionTests.kt
//  BlueiotMinimalTests
//
//  The rules `RootScreen` and `WristbandPrompt` read from `WristbandSession`. A wrong
//  rule closes the map during a bind from the sheet, keeps the map after a session
//  ended, or asks for a location Android cannot grant precisely.
//
package io.proximi.blueiot.minimal

import android.Manifest
import io.proximi.sdk.blueiot.binding.BlueiotBindingEndReason
import io.proximi.sdk.blueiot.binding.BlueiotBindingLink
import io.proximi.sdk.blueiot.binding.BlueiotBindingSession
import io.proximi.sdk.blueiot.binding.BlueiotBindingSignal
import io.proximi.sdk.blueiot.binding.BlueiotBindingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WristbandSessionTests {
    private val active =
        BlueiotBindingState.Active(
            BlueiotBindingSession(bindingID = UUID.randomUUID(), endsAt = Long.MAX_VALUE, tokenExpiresAt = Long.MAX_VALUE),
            BlueiotBindingLink.ONLINE,
            BlueiotBindingSignal.Ok,
        )

    /** A bind in progress keeps whatever screen is shown. */
    @Test
    fun bindingKeepsTheCurrentScreen() {
        assertTrue(WristbandSession.following(true, BlueiotBindingState.Binding))
        assertFalse(WristbandSession.following(false, BlueiotBindingState.Binding))
    }

    /** An active session opens the map; an ended or absent one shows the prompt. */
    @Test
    fun sessionStateDecidesThePrompt() {
        assertTrue(WristbandSession.following(false, active))
        assertFalse(WristbandSession.following(true, BlueiotBindingState.Ended(BlueiotBindingEndReason.Superseded)))
        assertFalse(WristbandSession.following(true, BlueiotBindingState.Unbound))
    }

    /** The prompt shows the end reason's notice, and none before a session ended. */
    @Test
    fun endNoticeFollowsTheEndReason() {
        assertEquals(
            WristbandCopy.notice(BlueiotBindingEndReason.Superseded),
            WristbandCopy.endNotice(BlueiotBindingState.Ended(BlueiotBindingEndReason.Superseded)),
        )
        assertNull(WristbandCopy.endNotice(BlueiotBindingState.Unbound))
        assertNull(WristbandCopy.endNotice(active))
    }

    /** Android offers precise location only when fine and coarse are requested together. */
    @Test
    fun locationAskRequestsFineAndCoarse() {
        assertEquals(
            setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            WristbandSession.locationPermissions.toSet(),
        )
    }
}

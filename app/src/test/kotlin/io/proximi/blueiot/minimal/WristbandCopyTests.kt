//
//  WristbandCopyTests.kt
//  BlueiotMinimalTests
//
//  The visitor-facing text of the wristband session. A wrong mapping fails silently: the
//  visitor reads advice for a different failure. The composables are not tested.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.blueiot.binding.BlueiotBindingEndReason
import io.proximi.sdk.blueiot.binding.BlueiotBindingError
import io.proximi.sdk.blueiot.binding.BlueiotBindingLink
import io.proximi.sdk.blueiot.binding.BlueiotBindingSession
import io.proximi.sdk.blueiot.binding.BlueiotBindingSignal
import io.proximi.sdk.blueiot.binding.BlueiotBindingState
import io.proximi.sdk.blueiot.binding.location.BlueiotLocationCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WristbandCopyTests {
    // Bind errors

    @Test
    fun bindErrorsHaveTheirOwnMessage() {
        assertEquals(
            "This wristband is already in use. Please ask the staff.",
            WristbandCopy.message(BlueiotBindingError.TagAlreadyBound(requestID = null)),
        )
        assertEquals(
            "This wristband is not active. Please ask the staff.",
            WristbandCopy.message(BlueiotBindingError.TagNotAvailable(requestID = null)),
        )
        assertEquals(
            "Connect this wristband at the reception desk.",
            WristbandCopy.message(BlueiotBindingError.TagNotAtReception(requestID = null)),
        )
        assertTrue(
            WristbandCopy.message(BlueiotBindingError.AppTokenRejected(requestID = null)).contains("BLUEIOT_RELAY_APP_TOKEN"),
        )
    }

    /** The take-over refusal names what the phone sent, not the relay's text. */
    @Test
    fun zoneRefusalFollowsTheLocationCause() {
        fun message(cause: BlueiotLocationCause): String =
            WristbandCopy.message(
                BlueiotBindingError.NotInAuthorizedZone(locationCause = cause, serverMessage = "outside zone", requestID = null),
            )
        assertTrue(message(BlueiotLocationCause.PERMISSION_MISSING).contains("allow location"))
        assertTrue(message(BlueiotLocationCause.APPROXIMATE_LOCATION).contains("Use precise location"))
        assertTrue(message(BlueiotLocationCause.SIMULATED_LOCATION).contains("simulated"))
        assertEquals(
            "To take over this wristband, be inside the museum with location enabled, or ask at the reception desk.",
            message(BlueiotLocationCause.REJECTED_BY_RELAY),
        )
        assertFalse(message(BlueiotLocationCause.REJECTED_BY_RELAY).contains("outside zone"))
    }

    @Test
    fun rateLimitStatesTheWait() {
        assertEquals(
            "Too many attempts. Try again in 30 seconds.",
            WristbandCopy.message(BlueiotBindingError.RateLimited(retryAfter = 29.2, requestID = null)),
        )
        assertEquals(
            "Too many attempts. Try again in a moment.",
            WristbandCopy.message(BlueiotBindingError.RateLimited(retryAfter = null, requestID = null)),
        )
    }

    // End reasons

    @Test
    fun supersededNoticeNamesTheOtherPhone() {
        assertTrue(
            WristbandCopy.notice(BlueiotBindingEndReason.Superseded).startsWith("Your wristband was scanned by another phone."),
        )
    }

    /** A reason a newer relay adds still ends the visit with a notice. */
    @Test
    fun unknownReasonHasANotice() {
        assertEquals("Your visit has ended.", WristbandCopy.notice(BlueiotBindingEndReason.Unknown("NEW_REASON")))
    }

    // Status line

    @Test
    fun statusLineFollowsLinkAndSignal() {
        val session = BlueiotBindingSession(bindingID = UUID.randomUUID(), endsAt = Long.MAX_VALUE, tokenExpiresAt = Long.MAX_VALUE)
        fun active(
            link: BlueiotBindingLink,
            signal: BlueiotBindingSignal,
        ) = BlueiotBindingState.Active(session, link, signal)
        assertEquals("Connecting…", WristbandCopy.status(active(BlueiotBindingLink.CONNECTING, BlueiotBindingSignal.Ok)))
        assertEquals("Online", WristbandCopy.status(active(BlueiotBindingLink.ONLINE, BlueiotBindingSignal.Ok)))
        assertEquals("Signal lost", WristbandCopy.status(active(BlueiotBindingLink.ONLINE, BlueiotBindingSignal.Lost(since = 0))))
        assertEquals("Reconnecting…", WristbandCopy.status(active(BlueiotBindingLink.RECONNECTING, BlueiotBindingSignal.Ok)))
        assertNull(WristbandCopy.status(BlueiotBindingState.Ended(BlueiotBindingEndReason.Superseded)))
        assertNull(WristbandCopy.status(BlueiotBindingState.Unbound))
    }

    /** The diagnostics line carries the state and the reason, never a tag id. */
    @Test
    fun logLineHasNoTagID() {
        assertEquals("ended, SUPERSEDED", WristbandCopy.logLine(BlueiotBindingState.Ended(BlueiotBindingEndReason.Superseded)))
    }
}

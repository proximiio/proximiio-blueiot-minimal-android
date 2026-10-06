//
//  WristbandCopy.kt
//  BlueiotMinimal
//
//  The visitor-facing text for the wristband session: one message per bind error, one
//  notice per end reason, and the status line on the map. Pure functions, covered by
//  tests.
//
//  The text is keyed on the SDK's error cases and end reasons, never on the relay-api's
//  English messages, which may change.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.blueiot.binding.BlueiotBindingEndReason
import io.proximi.sdk.blueiot.binding.BlueiotBindingError
import io.proximi.sdk.blueiot.binding.BlueiotBindingLink
import io.proximi.sdk.blueiot.binding.BlueiotBindingSignal
import io.proximi.sdk.blueiot.binding.BlueiotBindingState
import io.proximi.sdk.blueiot.binding.location.BlueiotLocationCause
import kotlin.math.ceil

object WristbandCopy {
    /** The message for a failed bind or end. */
    fun message(error: Throwable): String {
        if (error !is BlueiotBindingError) return error.message ?: error.toString()
        return when (error) {
            is BlueiotBindingError.TagAlreadyBound -> "This wristband is already in use. Please ask the staff."
            is BlueiotBindingError.NotInAuthorizedZone -> message(error.locationCause)
            is BlueiotBindingError.TagNotAvailable -> "This wristband is not active. Please ask the staff."
            is BlueiotBindingError.TagNotAtReception -> "Connect this wristband at the reception desk."
            is BlueiotBindingError.TagNotIssued -> "This wristband has not been issued yet. Please ask the staff."
            is BlueiotBindingError.InvalidTagID ->
                "This is not a wristband number. Check the number printed on the band."
            is BlueiotBindingError.RateLimited -> rateLimited(error.retryAfter)
            is BlueiotBindingError.Network ->
                "The venue's server cannot be reached. Check the connection and try again."
            is BlueiotBindingError.Server, is BlueiotBindingError.InvalidResponse ->
                "The venue's server failed. Try again in a moment."
            is BlueiotBindingError.SessionLost, is BlueiotBindingError.BindingClosed ->
                "The wristband session has ended. Connect the wristband again."
            // Configuration faults. A visitor never sees them in a configured build.
            is BlueiotBindingError.AppTokenRejected ->
                "The relay-api refused the app token. Check BLUEIOT_RELAY_APP_TOKEN."
            is BlueiotBindingError.NotARelayAPI -> "BLUEIOT_RELAY_URL is not a relay-api address."
            is BlueiotBindingError.InvalidRequest, is BlueiotBindingError.Forbidden, is BlueiotBindingError.Unexpected ->
                error.errorDescription
        }
    }

    /**
     * The message for a take-over refused with `not_in_authorized_zone`, per what the
     * phone sent.
     */
    fun message(cause: BlueiotLocationCause): String =
        when (cause) {
            BlueiotLocationCause.PERMISSION_MISSING ->
                "To take over this wristband, allow location for this app in Settings, or ask at the reception desk."
            BlueiotLocationCause.APPROXIMATE_LOCATION ->
                "To take over this wristband, turn on Use precise location for this app in Settings, or ask at the reception desk."
            BlueiotLocationCause.NO_FRESH_FIX ->
                "Your location could not be determined in time. Try again, or ask at the reception desk."
            BlueiotLocationCause.SIMULATED_LOCATION ->
                "This phone reports a simulated location. Turn it off, or ask at the reception desk."
            BlueiotLocationCause.REJECTED_BY_RELAY ->
                "To take over this wristband, be inside the museum with location enabled, or ask at the reception desk."
        }

    /** The notice shown on the wristband prompt after a session ended. */
    fun notice(reason: BlueiotBindingEndReason): String =
        when (reason) {
            BlueiotBindingEndReason.Superseded ->
                "Your wristband was scanned by another phone. Connect it again to follow it on this phone."
            BlueiotBindingEndReason.UserEnded -> "Your visit has ended."
            BlueiotBindingEndReason.Returned -> "The wristband was returned. Thank you for your visit."
            BlueiotBindingEndReason.Timeout -> "The wristband stopped reporting. Please ask the staff."
            BlueiotBindingEndReason.LeftVenue -> "The wristband left the venue."
            BlueiotBindingEndReason.StaffRevoked -> "The staff released this wristband."
            BlueiotBindingEndReason.Expired -> "Your visit time is over."
            BlueiotBindingEndReason.Replaced -> "This wristband session was replaced by a newer one."
            BlueiotBindingEndReason.SessionLost -> "The connection to your wristband was lost. Connect it again."
            is BlueiotBindingEndReason.Unknown -> "Your visit has ended."
        }

    /** The notice for an ended session, or `null` while none has ended. */
    fun endNotice(state: BlueiotBindingState): String? = (state as? BlueiotBindingState.Ended)?.let { notice(it.reason) }

    /**
     * Why the app is about to show an Android location dialog. Shown on the prompt before
     * the visitor taps Connect.
     */
    fun explanation(ask: WristbandSession.LocationAsk): String =
        when (ask) {
            WristbandSession.LocationAsk.PERMISSION ->
                "Connecting asks for your location. It confirms that you are in the venue."
            WristbandSession.LocationAsk.PRECISE_LOCATION ->
                "Connecting asks for your precise location. It confirms that you are in the venue."
        }

    /** The status line on the map, or `null` when no session is active. */
    fun status(state: BlueiotBindingState?): String? =
        when (state) {
            BlueiotBindingState.Binding -> "Connecting…"
            is BlueiotBindingState.Active ->
                when {
                    state.link == BlueiotBindingLink.CONNECTING -> "Connecting…"
                    state.link == BlueiotBindingLink.RECONNECTING -> "Reconnecting…"
                    state.signal is BlueiotBindingSignal.Lost -> "Signal lost"
                    else -> "Online"
                }
            BlueiotBindingState.Unbound, is BlueiotBindingState.Ended, null -> null
        }

    /** The state for the diagnostics log. Holds no tag id. */
    fun logLine(state: BlueiotBindingState): String =
        when (state) {
            BlueiotBindingState.Unbound -> "unbound"
            BlueiotBindingState.Binding -> "binding"
            is BlueiotBindingState.Active ->
                if (state.signal is BlueiotBindingSignal.Lost) {
                    "active, ${state.link.rawValue}, signal lost"
                } else {
                    "active, ${state.link.rawValue}"
                }
            is BlueiotBindingState.Ended -> "ended, ${state.reason.rawValue}"
        }

    private fun rateLimited(retryAfter: Double?): String {
        if (retryAfter == null || retryAfter <= 0) return "Too many attempts. Try again in a moment."
        val seconds = ceil(retryAfter).toInt()
        return "Too many attempts. Try again in $seconds ${if (seconds == 1) "second" else "seconds"}."
    }
}

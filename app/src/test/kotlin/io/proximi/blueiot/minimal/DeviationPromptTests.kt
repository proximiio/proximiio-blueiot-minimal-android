package io.proximi.blueiot.minimal

import io.proximi.map.core.FloorKey
import io.proximi.map.core.JourneyEvent
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `DeviationPrompt.after`: which journey events open the deviation prompt, which close
 * it and which leave it. A wrong rule leaves a visitor off the route without a prompt,
 * or keeps a prompt on screen after the visitor has returned.
 */
class DeviationPromptTests {
    private val cafe =
        JourneyStop(
            id = "cafe",
            title = "Café",
            coordinate = MapCoordinate(latitude = 0.0, longitude = 0.0),
            floor = FloorKey(0.0),
            poiId = "cafe",
            kind = JourneyStop.Kind.DETOUR,
            state = JourneyStop.State.ACTIVE,
        )

    private val farPrompt: DeviationPrompt?
        get() = DeviationPrompt.after(JourneyEvent.FarFromRoute(distance = 31.6), showing = null)

    /** The three events that open the prompt, each with its own sentence. */
    @Test
    fun deviationEventsOpenThePrompt() {
        assertEquals(
            DeviationPrompt(DeviationPrompt.Reason.FAR_FROM_ROUTE, "You are 32 m from your route."),
            farPrompt,
        )
        assertEquals(
            DeviationPrompt(DeviationPrompt.Reason.OFF_ROUTE_TOO_LONG, "You have been off your route for 5 min."),
            DeviationPrompt.after(JourneyEvent.OffRouteTooLong(duration = 300.0), showing = null),
        )
        assertEquals(
            DeviationPrompt(DeviationPrompt.Reason.DETOUR_OVERSTAYED, "You left your route for Café 17 min ago."),
            DeviationPrompt.after(JourneyEvent.DetourOverstayed(stop = cafe, duration = 1020.0), showing = null),
        )
    }

    /** A duration under half a minute is shown as 1 min, not 0. */
    @Test
    fun minutesAreAtLeastOne() {
        assertEquals(
            "You have been off your route for 1 min.",
            DeviationPrompt.after(JourneyEvent.OffRouteTooLong(duration = 10.0), showing = null)?.message,
        )
    }

    /** `LeftRoute` opens no prompt, and leaves an open one unchanged. */
    @Test
    fun leftRouteOpensNothing() {
        assertNull(DeviationPrompt.after(JourneyEvent.LeftRoute(distance = 14.0), showing = null))
        assertEquals(farPrompt, DeviationPrompt.after(JourneyEvent.LeftRoute(distance = 14.0), showing = farPrompt))
    }

    /** A newer deviation event replaces the prompt on screen. */
    @Test
    fun newerDeviationReplacesThePrompt() {
        assertEquals(
            DeviationPrompt.Reason.OFF_ROUTE_TOO_LONG,
            DeviationPrompt.after(JourneyEvent.OffRouteTooLong(duration = 300.0), showing = farPrompt)?.reason,
        )
    }

    /** Returning to the route and finishing the visit close any prompt. */
    @Test
    fun returnAndFinishClose() {
        assertNull(DeviationPrompt.after(JourneyEvent.ReturnedToRoute, showing = farPrompt))
        assertNull(DeviationPrompt.after(JourneyEvent.JourneyFinished, showing = farPrompt))
    }

    /** The end of a detour closes a detour prompt only. */
    @Test
    fun detourEndClosesOnlyADetourPrompt() {
        val detourPrompt =
            DeviationPrompt.after(JourneyEvent.DetourOverstayed(stop = cafe, duration = 1020.0), showing = null)
        assertNull(DeviationPrompt.after(JourneyEvent.DetourEnded(cafe, completed = false), showing = detourPrompt))
        assertEquals(
            farPrompt,
            DeviationPrompt.after(JourneyEvent.DetourEnded(cafe, completed = false), showing = farPrompt),
        )
    }

    /** Transitions that are not deviations leave the prompt as it is. */
    @Test
    fun otherEventsKeepThePrompt() {
        assertEquals(farPrompt, DeviationPrompt.after(JourneyEvent.Rerouted(to = cafe), showing = farPrompt))
        assertEquals(farPrompt, DeviationPrompt.after(JourneyEvent.StopReached(cafe), showing = farPrompt))
        assertNull(DeviationPrompt.after(JourneyEvent.DetourStarted(cafe), showing = null))
    }
}

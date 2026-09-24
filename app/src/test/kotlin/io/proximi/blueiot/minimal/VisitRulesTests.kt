//
//  VisitRulesTests.kt
//  BlueiotMinimalTests
//
//  The rules in `VisitRules.kt` and `GuidanceLine.offersReroute`, and the two library
//  behaviours they answer: `proposeOrder(JourneyOrderOrigin.VISITOR)` returns `null`
//  without a position, and `JourneyNavigator.end()` switches the session's single-route
//  guidance off.
//
package io.proximi.blueiot.minimal

import io.proximi.map.core.FloorKey
import io.proximi.map.core.Journey
import io.proximi.map.core.JourneyOrderOrigin
import io.proximi.map.core.JourneyOrderProposal
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapCoordinate
import io.proximi.map.core.RouteFollowRules
import io.proximi.map.core.RouteGeometry
import io.proximi.map.core.RouteGuidance
import io.proximi.map.core.RouteProjection
import io.proximi.map.core.VenuePosition
import io.proximi.map.live.JourneyNavigator
import io.proximi.map.live.JourneyRouting
import io.proximi.map.live.ProximiioMapSession
import io.proximi.map.live.VenueDataSource
import io.proximi.sdk.core.model.ProximiioCoordinate
import io.proximi.sdk.wayfinding.ComputedRoute
import io.proximi.sdk.wayfinding.RouteInstruction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// SDK 35, not 36: Robolectric's API 36 image requires Java 21 and this project builds on
// 17. The last three tests build a `ProximiioMapSession`, which takes a `Context`.
@Config(sdk = [35])
class VisitRulesTests {
    private fun stop(
        id: String,
        state: JourneyStop.State = JourneyStop.State.PENDING,
        kind: JourneyStop.Kind = JourneyStop.Kind.PLANNED,
        east: Double = 0.0,
    ) = JourneyStop(
        id = id,
        title = id.replaceFirstChar(Char::uppercase),
        coordinate = point(east),
        floor = FloorKey(0.0),
        poiId = id,
        kind = kind,
        state = state,
    )

    private fun proposal(
        saving: Double,
        liveStopId: String? = null,
    ) = JourneyOrderProposal(
        order = if (saving > 0) listOf("b", "a") else listOf("a", "b"),
        currentOrder = listOf("a", "b"),
        pinnedStopIds = emptyList(),
        currentMeters = 200.0,
        proposedMeters = 200.0 - saving,
        liveStopId = liveStopId,
    )

    // MARK: - StartOrder

    /** A new visit, and a visit walking to its first stop, are still ordered. */
    @Test
    fun startOrderIsOwedBeforeAnyStopIsReached() {
        assertTrue(StartOrder.isOwed(Journey(stops = listOf(stop("a"), stop("b")))))
        assertTrue(StartOrder.isOwed(Journey(stops = listOf(stop("a", JourneyStop.State.ACTIVE), stop("b")))))
    }

    /** A reached, done or skipped stop, a stop-off or a single stop ends it. */
    @Test
    fun startOrderIsNotOwedOnceTheVisitMoved() {
        assertFalse(StartOrder.isOwed(Journey(stops = listOf(stop("a", JourneyStop.State.REACHED), stop("b")))))
        assertFalse(StartOrder.isOwed(Journey(stops = listOf(stop("a", JourneyStop.State.DONE), stop("b")))))
        assertFalse(StartOrder.isOwed(Journey(stops = listOf(stop("a", JourneyStop.State.SKIPPED), stop("b")))))
        assertFalse(
            StartOrder.isOwed(
                Journey(
                    stops =
                        listOf(
                            stop("wc", JourneyStop.State.ACTIVE, JourneyStop.Kind.DETOUR),
                            stop("a"),
                            stop("b"),
                        ),
                ),
            ),
        )
        assertFalse(StartOrder.isOwed(Journey(stops = listOf(stop("a")))))
    }

    /** The bar says what happened: shortened, already shortest, or nothing. */
    @Test
    fun startOrderNote() {
        assertEquals(
            "Stops put in the shortest order: 42 m less to walk.",
            StartOrder.note(proposal(saving = 42.4), applied = true),
        )
        assertEquals(
            "Your stops are already in the shortest order.",
            StartOrder.note(proposal(saving = 0.0), applied = false),
        )
        // Under 1 m is not a saving to announce.
        assertEquals(
            "Your stops are already in the shortest order.",
            StartOrder.note(proposal(saving = 0.3), applied = true),
        )
        // A shorter order refused as stale: nothing to say yet.
        assertNull(StartOrder.note(proposal(saving = 42.0), applied = false))
        assertNull(StartOrder.note(null, applied = false))
    }

    /** The first stop was reached, or a stop-off added, before the first position. */
    @Test
    fun theWaitingNoteClearsWhenTheVisitIsNoLongerOwedOnTheFirstFix() {
        assertNull(StartOrder.noteAfterFirstFix(StartOrder.WAITING_NOTE, result = null))
    }

    /** `proposeOrder(JourneyOrderOrigin.VISITOR)` returned `null` on the first position. */
    @Test
    fun theWaitingNoteClearsWhenTheFirstFixCannotBeMeasured() {
        val result = StartOrder.note(null, applied = false)
        assertNull(StartOrder.noteAfterFirstFix(StartOrder.WAITING_NOTE, result))
    }

    /** `apply` refused both shorter orders as stale. */
    @Test
    fun theWaitingNoteClearsWhenBothProposalsAreRefused() {
        var note: String? = StartOrder.WAITING_NOTE
        repeat(2) {
            val result = StartOrder.note(proposal(saving = 42.0), applied = false)
            note = StartOrder.noteAfterFirstFix(note, result)
        }
        assertNull(note)
    }

    /**
     * A result replaces the waiting note. With a position at start there is no waiting
     * note and the result is shown as before.
     */
    @Test
    fun aResultNoteReplacesTheWaitingNote() {
        val shortened = StartOrder.note(proposal(saving = 42.4), applied = true)
        assertEquals(
            "Stops put in the shortest order: 42 m less to walk.",
            StartOrder.noteAfterFirstFix(StartOrder.WAITING_NOTE, shortened),
        )
        val alreadyShortest = StartOrder.note(proposal(saving = 0.0), applied = false)
        assertEquals(
            "Your stops are already in the shortest order.",
            StartOrder.noteAfterFirstFix(StartOrder.WAITING_NOTE, alreadyShortest),
        )
        assertEquals(shortened, StartOrder.noteAfterFirstFix(null, shortened))
        assertNull(StartOrder.noteAfterFirstFix(null, result = null))
    }

    // MARK: - OrderAdvice

    @Test
    fun orderAdviceShowsTheSaving() {
        val advice = OrderAdvice.of(proposal(saving = 57.6), movableStops = 2, isMeasuring = false, canApply = true)
        assertEquals(OrderAdvice.Save(meters = 58), advice)
        assertEquals("Save 58 m by reordering", advice.text)
    }

    /** The row is shown when no order is shorter, so the visitor sees the order was measured. */
    @Test
    fun orderAdviceSaysWhenTheOrderIsAlreadyShortest() {
        assertEquals(
            OrderAdvice.AlreadyShortest,
            OrderAdvice.of(proposal(saving = 0.0), movableStops = 2, isMeasuring = false, canApply = true),
        )
        assertEquals(
            OrderAdvice.AlreadyShortest,
            OrderAdvice.of(proposal(saving = 0.4), movableStops = 2, isMeasuring = false, canApply = true),
        )
    }

    @Test
    fun orderAdviceWhileMeasuringStaleOrUnmeasurable() {
        assertEquals(OrderAdvice.Measuring, OrderAdvice.of(null, movableStops = 3, isMeasuring = true, canApply = false))
        assertEquals(
            OrderAdvice.Measuring,
            OrderAdvice.of(proposal(saving = 50.0), movableStops = 2, isMeasuring = false, canApply = false),
        )
        assertEquals(
            OrderAdvice.Unmeasurable,
            OrderAdvice.of(null, movableStops = 3, isMeasuring = false, canApply = false),
        )
        assertEquals(OrderAdvice.None, OrderAdvice.of(null, movableStops = 1, isMeasuring = false, canApply = false))
        assertNull(OrderAdvice.None.text)
    }

    // MARK: - StopOff

    @Test
    fun stopOffText() {
        assertEquals(
            "Go to the nearest one before Gallery. Your plan continues afterwards.",
            StopOff.menuHeader(goingTo = "Gallery"),
        )
        assertEquals("Stop off: Toilets", StopOff.title(stop("toilets", kind = JourneyStop.Kind.DETOUR)))
        assertEquals(
            "On the way, then on to Gallery. Back to the plan cancels the stop-off.",
            StopOff.status(hasArrived = false, next = "Gallery"),
        )
        assertEquals(
            "Tap Continue when you are done, then on to Gallery.",
            StopOff.status(hasArrived = true, next = "Gallery"),
        )
        assertEquals(
            "Tap Continue when you are done, then the visit ends.",
            StopOff.status(hasArrived = true, next = null),
        )
    }

    // MARK: - Single-route re-route

    private fun guidance(
        offRoute: Boolean,
        arrived: Boolean,
    ) = RouteGuidance(
        projection =
            RouteProjection(
                segmentIndex = 0,
                t = 0.0,
                coordinate = point(0.0),
                floor = FloorKey(0.0),
                offRouteMeters = if (offRoute) 20.0 else 0.0,
                traveledMeters = 0.0,
            ),
        manoeuvre = null,
        manoeuvreIndex = null,
        nextManoeuvre = null,
        distanceToManoeuvreMeters = 10.0,
        remainingMeters = 10.0,
        traveledMeters = 0.0,
        offRouteMeters = if (offRoute) 20.0 else 0.0,
        isOffRoute = offRoute,
        hasArrived = arrived,
        arrivalProgress = if (arrived) 1.0 else 0.0,
        progress = RouteGeometry.Progress(completedThrough = 0),
    )

    @Test
    fun rerouteIsOfferedOnlyOffTheRoute() {
        assertTrue(GuidanceLine.offersReroute(guidance(offRoute = true, arrived = false)))
        assertFalse(GuidanceLine.offersReroute(guidance(offRoute = false, arrived = false)))
        assertFalse(GuidanceLine.offersReroute(guidance(offRoute = true, arrived = true)))
        assertFalse(GuidanceLine.offersReroute(null))
        assertEquals("You have left the route.", GuidanceLine.sentence(guidance(offRoute = true, arrived = false)))
    }

    // MARK: - The library behaviours behind the fixes

    /** `east` metres east of the anchor, on the corridor. */
    private fun point(east: Double) =
        MapCoordinate(
            latitude = ANCHOR_LATITUDE,
            longitude = ANCHOR_LONGITUDE + east / (METERS_PER_DEGREE * cos(ANCHOR_LATITUDE * PI / 180)),
        )

    private fun east(coordinate: MapCoordinate): Double =
        (coordinate.longitude - ANCHOR_LONGITUDE) * METERS_PER_DEGREE * cos(ANCHOR_LATITUDE * PI / 180)

    /**
     * One 200 m corridor running east. iOS installs it as a route network in a real
     * `Proximiio`; on Android that needs a validated token, so the navigator gets it as
     * its `JourneyRouting`, the seam the map library provides for this.
     */
    private val corridor =
        JourneyRouting { origin, _, destination, _ ->
            val from = ProximiioCoordinate(latitude = origin.latitude, longitude = origin.longitude)
            val to = ProximiioCoordinate(latitude = destination.latitude, longitude = destination.longitude)
            val meters = abs(east(destination) - east(origin))
            ComputedRoute(
                coordinates = listOf(from, to),
                distanceMeters = meters,
                segments = emptyList(),
                level = 0.0,
                levelChanges = emptyList(),
                instructions =
                    listOf(
                        RouteInstruction(RouteInstruction.Kind.Start, from, 0.0, 0.0),
                        RouteInstruction(RouteInstruction.Kind.Arrive, to, meters, 0.0),
                    ),
            )
        }

    /** A source with no venue and no positions behind it. */
    private class NoPositions : VenueDataSource {
        override fun positions(): Flow<VenuePosition> = emptyFlow()
    }

    private fun session() = ProximiioMapSession(NoPositions(), RuntimeEnvironment.getApplication())

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Ending a visit: `end()` sets `guidanceRules` to `null`. A second `end()` after the
     * map screen restored the rules left single-route guidance off, with no instruction
     * line and no off-route warning. `JourneyBar` ends the navigator once, before `onEnd`
     * restores the rules.
     */
    @Test
    fun endingAVisitSwitchesGuidanceOffUntilTheRulesAreSetAgain() =
        runTest {
            val session = session()
            session.guidanceRules = RouteFollowRules.VENUE_WALK
            val navigator =
                JourneyNavigator(
                    session,
                    Journey(stops = listOf(stop("a", east = 50.0), stop("b", east = 150.0))),
                    routing = corridor,
                )
            navigator.start()

            navigator.end()
            assertNull(session.guidanceRules)

            session.guidanceRules = RouteFollowRules.VENUE_WALK // `VenueMapScreen`'s `onEnd`
            assertEquals(RouteFollowRules.VENUE_WALK, session.guidanceRules)
        }

    /**
     * The order `JourneyBar` ends a visit in: `VisitEnding.end` ends the navigator, then
     * `onEnd` sets the single-route rules, and the bar leaving the screen afterwards
     * does not end the navigator again. No iOS twin: iOS holds this in the view.
     */
    @Test
    fun endingAVisitOnceKeepsSingleRouteGuidance() =
        runTest {
            val session = session()
            val navigator =
                JourneyNavigator(
                    session,
                    Journey(stops = listOf(stop("a", east = 50.0), stop("b", east = 150.0))),
                    routing = corridor,
                )
            navigator.start()
            var ends = 0
            val ending =
                VisitEnding {
                    ends += 1
                    navigator.end()
                }

            ending.end { session.guidanceRules = RouteFollowRules.VENUE_WALK }
            ending.disposed()

            assertEquals(1, ends)
            assertTrue(ending.hasEnded)
            assertEquals(RouteFollowRules.VENUE_WALK, session.guidanceRules)
        }

    /**
     * Without a position, `VISITOR` has no answer; the tap order stays and `JourneyBar`
     * orders the visit on the first position. `ACTIVE_STOP` measures a badly tapped
     * order without a position, holding the first stop.
     */
    @Test
    fun visitorOrderNeedsAFix() =
        runTest {
            val navigator =
                JourneyNavigator(
                    session(),
                    Journey(stops = listOf(stop("a", east = 20.0), stop("c", east = 180.0), stop("b", east = 100.0))),
                    routing = corridor,
                )

            assertNull(navigator.proposeOrder(JourneyOrderOrigin.VISITOR))

            val fromFirstStop = requireNotNull(navigator.proposeOrder(JourneyOrderOrigin.ACTIVE_STOP))
            assertEquals(listOf("a", "b", "c"), fromFirstStop.order)
            assertTrue(fromFirstStop.isImprovement)
            assertEquals(
                OrderAdvice.Save(meters = fromFirstStop.savedMeters.roundToInt()),
                OrderAdvice.of(
                    fromFirstStop,
                    movableStops = 3,
                    isMeasuring = false,
                    canApply = navigator.canApply(fromFirstStop),
                ),
            )
        }

    private companion object {
        const val ANCHOR_LATITUDE = 48.1486
        const val ANCHOR_LONGITUDE = 17.1077
        const val METERS_PER_DEGREE = 111_320.0
    }
}

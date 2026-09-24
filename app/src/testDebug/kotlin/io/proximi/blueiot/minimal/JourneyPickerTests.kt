//
//  JourneyPickerTests.kt
//  BlueiotMinimalTests
//
//  Debug builds only, as the code under test. The journey picker's rows and list states,
//  the playback session state, and the launch extras. A wrong rule offers an unplayable
//  journey, hides the reason a journey cannot play, or leaves the playback controls in a
//  state the provider is not in.
//
package io.proximi.blueiot.minimal

import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.JOURNEY_EXTRA
import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.LOOP_EXTRA
import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.SPEED_EXTRA
import io.proximi.blueiot.minimal.JourneyPlaybackSession.Phase
import io.proximi.sdk.journey.JourneyPlaybackState
import io.proximi.sdk.journey.ProximiioJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyPickerTests {
    /** 111 m north, from level 0 to level 1, at 1 m/s. */
    private val playable =
        ProximiioJourney(
            id = "org:one",
            name = "  Lobby to café  ",
            speed = 1.0,
            waypoints =
                listOf(
                    ProximiioJourney.Waypoint(latitude = 0.0, longitude = 0.0, level = 0.0),
                    ProximiioJourney.Waypoint(latitude = 0.001, longitude = 0.0, level = 1.0),
                ),
        )

    private val oneWaypoint =
        ProximiioJourney(
            id = "org:two",
            name = "Stub",
            waypoints = listOf(ProximiioJourney.Waypoint(latitude = 0.0, longitude = 0.0, level = 0.0)),
        )

    @Test
    fun playableRowHasSummaryAndNoFailure() {
        val row = JourneyPickerRow.rows(listOf(playable))[0]
        assertTrue(row.isPlayable)
        assertEquals("org:one", row.id)
        assertEquals("Lobby to café", row.title)
        assertNull(row.failure)
        assertEquals("111 m · 2 min · 2 waypoints · levels 0, 1", row.summary)
    }

    @Test
    fun unplayableRowCarriesValidationFailure() {
        val row = JourneyPickerRow.rows(listOf(oneWaypoint))[0]
        assertFalse(row.isPlayable)
        assertEquals(oneWaypoint.validationFailure(), row.failure)
        assertNotNull(row.failure)
        assertNull(row.summary)
    }

    @Test
    fun rowsKeepApiOrderAndIdentifyJourneysWithoutID() {
        val unnamed = playable.copy(id = null, name = " ")
        val rows = JourneyPickerRow.rows(listOf(oneWaypoint, unnamed, playable))
        assertEquals(listOf("org:two", "journey-1", "org:one"), rows.map { it.id })
        assertEquals("Untitled journey", rows[1].title)
        assertFalse("a journey without a name does not validate", rows[1].isPlayable)
    }

    @Test
    fun singleLevelSummary() {
        val flat = playable.copy(waypoints = listOf(playable.waypoints[0], playable.waypoints[1].copy(level = 0.0)))
        assertTrue(JourneyPickerRow.summary(flat).endsWith("· level 0"))
    }

    @Test
    fun contentForEachResult() {
        assertEquals(JourneyPickerContent.Empty, JourneyPickerContent.from(Result.success(emptyList())))
        assertEquals(JourneyPickerContent.Failed("offline"), JourneyPickerContent.from(Result.failure(Exception("offline"))))
        assertEquals(
            JourneyPickerContent.Loaded(JourneyPickerRow.rows(listOf(playable, oneWaypoint))),
            JourneyPickerContent.from(Result.success(listOf(playable, oneWaypoint))),
        )
    }

    @Test
    fun formats() {
        assertEquals("85 m", JourneyFormat.distance(84.6))
        assertEquals("1.2 km", JourneyFormat.distance(1234.0))
        assertEquals("45 s", JourneyFormat.duration(45.0))
        assertEquals("6 min", JourneyFormat.duration(365.0))
        assertEquals("1 h 5 min", JourneyFormat.duration(3900.0))
        assertEquals("3:07", JourneyFormat.clock(187.0))
        assertEquals("1:02:05", JourneyFormat.clock(3725.0))
        assertEquals("2x", JourneyFormat.speed(2.0))
        assertEquals("2.5x", JourneyFormat.speed(2.5))
    }
}

class JourneyPlaybackSessionTests {
    @Test
    fun startsOffWithoutControls() {
        val session = JourneyPlaybackSession()
        assertEquals(Phase.Off, session.phase)
        assertFalse(session.showsControls)
    }

    @Test
    fun beginAttachPauseResume() {
        var session = JourneyPlaybackSession().begin("org:one")
        assertEquals(Phase.Starting, session.phase)
        assertTrue(session.showsControls)
        assertNull("nothing to pause before the provider is attached", session.pause())

        session = session.attached("Lobby", 125.0)
        assertEquals(Phase.Playing, session.phase)
        assertEquals("Lobby", session.title)
        assertEquals("Playing · 0:00 / 2:05", session.status)

        session = requireNotNull(session.pause())
        assertNull(session.pause())
        assertEquals(Phase.Paused, session.phase)
        assertTrue(session.canResume)

        session = requireNotNull(session.resume())
        assertNull(session.resume())
        assertEquals(Phase.Playing, session.phase)
    }

    @Test
    fun providerStateDrivesProgressAndFinish() {
        var session = JourneyPlaybackSession().begin("x").attached("x", 60.0)
        session = session.observe(JourneyPlaybackState.RUNNING, 30.0)
        assertEquals("Playing · 0:30 / 1:00", session.status)
        session = session.observe(JourneyPlaybackState.FINISHED, 60.0)
        assertEquals(Phase.Finished, session.phase)
        assertFalse(session.canPause)
        assertFalse(session.canResume)
        assertTrue("Stop stays available to re-attach the relay", session.showsControls)
    }

    @Test
    fun providerStateIgnoredOutsidePlayback() {
        var session = JourneyPlaybackSession().observe(JourneyPlaybackState.FINISHED, 10.0)
        assertEquals(Phase.Off, session.phase)
        session = session.begin("x").observe(JourneyPlaybackState.RUNNING, 10.0)
        assertEquals(Phase.Starting, session.phase)
    }

    @Test
    fun failureOnlyWhileStartingAndEndClearsEverything() {
        var session = JourneyPlaybackSession().fail("late")
        assertEquals(Phase.Off, session.phase)
        session = session.begin("org:bad").fail("not found")
        assertEquals(Phase.Failed("not found"), session.phase)
        assertEquals("Failed: not found", session.status)
        session = session.attached("x", 1.0)
        assertEquals("a failed start does not become playing", Phase.Failed("not found"), session.phase)
        assertEquals(JourneyPlaybackSession(), session.end())
    }

    /**
     * iOS reads launch arguments; Android reads intent extras. The cases are the iOS
     * ones: no id, no usable id, every option set, and a loop value of "NO".
     */
    @Test
    fun launchArguments() {
        fun request(vararg extras: Pair<String, Any>) = JourneyPlaybackLaunch.request(mapOf(*extras)::get)
        assertNull(request())
        assertNull(request(JOURNEY_EXTRA to " ", LOOP_EXTRA to true))
        assertEquals(
            JourneyPlaybackLaunch.Request("org:one", JourneyPlaybackOptions(speed = 5.0, loops = true)),
            request(JOURNEY_EXTRA to "org:one", SPEED_EXTRA to "5", LOOP_EXTRA to true),
        )
        assertEquals(JourneyPlaybackOptions(), request(JOURNEY_EXTRA to "org:one", LOOP_EXTRA to "NO")?.options)
    }

    @Test
    fun optionsLogLine() {
        assertEquals("1x", JourneyPlaybackOptions().logLine)
        assertEquals("2x, looping", JourneyPlaybackOptions(speed = 2.0, loops = true).logLine)
        assertEquals(listOf(1.0, 2.0, 5.0), JourneyPlaybackOptions.pickerSpeeds)
    }
}

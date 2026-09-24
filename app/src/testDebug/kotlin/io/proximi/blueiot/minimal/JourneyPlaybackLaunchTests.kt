package io.proximi.blueiot.minimal

import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.JOURNEY_EXTRA
import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.LOOP_EXTRA
import io.proximi.blueiot.minimal.JourneyPlaybackLaunch.SPEED_EXTRA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `JourneyPlaybackLaunch.request`: how the launch extras become a playback. A misread
 * extra plays the wrong journey, at the wrong speed, or attaches the relay instead.
 */
class JourneyPlaybackLaunchTests {
    private val journeyId = "00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000002"

    private fun request(vararg extras: Pair<String, Any>) = JourneyPlaybackLaunch.request(mapOf(*extras)::get)

    @Test
    fun noJourneyIdRequestsNothing() {
        assertNull(request())
        assertNull(request(SPEED_EXTRA to 2f, LOOP_EXTRA to true))
        assertNull(request(JOURNEY_EXTRA to "  "))
    }

    /** Speed 1 and no loop unless the extras say otherwise. */
    @Test
    fun theIdAloneUsesTheDefaults() {
        assertEquals(JourneyPlaybackLaunch.Request(journeyId, JourneyPlaybackOptions()), request(JOURNEY_EXTRA to journeyId))
    }

    /** `--ef`, `--ei` and `--es` all carry the speed. */
    @Test
    fun theSpeedIsReadInEachForm() {
        assertEquals(2.0, request(JOURNEY_EXTRA to journeyId, SPEED_EXTRA to 2f)?.options?.speed)
        assertEquals(3.0, request(JOURNEY_EXTRA to journeyId, SPEED_EXTRA to 3)?.options?.speed)
        assertEquals(0.5, request(JOURNEY_EXTRA to journeyId, SPEED_EXTRA to "0.5")?.options?.speed)
    }

    @Test
    fun aSpeedThatIsNotANumberIsOne() {
        assertEquals(1.0, request(JOURNEY_EXTRA to journeyId, SPEED_EXTRA to "fast")?.options?.speed)
    }

    /** `--ez journeyLoop true` loops; `false`, `no` and `0` in any form do not. */
    @Test
    fun theLoopFlagIsReadInEachForm() {
        assertTrue(request(JOURNEY_EXTRA to journeyId, LOOP_EXTRA to true)?.options?.loops == true)
        assertTrue(request(JOURNEY_EXTRA to journeyId, LOOP_EXTRA to "yes")?.options?.loops == true)
        assertFalse(request(JOURNEY_EXTRA to journeyId, LOOP_EXTRA to false)?.options?.loops == true)
        assertFalse(request(JOURNEY_EXTRA to journeyId, LOOP_EXTRA to "No")?.options?.loops == true)
        assertFalse(request(JOURNEY_EXTRA to journeyId, LOOP_EXTRA to 0)?.options?.loops == true)
    }
}

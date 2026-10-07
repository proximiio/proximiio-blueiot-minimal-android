//
//  PositionSmoothingSettingTests.kt
//  BlueiotMinimalTests
//
//  The "Smooth position" switch and the map smoothing it selects, and the "Smoothing"
//  values and the tuning they select. A wrong mapping fails silently: the dot is smoothed
//  while a tester compares raw positions, or jumps for a visitor. The iOS test that
//  compares the default with `Settings.bundle` has no twin: the Android switch is in the
//  app, and its default is `DEFAULT_VALUE`.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import io.proximi.map.core.PositionSmoothing
import io.proximi.map.core.PositionSmoothingTuning
import io.proximi.map.core.PositionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
// SDK 35, not 36: Robolectric's API 36 image requires Java 21 and this project builds on
// 17. These tests exercise `SharedPreferences` only.
@Config(sdk = [35])
class PositionSmoothingSettingTests {
    /** A file of its own, so a test never reads or writes the app's real store. */
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        store =
            RuntimeEnvironment
                .getApplication()
                .getSharedPreferences("PositionSmoothingSettingTests.${UUID.randomUUID()}", Context.MODE_PRIVATE)
    }

    @Test
    fun unchangedSwitchSmooths() {
        assertEquals(PositionSmoothing.ADAPTIVE, PositionSmoothingSetting.smoothing(store))
    }

    @Test
    fun switchOffDrawsEachFix() {
        PositionSmoothingSetting.save(false, store)
        assertEquals(PositionSmoothing.NONE, PositionSmoothingSetting.smoothing(store))
    }

    @Test
    fun switchBackOnSmooths() {
        PositionSmoothingSetting.save(false, store)
        PositionSmoothingSetting.save(true, store)
        assertEquals(PositionSmoothing.ADAPTIVE, PositionSmoothingSetting.smoothing(store))
    }

    // Smoothing values

    @Test
    fun numberAcceptsDotAndComma() {
        assertEquals(0.75, PositionSmoothingSetting.number("0.75"))
        assertEquals(0.75, PositionSmoothingSetting.number("0,75"))
        assertEquals(2.0, PositionSmoothingSetting.number(" 2 "))
        assertEquals(1.5, PositionSmoothingSetting.number(1.5))
    }

    @Test
    fun numberIgnoresEmptyAndInvalidText() {
        assertNull(PositionSmoothingSetting.number(null))
        assertNull(PositionSmoothingSetting.number(""))
        assertNull(PositionSmoothingSetting.number("   "))
        assertNull(PositionSmoothingSetting.number("abc"))
        assertNull(PositionSmoothingSetting.number("1,2,3"))
        assertNull(PositionSmoothingSetting.number("nan"))
        assertNull(PositionSmoothingSetting.number("inf"))
        // Kotlin's `toDoubleOrNull` reads these; Swift's `Double(_:)` does not.
        assertNull(PositionSmoothingSetting.number("NaN"))
        assertNull(PositionSmoothingSetting.number("1d"))
    }

    @Test
    fun emptyFieldsGiveTheMapDefault() {
        assertEquals(PositionSmoothingTuning.DEFAULT, PositionSmoothingSetting.tuning(store))
    }

    @Test
    fun enteredValuesAreUsed() {
        PositionSmoothingSetting.save("1,5", PositionSmoothingSetting.Knob.DEAD_BAND_METERS, store)
        PositionSmoothingSetting.save("0.2", PositionSmoothingSetting.Knob.POSITION_SETTLING_SECONDS, store)
        val tuning = PositionSmoothingSetting.tuning(store)
        assertEquals(1.5, tuning.deadBandMeters, 0.0)
        assertEquals(0.2, tuning.positionSettlingSeconds, 0.0)
        assertEquals(PositionSmoothingTuning.DEFAULT.windowSeconds, tuning.windowSeconds, 0.0)
    }

    @Test
    fun invalidValueFallsBackToDefault() {
        PositionSmoothingSetting.save("fast", PositionSmoothingSetting.Knob.WALK_SMOOTHING_SECONDS, store)
        PositionSmoothingSetting.save("", PositionSmoothingSetting.Knob.DEAD_BAND_METERS, store)
        assertEquals(PositionSmoothingTuning.DEFAULT, PositionSmoothingSetting.tuning(store))
    }

    /** The app passes a negative value through. The map raises it to 0, and the window to its 0.5 s minimum. */
    @Test
    fun negativeValueIsClampedByTheMap() {
        PositionSmoothingSetting.save("-1", PositionSmoothingSetting.Knob.DEAD_BAND_METERS, store)
        PositionSmoothingSetting.save("-2,5", PositionSmoothingSetting.Knob.WINDOW_SECONDS, store)
        val tuning = PositionSmoothingSetting.tuning(store)
        assertEquals(0.0, tuning.deadBandMeters, 0.0)
        assertEquals(0.5, tuning.windowSeconds, 0.0)
    }

    /**
     * iOS `testResetEmptiesTheValuesAndTurnsItselfOff`. Android has a button, not a reset
     * switch, so there is no switch to turn off.
     */
    @Test
    fun resetEmptiesTheValues() {
        for (knob in PositionSmoothingSetting.Knob.entries) PositionSmoothingSetting.save("0,1", knob, store)
        assertNotEquals(PositionSmoothingTuning.DEFAULT, PositionSmoothingSetting.tuning(store))

        PositionSmoothingSetting.resetTuning(store)
        assertEquals(PositionSmoothingTuning.DEFAULT, PositionSmoothingSetting.tuning(store))
        for (knob in PositionSmoothingSetting.Knob.entries) {
            assertFalse(store.contains(knob.key))
            assertEquals("", PositionSmoothingSetting.text(knob, store))
        }
    }

    /**
     * iOS `testSettingsBundleListsEveryValue`: each value has the iOS key, and its title
     * shows the map default.
     */
    @Test
    fun sheetListsEveryValue() {
        assertEquals(
            listOf(
                "smoothingWindowSeconds",
                "smoothingStillSpeed",
                "smoothingWalkSpeed",
                "smoothingStillSeconds",
                "smoothingWalkSeconds",
                "smoothingDeadBand",
                "smoothingPositionSettling",
                "smoothingHeadingSettling",
            ),
            PositionSmoothingSetting.Knob.entries.map { it.key },
        )
        assertEquals(
            listOf(
                "Speed window (s) — default 3",
                "Standing speed (m/s) — default 0.35",
                "Walking speed (m/s) — default 1",
                "Standing smoothing (s) — default 3",
                "Walking smoothing (s) — default 0.35",
                "Dead band (m) — default 0.75",
                "Dot glide (s) — default 0.5",
                "Heading turn (s) — default 0.45",
            ),
            PositionSmoothingSetting.Knob.entries.map { it.title },
        )
    }

    @Test
    fun summaryListsTheTuningWhenOn() {
        var style = PositionStyle()
        assertTrue(PositionSmoothingSetting.summary(style).endsWith("(defaults)"))
        style = style.copy(smoothingTuning = PositionSmoothingTuning(deadBandMeters = 0.2))
        assertTrue(PositionSmoothingSetting.summary(style).contains("dead band 0.2 m"))
        style = style.copy(smoothing = PositionSmoothing.NONE)
        assertEquals("position smoothing: off", PositionSmoothingSetting.summary(style))
    }
}

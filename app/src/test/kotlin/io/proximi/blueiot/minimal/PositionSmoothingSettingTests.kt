//
//  PositionSmoothingSettingTests.kt
//  BlueiotMinimalTests
//
//  The "Smooth position" switch and the map smoothing it selects. A wrong mapping fails
//  silently: the dot is smoothed while a tester compares raw positions, or jumps for a
//  visitor. The iOS test that compares the default with `Settings.bundle` has no twin:
//  the Android switch is in the app, and its default is `DEFAULT_VALUE`.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import io.proximi.map.core.PositionSmoothing
import org.junit.Assert.assertEquals
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
}

//
//  VenueConfigurationTests.kt
//  BlueiotMinimalTests
//
//  The ground floor number read from venue.properties. A value parsed wrongly shifts
//  every floor, and the symptom is a position drawn on the wrong floor or on none.
//
package io.proximi.blueiot.minimal

import org.junit.Assert.assertEquals
import org.junit.Test

class VenueConfigurationTests {
    /** A negative value keeps its sign. */
    @Test
    fun negativeGroundFloorNumberParses() {
        assertEquals(-1, VenueConfiguration.groundFloorNumber("-1"))
        assertEquals(-1, VenueConfiguration.groundFloorNumber(" -1 "))
        assertEquals(1, VenueConfiguration.groundFloorNumber("1"))
    }

    /** Empty or not a whole number means 0, no shift. */
    @Test
    fun emptyOrInvalidGroundFloorNumberIsZero() {
        assertEquals(0, VenueConfiguration.groundFloorNumber(""))
        assertEquals(0, VenueConfiguration.groundFloorNumber("   "))
        assertEquals(0, VenueConfiguration.groundFloorNumber("-1.5"))
    }

    /** The tracked venue.properties value reaches the app through BuildConfig. */
    @Test
    fun trackedVenueValueIsMinusOne() {
        assertEquals("-1", BuildConfig.BLUEIOT_GROUND_FLOOR_NO)
        assertEquals(-1, VenueConfiguration.groundFloorNumber)
    }
}

//
//  GeofenceNotifierTests.kt
//  BlueiotMinimalTests
//
//  What `GeofenceNotifier` decides: the notification text and the id it is posted under.
//  No notification is posted here. No iOS twin: the iOS app posts no notifications
//  (README, "Place notifications").
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.core.model.ProximiioGeofence
import io.proximi.sdk.core.model.ProximiioPrivacyZone
import io.proximi.sdk.geofencing.GeofenceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceNotifierTests {
    private val mainHall = ProximiioGeofence(id = "a1b2c3d4", name = "Main Hall")

    // MARK: - The sentence

    /** The title is the geofence name; the body is one sentence. */
    @Test
    fun enteringAPlaceSaysSo() {
        val note = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(mainHall)))
        assertEquals("Main Hall", note.title)
        assertEquals("You are now inside Main Hall.", note.body)
    }

    /** The exit event carries a dwell time. The sentence does not use it. */
    @Test
    fun leavingAPlaceSaysSo() {
        val note = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Exited(mainHall, dwellTime = 412.0)))
        assertEquals("Main Hall", note.title)
        assertEquals("You have left Main Hall.", note.body)
    }

    /** A geofence with no name, or a blank one, produces no notification. */
    @Test
    fun anAreaTheVenueNeverNamedSaysNothing() {
        assertNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "a1b2c3d4"))))
        assertNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "a1b2c3d4", name = "  "))))
    }

    // MARK: - Privacy zones

    /**
     * Privacy zone events produce no notification in either direction. A privacy zone
     * exists so that the visitor's presence inside it is not reported.
     */
    @Test
    fun aPrivacyZoneIsNeverAnnounced() {
        val zone = ProximiioPrivacyZone(id = "3ab7", name = "Staff room")
        assertNull(GeofenceNotifier.note(GeofenceEvent.PrivacyZoneEntered(zone)))
        assertNull(GeofenceNotifier.note(GeofenceEvent.PrivacyZoneExited(zone)))
    }

    // MARK: - The id

    /**
     * One notification id per geofence, so an exit replaces its enter, and a different
     * geofence gets a different id.
     */
    @Test
    fun theExitReplacesItsEnter() {
        val entered = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(mainHall)))
        val left = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Exited(mainHall, dwellTime = 9.0)))
        assertEquals(entered.id, left.id)
        // Stable across a relaunch: `String.hashCode` is specified by the language, so
        // the same geofence id is the same notification id on every device and run.
        assertEquals("a1b2c3d4".hashCode(), entered.id)

        val cafe = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "5d20", name = "Café"))))
        assertNotEquals(entered.id, cafe.id)
    }
}

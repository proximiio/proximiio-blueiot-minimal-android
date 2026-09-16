//
//  GeofenceNotifierTests.kt
//  BlueiotMinimalTests
//
//  The words on the lock screen and the id they are posted under — the whole of what
//  `GeofenceNotifier` decides, with none of what Android does. No iOS twin: the iOS app
//  has no notifications at all (README, "Place notifications").
//
//  These three fail without anything looking wrong. A sentence naming the wrong place
//  reads perfectly. A privacy zone announced on a lock screen is the one thing a privacy
//  zone exists to prevent, and nothing on screen would say so. An id that moves between
//  the enter and the exit leaves two rows in the shade where the visitor expected one.
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
    private val futurePast = ProximiioGeofence(id = "a1b2c3d4", name = "Main Hall")

    // MARK: - The sentence

    /** The title is what the venue calls the place; the body is one plain sentence. */
    @Test
    fun enteringAPlaceSaysSo() {
        val note = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(futurePast)))
        assertEquals("Main Hall", note.title)
        assertEquals("You are now inside Main Hall.", note.body)
    }

    /**
     * The exit carries a dwell time and the sentence deliberately leaves it out: "You
     * spent 7 minutes here" is a fact about the log, not about where the visitor is now.
     */
    @Test
    fun leavingAPlaceSaysSo() {
        val note = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Exited(futurePast, dwellTime = 412.0)))
        assertEquals("Main Hall", note.title)
        assertEquals("You have left Main Hall.", note.body)
    }

    /** A name the venue left empty is no name: the id never reaches a visitor. */
    @Test
    fun anAreaTheVenueNeverNamedSaysNothing() {
        assertNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "a1b2c3d4"))))
        assertNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "a1b2c3d4", name = "  "))))
    }

    // MARK: - Privacy zones

    /**
     * Never, in either direction. A privacy zone exists so that the visitor's presence
     * inside it is not reported, and a lock screen anyone can read is the last place to
     * report it.
     */
    @Test
    fun aPrivacyZoneIsNeverAnnounced() {
        val zone = ProximiioPrivacyZone(id = "3ab7", name = "Staff room")
        assertNull(GeofenceNotifier.note(GeofenceEvent.PrivacyZoneEntered(zone)))
        assertNull(GeofenceNotifier.note(GeofenceEvent.PrivacyZoneExited(zone)))
    }

    // MARK: - The id

    /**
     * One id per geofence, so the exit replaces the enter instead of stacking a second
     * row under it — and a different place gets a different row.
     */
    @Test
    fun theExitReplacesItsEnter() {
        val entered = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(futurePast)))
        val left = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Exited(futurePast, dwellTime = 9.0)))
        assertEquals(entered.id, left.id)
        // And stable across a relaunch: `String.hashCode` is specified by the language,
        // so the same geofence id is the same notification id on every device and run.
        assertEquals("a1b2c3d4".hashCode(), entered.id)

        val cafe = requireNotNull(GeofenceNotifier.note(GeofenceEvent.Entered(ProximiioGeofence(id = "5d20", name = "Café"))))
        assertNotEquals(entered.id, cafe.id)
    }
}

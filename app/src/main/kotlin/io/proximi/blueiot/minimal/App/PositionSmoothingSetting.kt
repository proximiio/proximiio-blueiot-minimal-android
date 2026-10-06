//
//  PositionSmoothingSetting.kt
//  BlueiotMinimal
//
//  The "Smooth position" switch. It is in the sheet that a long press on the map opens,
//  in debug and release builds. Android has no system Settings page for an app's own
//  values, so the switch is in the app.
//
//  On, the default: the map smooths the dot (`PositionSmoothing.ADAPTIVE`).
//  Off: the map draws the dot exactly on each fix (`PositionSmoothing.NONE`).
//  Use off to compare positions with the venue's own RTLS viewer. The dot then
//  jumps between fixes, so leave it on for visitors.
//
//  The switch changes the map only. The SDK does not smooth fixes from the
//  binding's position provider: it drops a fix older than
//  `customPositionDuration` or with an invalid coordinate, and passes the others
//  to `positions()` with their coordinate unchanged. The SDK route snapping would
//  move them, but it is off unless `enableRouteSnapping()` is called, and this app
//  does not call it.
//
//  `VenueMapScreen` reads the value when it creates the map session, and assigns
//  `ProximiioMapSession.options` when the switch changes, so the change applies at
//  once.
//
package io.proximi.blueiot.minimal

import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.map.core.PositionSmoothing

object PositionSmoothingSetting {
    /** The key in the app's preferences file (`WristbandStore.preferences`). */
    const val KEY = "smoothPosition"

    /** The value until the visitor changes the switch. */
    const val DEFAULT_VALUE = true

    /** The stored value: `true` when the switch is on. */
    fun isOn(store: SharedPreferences): Boolean = store.getBoolean(KEY, DEFAULT_VALUE)

    /** Stores the switch's value. */
    fun save(
        isOn: Boolean,
        store: SharedPreferences,
    ) {
        store.edit { putBoolean(KEY, isOn) }
    }

    /**
     * The map smoothing for the stored value: `ADAPTIVE` when the switch is on, `NONE`
     * when it is off.
     */
    fun smoothing(store: SharedPreferences): PositionSmoothing = smoothing(isOn(store))

    /** The map smoothing for a switch value. */
    fun smoothing(isOn: Boolean): PositionSmoothing = if (isOn) PositionSmoothing.ADAPTIVE else PositionSmoothing.NONE
}

//
//  PositionSmoothingSetting.kt
//  BlueiotMinimal
//
//  The "Smooth position" switch and the "Smoothing" values. They are in the sheet that
//  a long press on the map opens, in debug and release builds. Android has no system
//  Settings page for an app's own values, so they are in the app.
//
//  On, the default: the map smooths the dot (`PositionSmoothing.ADAPTIVE`).
//  Off: the map draws the dot exactly on each fix (`PositionSmoothing.NONE`).
//  Use off to compare positions with the venue's own RTLS viewer. The dot then
//  jumps between fixes, so leave it on for visitors.
//
//  The "Smoothing" values set `PositionStyle.smoothingTuning`. The map reads them only
//  while the switch is on. An empty field, or text that is not a number, keeps the map
//  default. A comma is accepted as the decimal separator. The map clamps each value
//  (`PositionSmoothingTuning`), so a negative value becomes 0. The values are for
//  testers who compare tunings on one venue; visitors keep the defaults.
//
//  The switch changes the map only. The SDK does not smooth fixes from the
//  binding's position provider: it drops a fix older than
//  `customPositionDuration` or with an invalid coordinate, and passes the others
//  to `positions()` with their coordinate unchanged. The SDK route snapping would
//  move them, but it is off unless `enableRouteSnapping()` is called, and this app
//  does not call it.
//
//  `VenueMapScreen` reads the values when it creates the map session, and assigns
//  `ProximiioMapSession.options` when one changes, so the change applies at once.
//
package io.proximi.blueiot.minimal

import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.map.core.PositionSmoothing
import io.proximi.map.core.PositionSmoothingTuning
import io.proximi.map.core.PositionStyle

object PositionSmoothingSetting {
    /** The key in the app's preferences file (`WristbandStore.preferences`). */
    const val KEY = "smoothPosition"

    /** The value until the visitor changes the switch. */
    const val DEFAULT_VALUE = true

    /**
     * One text field in the "Smoothing" section. [key] is the key in the app's
     * preferences file, the same key as on iOS. A field is empty until a tester enters a
     * value.
     */
    enum class Knob(
        val key: String,
        private val label: String,
    ) {
        WINDOW_SECONDS("smoothingWindowSeconds", "Speed window (s)"),
        STILL_SPEED_METERS_PER_SECOND("smoothingStillSpeed", "Standing speed (m/s)"),
        WALK_SPEED_METERS_PER_SECOND("smoothingWalkSpeed", "Walking speed (m/s)"),
        STILL_SMOOTHING_SECONDS("smoothingStillSeconds", "Standing smoothing (s)"),
        WALK_SMOOTHING_SECONDS("smoothingWalkSeconds", "Walking smoothing (s)"),
        DEAD_BAND_METERS("smoothingDeadBand", "Dead band (m)"),
        POSITION_SETTLING_SECONDS("smoothingPositionSettling", "Dot glide (s)"),
        HEADING_SETTLING_SECONDS("smoothingHeadingSettling", "Heading turn (s)"),
        ;

        /** The value in `PositionSmoothingTuning.DEFAULT`. */
        val defaultValue: Double get() = value(PositionSmoothingTuning.DEFAULT)

        /** The field title with the unit and the map default: "Dead band (m) — default 0.75". */
        val title: String get() = "$label — default ${format(defaultValue)}"

        /** This knob's value in [tuning]. */
        fun value(tuning: PositionSmoothingTuning): Double =
            when (this) {
                WINDOW_SECONDS -> tuning.windowSeconds
                STILL_SPEED_METERS_PER_SECOND -> tuning.stillSpeedMetersPerSecond
                WALK_SPEED_METERS_PER_SECOND -> tuning.walkSpeedMetersPerSecond
                STILL_SMOOTHING_SECONDS -> tuning.stillSmoothingSeconds
                WALK_SMOOTHING_SECONDS -> tuning.walkSmoothingSeconds
                DEAD_BAND_METERS -> tuning.deadBandMeters
                POSITION_SETTLING_SECONDS -> tuning.positionSettlingSeconds
                HEADING_SETTLING_SECONDS -> tuning.headingSettlingSeconds
            }
    }

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

    /** The text stored for [knob], as the tester typed it, or `""` when none is stored. */
    fun text(
        knob: Knob,
        store: SharedPreferences,
    ): String = store.all[knob.key]?.toString().orEmpty()

    /** Stores [text] for [knob]. Blank text removes the value, so the default applies. */
    fun save(
        text: String,
        knob: Knob,
        store: SharedPreferences,
    ) {
        store.edit { if (text.isBlank()) remove(knob.key) else putString(knob.key, text) }
    }

    /**
     * The tuning from the "Smoothing" fields. A field that is empty or not a number uses
     * its default. The map clamps the result.
     */
    fun tuning(store: SharedPreferences): PositionSmoothingTuning {
        val stored = store.all

        fun value(knob: Knob): Double = number(stored[knob.key]) ?: knob.defaultValue
        return PositionSmoothingTuning(
            windowSeconds = value(Knob.WINDOW_SECONDS),
            stillSpeedMetersPerSecond = value(Knob.STILL_SPEED_METERS_PER_SECOND),
            walkSpeedMetersPerSecond = value(Knob.WALK_SPEED_METERS_PER_SECOND),
            stillSmoothingSeconds = value(Knob.STILL_SMOOTHING_SECONDS),
            walkSmoothingSeconds = value(Knob.WALK_SMOOTHING_SECONDS),
            deadBandMeters = value(Knob.DEAD_BAND_METERS),
            positionSettlingSeconds = value(Knob.POSITION_SETTLING_SECONDS),
            headingSettlingSeconds = value(Knob.HEADING_SETTLING_SECONDS),
        )
    }

    /**
     * The number in a stored field value, or `null`. Surrounding spaces are ignored and a
     * comma is read as the decimal separator. Text that is not a finite decimal number
     * returns `null`.
     */
    fun number(stored: Any?): Double? {
        val parsed =
            when (stored) {
                is String -> {
                    val normalized = stored.trim().replace(',', '.')
                    if (decimal.matches(normalized)) normalized.toDoubleOrNull() else null
                }
                is Number -> stored.toDouble()
                else -> null
            }
        return parsed?.takeIf { it.isFinite() }
    }

    /** Removes every [Knob] value, so each field is empty and uses its default. */
    fun resetTuning(store: SharedPreferences) {
        store.edit { Knob.entries.forEach { remove(it.key) } }
    }

    /** The diagnostics log line for [style]: "on" with each tuning value, or "off". */
    fun summary(style: PositionStyle): String {
        if (style.smoothing == PositionSmoothing.NONE) return "position smoothing: off"
        val tuning = style.smoothingTuning
        return "position smoothing: on" +
            ", window ${tuning.windowSeconds} s" +
            ", still speed ${tuning.stillSpeedMetersPerSecond} m/s" +
            ", walk speed ${tuning.walkSpeedMetersPerSecond} m/s" +
            ", still smoothing ${tuning.stillSmoothingSeconds} s" +
            ", walk smoothing ${tuning.walkSmoothingSeconds} s" +
            ", dead band ${tuning.deadBandMeters} m" +
            ", dot settling ${tuning.positionSettlingSeconds} s" +
            ", heading settling ${tuning.headingSettlingSeconds} s" +
            (if (tuning == PositionSmoothingTuning.DEFAULT) " (defaults)" else "")
    }

    /**
     * Digits with an optional fraction and exponent. `toDoubleOrNull` alone would also
     * read "1d", "0x1p3" and "NaN".
     */
    private val decimal = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    /** 3.0 as "3", 0.35 as "0.35", as in the iOS field titles. */
    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}

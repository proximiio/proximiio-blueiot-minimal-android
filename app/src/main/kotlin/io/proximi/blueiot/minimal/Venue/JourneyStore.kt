//
//  JourneyStore.kt
//  BlueiotMinimal
//
//  Persists the visit in progress to `SharedPreferences`, and builds a `JourneyStop`
//  from a picked place. `Journey` has a codec and each stop carries its own state, so
//  writing it on every change is enough to restore a visit across launches. The
//  navigator recomputes the leg from the first position after launch.
//
package io.proximi.blueiot.minimal

import android.content.SharedPreferences
import androidx.core.content.edit
import io.proximi.map.core.FloorKey
import io.proximi.map.core.Journey
import io.proximi.map.core.JourneyCodec
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapCoordinate

object JourneyStore {
    private const val KEY = "BlueiotMinimal.journey"

    /**
     * The visit in progress, or `null` when there is none. `JourneyCodec.decode` throws
     * on text it cannot read, such as a value written by an older build; this returns
     * `null` in that case.
     */
    fun load(store: SharedPreferences): Journey? {
        val text = store.getString(KEY, null) ?: return null
        val journey = runCatching { JourneyCodec.decode(text) }.getOrNull() ?: return null
        return journey.takeIf { it.stops.isNotEmpty() }
    }

    /** `null`, or a journey with no stops, removes the stored value. */
    fun save(
        journey: Journey?,
        store: SharedPreferences,
    ) {
        if (journey == null || journey.stops.isEmpty()) {
            store.edit { remove(KEY) }
            return
        }
        store.edit { putString(KEY, JourneyCodec.encode(journey)) }
    }
}

/**
 * Builds a stop from a picked place. The POI id is used as both the stop id and
 * `poiId`, so a restored journey still points at a venue POI rather than at a bare
 * coordinate.
 */
fun JourneyStop(poi: VenuePoi): JourneyStop =
    JourneyStop(
        id = poi.id,
        title = poi.title,
        coordinate =
            MapCoordinate(
                latitude = poi.coordinate.latitude,
                longitude = poi.coordinate.longitude,
            ),
        floor = FloorKey(poi.level),
        poiId = poi.id,
    )

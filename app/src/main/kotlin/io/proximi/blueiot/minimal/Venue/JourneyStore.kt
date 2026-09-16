//
//  JourneyStore.kt
//  BlueiotMinimal
//
//  Where a visit is kept between launches, and how a picked place becomes a stop.
//
//  `Journey` has a codec and each stop carries its own state, so writing it whenever
//  it changes is the whole of "my afternoon survived the app being closed": the stops
//  come back with the ones already seen marked, and the navigator recomputes the leg
//  from the first fix after launch. A visitor who closed the app in one gallery is
//  routed onward from wherever they re-open it.
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
     * The visit in progress, or `null` when there is none. A value written by an
     * older build that no longer decodes is treated as none rather than as a crash —
     * `JourneyCodec.decode` throws on text it cannot read, and this is where that
     * becomes "no visit".
     */
    fun load(store: SharedPreferences): Journey? {
        val text = store.getString(KEY, null) ?: return null
        val journey = runCatching { JourneyCodec.decode(text) }.getOrNull() ?: return null
        return journey.takeIf { it.stops.isNotEmpty() }
    }

    /**
     * `null`, or a journey with no stops, clears it — so ending a visit is the same
     * call as saving one.
     */
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
 * A stop is a place the visitor picked. The POI's own id is used both as the stop id
 * and as `poiId`, so a journey read back off disk still points at somewhere in the
 * venue rather than at a coordinate nobody can name.
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

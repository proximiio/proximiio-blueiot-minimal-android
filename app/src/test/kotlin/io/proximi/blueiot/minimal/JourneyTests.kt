//
//  JourneyTests.kt
//  BlueiotMinimalTests
//
//  The two pieces of the visit that fail silently.
//
//  A journey that does not survive a launch loses a visitor's afternoon without
//  anything on screen going wrong, and an amenity query that reads the venue's data
//  the wrong way offers a detour to nowhere — or, worse, offers nothing and looks
//  like a venue with no toilets. Neither shows up in a screenshot. The Compose around
//  them is not tested, because a layout that is wrong is a layout you can see.
//
package io.proximi.blueiot.minimal

import android.content.Context
import android.content.SharedPreferences
import io.proximi.map.core.FloorKey
import io.proximi.map.core.Journey
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapCoordinate
import io.proximi.sdk.core.model.JsonValue
import io.proximi.sdk.core.model.ProximiioCoordinate
import io.proximi.sdk.core.model.ProximiioFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
// SDK 35, not the newest: Robolectric's API 36 image needs Java 21 and this project
// builds on 17. `SharedPreferences` is the whole of what these exercise, and it has
// not moved.
@Config(sdk = [35])
class JourneyPersistenceTests {
    /** A file of its own, so a test never writes into the real app's store. */
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        store =
            RuntimeEnvironment
                .getApplication()
                .getSharedPreferences("JourneyTests.${UUID.randomUUID()}", Context.MODE_PRIVATE)
    }

    private fun stop(
        id: String,
        state: JourneyStop.State = JourneyStop.State.PENDING,
    ) = JourneyStop(
        id = id,
        title = id.replaceFirstChar(Char::uppercase),
        coordinate = MapCoordinate(latitude = 50.0000, longitude = 10.1000),
        floor = FloorKey(1.0),
        poiId = id,
        kind = JourneyStop.Kind.PLANNED,
        state = state,
    )

    /**
     * The whole point: the order AND each stop's state come back, so a visitor who
     * closed the app in the second gallery re-opens it in the second gallery rather
     * than at the front door.
     */
    @Test
    fun roundTripKeepsOrderAndState() {
        val journey =
            Journey(
                stops =
                    listOf(
                        stop("atrium", JourneyStop.State.DONE),
                        stop("zigzag", JourneyStop.State.SKIPPED),
                        stop("capsules", JourneyStop.State.ACTIVE),
                        stop("dividing-line"),
                    ),
            )
        JourneyStore.save(journey, store)

        val restored = requireNotNull(JourneyStore.load(store))
        assertEquals(journey, restored)
        assertEquals(listOf("atrium", "zigzag", "capsules", "dividing-line"), restored.stops.map { it.id })
        assertEquals(
            listOf(
                JourneyStop.State.DONE,
                JourneyStop.State.SKIPPED,
                JourneyStop.State.ACTIVE,
                JourneyStop.State.PENDING,
            ),
            restored.stops.map { it.state },
        )
        assertEquals(FloorKey(1.0), restored.stops[3].floor)
        assertEquals("dividing-line", restored.stops[3].poiId)
    }

    @Test
    fun nothingSavedIsNothingRestored() {
        assertNull(JourneyStore.load(store))
    }

    /**
     * Ending a visit is the same call as saving one, so the next launch must not
     * resume the afternoon the visitor just finished.
     */
    @Test
    fun endingClears() {
        JourneyStore.save(Journey(stops = listOf(stop("atrium"))), store)
        JourneyStore.save(null, store)
        assertNull(JourneyStore.load(store))
    }

    /**
     * An empty journey is not a journey. Saving one clears rather than restoring a
     * bar with nothing in it.
     */
    @Test
    fun emptyJourneyIsNotAVisit() {
        JourneyStore.save(Journey(stops = listOf(stop("atrium"))), store)
        JourneyStore.save(Journey(stops = emptyList()), store)
        assertNull(JourneyStore.load(store))
    }

    /**
     * Something else wrote to the key — an older build, a different shape. Treated
     * as "no visit", never as a crash on launch.
     */
    @Test
    fun unreadableValueIsNoVisit() {
        store.edit().putString("BlueiotMinimal.journey", "not a journey").apply()
        assertNull(JourneyStore.load(store))
    }
}

/**
 * `VenuePoi.nearestByAmenity` — "find me a toilet", answered off the venue's own
 * data rather than off a list of categories somebody assumed.
 */
class AmenityQueryTests {
    /**
     * A POI as `Proximiio.features()` returns one. Longitudes only, at this latitude
     * roughly 74 km per degree, so "further east" is "further away".
     */
    private fun poi(
        id: String,
        amenity: String?,
        longitude: Double,
    ): ProximiioFeature {
        val properties =
            buildMap {
                put("type", JsonValue.String("poi"))
                put("title", JsonValue.String(id))
                put("level", JsonValue.Number(1.0))
                if (amenity != null) put("amenity", JsonValue.String(amenity))
            }
        return ProximiioFeature(
            id = id,
            geometry =
                ProximiioFeature.Geometry(
                    type = "Point",
                    coordinates = JsonValue.Array(listOf(JsonValue.Number(longitude), JsonValue.Number(50.0000))),
                ),
            properties = JsonValue.Object(properties),
        )
    }

    private val here = ProximiioCoordinate(latitude = 50.0000, longitude = 10.1000)

    private fun places(features: List<ProximiioFeature>) = VenuePoi.all(features)

    /**
     * One answer per kind, and it is the nearest one of that kind — not the first in
     * the list, which is the mistake that looks right in a venue with one toilet.
     */
    @Test
    fun nearestOfEachKind() {
        val pois =
            places(
                listOf(
                    poi("far toilet", "sanitary:toilet", 10.1090),
                    poi("near toilet", "sanitary:toilet", 10.1010),
                    poi("cafe", "sustenance:cafe", 10.1050),
                ),
            )
        val nearest = VenuePoi.nearestByAmenity(pois, here)

        assertEquals(2, nearest.size)
        assertEquals("near toilet", nearest["sanitary:toilet"]?.title)
        assertEquals("cafe", nearest["sustenance:cafe"]?.title)
    }

    /**
     * A venue tags what it tags. Nothing here knows the word "toilet", so a venue
     * whose POIs are all artworks offers artworks and a venue that tags nothing
     * offers nothing — which is the honest answer, not an empty hard-coded list.
     */
    @Test
    fun kindsComeFromTheDataNotFromUs() {
        val artworks =
            places(
                listOf(
                    poi("Sculpture North", "culture:artwork", 10.1020),
                    poi("Sculpture South", "culture:artwork", 10.1040),
                ),
            )
        assertEquals(listOf("culture:artwork"), VenuePoi.nearestByAmenity(artworks, here).keys.toList())

        val untagged = places(listOf(poi("a room", null, 10.1020)))
        assertTrue(VenuePoi.nearestByAmenity(untagged, here).isEmpty())
        assertTrue(VenuePoi.nearestByAmenity(emptyList(), here).isEmpty())
    }

    /**
     * An untagged place is still searchable and still routable — it is only not a
     * detour offer.
     */
    @Test
    fun untaggedPlacesAreStillPlaces() {
        val pois =
            places(
                listOf(
                    poi("a room", null, 10.1020),
                    poi("toilet", "sanitary:toilet", 10.1040),
                ),
            )
        assertEquals(2, pois.size)
        assertNull(pois.first { it.title == "a room" }.amenityId)
        assertEquals(1, VenuePoi.nearestByAmenity(pois, here).size)
    }
}

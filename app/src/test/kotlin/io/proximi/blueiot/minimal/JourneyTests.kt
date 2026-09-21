//
//  JourneyTests.kt
//  BlueiotMinimalTests
//
//  The two parts of the visit that fail silently: journey persistence, and the amenity
//  query behind the detour offers. A journey that does not survive a launch, or an
//  amenity query that reads the venue's data wrongly, produces no visible error. The
//  Compose around them is not tested.
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
// SDK 35, not 36: Robolectric's API 36 image requires Java 21 and this project builds on
// 17. These tests exercise `SharedPreferences` only.
@Config(sdk = [35])
class JourneyPersistenceTests {
    /** A file of its own, so a test never writes into the app's real store. */
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

    /** The order and each stop's state both survive a save and a load. */
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
     * Ending a visit is the same call as saving one, so the next launch must not resume
     * the finished visit.
     */
    @Test
    fun endingClears() {
        JourneyStore.save(Journey(stops = listOf(stop("atrium"))), store)
        JourneyStore.save(null, store)
        assertNull(JourneyStore.load(store))
    }

    /** Saving a journey with no stops clears the stored value. */
    @Test
    fun emptyJourneyIsNotAVisit() {
        JourneyStore.save(Journey(stops = listOf(stop("atrium"))), store)
        JourneyStore.save(Journey(stops = emptyList()), store)
        assertNull(JourneyStore.load(store))
    }

    /**
     * A value this build cannot decode, such as one an older build wrote, is treated as
     * no visit rather than as a crash on launch.
     */
    @Test
    fun unreadableValueIsNoVisit() {
        store.edit().putString("BlueiotMinimal.journey", "not a journey").apply()
        assertNull(JourneyStore.load(store))
    }
}

/**
 * `VenuePoi.nearestByAmenity`, answered from the venue's own amenity tags rather than
 * from a list of categories in the app.
 */
class AmenityQueryTests {
    /**
     * A POI as `Proximiio.features()` returns one. Only the longitude varies; at this
     * latitude one degree is roughly 72 km, so a larger longitude is further away.
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
     * One answer per kind, and it is the nearest of that kind rather than the first in
     * the list.
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
     * The kinds come from the venue's data. Nothing in the app knows the word "toilet",
     * so a venue whose POIs are all artworks offers artworks, and a venue that tags
     * nothing offers nothing.
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

    /** An untagged place is searchable and routable; it is only not a detour offer. */
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

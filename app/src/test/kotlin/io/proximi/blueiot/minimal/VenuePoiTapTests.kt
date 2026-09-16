package io.proximi.blueiot.minimal

import io.proximi.sdk.core.model.JsonValue
import io.proximi.sdk.core.model.ProximiioFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `VenuePoi.tapped`: which place a tap on the map resolves to. */
class VenuePoiTapTests {
    private fun poi(id: String): ProximiioFeature =
        ProximiioFeature(
            id = id,
            geometry =
                ProximiioFeature.Geometry(
                    type = "Point",
                    coordinates = JsonValue.Array(listOf(JsonValue.Number(10.1000), JsonValue.Number(50.0000))),
                ),
            properties =
                JsonValue.Object(
                    mapOf(
                        "type" to JsonValue.String("poi"),
                        "title" to JsonValue.String(id),
                        "level" to JsonValue.Number(1.0),
                    ),
                ),
        )

    private val places = VenuePoi.all(listOf(poi("time-capsules"), poi("zigzag")))

    /** The ids arrive nearest first; the first one that is a place wins. */
    @Test
    fun theFirstTappedIdThatIsAPlaceIsChosen() {
        val tapped = VenuePoi.tapped(listOf("room-12", "zigzag", "time-capsules"), places)
        assertEquals("zigzag", tapped?.id)
    }

    @Test
    fun aTapOnNoPlaceChoosesNothing() {
        assertNull(VenuePoi.tapped(listOf("room-12", "wall-3"), places))
    }

    @Test
    fun anEmptyTapChoosesNothing() {
        assertNull(VenuePoi.tapped(emptyList(), places))
    }

    @Test
    fun noPlacesLoadedChoosesNothing() {
        assertNull(VenuePoi.tapped(listOf("zigzag"), emptyList()))
    }
}

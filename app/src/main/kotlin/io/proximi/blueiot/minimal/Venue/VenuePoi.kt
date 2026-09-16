//
//  VenuePoi.kt
//  BlueiotMinimal
//
//  One place a visitor can search for and be routed to.
//
//  Built from `Proximiio.features()` — the SDK's own venue model, read out of its
//  local cache, which `Venue.start` filled with `loadRouteNetwork()`. There is no
//  second download and no app-side place model beyond these four fields; add to it
//  when your product needs an opening time or a photo, not before.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.core.geo.GeoMath
import io.proximi.sdk.core.model.JsonValue
import io.proximi.sdk.core.model.ProximiioCoordinate
import io.proximi.sdk.core.model.ProximiioFeature

data class VenuePoi(
    val id: String,
    val title: String,
    val coordinate: ProximiioCoordinate,
    /** The floor it is on. `computeRoute` takes this as `toLevel`. */
    val level: Double,
    /**
     * What kind of place the venue says this is, and `null` when it says nothing.
     * A Proximi.io amenity id is `<category>:<amenity>` — the only thing in the
     * data that tells a toilet from an exhibit.
     */
    val amenityId: String?,
) {
    companion object {
        /** Every place in the venue, alphabetically. */
        fun all(features: List<ProximiioFeature>): List<VenuePoi> =
            features.mapNotNull(::of).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, VenuePoi::title))

        /**
         * Substring match, case-insensitive. An empty query matches everything, which
         * is what lets the search sheet open on the full list.
         */
        fun matching(
            query: String,
            pois: List<VenuePoi>,
        ): List<VenuePoi> {
            val needle = query.trim()
            if (needle.isEmpty()) return pois
            return pois.filter { it.title.contains(needle, ignoreCase = true) }
        }

        /**
         * `null` for every feature that is not a searchable place: the venue's rooms,
         * walls, level changers and its walkable path network all arrive in the same
         * list.
         */
        private fun of(feature: ProximiioFeature): VenuePoi? {
            if (feature.propertyType != "poi") return null
            val geometry = feature.geometry ?: return null
            if (geometry.type != "Point") return null
            val pair = geometry.coordinates.arrayValue ?: return null
            if (pair.size < 2) return null
            // GeoJSON is [longitude, latitude] — the reverse of how it is spoken.
            val longitude = pair[0].doubleValue ?: return null
            val latitude = pair[1].doubleValue ?: return null
            if (!longitude.isFinite() || !latitude.isFinite()) return null

            return VenuePoi(
                id = feature.id,
                // Organisations label places `title` or `name`; either is the visitor's
                // word for the place. Falling back to the id keeps a mislabelled POI
                // routable rather than invisible.
                title =
                    text(feature.properties?.get("title"))
                        ?: text(feature.properties?.get("name"))
                        ?: feature.id,
                coordinate = ProximiioCoordinate(latitude = latitude, longitude = longitude),
                level = feature.level ?: 0.0,
                amenityId = text(feature.properties?.get("amenity")),
            )
        }

        /**
         * The nearest place of every kind the venue tags, from where the visitor is
         * standing — which is the whole of "find me a toilet".
         *
         * The kinds are read off the venue's own data rather than listed here. A venue
         * that tags toilets and cafes offers toilets and cafes; one that tags only its
         * artworks offers those; one that tags nothing offers nothing, which is a
         * better answer than a hard-coded category no POI carries. Straight-line
         * distance, deliberately: this picks which place to ask for a route to, and the
         * route itself is the SDK's answer to how far it really is.
         */
        fun nearestByAmenity(
            pois: List<VenuePoi>,
            from: ProximiioCoordinate,
        ): Map<String, VenuePoi> {
            fun metres(poi: VenuePoi) = GeoMath.haversineDistance(from, poi.coordinate)
            val nearest = mutableMapOf<String, VenuePoi>()
            for (poi in pois) {
                val amenityId = poi.amenityId ?: continue
                val held = nearest[amenityId]
                if (held != null && metres(held) <= metres(poi)) continue
                nearest[amenityId] = poi
            }
            return nearest
        }

        private fun text(value: JsonValue?): String? = value?.stringValue?.ifEmpty { null }
    }
}

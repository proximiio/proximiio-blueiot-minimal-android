//
//  VenuePoi.kt
//  BlueiotMinimal
//
//  A place a visitor can search for and be routed to, built from `Proximiio.features()`.
//  That reads the SDK's local cache, which `Venue.start` filled with
//  `loadRouteNetwork()`; it is not a second download.
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
    /** The floor level. `computeRoute` takes this as `toLevel`. */
    val level: Double,
    /**
     * The venue's amenity id for this place, or `null` when it has none. A Proximi.io
     * amenity id has the form `<category>:<amenity>`.
     */
    val amenityId: String?,
) {
    companion object {
        /** Every place in the venue, sorted by title, case-insensitively. */
        fun all(features: List<ProximiioFeature>): List<VenuePoi> =
            features.mapNotNull(::of).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, VenuePoi::title))

        /**
         * Case-insensitive substring match on the title. An empty query returns every
         * place, which is what lets the search sheet open on the full list.
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
         * `null` for a feature that is not a searchable place. Rooms, walls, level
         * changers and the walkable path network arrive in the same list.
         */
        private fun of(feature: ProximiioFeature): VenuePoi? {
            if (feature.propertyType != "poi") return null
            val geometry = feature.geometry ?: return null
            if (geometry.type != "Point") return null
            val pair = geometry.coordinates.arrayValue ?: return null
            if (pair.size < 2) return null
            // GeoJSON coordinates are [longitude, latitude].
            val longitude = pair[0].doubleValue ?: return null
            val latitude = pair[1].doubleValue ?: return null
            if (!longitude.isFinite() || !latitude.isFinite()) return null

            return VenuePoi(
                id = feature.id,
                // A venue labels a place `title` or `name`. The id is the fallback, so a
                // mislabelled POI stays searchable and routable.
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
         * The nearest place of each amenity kind the venue tags, measured from [from].
         *
         * The kinds come from the venue's own data; this file lists none, so a venue
         * that tags nothing returns nothing. Distance is straight-line
         * (`GeoMath.haversineDistance`), which selects the candidate; the walking
         * distance is whatever `computeRoute` returns for it.
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

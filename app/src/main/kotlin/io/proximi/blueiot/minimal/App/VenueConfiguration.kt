//
//  VenueConfiguration.kt
//  BlueiotMinimal
//
//  The app's build-time configuration, read from BuildConfig: three credentials from
//  the git-ignored secrets.properties and one venue value from the tracked
//  venue.properties. No value is editable at runtime.
//
package io.proximi.blueiot.minimal

object VenueConfiguration {
    /** Proximi.io application token, from `PROXIMIIO_APPLICATION_TOKEN`. */
    val token: String? = value(BuildConfig.PROXIMIIO_APPLICATION_TOKEN)

    /**
     * Cloud relay address, from `BLUEIOT_CLOUD_RELAY_URL`. A bare host is accepted. Kept
     * as text so `BlueiotCloudRelayEndpoint.fromText` performs the single normalisation
     * into `https://…` and `wss://…/stream`.
     */
    val relayHost: String? = value(BuildConfig.BLUEIOT_CLOUD_RELAY_URL)

    /**
     * Relay stream token, from `BLUEIOT_CLOUD_RELAY_TOKEN`, sent as
     * `Authorization: Bearer`. The relay answers HTTP 401 without it.
     */
    val relayToken: String? = value(BuildConfig.BLUEIOT_CLOUD_RELAY_TOKEN)

    /**
     * The engine floor number for the venue's ground floor, from
     * `BLUEIOT_GROUND_FLOOR_NO`. Not a credential and not required: empty means 0, which
     * is an engine numbering floors the way Proximi.io does. See [Venue.attachRelay].
     */
    val groundFloorNumber: Int = value(BuildConfig.BLUEIOT_GROUND_FLOOR_NO)?.toIntOrNull() ?: 0

    /** The two credentials, passed to the diagnostics recorder for redaction. */
    val secrets: List<String> get() = listOfNotNull(token, relayToken)

    /**
     * The keys that are still empty, as one sentence, or `null` when none are. Covers
     * the three required credentials only.
     */
    val missing: String?
        get() {
            val keys =
                listOf(
                    "PROXIMIIO_APPLICATION_TOKEN" to token,
                    "BLUEIOT_CLOUD_RELAY_URL" to relayHost,
                    "BLUEIOT_CLOUD_RELAY_TOKEN" to relayToken,
                ).filter { it.second == null }.map { it.first }
            if (keys.isEmpty()) return null
            return "Set ${keys.joinToString(", ")} in secrets.properties, then rebuild."
        }

    /**
     * Thrown when a required credential is empty, so a clone with no secrets file runs
     * and reports the missing key instead of crashing.
     */
    class SetupIncomplete : Exception(missing ?: "Configuration is incomplete.")

    /** A BuildConfig string, or `null` when the properties file left it empty. */
    private fun value(text: String): String? = text.trim().ifEmpty { null }
}

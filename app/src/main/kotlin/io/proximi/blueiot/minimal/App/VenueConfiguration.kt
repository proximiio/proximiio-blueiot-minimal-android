//
//  VenueConfiguration.kt
//  BlueiotMinimal
//
//  The whole of the app's configuration: three credentials and one survey value,
//  injected through the git-ignored secrets.properties (plus the tracked
//  venue.properties) into BuildConfig and read here once. None of them is editable at
//  runtime, because a visitor has no business editing them and this app has no settings
//  screen for staff to get lost in.
//
package io.proximi.blueiot.minimal

object VenueConfiguration {
    /** Proximi.io application token — `PROXIMIIO_APPLICATION_TOKEN`. */
    val token: String? = value(BuildConfig.PROXIMIIO_APPLICATION_TOKEN)

    /**
     * The cloud relay's address as typed — `BLUEIOT_CLOUD_RELAY_URL`. A bare host is
     * fine. Kept as text so the SDK's `BlueiotCloudRelayEndpoint` does the one
     * normalisation into `https://…` and `wss://…/stream`.
     */
    val relayHost: String? = value(BuildConfig.BLUEIOT_CLOUD_RELAY_URL)

    /**
     * The relay's stream token, sent as `Authorization: Bearer` —
     * `BLUEIOT_CLOUD_RELAY_TOKEN`. Without it the relay answers HTTP 401.
     */
    val relayToken: String? = value(BuildConfig.BLUEIOT_CLOUD_RELAY_TOKEN)

    /**
     * Which floor number the venue's Blueiot engine calls the ground floor —
     * `BLUEIOT_GROUND_FLOOR_NO`. Not required and not a credential: empty means 0,
     * which is the engine numbering storeys exactly the way Proximi.io does, and is
     * the only case that needs no value at all. See [Venue.follow].
     */
    val groundFloorNumber: Int = value(BuildConfig.BLUEIOT_GROUND_FLOOR_NO)?.toIntOrNull() ?: 0

    // On iOS the two credentials are also handed to the diagnostics recorder, which
    // strips them wherever they appear in the log. The Android SDK records no log yet
    // (README, "The diagnostics log"), so there is nothing here to hand them to.

    /**
     * Which keys are still empty, in one sentence, or `null` when none are. Only the
     * three the app cannot run without; the survey value has an honest default.
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
     * Thrown rather than crashing, so a fresh clone with no secrets file still runs
     * and says what is missing.
     */
    class SetupIncomplete : Exception(missing ?: "Configuration is incomplete.")

    /** A BuildConfig string, or `null` when the properties file left it empty. */
    private fun value(text: String): String? = text.trim().ifEmpty { null }
}

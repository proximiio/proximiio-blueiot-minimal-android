//
//  VenueConfiguration.kt
//  BlueiotMinimal
//
//  The app's build-time configuration, read from BuildConfig: two credentials from the
//  git-ignored secrets.properties and the relay-api URL, whose default is in the tracked
//  venue.properties. No value is editable at runtime; the app has no settings screen.
//
package io.proximi.blueiot.minimal

import io.proximi.sdk.blueiot.binding.BlueiotBindingConfiguration
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayEndpoint

object VenueConfiguration {
    /** Proximi.io application token, from `PROXIMIIO_APPLICATION_TOKEN`. */
    val token: String? = value(BuildConfig.PROXIMIIO_APPLICATION_TOKEN)

    /**
     * The relay-api base URL as configured, from `BLUEIOT_RELAY_URL`. Kept as text;
     * `BlueiotCloudRelayEndpoint.fromText` parses it. A bare host becomes `https://…`.
     */
    val relayURL: String? = value(BuildConfig.BLUEIOT_RELAY_URL)

    /**
     * The relay-api app token, from `BLUEIOT_RELAY_APP_TOKEN`. Sent as
     * `Authorization: Bearer` to the relay-api.
     */
    val relayAppToken: String? = value(BuildConfig.BLUEIOT_RELAY_APP_TOKEN)

    /** The two credentials, passed to the diagnostics recorder for redaction. */
    val secrets: List<String> get() = listOfNotNull(token, relayAppToken)

    /**
     * The wristband binding configuration, or `null` while the relay URL or the app
     * token is missing or the URL does not parse.
     */
    val binding: BlueiotBindingConfiguration? get() = binding(relayURL, relayAppToken)

    /**
     * The empty or invalid keys in one sentence, or `null` when all three are set and
     * the URL parses.
     */
    val missing: String? get() = missing(token, relayURL, relayAppToken)

    /**
     * Thrown when a required value is empty, so a clone with no secrets file runs and
     * reports the missing key instead of crashing.
     */
    class SetupIncomplete : Exception(missing ?: "Configuration is incomplete.")

    /**
     * The binding configuration for [relayURL] and [appToken].
     *
     * `runsInBackground = true` keeps the position stream open after the app leaves the
     * screen. Without it the SDK pauses the provider whenever the app is backgrounded,
     * whatever the process itself is allowed to do. The default is `false`.
     */
    internal fun binding(
        relayURL: String?,
        appToken: String?,
    ): BlueiotBindingConfiguration? {
        val endpoint = relayURL?.let { BlueiotCloudRelayEndpoint.fromText(it) } ?: return null
        if (appToken == null) return null
        return BlueiotBindingConfiguration(relayURL = endpoint, appToken = appToken, runsInBackground = true)
    }

    /** [missing] for the given values. */
    internal fun missing(
        token: String?,
        relayURL: String?,
        relayAppToken: String?,
    ): String? {
        val keys =
            listOf(
                "PROXIMIIO_APPLICATION_TOKEN" to token,
                "BLUEIOT_RELAY_URL" to relayURL,
                "BLUEIOT_RELAY_APP_TOKEN" to relayAppToken,
            ).filter { it.second == null }.map { it.first }.toMutableList()
        if (relayURL != null && BlueiotCloudRelayEndpoint.fromText(relayURL) == null) {
            keys += "a valid BLUEIOT_RELAY_URL"
        }
        if (keys.isEmpty()) return null
        return "Set ${keys.joinToString(", ")} in secrets.properties, then rebuild."
    }

    /** A BuildConfig string, or `null` when the properties files left it empty. */
    private fun value(text: String): String? = text.trim().ifEmpty { null }
}

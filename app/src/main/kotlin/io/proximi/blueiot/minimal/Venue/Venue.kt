//
//  Venue.kt
//  BlueiotMinimal
//
//  All of this app's positioning: it starts the SDK and attaches the BlueIoT cloud
//  relay position provider for one wristband. The venue's anchors locate the wristband
//  and report to the relay; the phone scans nothing.
//  `ProximiioConfiguration.relayOnly(token, serviceOptions)` is the preset for that
//  shape of app. It turns the SDK's iBeacon, Eddystone and UWB sources off, and native
//  location with them.
//
//  Positioning while the app is backgrounded requires all four of: `serviceOptions` on
//  the SDK configuration and `runsInBackground = true` on the relay configuration (both
//  below), the foreground-service permissions (`AndroidManifest.xml`), and a location
//  grant (`LocationPrompt`). Any one of them missing stops position updates within
//  minutes of the screen locking. `serviceOptions` keeps the process alive;
//  `runsInBackground` stops the SDK pausing the relay provider when the app
//  backgrounds.
//
package io.proximi.blueiot.minimal

import android.content.Context
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioConfiguration
import io.proximi.sdk.attachPositionProvider
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayConfiguration
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayEndpoint
import io.proximi.sdk.blueiot.cloudrelay.BlueiotCloudRelayPositionProvider
import io.proximi.sdk.core.platform.PermissionRequestLauncher
import io.proximi.sdk.detachPositionProvider
import io.proximi.sdk.loadRouteNetwork
import io.proximi.sdk.requestPermissions
import io.proximi.sdk.service.ProximiioServiceOptions
import io.proximi.sdk.setPermissionLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class Venue private constructor(
    /**
     * The started SDK. `VenueMapScreen` passes it to the map session, which reads the
     * venue, the floors and the live position from it.
     */
    val sdk: Proximiio,
) {
    /** The attached provider's name, so a second [follow] can detach it. */
    private var attachedProvider: String? = null

    /**
     * The scope for work that outlives the map screen, such as the place notifications.
     * Cancelled in [stop].
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Attaches the relay position provider for one wristband.
     *
     * Calling it again with a different id detaches the previous provider first, so
     * changing the wristband is a re-attach rather than an SDK restart.
     *
     * No `floorNoMap` is passed. An engine floor number is the Proximi.io floor level,
     * and the SDK syncs every floor with its level, so it derives the number-to-floor
     * table itself; a table supplied here switches that derivation off. A fix naming a
     * number the venue has no floor for is reported in the SDK log.
     */
    suspend fun follow(wristband: WristbandId) {
        attachedProvider?.let {
            sdk.detachPositionProvider(it)
            attachedProvider = null
        }
        val host = VenueConfiguration.relayHost ?: return
        val endpoint = BlueiotCloudRelayEndpoint.fromText(host) ?: return

        val configuration =
            BlueiotCloudRelayConfiguration(
                endpoint = endpoint,
                token = VenueConfiguration.relayToken,
                tagId = wristband.canonical,
                // Default `false`, which pauses the provider whenever the app is
                // backgrounded, whatever the process itself is allowed to do.
                runsInBackground = true,
                // The only floor value the SDK cannot derive: this venue's LocalSense
                // engine numbers the ground floor 1 where Proximi.io uses level 0.
                engineGroundFloorNumber = VenueConfiguration.groundFloorNumber,
            )

        val provider = BlueiotCloudRelayPositionProvider(configuration)
        attachedProvider = provider.name
        sdk.attachPositionProvider(provider)
    }

    /**
     * Starts the place notifications (`GeofenceNotifier`).
     *
     * Geofences are evaluated in the process the foreground service keeps alive, so the
     * notifications need no further component to arrive with the screen off. The
     * collection runs on this venue's scope and ends with [stop].
     */
    private fun notifyOnPlaceChanges(context: Context) {
        scope.launch { GeofenceNotifier(context).collectFrom(sdk) }
    }

    /** Cancels the venue's scope and stops the SDK. */
    suspend fun stop() {
        scope.cancel()
        sdk.stop()
    }

    companion object {
        /**
         * The SDK configuration for this app. Separate from [start] so a test can read
         * the options. `relayOnly` leaves `serviceOptions` at `null`, which is
         * foreground-only positioning.
         */
        fun configuration(token: String): ProximiioConfiguration =
            ProximiioConfiguration.relayOnly(
                token = token,
                serviceOptions =
                    ProximiioServiceOptions(
                        notificationChannelName = "Venue positioning",
                        notificationChannelDescription = "Shown while the venue is placing you on the map.",
                        notificationTitle = "Venue Map",
                        notificationText = "Following your wristband around the venue.",
                        // A relay-only app scans nothing, so the `connectedDevice`
                        // foreground-service type is never warranted. The flag is the
                        // host's opt-out and the SDK never derives a type back on, so
                        // the service's type mask stays `location` only, which is the
                        // one type whose permission this app's manifest declares.
                        includesConnectedDeviceType = false,
                        // The position source is a socket and a socket is read on the
                        // CPU. In Doze the frame would wait for the next maintenance
                        // window. The lock is released with the service and is bounded
                        // by `ProximiioServiceOptions.wakeLockTimeoutMillis`, 30 minutes
                        // by default.
                        holdsWakeLock = true,
                    ),
            )

        /**
         * Requests permissions, authenticates, starts positioning and downloads the
         * venue.
         *
         * The four suspending calls run in this order, and none is optional:
         *  1. [requestPermissions] reads the grants. It prompts only while Android has
         *     never been asked, so `LocationPrompt` is the only prompt a visitor sees.
         *  2. [Proximiio.authenticate] validates the token and runs the first sync,
         *     which fills the floors relay fixes are resolved against.
         *  3. [Proximiio.start] starts positioning and, with `serviceOptions` set, the
         *     foreground service. Fixes arrive once a provider is attached.
         *  4. [loadRouteNetwork] downloads the venue GeoJSON, which holds both the POIs
         *     this app searches and the path network [computeRoute] uses. It is cached
         *     locally and is the only network call wayfinding needs.
         *
         * The place notifications are attached between steps 3 and 4: the geofence
         * stream exists from `start()`, and a transition can arrive while the route
         * network is still downloading.
         */
        suspend fun start(
            context: Context,
            token: String,
            launcher: PermissionRequestLauncher,
        ): Venue {
            val sdk = Proximiio(context, configuration(token))
            sdk.setPermissionLauncher(launcher)
            sdk.requestPermissions()
            sdk.authenticate()
            sdk.start()
            val venue = Venue(sdk)
            venue.notifyOnPlaceChanges(context)
            sdk.loadRouteNetwork()
            return venue
        }
    }
}

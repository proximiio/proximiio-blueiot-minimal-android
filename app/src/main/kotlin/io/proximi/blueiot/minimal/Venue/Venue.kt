//
//  Venue.kt
//  BlueiotMinimal
//
//  All of this app's positioning: it starts the SDK and attaches the position provider
//  of the wristband binding (`WristbandSession`). The venue's anchors locate the
//  wristband and report to the Proximi.io relay-api; the phone scans nothing.
//  `ProximiioConfiguration.relayOnly(token, serviceOptions)` is the preset for that
//  shape of app. It turns the SDK's iBeacon, Eddystone and UWB sources off, and native
//  location with them.
//
//  Positioning while the app is backgrounded requires all four of: `serviceOptions` on
//  the SDK configuration (below) and `runsInBackground = true` on the binding
//  configuration (`VenueConfiguration.binding`), the foreground-service permissions
//  (`AndroidManifest.xml`), and a location grant (`LocationPrompt`). Any one of them
//  missing stops position updates within minutes of the screen locking.
//  `serviceOptions` keeps the process alive; `runsInBackground` stops the SDK pausing
//  the binding's provider when the app backgrounds.
//
package io.proximi.blueiot.minimal

import android.content.Context
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioConfiguration
import io.proximi.sdk.attachPositionProvider
import io.proximi.sdk.blueiot.binding.BlueiotWristbandBinding
import io.proximi.sdk.detachPositionProvider
import io.proximi.sdk.loadRouteNetwork
import io.proximi.sdk.positioning.engine.CustomPositionProviding
import io.proximi.sdk.refreshPermissions
import io.proximi.sdk.service.ProximiioServiceOptions
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
    /** The binding client whose provider delivers the wristband's positions. */
    private val binding: BlueiotWristbandBinding,
) {
    /** The attached provider's name, so [attach] and [detachProvider] can detach it. */
    private var attachedProvider: String? = null

    /**
     * The scope for work that outlives the map screen, such as the place notifications.
     * Cancelled in [stop].
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Attaches the binding's position provider.
     *
     * The provider follows whichever session the binding holds, so a new bind, a
     * take-over or an ended visit needs no re-attach. The relay-api sends Proximi.io
     * floor levels, and the floor id when it knows it; the SDK resolves a level against
     * the floors it synced. The app sets no floor mapping.
     */
    suspend fun attachRelay() {
        attach(binding.positionProvider)
    }

    /** Attaches [provider] in place of the attached one. Detaching is by name. */
    suspend fun attach(provider: CustomPositionProviding) {
        detachProvider()
        attachedProvider = provider.name
        sdk.attachPositionProvider(provider)
    }

    /** Detaches the attached provider, if any. */
    suspend fun detachProvider() {
        val name = attachedProvider ?: return
        sdk.detachPositionProvider(name)
        attachedProvider = null
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
         * Reads the permission grants, authenticates, starts positioning, downloads the
         * venue and attaches the binding's position provider.
         *
         * The four suspending calls run in this order, and none is optional:
         *  1. [refreshPermissions] reads the grants `LocationPrompt` obtained. It never
         *     shows a dialog. `requestPermissions()` is not called: the SDK counts only
         *     the prompts it raised itself, so after a refusal in `LocationPrompt` it
         *     can show the system location dialog a second time.
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
         * network is still downloading. The binding's provider is attached last, once.
         */
        suspend fun start(
            context: Context,
            token: String,
            binding: BlueiotWristbandBinding,
        ): Venue {
            val sdk = Proximiio(context, configuration(token))
            sdk.refreshPermissions()
            sdk.authenticate()
            sdk.start()
            val venue = Venue(sdk, binding)
            venue.notifyOnPlaceChanges(context)
            sdk.loadRouteNetwork()
            // Debug builds play a journey instead when launched with a `journeyPlayback`
            // extra. Release builds never do. See `DebugPositionSource`.
            if (!DebugPositionSource.attachAtStart(venue)) venue.attachRelay()
            return venue
        }
    }
}

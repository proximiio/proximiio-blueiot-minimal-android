//
//  Venue.kt
//  BlueiotMinimal
//
//  THE WHOLE OF THIS APP'S POSITIONING.
//
//  The venue's anchors locate the wristband and report to a Proximi.io cloud relay;
//  the phone scans nothing. `ProximiioConfiguration.relayOnly(token, serviceOptions)`
//  is the preset for exactly that shape of app — it turns the SDK's own iBeacon,
//  Eddystone and UWB sources off, and native location with them, so there is no radio
//  to tune. Everything else here is two calls: start the SDK, attach the relay provider.
//
//  POSITIONING CARRIES ON IN A POCKET. That takes four things, and each one missing
//  looks the same — the dot stops within minutes of the screen locking, as if the
//  relay had died: `serviceOptions` on the SDK configuration and `runsInBackground`
//  on the relay provider's (both below); the foreground-service permissions
//  (`AndroidManifest.xml`); and a location grant (`LocationPrompt`). The SDK's own
//  background guide puts the first two like this: "These are two switches, not one…
//  Set only the first and the service sits there healthy while the facade pauses the
//  relay provider a second after the screen locks. Set only the second and Doze
//  freezes the process, socket and all."
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
import io.proximi.sdk.refreshPermissions
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
     * The started SDK. `VenueMapScreen` hands this to the map, which reads the venue,
     * the floors and the live position off it.
     */
    val sdk: Proximiio,
) {
    /**
     * So a second [follow] can take the first one down. Only the name is kept —
     * detaching is by name.
     */
    private var attachedProvider: String? = null

    /**
     * The one coroutine scope this app owns, and it belongs to the visit rather than to a
     * screen: the map comes and goes with the Activity, and a note about the gallery the
     * visitor has just walked into is due whether or not anything is on screen. Cancelled
     * in [stop], which is the only thing that ends the visit.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Points positioning at one wristband.
     *
     * Safe to call again with a different band: the previous provider is detached
     * first, so changing the id is a re-attach rather than a restart.
     *
     * WHICH STOREY A FIX LANDS ON is not this app's arithmetic. An engine floor
     * number *is* the Proximi.io floor level, and the SDK already syncs every floor
     * with its level, so it derives the number-to-floor table itself and tells you
     * in the log when a fix names a number the venue has no floor for. Pass no
     * `floorNoMap` and none of that happens: a table you supply switches derivation
     * off.
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
                // Without this the SDK pauses the provider on backgrounding, whatever
                // the process itself is allowed to do.
                runsInBackground = true,
                // The one thing the SDK cannot know: this venue's LocalSense calls the
                // ground floor 1 where Proximi.io calls it level 0. Delete this line,
                // and `BLUEIOT_GROUND_FLOOR_NO` with it, the day the deployment is
                // renumbered.
                engineGroundFloorNumber = VenueConfiguration.groundFloorNumber,
            )

        val provider = BlueiotCloudRelayPositionProvider(configuration)
        attachedProvider = provider.name
        sdk.attachPositionProvider(provider)
    }

    /**
     * Re-reads the grants after the app has run its own permission dialog.
     *
     * Android has no authorization-changed callback, so the SDK sees a grant it asked
     * for itself and nothing else. Everything downstream of a permission change
     * follows from this call, including raising the foreground service that could not
     * be raised before the grant.
     */
    suspend fun refreshPermissions() {
        sdk.refreshPermissions()
    }

    /**
     * Starts the place notifications (`GeofenceNotifier`).
     *
     * Nothing extra is needed for them to arrive with the screen off: the geofences are
     * evaluated in the very process the foreground service already keeps alive for the
     * pocket, so a note reaches the lock screen for the same four reasons the dot keeps
     * moving. Off a scope of this venue's, so it stops when the visit does.
     */
    private fun notifyOnPlaceChanges(context: Context) {
        scope.launch { GeofenceNotifier(context).collectFrom(sdk) }
    }

    /** Called when the screen holding this venue is gone for good. */
    suspend fun stop() {
        scope.cancel()
        sdk.stop()
    }

    companion object {
        /**
         * The SDK, configured for this shape of app. Apart from `start` so a test can
         * read the options off it — `serviceOptions` defaults to `null`, which is
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
                        // A relay-only app scans nothing — no BLE, no connected device —
                        // so `location` is the only foreground-service type it can hold,
                        // and it is the only one whose permission the manifest declares.
                        // Asking for `connectedDevice` here would make `startForeground`
                        // throw on API 34+.
                        includesConnectedDeviceType = false,
                        // The position source is a socket, and a socket is read on the
                        // CPU: in Doze the frame waits for the next maintenance window,
                        // which is the difference between a dot that moves as the
                        // visitor walks and one that jumps every ten minutes. The lock
                        // lives only as long as the service, and the SDK's 30-minute
                        // timeout is the safety net under it.
                        holdsWakeLock = true,
                    ),
            )

        /**
         * Asks for location, authenticates, starts, and downloads the venue.
         *
         * Four awaits, in this order, and none of them is optional:
         *  1. [requestPermissions] is the SDK's own read of the grants. It prompts only
         *     while Android has never been asked, so the ask `LocationPrompt` already
         *     ran is the only one a visitor sees, and a returning visitor pays nothing
         *     here.
         *  2. [authenticate] validates the token and runs the first sync — which is
         *     what fills the floors the SDK resolves relay fixes against.
         *  3. [Proximiio.start] starts positioning. Under `relayOnly` that means the
         *     engine and the foreground service; the fixes arrive once a provider is
         *     attached.
         *  4. [loadRouteNetwork] downloads the venue's GeoJSON: the POIs this app
         *     searches *and* the path network `computeRoute` walks, cached locally, so
         *     it is the one network call wayfinding needs.
         *
         * The place notifications are attached between 3 and 4, because the geofence
         * stream exists from the moment the SDK is started and a transition can arrive
         * while the route network is still downloading.
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

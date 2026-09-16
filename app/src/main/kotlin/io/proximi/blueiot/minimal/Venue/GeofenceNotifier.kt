//
//  GeofenceNotifier.kt
//  BlueiotMinimal
//
//  Posts a notification when the visitor enters or leaves one of the venue's geofences.
//  One channel, one sentence and one notification id per geofence. Geofences are
//  defined in Proximi.io Portal and evaluated by the SDK against every position.
//
//  This file has no iOS twin. The iOS app posts no notifications; Android does because
//  the product required it.
//
//  Notifications arrive with the screen off for the same four reasons positioning
//  continues (`Venue.kt`): the geofence is evaluated in the process the foreground
//  service keeps alive. No second service, receiver or background job is involved.
//
//  Privacy zone events never produce a notification. A privacy zone exists so that the
//  visitor's presence inside it is not reported.
//
package io.proximi.blueiot.minimal

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.proximi.sdk.Proximiio
import io.proximi.sdk.geofenceEvents
import io.proximi.sdk.geofencing.GeofenceEvent

/** One geofence transition, reduced to what a notification needs. */
data class PlaceNote(val id: Int, val title: String, val body: String)

class GeofenceNotifier(context: Context) {
    private val appContext: Context = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    /** Collects until the scope that launched it is cancelled, which is `Venue.stop`. */
    suspend fun collectFrom(sdk: Proximiio) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Place updates")
                .setDescription("Says when you walk into one of the venue's places, and when you leave it.")
                .build(),
        )
        sdk.geofenceEvents().collect { event -> note(event)?.let(::post) }
    }

    /**
     * `POST_NOTIFICATIONS` is requested alongside location in `LocationPrompt` on API 33
     * and above. This app never requests it a second time. A refusal suppresses the
     * notifications and affects nothing else.
     */
    private fun post(note: PlaceNote) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // The Activity is `singleTop` in the manifest, so a tap brings the running map
        // forward rather than creating a second instance.
        val open =
            Intent(appContext, MainActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val tap = PendingIntent.getActivity(appContext, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification =
            NotificationCompat.Builder(appContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_place)
                .setContentTitle(note.title)
                .setContentText(note.body)
                .setContentIntent(tap)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
        manager.notify(note.id, notification)
    }

    companion object {
        /** Renaming this orphans the channel settings the visitor has already chosen. */
        private const val CHANNEL = "place-updates"

        /**
         * The notification for a transition, or `null` when none is posted: privacy zone
         * events, and geofences the venue left unnamed. A geofence id is never shown to
         * a visitor.
         */
        fun note(event: GeofenceEvent): PlaceNote? {
            val geofence =
                when (event) {
                    is GeofenceEvent.Entered -> event.geofence
                    is GeofenceEvent.Exited -> event.geofence
                    is GeofenceEvent.PrivacyZoneEntered, is GeofenceEvent.PrivacyZoneExited -> return null
                }
            val name = geofence.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return PlaceNote(
                // One id per geofence, derived from the geofence id. `String.hashCode` is
                // specified by the language, so the value is the same on every device and
                // after a relaunch, which is what lets the exit replace the enter.
                id = geofence.id.hashCode(),
                title = name,
                body =
                    if (event is GeofenceEvent.Entered) {
                        "You are now inside $name."
                    } else {
                        "You have left $name."
                    },
            )
        }
    }
}

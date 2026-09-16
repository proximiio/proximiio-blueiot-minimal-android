//
//  GeofenceNotifier.kt
//  BlueiotMinimal
//
//  A note when the visitor walks into one of the venue's places, and one when they
//  walk out of it.
//
//  THE ONE FILE WITH NO iOS TWIN. The iOS app lists "no notification prompts" among
//  the things it deliberately is not; this one posts notes because the product asked
//  for them on Android. One channel, one sentence, one row per geofence. The geofences
//  are drawn in Proximi.io Portal and evaluated by the SDK against every fix.
//
//  NOTHING EXTRA IS NEEDED FOR THE SCREEN TO BE OFF: the geofence is evaluated in the
//  same process the foreground service already keeps alive for the pocket (`Venue.kt`),
//  so a note reaches a locked screen for the four reasons the dot keeps moving. No
//  second service, no receiver, no background job.
//
//  PRIVACY ZONES ARE NEVER ANNOUNCED. A privacy zone exists so that the visitor's
//  presence inside it is not reported; putting "You are now inside the staff room." on
//  a lock screen anyone can read would be the one thing it is there to prevent.
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

/** One transition, reduced to what a notification needs. Pure, so the words are testable. */
data class PlaceNote(val id: Int, val title: String, val body: String)

class GeofenceNotifier(context: Context) {
    private val appContext: Context = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    /** Collects until the scope that launched it is cancelled — the venue's, in `Venue.stop`. */
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
     * A refusal costs the banners and nothing else: `POST_NOTIFICATIONS` is asked for
     * alongside location in `LocationPrompt` on API 33+, this app never asks a second
     * time, and everything else carries on without it.
     */
    private fun post(note: PlaceNote) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // `singleTop` in the manifest, so a tap brings the map already running forward
        // rather than building a second one behind it.
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
        /** Renaming this would orphan whatever the visitor chose for it in Android's settings. */
        private const val CHANNEL = "place-updates"

        /**
         * What a transition says, or `null` when it says nothing: a privacy zone, for the
         * reason at the head of this file, and an area the venue never named — a geofence
         * id means nothing to a visitor, and a banner is no place to put one.
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
                // One id per geofence, from its own id. `String.hashCode` is specified by
                // the language, so the id is the same on every device and after a relaunch
                // — which is what makes the exit replace the enter at all.
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

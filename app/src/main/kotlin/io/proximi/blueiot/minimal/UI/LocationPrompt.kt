//
//  LocationPrompt.kt
//  BlueiotMinimal
//
//  Requests `ACCESS_COARSE_LOCATION`, and `POST_NOTIFICATIONS` on API 33 and above.
//  Shown once, between the wristband prompt and the map.
//
//  The location grant is not used for the position: the venue's anchors locate the
//  wristband. It is required because Android freezes a backgrounded process within
//  minutes unless a foreground service is running, and from API 34 the platform refuses
//  to start a service of type `location` without a location grant. Coarse is enough.
//  Background location is never requested. `POST_NOTIFICATIONS` is requested with it so
//  the service's ongoing notification is visible; a refusal leaves the service running
//  with the notification hidden and affects nothing else. The two configuration flags
//  are in `Venue.kt`, the manifest permissions in `AndroidManifest.xml`.
//
package io.proximi.blueiot.minimal

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocationPrompt(onAnswered: () -> Unit) {
    // The app runs its own prompt, for exactly the permissions its manifest declares,
    // rather than letting the SDK request them: the SDK would also request Bluetooth,
    // which a relay-only app never uses. This is a deliberate divergence from the iOS
    // app, which lets the SDK raise the dialog. Any result counts as answered.
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onAnswered()
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null)
        Text("Your location", style = MaterialTheme.typography.titleMedium)
        Text(
            "Allow location when Android asks, and your position keeps updating with the phone " +
                "in your pocket — refuse, and the map still works while the app is on screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { request.launch(LocationPrompt.permissions()) }) { Text("Continue") }
    }
}

object LocationPrompt {
    private const val KEY = "LocationAsked"

    /**
     * Whether the prompt is still owed. It is shown only while Android has never been
     * asked; a refusal counts as an answer and nobody is asked twice.
     *
     * The flag is the app's own because Android reports no "not determined" state and
     * `shouldShowRequestPermissionRationale` cannot distinguish "never asked" from
     * "denied twice".
     */
    fun isOwed(hasBeenAsked: Boolean): Boolean = !hasBeenAsked

    fun hasBeenAsked(store: SharedPreferences): Boolean = store.getBoolean(KEY, false)

    fun markAsked(store: SharedPreferences) {
        store.edit { putBoolean(KEY, true) }
    }

    /** Exactly the runtime permissions this app's manifest declares. */
    fun permissions(): Array<String> =
        buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
}

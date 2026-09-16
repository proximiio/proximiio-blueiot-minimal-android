//
//  LocationPrompt.kt
//  BlueiotMinimal
//
//  The other thing this app asks a person for — once, between the wristband and
//  the map.
//
//  Not for the position: the venue's anchors place the wristband, and the phone's
//  location never enters it. It is asked for because Android freezes a backgrounded
//  process within minutes unless a foreground service is running, and from API 34 the
//  platform refuses to raise one of type `location` without a location grant — so with
//  the question unanswered, the dot stops the moment the phone goes in a pocket.
//  Coarse is enough; nothing here asks for background location. `POST_NOTIFICATIONS`
//  is asked with it on 33+ so the service's ongoing row is visible; a refusal leaves
//  the service running invisibly and costs nothing else. The two flags that go with
//  them are in `Venue.kt`, the manifest permissions in `AndroidManifest.xml`.
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
    // The app runs its own prompt, for exactly the permissions the manifest declares,
    // rather than letting the SDK ask: a relay-only app scans nothing, and a visitor
    // handed a Bluetooth dialog for a radio the app never turns on would be right to
    // wonder. Whatever comes back, the question has been asked.
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
     * Shown only while Android has never been asked. A refusal is an answer: the map
     * works on screen without it, and nobody is asked twice.
     *
     * The flag is the app's own, because Android has no `notDetermined`:
     * `shouldShowRequestPermissionRationale` cannot tell "never asked" from "denied
     * twice", and the two want opposite things from this screen.
     */
    fun isOwed(hasBeenAsked: Boolean): Boolean = !hasBeenAsked

    fun hasBeenAsked(store: SharedPreferences): Boolean = store.getBoolean(KEY, false)

    fun markAsked(store: SharedPreferences) {
        store.edit { putBoolean(KEY, true) }
    }

    /** Exactly what the manifest declares, and nothing else. */
    fun permissions(): Array<String> =
        buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
}

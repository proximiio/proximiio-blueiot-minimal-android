//
//  MainActivity.kt
//  BlueiotMinimal
//
//  THE WHOLE APP, IN ORDER: ask for the wristband, ask for location, start the
//  SDK, show the map.
//
//  Nothing else happens at this level. There is no navigation graph, no bottom bar,
//  no onboarding flow and no settings — a visitor is handed a band, types the number
//  on it once, answers one location prompt, and is on the map. Your product's screens
//  go where `VenueMapScreen` is built.
//
//  One Activity, and no ViewModel: every piece of state below outlives nothing but a
//  configuration change, which `rememberSaveable` and the stores already cover. The
//  SDK's own session is what survives the screen going away, and it survives in the
//  foreground service rather than in a holder of this app's.
//
package io.proximi.blueiot.minimal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.proximi.sdk.core.platform.PermissionRequestLauncher
import io.proximi.sdk.permissions.ActivityResultPermissionLauncher

class MainActivity : ComponentActivity() {
    // Registered at construction time, which is what `registerForActivityResult`
    // requires: the SDK is handed this in `Venue.start` so its own permission calls
    // have somewhere to run.
    private val permissionLauncher = ActivityResultPermissionLauncher(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(permissionLauncher)
                }
            }
        }
    }
}

/**
 * First run asks for the wristband, then for location; every run after that goes
 * straight to the map.
 */
@Composable
fun RootScreen(launcher: PermissionRequestLauncher) {
    val context = LocalContext.current
    val store = remember(context) { WristbandStore.preferences(context) }

    // Read from the store on the first composition, so a returning visitor never sees
    // the prompt.
    var wristband by remember { mutableStateOf(WristbandStore.load(store)) }
    // Likewise read once. Android has no `notDetermined`, so the app keeps its own
    // flag: this is `true` exactly until the first time `LocationPrompt` is answered.
    var owesLocationAsk by remember { mutableStateOf(LocationPrompt.isOwed(LocationPrompt.hasBeenAsked(store))) }
    var venue by remember { mutableStateOf<Venue?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Keyed on the wristband: saving a different one re-runs this, and `follow()`
    // re-points positioning at the new band without restarting the SDK or rebuilding
    // the map. Held at `null` while the location ask is on screen, so the SDK — and
    // the service it raises — starts only once that screen has been answered.
    LaunchedEffect(if (owesLocationAsk) null else wristband) {
        val band = wristband ?: return@LaunchedEffect
        if (owesLocationAsk) return@LaunchedEffect
        try {
            val running = venue
            if (running != null) {
                // Already running: only the band changed.
                running.follow(band)
                return@LaunchedEffect
            }
            val token = VenueConfiguration.token ?: throw VenueConfiguration.SetupIncomplete()
            val started = Venue.start(context, token, launcher)
            started.follow(band)
            venue = started
        } catch (error: Exception) {
            failure = error.message ?: error.toString()
        }
    }

    val started = venue
    when {
        wristband == null ->
            WristbandPrompt(onSave = {
                WristbandStore.save(it, store)
                wristband = it
            })

        owesLocationAsk ->
            LocationPrompt(onAnswered = {
                LocationPrompt.markAsked(store)
                owesLocationAsk = false
            })

        started != null ->
            VenueMapScreen(
                venue = started,
                wristband = wristband?.canonical.orEmpty(),
                onSaveWristband = {
                    WristbandStore.save(it, store)
                    wristband = it
                },
            )

        failure != null -> CannotReachTheVenue(failure.orEmpty())

        else ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
    }
}

@Composable
private fun CannotReachTheVenue(reason: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cannot reach the venue", style = MaterialTheme.typography.titleMedium)
        Text(reason, style = MaterialTheme.typography.bodyMedium)
    }
}

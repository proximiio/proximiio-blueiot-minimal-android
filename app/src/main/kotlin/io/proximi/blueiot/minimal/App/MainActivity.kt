//
//  MainActivity.kt
//  BlueiotMinimal
//
//  The single Activity. It shows, in order, the wristband prompt, the location prompt
//  and the venue map. Product screens replace or sit beside `VenueMapScreen`.
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.proximi.sdk.refreshPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SdkLogcat.installInDebugBuilds()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }
}

/**
 * Shows the wristband prompt and then the location prompt on first run. Later runs go
 * straight to the map.
 */
@Composable
fun RootScreen() {
    val context = LocalContext.current
    val store = remember(context) { WristbandStore.preferences(context) }

    var wristband by remember { mutableStateOf(WristbandStore.load(store)) }
    // Android reports no "not determined" state for a runtime permission, so the app
    // keeps its own flag. It is `true` until `LocationPrompt` has been answered once.
    var owesLocationAsk by remember { mutableStateOf(LocationPrompt.isOwed(LocationPrompt.hasBeenAsked(store))) }
    var venue by remember { mutableStateOf<Venue?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Keyed on the wristband: a new id re-runs this and `follow()` re-attaches the relay
    // provider without restarting the SDK. The key is `null` while the location prompt is
    // on screen, so the SDK and its foreground service start only after it is answered.
    LaunchedEffect(if (owesLocationAsk) null else wristband) {
        val band = wristband ?: return@LaunchedEffect
        if (owesLocationAsk) return@LaunchedEffect
        try {
            val running = venue
            if (running != null) {
                running.follow(band)
                return@LaunchedEffect
            }
            val token = VenueConfiguration.token ?: throw VenueConfiguration.SetupIncomplete()
            val started = Venue.start(context, token)
            started.follow(band)
            venue = started
        } catch (error: Exception) {
            failure = error.message ?: error.toString()
        }
    }

    // Android reports no permission change to a running app. A grant changed in the
    // system settings reaches the SDK when the app returns to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(venue) {
        val running = venue ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { running.sdk.refreshPermissions() }
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

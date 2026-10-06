//
//  MainActivity.kt
//  BlueiotMinimal
//
//  The single Activity. It shows, in order, the wristband prompt, the location prompt
//  and the venue map, with the wristband session's state on top of the map. Product
//  screens replace or sit beside `VenueMapScreen`.
//
package io.proximi.blueiot.minimal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.proximi.sdk.Proximiio
import io.proximi.sdk.ProximiioDiagnosticsEventKind
import io.proximi.sdk.recordDiagnosticsEvent
import io.proximi.sdk.refreshPermissions
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugPositionSource.readLaunch(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }

    // Records the app leaving and returning to the screen in the diagnostics log. The SDK
    // does not observe it itself. This is the app's only Activity, so its start and stop
    // are the app's.
    override fun onStart() {
        super.onStart()
        Proximiio.recordDiagnosticsEvent(ProximiioDiagnosticsEventKind.state, "scene: foreground")
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            Proximiio.recordDiagnosticsEvent(ProximiioDiagnosticsEventKind.state, "scene: background")
        }
    }
}

/**
 * Shows the wristband prompt while no session is active, then the location prompt on
 * first run, then the map. A returning visitor with an active session opens the map
 * directly.
 */
@Composable
fun RootScreen() {
    val context = LocalContext.current
    val store = remember(context) { WristbandStore.preferences(context) }

    // The binding state decides whether the wristband prompt is shown; the app stores no
    // wristband id of its own. The first access creates the session, which runs
    // `restore()`.
    val session = remember(context) { BlueiotMinimalApplication.wristband(context) }
    val state by session.state.collectAsStateWithLifecycle()
    val isFollowing by session.isFollowing.collectAsStateWithLifecycle()

    // Android reports no "not determined" state for a runtime permission, so the app
    // keeps its own flag. It is `true` until `LocationPrompt` has been answered once.
    var owesLocationAsk by remember { mutableStateOf(LocationPrompt.isOwed(LocationPrompt.hasBeenAsked(store))) }
    var venue by remember { mutableStateOf<Venue?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    // The SDK starts once, when a session is active and `LocationPrompt` is answered, so
    // the SDK and its foreground service start only after that screen. The SDK keeps
    // running when the session ends; the binding's provider then delivers nothing.
    LaunchedEffect(isFollowing && !owesLocationAsk) {
        if (!isFollowing || owesLocationAsk || venue != null) return@LaunchedEffect
        try {
            val token = VenueConfiguration.token ?: throw VenueConfiguration.SetupIncomplete()
            val binding = session.binding ?: throw VenueConfiguration.SetupIncomplete()
            venue = Venue.start(context, token, binding)
            failure = null
        } catch (error: CancellationException) {
            throw error
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
        !isFollowing -> WristbandPrompt(notice = WristbandCopy.endNotice(state))

        owesLocationAsk ->
            LocationPrompt(onAnswered = {
                LocationPrompt.markAsked(store)
                owesLocationAsk = false
            })

        started != null ->
            Box(modifier = Modifier.fillMaxSize()) {
                // The sheet behind the map's long press binds through the same session,
                // so nothing is left to do when it reports a new band.
                VenueMapScreen(venue = started, wristband = session.lastLabel, onSaveWristband = {})
                WristbandStatus(
                    session = session,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                )
            }

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
        // No SDK is running here, so the report holds the recorded log alone. That log
        // is what shows why the start failed.
        SupportReportButton(sdk = null)
    }
}

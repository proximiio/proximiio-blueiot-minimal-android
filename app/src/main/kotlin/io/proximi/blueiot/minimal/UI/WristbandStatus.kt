//
//  WristbandStatus.kt
//  BlueiotMinimal
//
//  The wristband session on the map: its state from `stateChanges()` and the End visit
//  action. `RootScreen` (`MainActivity.kt`) draws it at the top of the map.
//
package io.proximi.blueiot.minimal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.proximi.sdk.blueiot.binding.BlueiotBindingLink
import io.proximi.sdk.blueiot.binding.BlueiotBindingSignal
import io.proximi.sdk.blueiot.binding.BlueiotBindingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun WristbandStatus(
    session: WristbandSession,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val status = WristbandCopy.status(state) ?: return
    val scope = rememberCoroutineScope()

    var confirmsEnd by remember { mutableStateOf(false) }
    var isEnding by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Online with the band heard by the venue.
    val isOnline =
        (state as? BlueiotBindingState.Active)?.let {
            it.link == BlueiotBindingLink.ONLINE && it.signal == BlueiotBindingSignal.Ok
        } == true

    Surface(modifier = modifier, shape = CircleShape, tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(if (isOnline) Color(0xFF34C759) else Color(0xFFFF9500), CircleShape))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { confirmsEnd = true }, enabled = !isEnding) { Text("End visit") }
        }
    }

    if (confirmsEnd) {
        AlertDialog(
            onDismissRequest = { confirmsEnd = false },
            title = { Text("End your visit?") },
            text = { Text("This phone stops following the wristband.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmsEnd = false
                    isEnding = true
                    // On success the state becomes `Ended(UserEnded)` and `RootScreen`
                    // shows the wristband prompt.
                    scope.launch {
                        try {
                            session.end()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            failure = WristbandCopy.message(error)
                        }
                        isEnding = false
                    }
                }) { Text("End visit", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmsEnd = false }) { Text("Cancel") } },
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text("The visit was not ended") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text("OK") } },
        )
    }
}

//
//  WristbandPrompt.kt
//  BlueiotMinimal
//
//  The wristband prompt. Shown full-screen while no session is active, and as a bottom
//  sheet to connect another band; the same composable in both cases, so there is no
//  settings screen. See `VenueMapScreen` for how the sheet is reached.
//
//  Connect binds the typed label through `WristbandSession`. Before the bind it asks for
//  location when the relay-api needs the phone's location for a take-over.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.proximi.map.kit.MapAttribution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * @param current what the field opens with. Empty means the label of the last
 *   successful bind in this process, if any.
 * @param notice why the previous session ended, or `null`.
 * @param credits `ProximiioMapSession.attributions` for the loaded style. The map hides
 *   MapLibre's attribution control (`VenueMapScreen`), so an app that hides it must
 *   show the credits itself. Empty on the full-screen prompt, when no style is loaded.
 * @param onCancel `null` on the full-screen prompt, when there is nothing to return to.
 * @param footer shown under the credits. The map's sheet puts the support report
 *   button here.
 * @param onSave called after a successful bind, with the label that was bound.
 */
@Composable
fun WristbandPrompt(
    current: String = "",
    notice: String? = null,
    credits: List<MapAttribution> = emptyList(),
    onCancel: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
    onSave: (WristbandId) -> Unit = {},
) {
    val context = LocalContext.current
    val session = remember(context) { BlueiotMinimalApplication.wristband(context) }
    val scope = rememberCoroutineScope()

    var text by rememberSaveable(current) { mutableStateOf(current.ifEmpty { session.lastLabel }) }
    var pendingAsk by remember { mutableStateOf<WristbandSession.LocationAsk?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val label = WristbandId.of(text)

    // The system dialog reports its answer to a callback; the channel turns it into a
    // suspension point for `connect`.
    val answers = remember { Channel<Unit>(Channel.CONFLATED) }
    val locationDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            answers.trySend(Unit)
        }

    // Reads the relay-api policy (cached by the SDK) and the grants, so the line under
    // the field names the dialog before Connect shows it.
    LaunchedEffect(session) { pendingAsk = session.locationAsk() }

    // Asks for location when needed, then binds. A refused location does not stop the
    // bind: a band at the reception desk binds without one.
    fun connect() {
        val band = label ?: return
        if (isConnecting) return
        isConnecting = true
        failure = null
        scope.launch {
            session.locationAsk()?.let {
                locationDialog.launch(WristbandSession.locationPermissions)
                answers.receive()
            }
            pendingAsk = null
            try {
                session.bind(band.label)
                isConnecting = false
                onSave(band)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                isConnecting = false
                failure = WristbandCopy.message(error)
                pendingAsk = session.locationAsk()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your wristband", style = MaterialTheme.typography.titleMedium)

        notice?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Wristband number") },
            placeholder = { Text("1234567890") },
            singleLine = true,
            enabled = !isConnecting,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            supportingText = {
                Text(pendingAsk?.let(WristbandCopy::explanation) ?: "The number printed on your band.")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (isConnecting) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Connecting…")
            }
        } else {
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        VenueConfiguration.missing?.let { hint ->
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }

        // MapLibre strips the leading "©" from each credit on the way in; it is added
        // back here.
        if (credits.isNotEmpty()) {
            HorizontalDivider()
            Text("Map credits", style = MaterialTheme.typography.labelLarge)
            credits.forEach { credit ->
                val url = credit.url
                if (url != null) {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                        Text("© " + credit.title)
                    }
                } else {
                    Text("© " + credit.title, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        footer()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
            Button(
                onClick = { connect() },
                enabled = label != null && !isConnecting && session.binding != null,
            ) { Text("Connect") }
        }
    }
}

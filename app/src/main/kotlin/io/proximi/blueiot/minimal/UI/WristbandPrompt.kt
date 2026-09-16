//
//  WristbandPrompt.kt
//  BlueiotMinimal
//
//  The one thing this app ever asks a person for.
//
//  It is shown full-screen on first run and as a sheet when somebody changes the
//  band — the same composable either way, which is why there is no settings screen.
//  See `VenueMapScreen` for how the sheet is reached.
//
package io.proximi.blueiot.minimal

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.proximi.map.kit.MapAttribution

/**
 * @param current what is in the field when it opens: empty on first run, the current
 *   id when somebody is changing it.
 * @param credits what the map would have shown behind its attribution ⓘ, which this
 *   app hides (`VenueMapScreen`): the loaded style's credits. Empty on first run —
 *   no map yet.
 * @param onCancel `null` on first run — there is nothing to go back to.
 */
@Composable
fun WristbandPrompt(
    current: String = "",
    credits: List<MapAttribution> = emptyList(),
    onCancel: (() -> Unit)? = null,
    onSave: (WristbandId) -> Unit,
) {
    var text by rememberSaveable(current) { mutableStateOf(current) }
    val parsed = WristbandId.of(text)
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your wristband", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Wristband number") },
            placeholder = { Text("1234567890") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            supportingText = {
                // The echo is the safety net, not the validator: "that is not an id"
                // cannot tell you *which* tag was understood, and that is the question
                // somebody who mistyped a digit actually has.
                if (parsed != null) {
                    Text("Following tag ${parsed.canonical} (${parsed.hexadecimal}).")
                } else {
                    Text("The number printed on your band.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        VenueConfiguration.missing?.let { hint ->
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }

        // Nothing when the style declares nothing. MapLibre strips the leading "©"
        // from each credit on the way in; it goes back on here.
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
            Button(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null,
            ) { Text("Done") }
        }
    }
}

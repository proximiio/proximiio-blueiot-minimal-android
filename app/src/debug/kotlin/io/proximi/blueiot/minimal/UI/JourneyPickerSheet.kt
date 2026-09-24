//
//  JourneyPickerSheet.kt
//  BlueiotMinimal
//
//  Debug builds only. The journey picker and the playback controls, in the top-start
//  corner of the map. The picker lists the organisation's journeys
//  (`Proximiio.journeys()`) and plays one in place of the cloud relay; the controls pause,
//  resume and stop it. Stop attaches the relay again. The rules are in
//  `JourneyPlayback.kt`. This file is in the `debug` source set, so release builds do
//  not contain it.
//
package io.proximi.blueiot.minimal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.proximi.sdk.Proximiio
import io.proximi.sdk.journey.ProximiioJourney
import io.proximi.sdk.journeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The picker button, or the playback controls while a journey plays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyPlaybackOverlay(
    venue: Venue,
    playback: JourneyPlaybackController,
    modifier: Modifier = Modifier,
) {
    val session by playback.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isPicking by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (session.showsControls) {
            PlaybackControls(
                session = session,
                onTogglePause = { scope.launch { playback.togglePause() } },
                onStop = { scope.launch { DebugPositionSource.stopJourney(venue) } },
            )
            // Elapsed time and the finished state come from the provider's diagnostics.
            // The loop ends when the controls leave the composition.
            LaunchedEffect(playback) {
                while (true) {
                    playback.refresh()
                    delay(REFRESH_MILLIS)
                }
            }
        } else {
            FilledTonalIconButton(onClick = { isPicking = true }) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = "Play a journey")
            }
        }
    }

    if (isPicking) {
        ModalBottomSheet(onDismissRequest = { isPicking = false }) {
            JourneyPickerSheet(sdk = venue.sdk, onClose = { isPicking = false }) { journey, options ->
                isPicking = false
                scope.launch { DebugPositionSource.playJourney(venue, journey, options) }
            }
        }
    }
}

private const val REFRESH_MILLIS = 1_000L

@Composable
private fun PlaybackControls(
    session: JourneyPlaybackSession,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.status, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (session.canPause || session.canResume) {
                IconButton(onClick = onTogglePause) {
                    if (session.canPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause journey")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume journey")
                    }
                }
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop journey") }
        }
    }
}

/**
 * The organisation's journeys. A playable one opens the speed and loop options; an
 * unplayable one is disabled and shows its `validationFailure()`.
 */
@Composable
fun JourneyPickerSheet(
    sdk: Proximiio,
    onClose: () -> Unit,
    onPlay: (ProximiioJourney, JourneyPlaybackOptions) -> Unit,
) {
    var content by remember { mutableStateOf<JourneyPickerContent>(JourneyPickerContent.Loading) }
    var chosen by remember { mutableStateOf<JourneyPickerRow?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        content = JourneyPickerContent.Loading
        content =
            try {
                JourneyPickerContent.from(Result.success(sdk.journeys()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                JourneyPickerContent.from(Result.failure(error))
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val row = chosen
            if (row != null) {
                IconButton(onClick = { chosen = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to journeys")
                }
            }
            Text(
                row?.title ?: "Play a journey",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            TextButton(onClick = onClose) { Text("Close") }
        }

        val row = chosen
        if (row != null) {
            JourneyPlaybackOptionsView(row = row) { options -> onPlay(row.journey, options) }
            return@Column
        }

        when (val shown = content) {
            JourneyPickerContent.Loading ->
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            JourneyPickerContent.Empty ->
                Message(title = "No journeys", body = "The organisation has no journeys. Draw one in MapTap.")

            is JourneyPickerContent.Failed ->
                Message(title = "Cannot load journeys", body = shown.message) {
                    TextButton(onClick = { attempt++ }) { Text("Try again") }
                }

            is JourneyPickerContent.Loaded ->
                LazyColumn {
                    items(shown.rows, key = { it.id }) { item ->
                        JourneyRow(item) { chosen = item }
                        HorizontalDivider()
                    }
                }
        }
    }
}

@Composable
private fun JourneyRow(
    row: JourneyPickerRow,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = row.isPlayable, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.isPlayable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        row.failure?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Message(
    title: String,
    body: String,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}

/** Speed and loop for one journey, and the Play button. */
@Composable
private fun JourneyPlaybackOptionsView(
    row: JourneyPickerRow,
    onPlay: (JourneyPlaybackOptions) -> Unit,
) {
    var options by remember(row.id) { mutableStateOf(JourneyPlaybackOptions()) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Speed", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JourneyPlaybackOptions.pickerSpeeds.forEach { speed ->
                FilterChip(
                    selected = options.speed == speed,
                    onClick = { options = options.copy(speed = speed) },
                    label = { Text(JourneyFormat.speed(speed)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Loop", modifier = Modifier.weight(1f))
            Switch(checked = options.loops, onCheckedChange = { options = options.copy(loops = it) })
        }
        Button(onClick = { onPlay(options) }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
        Text(
            "Replaces the cloud relay until Stop. Stop attaches the relay again.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

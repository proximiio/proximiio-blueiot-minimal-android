//
//  JourneyBar.kt
//  BlueiotMinimal
//
//  The visit UI: which stop is in hand, what is left of the visit, and the controls
//  that change the plan.
//
//  `JourneyNavigator` owns the routing. It computes each leg from the live position,
//  draws and follows it through the same session as the map, and measures what
//  remains. None of that is in this file. With `deviationPolicy = ASK_APP` it does not
//  re-route a visitor who leaves the leg; this screen asks the visitor instead
//  (`DeviationPrompt`).
//
//  Neither the library nor this screen reorders a visit on its own: `proposeOrder`
//  measures an order and `apply` is what applies it, and `replanFromHere` runs only
//  when the visitor asks for it.
//
package io.proximi.blueiot.minimal

import android.content.SharedPreferences
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.proximi.map.core.Journey
import io.proximi.map.core.JourneyDeviationPolicy
import io.proximi.map.core.JourneyEvent
import io.proximi.map.core.JourneyOrderProposal
import io.proximi.map.core.JourneyOverlayStyle
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapLevelFormat
import io.proximi.map.live.JourneyNavigator
import io.proximi.map.live.ProximiioMapSession
import io.proximi.sdk.amenities
import io.proximi.sdk.core.model.ProximiioCoordinate
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val STOP_ROW_HEIGHT = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyBar(
    session: ProximiioMapSession,
    journey: Journey,
    places: List<VenuePoi>,
    store: SharedPreferences,
    modifier: Modifier = Modifier,
    /** Called when the visit is over, so the map screen can restore its search bar. */
    onEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // No automatic re-route when the visitor leaves the leg. The drawn leg stays until
    // the visitor answers the prompt. The thresholds are the library defaults
    // (`JourneyDeviationRules`).
    val navigator =
        remember(session, journey) {
            JourneyNavigator(session, journey).apply { deviationPolicy = JourneyDeviationPolicy.ASK_APP }
        }

    val plan by navigator.journey.collectAsStateWithLifecycle()
    val overview by navigator.overview.collectAsStateWithLifecycle()
    val guidance by navigator.guidance.collectAsStateWithLifecycle()
    val position by session.position.collectAsStateWithLifecycle()

    /** The venue's amenity kind titles, read once. */
    var amenityTitles by remember { mutableStateOf(emptyMap<String, String>()) }
    /** The detours on offer, named. An empty list shows no button. */
    var detours by remember { mutableStateOf(emptyList<Detour>()) }
    var isShowingPlan by remember { mutableStateOf(false) }
    /** The open deviation prompt, or `null`. Set from `navigator.events`. */
    var prompt by remember(navigator) { mutableStateOf<DeviationPrompt?>(null) }

    // `events` delivers only the events emitted after collection starts. Collection
    // ends when this effect leaves the composition.
    LaunchedEffect(navigator) {
        navigator.events.collect { event -> prompt = DeviationPrompt.after(event, prompt) }
    }

    LaunchedEffect(navigator) {
        // Nothing is drawn until the first position, because the leg is computed from
        // where the visitor is.
        navigator.start()
        // The only extra download in the app, and only for a visitor who started a visit.
        // A failure costs the detours and nothing else. The SDK exposes no
        // single-amenity lookup on Android, so the app builds the map once.
        amenityTitles =
            runCatching { session.sdk?.amenities().orEmpty() }
                .getOrDefault(emptyList())
                .mapNotNull { amenity -> amenity.title?.let { amenity.id to it } }
                .toMap()
    }

    // Re-offered as the visitor moves: "nearest" is measured from the live position.
    LaunchedEffect(position?.coordinate, amenityTitles, places) {
        detours = offers(places, position?.coordinate, amenityTitles)
    }

    // `Journey` carries each stop's state, so saving it on every change is the whole of
    // restoring a visit across launches.
    LaunchedEffect(plan) { JourneyStore.save(plan, store) }

    DisposableEffect(navigator) { onDispose { navigator.end() } }

    Surface(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // `overview` is re-measured as soon as the plan changes, so the totals are
            // complete rather than filled in as the visitor walks. A leg that could not
            // be routed is listed in `unreachableStopIds` rather than left out of the
            // sum.
            val stop = navigator.activeStop
            when {
                stop != null ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (navigator.detourStop == null) stop.title else "${stop.title} — on the way",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            val parts =
                                buildList {
                                    add("${overview.remainingStops.size} to go")
                                    add("${overview.remainingMeters.roundToInt()} m")
                                    add("${max(1, (overview.etaSeconds / 60).roundToInt())} min")
                                    // The library keeps a stop it could not route to, so
                                    // the count is shown rather than silently dropped.
                                    if (overview.unreachableStopIds.isNotEmpty()) {
                                        add("${overview.unreachableStopIds.size} unreachable")
                                    }
                                }
                            Text(
                                parts.joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PlanButton { isShowingPlan = true }
                    }

                navigator.isFinished ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your visit is done.", modifier = Modifier.weight(1f))
                        // Adding a stop revives a finished visit: the library makes the
                        // new one active and draws its leg. The way into the plan must
                        // therefore stay reachable after the last stop.
                        PlanButton { isShowingPlan = true }
                        TextButton(onClick = onEnd) { Text("Finish") }
                    }

                else ->
                    Text(
                        "Waiting for your wristband's first position.",
                        style = MaterialTheme.typography.bodySmall,
                    )
            }

            GuidanceLine(guidance)

            prompt?.let { open ->
                DeviationPromptRow(
                    prompt = open,
                    onResume = {
                        prompt = null
                        scope.launch { navigator.resumeJourney() }
                    },
                    onReplan = {
                        prompt = null
                        scope.launch { navigator.replanFromHere() }
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigator.hasArrived) {
                    // `JourneyRules.advance` defaults to `Advance.Manual`, so arrival
                    // does not move the visit on. This button calls `advance()`.
                    Button(onClick = { scope.launch { navigator.advance() } }) { Text("Continue") }
                }
                if (navigator.detourStop == null) {
                    DetourMenu(detours) { poi -> scope.launch { navigator.detour(JourneyStop(poi)) } }
                    TextButton(onClick = { scope.launch { navigator.skip() } }) { Text("Skip") }
                } else {
                    TextButton(onClick = { scope.launch { navigator.cancelDetour() } }) {
                        Text("Back to the plan")
                    }
                }
            }
        }
    }

    if (isShowingPlan) {
        ModalBottomSheet(onDismissRequest = { isShowingPlan = false }) {
            JourneyPlanSheet(
                navigator = navigator,
                places = places,
                onDismiss = { isShowingPlan = false },
                onEnd = {
                    isShowingPlan = false
                    onEnd()
                },
            )
        }
    }
}

/**
 * The visitor's two answers to a deviation. Both end a live detour first and clear the
 * deviation. `resumeJourney()` routes to the stop the plan is on. `replanFromHere()`
 * reorders the remaining stops from the visitor's position, applies the order and
 * routes to its first stop.
 */
@Composable
private fun DeviationPromptRow(
    prompt: DeviationPrompt,
    onResume: () -> Unit,
    onReplan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(prompt.message, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onResume) { Text("Back to my route") }
            TextButton(onClick = onReplan) { Text("New route from here") }
        }
    }
}

/**
 * The prompt shown when the visitor has left the visit's route, and the rule that opens
 * and closes it.
 *
 * Three [JourneyEvent]s open it: `FarFromRoute`, `OffRouteTooLong` and
 * `DetourOverstayed`. `LeftRoute` does not: a visitor a few metres off the route is not
 * asked. `ReturnedToRoute` and `JourneyFinished` close it. `DetourEnded` closes a prompt
 * opened by `DetourOverstayed`. Other events leave it as it is. Each event arrives once
 * per episode.
 */
data class DeviationPrompt(
    val reason: Reason,
    val message: String,
) {
    enum class Reason {
        FAR_FROM_ROUTE,
        OFF_ROUTE_TOO_LONG,
        DETOUR_OVERSTAYED,
    }

    companion object {
        /** The prompt after [event], given the prompt on screen. `null` shows none. */
        fun after(
            event: JourneyEvent,
            showing: DeviationPrompt?,
        ): DeviationPrompt? =
            when (event) {
                is JourneyEvent.FarFromRoute ->
                    DeviationPrompt(
                        Reason.FAR_FROM_ROUTE,
                        "You are ${event.distance.roundToInt()} m from your route.",
                    )
                is JourneyEvent.OffRouteTooLong ->
                    DeviationPrompt(
                        Reason.OFF_ROUTE_TOO_LONG,
                        "You have been off your route for ${minutes(event.duration)} min.",
                    )
                is JourneyEvent.DetourOverstayed ->
                    DeviationPrompt(
                        Reason.DETOUR_OVERSTAYED,
                        "You left your route for ${event.stop.title} ${minutes(event.duration)} min ago.",
                    )
                JourneyEvent.ReturnedToRoute, JourneyEvent.JourneyFinished -> null
                is JourneyEvent.DetourEnded -> if (showing?.reason == Reason.DETOUR_OVERSTAYED) null else showing
                else -> showing
            }

        /** Whole minutes, at least 1. */
        private fun minutes(seconds: Double): Int = max(1, (seconds / 60).roundToInt())
    }
}

@Composable
private fun PlanButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Outlined.ViewList, contentDescription = "The plan") }
}

/**
 * `JourneyNavigator.detour(stop)` inserts a stop before the one being walked to and
 * routes there immediately. The plan resumes from wherever the visitor ends up, not
 * from where they left it.
 */
@Composable
private fun DetourMenu(
    detours: List<Detour>,
    onPick: (VenuePoi) -> Unit,
) {
    if (detours.isEmpty()) return
    var isOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { isOpen = true }) { Text("Stop off") }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            detours.forEach { offer ->
                DropdownMenuItem(
                    text = { Text(offer.title) },
                    onClick = {
                        isOpen = false
                        onPick(offer.poi)
                    },
                )
            }
        }
    }
}

private data class Detour(
    val id: String,
    val title: String,
    val poi: VenuePoi,
)

/**
 * Which amenity kinds a venue has comes from its own data
 * ([VenuePoi.nearestByAmenity]). What each kind is called comes from the SDK's amenity
 * catalogue. An id the catalogue cannot name is left out rather than shown as a uuid;
 * this app keeps no titles of its own.
 */
private fun offers(
    places: List<VenuePoi>,
    here: io.proximi.map.core.MapCoordinate?,
    amenityTitles: Map<String, String>,
): List<Detour> {
    if (here == null) return emptyList()
    val from = ProximiioCoordinate(latitude = here.latitude, longitude = here.longitude)
    return VenuePoi
        .nearestByAmenity(places, from)
        .mapNotNull { (amenityId, poi) ->
            amenityTitles[amenityId]?.let { Detour(amenityId, it, poi) }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Detour::title))
}

/** The plan: what is left, in what order, and the ways to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyPlanSheet(
    navigator: JourneyNavigator,
    places: List<VenuePoi>,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val plan by navigator.journey.collectAsStateWithLifecycle()
    val overview by navigator.overview.collectAsStateWithLifecycle()
    var proposal by remember { mutableStateOf<JourneyOrderProposal?>(null) }
    var showsWholePlan by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    /** What the last "+" could not add. Replaced by the next one. */
    var note by remember { mutableStateOf<String?>(null) }

    val reorderable = navigator.reorderableStops

    // `proposeOrder` measures every walk between the remaining stops and returns a
    // proposal; it never applies itself. It is re-measured whenever those stops change,
    // because `apply` ignores a proposal that no longer describes the journey.
    LaunchedEffect(reorderable.map { it.id }) { proposal = navigator.proposeOrder() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your visit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { isAdding = true }, enabled = places.isNotEmpty()) {
                Icon(Icons.Outlined.Add, contentDescription = "Add places")
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }

        proposal?.takeIf { it.isImprovement }?.let { offer ->
            TextButton(onClick = {
                scope.launch {
                    navigator.apply(offer)
                    proposal = null
                }
            }) { Text("Save ${offer.savedMeters.roundToInt()} m by reordering") }
            Text(
                "Measured, not applied. Nothing moves until you tap it, and the stop you are " +
                    "walking to stays where it is.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text("Still to walk", style = MaterialTheme.typography.labelLarge)
        // Compose has no `.onMove`, so the drag is the app's. The rows are
        // `JourneyNavigator.reorderableStops`, the list `move(stopId, toIndex)` indexes
        // into, so the app holds no second copy of which stops may move.
        ReorderableStops(
            stops = reorderable,
            label = { label(it, overview.unreachableStopIds) },
            onMove = { stopId, toIndex -> scope.launch { navigator.move(stopId, toIndex) } },
        )

        val seen = plan.stops.filter { it.state == JourneyStop.State.DONE || it.state == JourneyStop.State.SKIPPED }
        if (seen.isNotEmpty()) {
            HorizontalDivider()
            Text("Behind you", style = MaterialTheme.typography.labelLarge)
            seen.forEach { StopRow(it, label(it, overview.unreachableStopIds), handle = false) }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Off by default. The overlay draws the rest of the plan under the leg in
            // hand.
            Text("Show the whole plan on the map", modifier = Modifier.weight(1f))
            Switch(
                checked = showsWholePlan,
                onCheckedChange = {
                    showsWholePlan = it
                    navigator.session.journeyOverlayStyle = if (it) JourneyOverlayStyle.VENUE else null
                },
            )
        }
        TextButton(onClick = onEnd) { Text("End the visit") }
    }

    // The same search sheet the visit was planned in. `add` puts each pick after
    // everything still to be walked and leaves the leg in hand alone. It returns `false`
    // for a place the plan already holds, which is reported rather than dropped.
    if (isAdding) {
        ModalBottomSheet(onDismissRequest = { isAdding = false }) {
            PoiSearchSheet(pois = places, allowsMultiple = true, adds = true) { picked ->
                isAdding = false
                scope.launch {
                    val already = picked.filterNot { navigator.add(JourneyStop(it)) }.map(VenuePoi::title)
                    note = if (already.isEmpty()) null else "Already in your visit: ${already.joinToString(", ")}."
                }
            }
        }
    }
}

/**
 * Drag a row to move it. The index it lands on is the index
 * `JourneyNavigator.move(stopId, toIndex)` takes, which is why the rows are a plain
 * column of one fixed height rather than a lazy list of measured ones.
 */
@Composable
private fun ReorderableStops(
    stops: List<JourneyStop>,
    label: (JourneyStop) -> String,
    onMove: (String, Int) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { STOP_ROW_HEIGHT.toPx() }
    stops.forEachIndexed { index, stop ->
        var dragged by remember(stop.id) { mutableFloatStateOf(0f) }
        StopRow(
            stop = stop,
            label = label(stop),
            handle = true,
            modifier =
                Modifier.pointerInput(stop.id, stops.size) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, amount ->
                            change.consume()
                            dragged += amount.y
                        },
                        onDragEnd = {
                            val target = (index + (dragged / rowHeightPx).roundToInt()).coerceIn(0, stops.size - 1)
                            dragged = 0f
                            if (target != index) onMove(stop.id, target)
                        },
                        onDragCancel = { dragged = 0f },
                    )
                },
        )
    }
}

@Composable
private fun StopRow(
    stop: JourneyStop,
    label: String,
    handle: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(STOP_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stop.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (stop.state == JourneyStop.State.SKIPPED) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        if (handle) Icon(Icons.Outlined.DragHandle, contentDescription = "Drag to reorder")
    }
}

private fun label(
    stop: JourneyStop,
    unreachable: List<String>,
): String {
    if (unreachable.contains(stop.id)) return "No route to this one"
    return when (stop.state) {
        JourneyStop.State.ACTIVE -> "Walking there"
        JourneyStop.State.REACHED -> "You are here"
        JourneyStop.State.DONE -> "Seen"
        JourneyStop.State.SKIPPED -> "Skipped"
        JourneyStop.State.PENDING ->
            stop.floor?.let { "Level ${MapLevelFormat.trimmed(it)}" } ?: "In the venue"
    }
}

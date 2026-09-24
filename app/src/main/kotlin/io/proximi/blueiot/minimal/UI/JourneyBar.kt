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
//  The library does not reorder a visit on its own. This screen applies one order
//  without a tap: the shortest order from the visitor's position, once, for a new visit,
//  and says so on the bar. Without a position it waits for the first one. After that
//  `proposeOrder` measures an order, a tap on it calls `apply`, and `replanFromHere` runs
//  only when the visitor asks for it.
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.runtime.rememberUpdatedState
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
import io.proximi.map.core.JourneyOrderOrigin
import io.proximi.map.core.JourneyOrderProposal
import io.proximi.map.core.JourneyOverlayStyle
import io.proximi.map.core.JourneyStop
import io.proximi.map.core.MapLevelFormat
import io.proximi.map.live.JourneyNavigator
import io.proximi.map.live.ProximiioMapSession
import io.proximi.sdk.Proximiio
import io.proximi.sdk.amenities
import io.proximi.sdk.amenity
import io.proximi.sdk.core.model.ProximiioCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val STOP_ROW_HEIGHT = 56.dp

/** How long the order note stays on the bar. */
private const val ORDER_NOTE_MILLIS = 8_000L

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

    /** `true` once the SDK's amenity catalogue has been read, or its download failed. */
    var isCatalogueRead by remember { mutableStateOf(false) }
    /** The detours on offer, named. An empty list shows no button. */
    var detours by remember { mutableStateOf(emptyList<Detour>()) }
    var isShowingPlan by remember { mutableStateOf(false) }
    /** The open deviation prompt, or `null`. Set from `navigator.events`. */
    var prompt by remember(navigator) { mutableStateOf<DeviationPrompt?>(null) }
    /** The line under the header after a new visit is ordered (`StartOrder`). */
    var orderNote by remember(navigator) { mutableStateOf<String?>(null) }
    /** `true` while a new visit waits for its first position to be ordered. */
    var ordersOnFirstFix by remember(navigator) { mutableStateOf(false) }
    // Ends the navigator once, before `onEnd`. `end()` sets `session.guidanceRules` to
    // `null` and `onEnd` sets the single-route rules again, so nothing may end the
    // navigator after `onEnd`.
    val ending = remember(navigator) { VisitEnding(navigator::end) }
    val currentOnEnd by rememberUpdatedState(onEnd)
    val endVisit = { ending.end(currentOnEnd) }

    /**
     * Puts a new visit in the shortest order from the visitor's position and sets
     * `orderNote`.
     *
     * `proposeOrder(VISITOR)` measures from the session's latest position, before
     * `start()` too, and can move the first stop. Without a position it returns `null`;
     * the tap order is kept and this runs again on the first position. `apply` refuses a
     * proposal after the plan or the live stop changed (the first position activates a
     * stop), so a refused proposal is measured once more.
     */
    suspend fun orderNewVisit() {
        if (!StartOrder.isOwed(navigator.journey.value)) {
            ordersOnFirstFix = false
            return
        }
        if (session.position.value == null) {
            ordersOnFirstFix = true
            orderNote = StartOrder.WAITING_NOTE
            return
        }
        ordersOnFirstFix = false
        repeat(2) {
            val proposal = navigator.proposeOrder(JourneyOrderOrigin.VISITOR)
            val applied = proposal != null && proposal.isImprovement && navigator.apply(proposal)
            orderNote = StartOrder.note(proposal, applied)
            if (orderNote != null || proposal == null) return
        }
    }

    // `events` delivers only the events emitted after collection starts. Collection
    // ends when this effect leaves the composition.
    LaunchedEffect(navigator) {
        navigator.events.collect { event -> prompt = DeviationPrompt.after(event, prompt) }
    }

    LaunchedEffect(navigator) {
        // A new visit (every stop pending) is put in the shortest order before it
        // starts. A restored visit that has already started is not reordered.
        if (navigator.journey.value.stops.all { it.state == JourneyStop.State.PENDING }) {
            orderNewVisit()
        }
        // Nothing is drawn until the first position, because the leg is computed from
        // where the visitor is.
        navigator.start()
        // The only extra download in the app, and only for a visitor who started a visit.
        // `amenities()` reads the stored catalogue first: with one stored it reaches no
        // network and cannot throw. A failure costs the detours and nothing else.
        try {
            session.sdk?.amenities()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // No catalogue: `amenity(id)` then names nothing and no detour is offered.
        }
        isCatalogueRead = true
    }

    // Re-offered as the visitor moves: "nearest" is measured from the live position.
    LaunchedEffect(position?.coordinate, isCatalogueRead, places) {
        val sdk = session.sdk
        detours = if (isCatalogueRead && sdk != null) offers(sdk, places, position?.coordinate) else emptyList()
    }

    // `Journey` carries each stop's state, so saving it on every change is the whole of
    // restoring a visit across launches.
    LaunchedEffect(plan) { JourneyStore.save(plan, store) }

    // The first position of a new visit that was started without one.
    LaunchedEffect(navigator, position == null) {
        if (ordersOnFirstFix && position != null) orderNewVisit()
    }

    // The note stays 8 s. The waiting note stays until the first position.
    LaunchedEffect(orderNote) {
        if (orderNote == null || ordersOnFirstFix) return@LaunchedEffect
        delay(ORDER_NOTE_MILLIS)
        orderNote = null
    }

    // After `endVisit` this does nothing: a second `end()` would switch the map screen's
    // single-route guidance off.
    DisposableEffect(navigator) { onDispose { ending.disposed() } }

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
                            val isStopOff = navigator.detourStop != null
                            Text(
                                if (isStopOff) StopOff.title(stop) else stop.title,
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
                                if (isStopOff) {
                                    StopOff.status(
                                        hasArrived = navigator.hasArrived,
                                        next = navigator.reorderableStops.firstOrNull()?.title,
                                    )
                                } else {
                                    parts.joinToString(" · ")
                                },
                                maxLines = 2,
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
                        TextButton(onClick = endVisit) { Text("Finish") }
                    }

                else ->
                    Text(
                        "Waiting for your wristband's first position.",
                        style = MaterialTheme.typography.bodySmall,
                    )
            }

            orderNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

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
                    DetourMenu(detours, goingTo = navigator.activeStop?.title) { poi ->
                        scope.launch { navigator.detour(JourneyStop(poi)) }
                    }
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
                    endVisit()
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
 * A stop-off: the nearest place of one kind, walked to before the planned stop.
 * `JourneyNavigator.detour(stop)` inserts it before the active stop and routes to it
 * immediately. **Continue** at the stop-off, or **Back to the plan** on the way, returns
 * to the plan from the visitor's current position. Each item names the kind and the
 * place.
 */
@Composable
private fun DetourMenu(
    detours: List<Detour>,
    goingTo: String?,
    onPick: (VenuePoi) -> Unit,
) {
    if (detours.isEmpty()) return
    var isOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { isOpen = true }) { Text("Stop off") }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            Text(
                StopOff.menuHeader(goingTo),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 280.dp),
            )
            HorizontalDivider()
            detours.forEach { offer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(offer.title)
                            Text(offer.poi.title, style = MaterialTheme.typography.bodySmall)
                        }
                    },
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
 * ([VenuePoi.nearestByAmenity]). What each kind is called comes from the SDK's stored
 * amenity catalogue: `amenity(id)` reads one row, locally and offline. An id the
 * catalogue cannot name is left out rather than shown as a uuid; this app keeps no
 * titles of its own.
 */
private suspend fun offers(
    sdk: Proximiio,
    places: List<VenuePoi>,
    here: io.proximi.map.core.MapCoordinate?,
): List<Detour> {
    if (here == null) return emptyList()
    val from = ProximiioCoordinate(latitude = here.latitude, longitude = here.longitude)
    return VenuePoi
        .nearestByAmenity(places, from)
        .mapNotNull { (amenityId, poi) ->
            sdk.amenity(amenityId)?.title?.let { Detour(amenityId, it, poi) }
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
    val position by navigator.session.position.collectAsStateWithLifecycle()
    var proposal by remember { mutableStateOf<JourneyOrderProposal?>(null) }
    var isMeasuring by remember { mutableStateOf(true) }
    var showsWholePlan by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    /** What the last "+" could not add, or that a reorder was applied. Replaced by the next one. */
    var note by remember { mutableStateOf<String?>(null) }

    val reorderable = navigator.reorderableStops

    // `proposeOrder(VISITOR)` measures the remaining stops from the visitor's position,
    // the stop being walked to included, and returns a proposal. It does not apply it.
    // Without a position it returns `null`; `ACTIVE_STOP` then keeps the stop being
    // walked to first and orders the rest. `apply` refuses a proposal after the remaining
    // stops or their order change, or after the live stop changes, so the proposal is
    // measured again on each of those changes. The first position measures again too,
    // from the visitor instead of the stop being walked to.
    LaunchedEffect(
        listOf(navigator.activeStop?.id.orEmpty(), if (position == null) "no fix" else "fix") +
            reorderable.map { it.id },
    ) {
        isMeasuring = true
        proposal = navigator.proposeOrder(JourneyOrderOrigin.VISITOR)
            ?: navigator.proposeOrder(JourneyOrderOrigin.ACTIVE_STOP)
        isMeasuring = false
    }

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

        // The order row, shown whenever two or more stops can move, so the visitor sees
        // either the saving or that the order is already the shortest. `OrderAdvice.of`
        // holds the rule.
        val advice =
            OrderAdvice.of(
                proposal,
                movableStops = reorderable.size,
                isMeasuring = isMeasuring,
                canApply = proposal?.let(navigator::canApply) ?: false,
            )
        if (advice != OrderAdvice.None) {
            Text("Order", style = MaterialTheme.typography.labelLarge)
            val offer = proposal
            if (advice is OrderAdvice.Save && offer != null) {
                TextButton(onClick = {
                    scope.launch {
                        // `false`: the plan changed after the proposal was measured, and
                        // nothing was applied.
                        if (navigator.apply(offer)) note = "Stops reordered: ${advice.meters} m less to walk."
                    }
                }) { Text(advice.text.orEmpty()) }
                Text(
                    "Measured from where you are. Nothing moves until you tap it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (advice) {
                            OrderAdvice.AlreadyShortest -> Icons.Outlined.Check
                            OrderAdvice.Unmeasurable -> Icons.Outlined.WarningAmber
                            else -> Icons.Outlined.Schedule
                        },
                        contentDescription = null,
                    )
                    Text(
                        advice.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
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

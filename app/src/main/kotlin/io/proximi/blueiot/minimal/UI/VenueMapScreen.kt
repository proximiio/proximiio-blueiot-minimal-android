//
//  VenueMapScreen.kt
//  BlueiotMinimal
//
//  The venue map, the place search, the route to a picked place, and the next turn.
//
//  Nothing here draws on the map. `ProximiioMap` owns the venue style, the floors, the
//  amenities, the position marker and, given a route, the drawing of it split so the
//  floor on screen shows its own segment. This screen owns the picked destination, the
//  `computeRoute` call, and handing the route to the session. The recentre button calls
//  the library's follow camera. Turn-by-turn is one opt-in line plus the English the
//  instruction is rendered in.
//
package io.proximi.blueiot.minimal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.proximi.map.compose.ProximiioMap
import io.proximi.map.core.Journey
import io.proximi.map.core.RouteFollowRules
import io.proximi.map.kit.MapCameraFollow
import io.proximi.map.kit.MapCanvasChrome
import io.proximi.map.live.MapOptions
import io.proximi.map.live.ProximiioMapSession
import io.proximi.sdk.computeRoute
import io.proximi.sdk.core.model.ProximiioCoordinate
import io.proximi.sdk.features
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueMapScreen(
    venue: Venue,
    /**
     * The wristband being followed, and what to do with a new one. The sheet that asks
     * is behind the long press below, and lists the map's credits with it.
     */
    wristband: String,
    onSaveWristband: (WristbandId) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { WristbandStore.preferences(context) }

    // The session is named here rather than left to `ProximiioMap(sdk = …)` because this
    // screen calls `setRoute` and `clearRoute` on it.
    val session =
        remember(venue) {
            ProximiioMapSession(
                sdk = venue.sdk,
                context = context,
                options =
                    MapOptions(
                        // The library's own floor picker; this app adds no other.
                        floorSelector = MapOptions.FloorSelector.TRAILING,
                        // Draw whatever route is set, and clear it when none is.
                        route = MapOptions.Route.AUTOMATIC,
                        // No attribution control, MapLibre logo or compass over the map.
                        // An app that hides the control must show the style's credits
                        // itself; the long-press sheet lists them.
                        chrome = MapCanvasChrome.BARE,
                    ),
            )
        }
    // The session owns a coroutine scope, a canvas and a style. Nothing else closes it.
    DisposableEffect(session) { onDispose { session.close() } }

    var places by remember { mutableStateOf(emptyList<VenuePoi>()) }
    var destination by remember { mutableStateOf<VenuePoi?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    // The visit in progress, restored from disk on the first composition. `null` is the
    // ordinary state: a search bar and one destination.
    var journey by remember { mutableStateOf(JourneyStore.load(store)) }
    var isPlanningVisit by remember { mutableStateOf(false) }
    // Held as the state object rather than its value: the long-press listener below is
    // registered once and would otherwise capture the first `false` permanently.
    val isChangingWristband = remember { mutableStateOf(false) }

    val guidance by session.guidance.collectAsStateWithLifecycle()
    val position by session.position.collectAsStateWithLifecycle()
    val cameraMode by session.cameraMode.collectAsStateWithLifecycle()
    val credits by session.attributions.collectAsStateWithLifecycle()

    LaunchedEffect(session) {
        // Turn-by-turn guidance is off by default. Setting the rules is the whole
        // opt-in; the session then follows the route it is already drawing.
        session.guidanceRules = RouteFollowRules.VENUE_WALK
        // A local cache read, not a download: `Venue.start` already fetched it.
        places = VenuePoi.all(venue.sdk.features())
    }

    suspend fun route(to: VenuePoi) {
        destination = to
        val here = position
        if (here == null) {
            note = "Waiting for your wristband's first position."
            session.clearRoute()
            return
        }
        val computed =
            withContext(Dispatchers.Default) {
                runCatching {
                    venue.sdk.computeRoute(
                        from =
                            ProximiioCoordinate(
                                latitude = here.coordinate.latitude,
                                longitude = here.coordinate.longitude,
                            ),
                        fromLevel = here.floor?.level ?: 0.0,
                        to = to.coordinate,
                        toLevel = to.level,
                    )
                }
            }
        computed
            .onSuccess {
                session.setRoute(it)
                note = null
            }.onFailure {
                session.clearRoute()
                note = "No route from here."
            }
    }

    // Tapping a place on the map routes to it, the same as picking it in the search.
    // While a visit runs, `JourneyBar` owns the route and a tap does nothing.
    // `places` and `journey` are read when the tap arrives, not when this is assigned.
    DisposableEffect(session) {
        session.onFeatureTap = { identifiers, _ ->
            val place = VenuePoi.tapped(identifiers, places)
            if (place != null && journey == null) scope.launch { route(place) }
        }
        onDispose { session.onFeatureTap = null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ProximiioMap(
            session = session,
            modifier = Modifier.fillMaxSize(),
            // Changing the wristband without a settings screen: long-press the map.
            // MapLibre's own long press, so pan, pinch and rotate are unaffected. There
            // is deliberately no visible control. `configure` runs before the first
            // style load and the map exists only after it, so the listener is registered
            // inside `onStyleLoaded`.
            configure = { canvas ->
                canvas.onStyleLoaded { map, _ ->
                    map.addOnMapLongClickListener {
                        isChangingWristband.value = true
                        // `true` consumes the gesture; `false` would also let the map
                        // handle it.
                        true
                    }
                }
            },
        )

        val visit = journey
        if (visit != null) {
            // The journey drives the same session as the map, so there is one camera and
            // one drawn route either way.
            JourneyBar(
                session = session,
                journey = visit,
                places = places,
                store = store,
                modifier = Modifier.align(Alignment.BottomCenter),
                onEnd = {
                    journey = null
                    JourneyStore.save(null, store)
                    // `JourneyNavigator.end()` clears the route, the journey overlay and
                    // `guidanceRules`, which a journey owns while it runs. Setting the
                    // rules again restores this screen's single-route behaviour.
                    session.guidanceRules = RouteFollowRules.VENUE_WALK
                },
            )
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuidanceLine(guidance)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp)
                                    .clickable { isSearching = true },
                        ) {
                            Text(
                                destination?.title ?: "Search places",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            note?.let {
                                Text(
                                    it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (destination != null) {
                            IconButton(onClick = {
                                destination = null
                                note = null
                                session.clearRoute()
                            }) { Icon(Icons.Filled.Close, contentDescription = "Clear route") }
                        }
                        // Hidden while a visit is running: `JourneyBar` replaces this
                        // bar then.
                        IconButton(onClick = { isPlanningVisit = true }, enabled = places.isNotEmpty()) {
                            Icon(Icons.Outlined.ViewList, contentDescription = "Plan a visit")
                        }
                        RecentreButton(
                            isFollowing = cameraMode != MapCameraFollow.Mode.FREE,
                            hasPosition = position != null,
                        ) {
                            session.followMyFloor()
                            session.recentre()
                        }
                    }
                }
            }
        }
    }

    if (isSearching) {
        ModalBottomSheet(onDismissRequest = { isSearching = false }) {
            PoiSearchSheet(pois = places) { picked ->
                isSearching = false
                picked.firstOrNull()?.let { place -> scope.launch { route(place) } }
            }
        }
    }

    if (isPlanningVisit) {
        // The same search sheet, picking several places. The tap order is the walk
        // order.
        ModalBottomSheet(onDismissRequest = { isPlanningVisit = false }) {
            PoiSearchSheet(pois = places, allowsMultiple = true) { picked ->
                isPlanningVisit = false
                if (picked.isEmpty()) return@PoiSearchSheet
                session.clearRoute()
                destination = null
                note = null
                journey = Journey(stops = picked.map(::JourneyStop))
            }
        }
    }

    if (isChangingWristband.value) {
        ModalBottomSheet(onDismissRequest = { isChangingWristband.value = false }) {
            // `attributions` is a flow, so the sheet lists what the loaded style
            // declares.
            WristbandPrompt(
                current = wristband,
                credits = credits,
                onCancel = { isChangingWristband.value = false },
                // The one place in a running app that reaches the support report.
                footer = {
                    HorizontalDivider()
                    SupportReportButton(sdk = venue.sdk)
                },
                onSave = {
                    isChangingWristband.value = false
                    onSaveWristband(it)
                },
            )
        }
    }
}

/**
 * Recentres the map on the wristband.
 *
 * `ProximiioMapSession` owns the follow camera. `MapOptions.camera` defaults to
 * `FOLLOW`, so the map is already following when the first position arrives.
 * `recentre()` re-arms that camera and eases the zoom back in; `followMyFloor()` unpins
 * the floor, so the map returns to the floor the visitor is on.
 *
 * Panning, pinching or rotating the map drops the camera to `FREE`, and the library
 * publishes that through `ProximiioMapSession.cameraMode`. This screen only reads it and
 * must add no gesture handling of its own.
 *
 * Filled symbol while following, outline while free. Disabled until a position exists,
 * because there is nothing to centre on until then.
 */
@Composable
private fun RecentreButton(
    isFollowing: Boolean,
    hasPosition: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = hasPosition) {
        Icon(
            imageVector = if (isFollowing) Icons.Filled.MyLocation else Icons.Outlined.MyLocation,
            contentDescription = "Centre on me",
        )
    }
}

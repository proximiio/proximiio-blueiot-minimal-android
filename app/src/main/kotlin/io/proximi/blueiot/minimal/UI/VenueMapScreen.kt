//
//  VenueMapScreen.kt
//  BlueiotMinimal
//
//  THE APP, ONCE IT KNOWS THE WRISTBAND: a venue map, a search, a route, and the
//  turn to take next.
//
//  Nothing here draws on the map. `ProximiioMap` owns the venue style, the floors,
//  the amenities, the blue dot and — given a route — the drawing of it, split so the
//  floor on screen shows its own segment. What this screen owns is three lines of
//  app: which place the visitor picked, asking the SDK for a route to it, and handing
//  that route over. The recentre button is a fourth, and it is one call into the
//  library's own follow camera rather than a camera this app wrote. Turn-by-turn is a
//  fifth: one line opts in, and the only thing left to the app is the English the
//  instruction is said in.
//
//  Your product's chrome goes in `BottomBar`. Your product's screens go beside this
//  one.
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
     * The band being followed, and what to do with a different one. The sheet that
     * asks is behind the long press below, and lists the map's credits with it.
     */
    wristband: String,
    onSaveWristband: (WristbandId) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { WristbandStore.preferences(context) }

    // The map session, named here rather than left to `ProximiioMap(sdk = …)`,
    // because naming it is what gives this screen something to call `setRoute` on.
    val session =
        remember(venue) {
            ProximiioMapSession(
                sdk = venue.sdk,
                context = context,
                options =
                    MapOptions(
                        // The library's own floor picker. This app has no other, so
                        // there is no risk of two.
                        floorSelector = MapOptions.FloorSelector.TRAILING,
                        // Draw a route whenever one is set, and clear it when one is not.
                        route = MapOptions.Route.AUTOMATIC,
                        // No attribution ⓘ, MapLibre logo or compass over the map. The
                        // credits the ⓘ presented are this app's to show now; the
                        // long-press sheet lists them.
                        chrome = MapCanvasChrome.BARE,
                    ),
            )
        }
    // The session owns a coroutine scope, a canvas and a style; nobody else closes it.
    DisposableEffect(session) { onDispose { session.close() } }

    var places by remember { mutableStateOf(emptyList<VenuePoi>()) }
    var destination by remember { mutableStateOf<VenuePoi?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    // The visit in progress, restored from disk on the first composition. `null` is
    // the ordinary state of this app: a search bar and one destination.
    var journey by remember { mutableStateOf(JourneyStore.load(store)) }
    var isPlanningVisit by remember { mutableStateOf(false) }
    // Held as the state object rather than its value, because the long-press handler
    // below is registered once and would otherwise capture the first `false` for ever.
    val isChangingWristband = remember { mutableStateOf(false) }

    val guidance by session.guidance.collectAsStateWithLifecycle()
    val position by session.position.collectAsStateWithLifecycle()
    val cameraMode by session.cameraMode.collectAsStateWithLifecycle()
    val credits by session.attributions.collectAsStateWithLifecycle()

    LaunchedEffect(session) {
        // One line turns turn-by-turn on, and the session follows the route it is
        // already drawing: which turn is next, how far is left to it, whether the
        // visitor has walked off it, whether they have arrived. Off by default, so an
        // app that does not want it writes nothing.
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

    Box(modifier = Modifier.fillMaxSize()) {
        ProximiioMap(
            session = session,
            modifier = Modifier.fillMaxSize(),
            // Changing the wristband without a settings screen: press and hold the
            // map. MapLibre's own long press, so the map keeps its pan, pinch and
            // rotate. Undiscoverable on purpose — a visitor is handed a band and never
            // needs this; staff are told about it once. `configure` runs before the
            // first style load and the map itself only exists after it, which is why
            // the listener is registered inside `onStyleLoaded`.
            configure = { canvas ->
                canvas.onStyleLoaded { map, _ ->
                    map.addOnMapLongClickListener {
                        isChangingWristband.value = true
                        // `false` would let the gesture fall through to the map's own
                        // handling as well; this press means one thing.
                        true
                    }
                }
            },
        )

        val visit = journey
        if (visit != null) {
            // The journey drives the same session the map is already using, so there
            // is one map, one camera and one drawn route either way.
            JourneyBar(
                session = session,
                journey = visit,
                places = places,
                store = store,
                modifier = Modifier.align(Alignment.BottomCenter),
                onEnd = {
                    journey = null
                    JourneyStore.save(null, store)
                    // `JourneyNavigator.end()` hands the session back: it clears the
                    // route, the journey overlay and — because a journey owns guidance
                    // while it runs — `guidanceRules`. Turning guidance back on is what
                    // returns this screen to the single-route behaviour it had before
                    // the visit started.
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
                        // Several places instead of one. Hidden while a visit is
                        // running, because `JourneyBar` takes this bar's place then.
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
        // The same search, in the same file, picking several places instead of one.
        // The order they are tapped is the order they are walked.
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
            // Read, not stored: `attributions` is a flow, so the sheet lists what the
            // loaded style declares.
            WristbandPrompt(
                current = wristband,
                credits = credits,
                onCancel = { isChangingWristband.value = false },
                onSave = {
                    isChangingWristband.value = false
                    onSaveWristband(it)
                },
            )
        }
    }
}

/**
 * "Show me where I am", and nothing else is needed to make it work.
 *
 * `ProximiioMapSession` owns the follow camera (`MapOptions.camera` defaults to
 * `FOLLOW`, so the map is already following when the first fix lands). `recentre()`
 * re-arms that camera and eases the zoom back in; `followMyFloor()` unpins the
 * storey, because a visitor who taps this while looking at another floor means "take
 * me back", and taking them back to a storey they are not on would not.
 *
 * Panning, pinching or rotating the map drops the camera to `FREE` on its own — the
 * library watches for the hand and publishes the change through
 * `ProximiioMapSession.cameraMode`. This screen only reads that, and must not add
 * gesture handling of its own.
 *
 * Filled symbol while following, outline while free. Disabled until there is a
 * position at all: with the wristband silent or the relay down there is nowhere to
 * centre on, and a button that looks live and does nothing is worse than one that
 * says so.
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

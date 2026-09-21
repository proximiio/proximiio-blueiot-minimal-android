# Proximi.io BlueIoT — minimal reference app (Android)

A complete venue app in thirteen Kotlin files. It asks for the visitor's wristband
number once, shows the venue map, searches the venue's places, routes to a picked
place, states the next turn, posts a notification when the visitor enters or leaves one
of the venue's geofences, and walks a planned sequence of places that can be added to,
reordered and detoured from. Positioning continues while the app is backgrounded.

The visitor is positioned by the venue's own BlueIoT anchors, through the Proximi.io
cloud relay. The phone scans nothing.

This is the Android twin of
[`proximiio-blueiot-minimal-ios`](https://github.com/proximiio/proximiio-blueiot-minimal-ios),
file for file, with one file more. The deliberate divergences are listed under
**Choosing a place**, **Place notifications**, **Positioning while the app is
backgrounded** and **The diagnostics log**.

## What it is not

It has no settings screen, no diagnostics screen, no staff mode, no engine controls, no
event log, no offline package and no step list. Nothing reorders a visit on its own. It
uses no navigation library, no dependency injection and no `ViewModel`: `remember` and
`rememberSaveable` hold every piece of state in the app.

Each of those exists in the SDK and is left out here on purpose: this app is a starting
point for a product, not a tour of the SDK.

## Fill in the configuration

The app draws one Proximi.io organisation and follows one BlueIoT wristband. Before it
shows anything, the organisation must hold the venue's floors, places and geofences, and
the venue's BlueIoT engine must report the wristband to the Proximi.io cloud relay.
Proximi.io supplies the three values below.

Three build-time credentials, none editable at runtime:

```sh
cp secrets.example.properties secrets.properties
$EDITOR secrets.properties
```

| Key | What it is |
| --- | --- |
| `PROXIMIIO_APPLICATION_TOKEN` | The Proximi.io application token (Proximi.io Portal → organisation → Application token) |
| `BLUEIOT_CLOUD_RELAY_URL` | The Proximi.io cloud relay carrying this venue's wristband positions. A bare host is enough |
| `BLUEIOT_CLOUD_RELAY_TOKEN` | That relay's stream token, sent as `Authorization: Bearer`. The relay answers HTTP 401 without it |

`secrets.properties` is gitignored and is the only place a real credential may live.
`secrets.example.properties` is tracked, leaves the token keys empty and is the file to
copy. The build reads `secrets.properties` when it exists and falls back to a Gradle
property of the same name, so CI builds with neither on disk. With any key empty the app
still builds and runs, and reports the missing key on screen.

The one non-secret value lives in the tracked `venue.properties`; see **Floor numbers**.

## Run it

```sh
./gradlew :app:installDebug
```

Or open the folder in Android Studio and press Run. The Gradle wrapper pins Gradle 9.5, and
`local.properties` points at the Android SDK; Android Studio writes it on first open.

Requirements: **JDK 17** or newer for the build (the project compiles to Java 17 bytecode),
an Android Studio version that supports AGP 9.3, and a device or emulator on API 26 or
newer. A build needs the network: the artifacts below are downloaded, not vendored.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Dependencies are the published artifacts, pinned to exact versions in
`gradle/libs.versions.toml`, resolved from `https://maven.eu.proximi.fi/releases/`, which
needs no credentials to read. There is no `mavenLocal()`.

| Artifact | Version |
| --- | --- |
| `io.proximi.sdk:proximiio` | `6.0.0-beta.6` |
| `io.proximi.sdk:proximiio-blueiot` | `6.0.0-beta.6` |
| `io.proximi.map:proximiio-map` | `6.0.0-beta.9` |
| AGP / Kotlin | `9.3.1` / `2.2.10` (AGP 9's built-in Kotlin) |
| compileSdk / targetSdk / minSdk | `37` / `36` / `26` |

MapLibre arrives transitively through `proximiio-map` and must not be declared here.

## The first run

1. The app asks for the wristband number printed on the band. The line under the field
   echoes the tag it understood, in decimal and hexadecimal.
2. It asks for location once, then Android's own dialog follows.
   **Positioning while the app is backgrounded** explains why a relay-fed app needs the
   grant.
3. The map opens on the venue. The dot appears when the relay reports the wristband;
   until then the map, the floor selector and the search work without it.

A key left empty in `secrets.properties` is named on screen instead of the map. No
position and no map usually means the token is for another organisation, the relay
address or token is wrong, or the venue's engine is not reporting that wristband.

The SDK and the map library are documented at
[docs.proximi.fi/android-sdk-v6](https://docs.proximi.fi/android-sdk-v6/) and
[docs.proximi.fi/android-map-v6](https://docs.proximi.fi/android-map-v6/).

## Where things are

Thirteen files in the iOS app's folder layout, all in one Kotlin package
(`io.proximi.blueiot.minimal`). The folders match the iOS app so the two can be read side by side.

| File | What it owns |
| --- | --- |
| `App/MainActivity.kt` | The start-up order: wristband, location, SDK, map |
| `App/VenueConfiguration.kt` | The build-time values |
| `Venue/WristbandId.kt` | The spelling rule for a wristband id, and where it is stored |
| `Venue/Venue.kt` | Starting the SDK and attaching the cloud relay to one wristband |
| `Venue/VenuePoi.kt` | Turning the venue's features into searchable places |
| `Venue/JourneyStore.kt` | Persisting a visit, and turning a picked place into a stop |
| `Venue/GeofenceNotifier.kt` | The notification text and posting it. No iOS twin |
| `UI/WristbandPrompt.kt` | The wristband field, and the map credits |
| `UI/LocationPrompt.kt` | The location prompt, and the rule for when it is shown |
| `UI/VenueMapScreen.kt` | Map, search, tap-to-route, route, and where a visit starts |
| `UI/PoiSearchSheet.kt` | The search list, single or multi-select |
| `UI/GuidanceLine.kt` | The turn-by-turn sentence |
| `UI/JourneyBar.kt` | The visit: the stop in hand, the plan, adding, detours, reordering |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | The app icon, a placeholder |

The icon is a placeholder: an adaptive icon made of a flat colour
(`res/values/colors.xml`) and the letter V drawn as a vector
(`res/drawable/ic_launcher_foreground.xml`). Replace the foreground drawable and the
background colour, or point `ic_launcher.xml` at an existing icon. Nothing else in the
project refers to the image.

## Changing the wristband, and the map credits

A returning visitor is never asked for the wristband number again. To change it,
long-press the map; the same prompt opens as a bottom sheet. There is deliberately no
visible control. `VenueMapScreen.isChangingWristband` is the single flag that opens it.
The gesture is MapLibre's `addOnMapLongClickListener`, registered inside the `configure`
lambda's `onStyleLoaded`, so pan, pinch and rotate are unaffected.

The same sheet lists the map credits. `MapOptions.chrome = MapCanvasChrome.BARE` in
`VenueMapScreen` hides MapLibre's attribution control, and an app that hides it must show
the style's credits somewhere reachable from the map. The credits are
`ProximiioMapSession.attributions`, which for the venue style are OpenStreetMap (ODbL) and
MapLibre. MapLibre strips the leading `©` from each credit; the app adds it back.

## Floor numbers

The relay reports the venue engine's floor numbers and the SDK resolves them to
Proximi.io floors on its own. An engine floor number is a Proximi.io floor level, and the
SDK syncs every floor with its level, so it derives the number-to-floor table itself. The
app passes no `floorNoMap`, because a table supplied by the host switches that derivation
off. A number the venue has no floor for is reported in the SDK log rather than drawn on
a blank floor.

One integer remains, in the tracked `venue.properties`, because the SDK cannot derive it:

| Key | What it is |
| --- | --- |
| `BLUEIOT_GROUND_FLOOR_NO` | The engine floor number for the ground floor. Proximi.io calls it level `0`; BlueIoT LocalSense venues are usually numbered from `1`, and this one is. Empty means `0`, and then the key is not needed |

It reaches the SDK as `BlueiotCloudRelayConfiguration.engineGroundFloorNumber` in
`Venue.follow`. The shift applies to floors above ground only: at `1`, engine floor 1 is
level 0 and engine floor 2 is level 1, while engine floor −1 stays level −1.

## Positioning while the app is backgrounded

Positioning continues when the app is backgrounded and when the screen locks. It requires
all four of the following. Each one missing produces the same symptom: position updates
stop within minutes of the screen going off.

| Requirement | Where it is set | What a missing one looks like |
| --- | --- | --- |
| `serviceOptions` on the SDK configuration | `Venue.configuration` | Android freezes the process within minutes of the last Activity stopping. No socket read, no coroutine tick. `relayOnly` leaves this at `null`, which is foreground-only positioning |
| `runsInBackground = true` on the relay configuration | `Venue.follow` | The service runs, but the SDK pauses the relay provider whenever the app is backgrounded. The default is `false` |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK` | `AndroidManifest.xml` | From API 34 the platform refuses `startForeground` without the permission for the service's type, and the SDK derives the type mask from the permissions it holds |
| A location grant | `LocationPrompt` | With no location grant and no Bluetooth grant, no foreground-service type is usable and the SDK does not start the service. It logs `Foreground service could not start: no foreground-service type is usable` and positions in the foreground only |

Those four are also all the place notifications need: a geofence notification reaches a
locked screen because the process that evaluates the geofence is the one the service keeps
alive.

Coarse location is all the app requests, and it is not used for the position: the venue's
anchors locate the wristband. The grant is what lets the service exist.
`POST_NOTIFICATIONS` is requested with it on API 33 and above so the service's ongoing
notification is visible; a refusal leaves the service running with the notification
hidden. Background location is never requested.

The app's own manifest declares no Bluetooth permission and the app requests none at
runtime: the phone scans nothing, and `ProximiioConfiguration.relayOnly` turns the SDK's
iBeacon, Eddystone and UWB sources off. The SDK's own manifest still contributes
`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` to the merged manifest.

The app raises the permission dialog itself rather than letting the SDK do it, which is a
deliberate divergence from the iOS app. The SDK's request path would also ask for
Bluetooth, which a relay-only app never uses. The prompt is shown once, gated by a flag of
the app's own: Android reports no "not determined" state, and
`shouldShowRequestPermissionRationale` cannot distinguish "never asked" from "denied
twice". A refusal is recorded like any other answer, and the prompt is not shown again.

A foreground service exempts the app from most of Doze while it runs.
`ProximiioServiceOptions.holdsWakeLock = true` is set because the position source is a
socket, a socket is read on the CPU, and in Doze the frame would wait for the next
maintenance window. The lock is released with the service and is bounded by
`wakeLockTimeoutMillis`, which defaults to 30 minutes. None of this covers what happens
after the service stops, and OEM process management on top of Doze may still reduce
delivery. The SDK never requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and neither does
this app. Test with `adb shell dumpsys deviceidle force-idle`.

## Choosing a place

A destination is chosen in two ways, and both call the same `route` function in
`VenueMapScreen`:

| Input | Where |
| --- | --- |
| Pick one place in the search sheet | `PoiSearchSheet`, single-select |
| Tap a place on the map | `session.onFeatureTap`, resolved by `VenuePoi.tapped` |

`ProximiioMapSession.onFeatureTap` reports the feature ids under the tap, nearest first.
`VenuePoi.tapped` returns the first id that is one of the venue's POIs, so a POI drawn over
a room is chosen before the room. A tap on anything else does nothing. While a visit runs,
`JourneyBar` owns the route and a tap on the map does nothing.

Tap-to-route is a deliberate divergence from the iOS app, which chooses places through the
search only.

## Turn-by-turn

`session.guidanceRules = RouteFollowRules.VENUE_WALK` in `VenueMapScreen` is the whole
opt-in; guidance is off by default. The map library then follows the route it is already
drawing and republishes `session.guidance` on every position. The bottom bar shows the
next manoeuvre, the distance left to it, and arrival.

The instruction sentences are the app's, in `GuidanceLine.instruction`, because
`RouteManoeuvre.Kind` carries no display strings.

Being off route is reported, not acted on. `RouteGuidance.isOffRoute` latches after
`offRouteFixes` consecutive positions beyond `offRouteMeters`, and clears on the first
position back inside. `RouteFollowRules.VENUE_WALK` sets those to 3 and 12 m. The app adds
no detector and no re-routing of its own.

## Place notifications

Entering one of the venue's geofences posts a notification: "You are now inside Main
Hall." Leaving it replaces that notification with "You have left Main Hall." All of it is `Venue/GeofenceNotifier.kt`: one channel ("Place updates"), the
geofence name as the title, one sentence as the body, and one notification id per
geofence, so an exit replaces its enter. Tapping a notification opens the map.

This is a deliberate divergence from the iOS app, which posts no notifications at all.

Geofences are defined in Proximi.io Portal and evaluated by the SDK against every
position; events are collected from `proximiio.geofenceEvents()`. A geofence the venue
left unnamed produces no notification.
Privacy zone events produce no notification in either direction: a privacy zone exists so
that the visitor's presence inside it is not reported.

The permission is the `POST_NOTIFICATIONS` that `LocationPrompt` already requests on API
33 and above. There is no second request, and a refusal suppresses the notifications and
nothing else.

## A visit

The list button next to the search opens the same search sheet in multi-select. The
places tapped, in that order, become a `Journey`. `JourneyNavigator` then owns every route
computation in the walk: it draws and follows one leg at a time through the same session
as the map, and re-routes a leg by itself when the visitor leaves it, which a single route
does not do.

The bar shows the stop in hand, `overview.remainingStops`, `overview.remainingMeters`, the
ETA, the count of `overview.unreachableStopIds`, and **Continue** once the visitor has
arrived. `JourneyRules.advance` defaults to `Advance.Manual`, so arrival does not move the
visit on by itself.

**Your visit**, the list button on the bar, is where the plan is changed:

| Control | What it does |
| --- | --- |
| **+** | Opens the same multi-select search. `JourneyNavigator.add` puts each pick after everything still to be walked and leaves the leg in hand alone. It returns `false` for a place the plan already holds, which the sheet reports. It is available after the last stop too: adding revives a finished visit and makes the new stop active |
| **Drag** | Long-press a row and drag to reorder what is still ahead. The rows are `JourneyNavigator.reorderableStops`, the list `move(stopId, toIndex)` indexes into, so the app holds no second copy of which stops may move. Compose has no `.onMove`, so this gesture is the app's, in `JourneyBar.ReorderableStops` |
| **Save N m by reordering** | A shorter order, measured by `proposeOrder`. Neither the library nor the sheet applies one; `apply` does. It is re-measured whenever the stops change, because `apply` ignores a proposal that no longer describes the journey |
| **Show the whole plan on the map** | Sets `session.journeyOverlayStyle`, which draws the rest of the plan under the leg in hand. Off by default |

"Stop off" is a detour. Which amenity kinds a venue has is read from the venue's own
amenity tags (`VenuePoi.nearestByAmenity`) rather than from a list of categories in the
app. What each kind is called comes from the SDK's amenity catalogue: `amenities()` is
read once when a visit starts and kept as a map. The app keeps no titles of its own, so an
amenity renamed on the server is renamed here without a release.

The visit is written to `SharedPreferences` on every change and restored on launch.
`JourneyCodec` encodes it and each stop carries its own state. `JourneyCodec.decode`
throws on text it cannot read, and `JourneyStore.load` returns `null` in that case rather
than letting the launch crash.

## Recentring on the visitor

The button at the right of the bottom bar recentres the map on the wristband. It is one
call into the map library's follow camera, `ProximiioMapSession.recentre()` plus
`followMyFloor()`; the app writes no camera of its own. Panning, pinching or rotating the
map drops the camera to `MapCameraFollow.Mode.FREE`, which the library publishes through
`ProximiioMapSession.cameraMode`; that is what fills or hollows the button's symbol. The
button is disabled until a position exists, because there is nothing to centre on until
then.

## The diagnostics log

There is none on Android. The iOS app records positions, floors, relay connection
changes, SDK warnings, and foreground and background transitions into a file support can
ask a visitor for, with the configured credentials stripped out. The
Android SDK has no equivalent of `startDiagnosticsRecording` or `recordDiagnosticsEvent`.

Two consequences. `MainActivity` and `Venue` start no recording and record no events. And
`DiagnosticsTests`, which on iOS asserts that the log never carries a configured secret
verbatim, is not ported; `BackgroundPositioningTests` notes this in its header.

`adb logcat` carries the SDK's own log. Nothing in this app writes a credential to it.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

Twenty-nine tests. All six subjects are chosen because they fail without anything on
screen looking wrong:

- a wristband id read one way by the app and another way by the relay matches no tag, and
  the symptom is that no position arrives;
- a visit that does not survive a launch loses the plan silently;
- an amenity query that reads the venue's data wrongly makes a venue with toilets look
  like a venue without any;
- a background flag left at its default, or a location prompt that nags or never fires,
  stops position updates minutes after the screen locks;
- a notification sentence naming the wrong place reads correctly, and a privacy zone
  announced on a lock screen is the one thing a privacy zone exists to prevent;
- a tap resolved to the room under a POI instead of the POI routes to the wrong place.

The screens are not tested; they hold no logic.

JUnit 4, as the SDK uses. Robolectric only where `SharedPreferences` is involved. The
wristband spelling rule and the amenity query are plain JVM tests with no Android in them.

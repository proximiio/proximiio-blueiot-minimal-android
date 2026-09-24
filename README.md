# Proximi.io BlueIoT — minimal reference app (Android)

A complete venue app in seventeen Kotlin files. It asks for the visitor's wristband
number once, shows the venue map, searches the venue's places, routes to a picked
place, states the next turn, posts a notification when the visitor enters or leaves one
of the venue's geofences, and walks a planned sequence of places that can be added to,
reordered and detoured from. Positioning continues while the app is backgrounded. The
SDK's diagnostics log is recorded from process start and can be sent as a support
report.

The visitor is positioned by the venue's own BlueIoT anchors, through the Proximi.io
cloud relay. The phone scans nothing.

This is the Android twin of
[`proximiio-blueiot-minimal-ios`](https://github.com/proximiio/proximiio-blueiot-minimal-ios).
The deliberate divergences are listed under **Choosing a place**, **Place
notifications**, **Positioning while the app is backgrounded**, **The diagnostics log**
and **Testing without the venue**. The largest one is the support report: the iOS app
has no export.

## What it is not

It has no settings screen, no diagnostics screen, no staff mode, no engine controls, no
event log, no offline package and no step list. Nothing reorders a started visit without a
tap. It
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
| `BLUEIOT_CLOUD_RELAY_URL` | The Proximi.io cloud relay carrying this venue's wristband positions. A bare host is enough. The template sets the production relay, `blueiot.proximi.fi`; **Testing without the venue** describes the sandbox relay |
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
| `io.proximi.sdk:proximiio` | `6.0.0-beta.13` |
| `io.proximi.sdk:proximiio-blueiot` | `6.0.0-beta.13` |
| `io.proximi.map:proximiio-map` | `6.0.0-beta.12` |
| AGP / Kotlin | `9.3.1` / `2.2.10` (AGP 9's built-in Kotlin) |
| compileSdk / targetSdk / minSdk | `37` / `36` / `26` |

MapLibre arrives transitively through `proximiio-map` and must not be declared here.

## The first run

1. The app asks for the wristband number printed on the band. The line under the field
   echoes the tag it understood, in decimal and hexadecimal.
2. It explains why it needs location. **Continue** opens Android's permission dialog,
   and no other permission dialog follows, whatever the answer.
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

Seventeen files in the iOS app's folder layout, all in one Kotlin package
(`io.proximi.blueiot.minimal`). The folders match the iOS app so the two can be read side by side.

| File | What it owns |
| --- | --- |
| `App/BlueiotMinimalApplication.kt` | Starting the diagnostics log at process start. On iOS this is in `BlueiotMinimalApp.swift` |
| `App/MainActivity.kt` | The start-up order: wristband, location, SDK, map |
| `App/VenueConfiguration.kt` | The build-time values |
| `App/SdkLogcat.kt` | Forwarding the SDK's log to logcat in debug builds. No iOS twin |
| `App/SupportReport.kt` | Building the support report and sharing it. No iOS twin |
| `Venue/WristbandId.kt` | The spelling rule for a wristband id, and where it is stored |
| `Venue/Venue.kt` | Starting the SDK, and attaching and detaching the position provider: the cloud relay for one wristband, or a journey playback in debug builds |
| `Venue/VenuePoi.kt` | Turning the venue's features into searchable places |
| `Venue/JourneyStore.kt` | Persisting a visit, and turning a picked place into a stop |
| `Venue/GeofenceNotifier.kt` | The notification text and posting it. On iOS this is in `Venue.swift` and `NotificationPrompt.swift` |
| `UI/WristbandPrompt.kt` | The wristband field, and the map credits |
| `UI/LocationPrompt.kt` | The location prompt, and the rule for when it is shown |
| `UI/VenueMapScreen.kt` | Map, search, tap-to-route, route, **New route from here**, and where a visit starts |
| `UI/PoiSearchSheet.kt` | The search list, single or multi-select |
| `UI/GuidanceLine.kt` | The turn-by-turn sentence |
| `Venue/VisitRules.kt` | The rules behind the visit's text: when a new visit is ordered, the order row in the plan, the stop-off lines, and ending the navigator once |
| `UI/JourneyBar.kt` | The visit: the stop in hand, the plan, adding, stop-offs, reordering, and the prompt shown when the visitor leaves the route |
| `res/xml/diagnostics_paths.xml` | The one directory the support report is shared from |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | The app icon, a placeholder |

Five more files belong to one build type each. They are outside the seventeen. A product
can delete them together with the calls to `DebugPositionSource` in `MainActivity`,
`Venue` and `VenueMapScreen`:

| File | What it owns |
| --- | --- |
| `src/debug/…/Venue/JourneyPlaybackLaunch.kt` | Reading the launch extras, and the playback provider they and the picker attach |
| `src/debug/…/Venue/JourneyPlayback.kt` | The journey picker's rows and list states, the playback options, and the playback controls' state |
| `src/debug/…/Venue/DebugPositionSource.kt` | Playing and stopping a journey in place of the relay, for the launch extras and the picker |
| `src/debug/…/UI/JourneyPickerSheet.kt` | The journey picker button, the picker sheet and the playback controls |
| `src/release/…/Venue/DebugPositionSource.kt` | The release build's switch, which always keeps the relay and shows no picker |

`scripts/journey-run.mjs` is a Node script for tests through the sandbox relay; see
**Testing without the venue**.

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

The same sheet lists the map credits and holds **Send diagnostics report** (see **The
diagnostics log**). `MapOptions.chrome = MapCanvasChrome.BARE` in
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
`Venue.attachRelay`. The shift applies to floors above ground only: at `1`, engine floor 1 is
level 0 and engine floor 2 is level 1, while engine floor −1 stays level −1.

## Positioning while the app is backgrounded

Positioning continues when the app is backgrounded and when the screen locks. It requires
all four of the following. Each one missing produces the same symptom: position updates
stop within minutes of the screen going off.

| Requirement | Where it is set | What a missing one looks like |
| --- | --- | --- |
| `serviceOptions` on the SDK configuration | `Venue.configuration` | Android freezes the process within minutes of the last Activity stopping. No socket read, no coroutine tick. `relayOnly` leaves this at `null`, which is foreground-only positioning |
| `runsInBackground = true` on the relay configuration | `Venue.attachRelay` | The service runs, but the SDK pauses the relay provider whenever the app is backgrounded. The default is `false` |
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
iBeacon, Eddystone and UWB sources off. The libraries still add permissions to the merged
manifest. At SDK `6.0.0-beta.13` and map `6.0.0-beta.12` the merged manifest holds:

| Permission | Declared by | Requested at runtime |
| --- | --- | --- |
| `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK` | The app (and the SDK) | No, granted at install |
| `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS` | The app (and the SDK) | Yes, by `LocationPrompt` |
| `ACCESS_FINE_LOCATION` | The SDK and MapLibre | No. The app uses coarse location |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` | The SDK | No |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion="30"`) | The SDK | No, granted at install |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | The SDK | No, granted at install. `includesConnectedDeviceType = false` keeps the type out of the service |
| `ACTIVITY_RECOGNITION` | The SDK, for pedestrian dead reckoning | No |
| `ACCESS_NETWORK_STATE` | The SDK and MapLibre | No, granted at install |
| `ACCESS_WIFI_STATE` | MapLibre | No, granted at install |
| `RECEIVE_BOOT_COMPLETED` | AndroidX WorkManager, through the SDK | No, granted at install |
| `ACCESS_BACKGROUND_LOCATION` | The SDK | Removed by the app |

The app removes `ACCESS_BACKGROUND_LOCATION` with `tools:node="remove"` in
`AndroidManifest.xml`. It never requests it: the foreground service keeps the process
alive, and a service started while the app is on screen needs only the foreground grant.
Without the declaration, Google Play asks for no background location declaration. Check
the merged manifest again after raising a library version:
`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`.

The app raises the permission dialog itself rather than letting the SDK do it, which is a
deliberate divergence from the iOS app. `Proximiio.requestPermissions()` asks for precise
location (`ACCESS_FINE_LOCATION`) and does not ask for `POST_NOTIFICATIONS`; this app
needs approximate location and notifications, in one dialog. The prompt is shown once,
gated by a flag of the app's own: Android reports no "not determined" state, and
`shouldShowRequestPermissionRationale` cannot distinguish "never asked" from "denied
twice". A refusal is recorded like any other answer, and the prompt is not shown again.

`Venue.start` then calls `refreshPermissions()`, not `requestPermissions()`.
`refreshPermissions()` reads the current grants and never shows a dialog. The SDK counts
only the prompts it raised itself, so `requestPermissions()` after a refusal in
`LocationPrompt` can show the system dialog a second time. `RootScreen` calls
`refreshPermissions()` again each time the app returns to the foreground, because Android
reports no permission change to a running app: a grant changed in the system settings
reaches the SDK that way.

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

`RouteGuidance.isOffRoute` latches after `offRouteFixes` consecutive positions beyond
`offRouteMeters`, and clears on the first position back inside.
`RouteFollowRules.VENUE_WALK` sets those to 3 and 12 m. The session does not re-route.
While `isOffRoute` is `true` the bar reads "You have left the route." and shows **New route
from here**, which computes a new route to the same place from the visitor's position
(`GuidanceLine.offersReroute`). The app adds no detector of its own. The instruction is one
line; the app shows no step list.

Ending a visit hands the guidance back to this bar. `JourneyNavigator.end()` sets
`guidanceRules` to `null`; `JourneyBar` calls it once, through `VisitEnding`, and then
`VenueMapScreen` sets `RouteFollowRules.VENUE_WALK` again. A second `end()` after that,
when the bar leaves the screen, would switch single-route guidance off, with no instruction
and no off-route line.

## Place notifications

Entering one of the venue's geofences posts a notification: "You are now inside Main
Hall." Leaving it replaces that notification with "You have left Main Hall." All of it is `Venue/GeofenceNotifier.kt`: one channel ("Place updates"), the
geofence name as the title, one sentence as the body, and one notification id per
geofence, so an exit replaces its enter. Tapping a notification opens the map.

Each transition is also a line in the diagnostics log: `geofence enter · Main Hall ·
notified`, or `· not authorized` when the permission is refused.

The iOS app posts a notification for every geofence transition too. Two details differ,
deliberately:

- iOS asks for notifications in a second prompt of its own, `NotificationPrompt`. This
  app asks in the same Android dialog as location.
- iOS gives each notification a new identifier, so an exit adds a second notification.
  This app keeps one notification per geofence, so an exit replaces its enter.

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
places tapped, in that order, become a `Journey`. Before the visit starts, `JourneyBar`
calls `proposeOrder(JourneyOrderOrigin.VISITOR)` and applies the result when it is
shorter. The first place can move. The bar then says which happened for 8 seconds: "Stops
put in the shortest order: N m less to walk." or "Your stops are already in the shortest
order." Without a position the call returns `null`; the tap order is kept, the bar says the
order is measured when the position arrives, and the first position runs the same call.
On that position the result replaces the note for 8 seconds; without a result the note is
cleared.
`StartOrder` holds the rule: no order is applied once a stop is reached, done or skipped,
or a stop-off is in the plan. A restored visit that has already started is not reordered.
`JourneyNavigator` then owns every route computation in the walk: it draws and follows one
leg at a time through the same session as the map.

The navigator does not re-route a visitor who leaves the leg. `JourneyBar` sets
`deviationPolicy = JourneyDeviationPolicy.ASK_APP`, and the drawn leg stays until the
visitor answers a prompt on the bar. The prompt opens on three `JourneyNavigator.events`:
`FarFromRoute`, `OffRouteTooLong` and `DetourOverstayed`. `LeftRoute` opens no prompt. The
prompt closes on `ReturnedToRoute`, on `JourneyFinished`, on `DetourEnded` for a detour
prompt, and when either button is tapped. `DeviationPrompt.after` holds this rule. The
thresholds are the library defaults in `JourneyDeviationRules`; the app sets none.

The bar shows the stop in hand, `overview.remainingStops`, `overview.remainingMeters`, the
ETA, the count of `overview.unreachableStopIds`, and **Continue** once the visitor has
arrived. `JourneyRules.advance` defaults to `Advance.Manual`, so arrival does not move the
visit on by itself.

The plan is changed on the bar and in **Your visit**, the list button on the bar:

| Control | What it does |
| --- | --- |
| **Back to my route** | On the deviation prompt. Calls `resumeJourney()`: a live detour ends (reached is recorded as visited, otherwise dropped), the leg to the stop the plan is on is drawn from the visitor's position, and the deviation clears |
| **New route from here** | On the deviation prompt. Calls `replanFromHere()`: a live detour ends, the remaining stops are reordered from the visitor's position and the order is applied. The stop being walked to is not kept in place |
| **Stop off** | On the bar. See below |
| **Back to the plan** | On the bar during a stop-off. Calls `cancelDetour()`: the stop-off is dropped and the leg to the planned stop is drawn from the visitor's position |
| **+** | Opens the same multi-select search. `JourneyNavigator.add` puts each pick after everything still to be walked and leaves the leg in hand alone. It returns `false` for a place the plan already holds, which the sheet reports. It is available after the last stop too: adding revives a finished visit and makes the new stop active |
| **Drag** | Long-press a row and drag to reorder what is still ahead. The rows are `JourneyNavigator.reorderableStops`, the list `move(stopId, toIndex)` indexes into, so the app holds no second copy of which stops may move. Compose has no `.onMove`, so this gesture is the app's, in `JourneyBar.ReorderableStops` |
| **Save N m by reordering** | `proposeOrder(JourneyOrderOrigin.VISITOR)` measures a shorter order from the visitor's position and returns a proposal. The stop being walked to can move. Without a position the sheet uses `proposeOrder(JourneyOrderOrigin.ACTIVE_STOP)`, which keeps the stop being walked to first. A tap applies the proposal. It is measured again when the remaining stops, their order, the live stop or the first position change, because `apply` refuses a proposal after any of those changes and returns `false`. The **Order** row is shown whenever two or more stops can move: the button, "Your stops are already in the shortest order.", "Measuring the shortest order…" while measuring or while `canApply` is `false`, or "The order cannot be measured: a stop has no route." when `proposeOrder` returns `null`. `OrderAdvice.of` holds the rule |
| **Show the whole plan on the map** | Sets `session.journeyOverlayStyle`, which draws the rest of the plan under the leg in hand. Off by default |

**Stop off** is a short stop at the nearest place of one kind, such as toilets or a café,
before the planned stop. The menu lists each kind with the nearest place of that kind,
under "Go to the nearest one before *planned stop*. Your plan continues afterwards." A pick
calls `detour(stop)`, which inserts the stop-off before the active stop and routes to it
immediately. During the stop-off the bar reads "Stop off: *place*" and says what follows:
on the way, **Back to the plan** cancels it; at the place, **Continue** records it and
routes to the planned stop from the visitor's position. `StopOff` holds the text. The menu
is hidden while no kind of place is named, and during a stop-off. Which amenity kinds a
venue has is read from the venue's own amenity tags (`VenuePoi.nearestByAmenity`) rather than from a list of categories in the
app. What each kind is called comes from the SDK's amenity store: `amenities()` when a
visit starts, which downloads only while nothing is stored, then `amenity(id)`, a local
row read. The app keeps no titles of its own, so an amenity renamed on the server is
renamed here without a release.

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

`BlueiotMinimalApplication.onCreate` calls `Proximiio.startDiagnosticsRecording` before
any Activity exists. Lines recorded before that call returns are dropped. With
`capturesSDKLog = true` the log records fixes, floors, relay connection state and the
SDK's own warnings. The app adds these lines:

| Line | Where |
| --- | --- |
| `notifications: enabled` or `disabled` | At launch, `BlueiotMinimalApplication` |
| `scene: foreground` / `scene: background` | `MainActivity.onStart` / `onStop` |
| `wristband: <id>` | `Venue.follow` |
| `geofence enter · <name> · notified`, `… · not authorized` | `GeofenceNotifier` |
| `journey playback: <name>, <speed>x`, `journey playback failed: <reason>` | Debug builds, `DebugPositionSource` |

The file is `filesDir/proximiio-diagnostics/proximiio-diagnostics.log`. If it cannot be
written, the app runs without a log. The directory is excluded from backup and device
transfer (`res/xml/data_extraction_rules.xml`), and so is the SDK's encrypted token file
`io.proximi.sdk.xml`: a copy restored onto a new install cannot be decrypted there.

The log carries no credential. The SDK redacts the shapes it recognises and its own
application token. `VenueConfiguration.secrets` passes the application token and the
relay token as `additionalSecrets`, so both are removed wherever they appear. The
wristband number is written; it is printed on the band. The log rotates at 2 MB when
recording starts and keeps one previous generation,
`proximiio-diagnostics-previous.log`. A report is capped at 10 MB.

**The support report.** **Send diagnostics report** is in the long-press sheet on the map
and on the "Cannot reach the venue" screen. It calls `prepareDiagnosticsReport()` on the
running SDK, or `Proximiio.prepareDiagnosticsReport(context)` when the SDK did not
start. The SDK writes one ZIP to `cacheDir/proximiio-reports/`. `SupportReport` shares it
with `ACTION_SEND` through the app's own `FileProvider`, authority
`${applicationId}.diagnostics`, which exposes that directory only
(`res/xml/diagnostics_paths.xml`). The receiving app gets read access to the one file.
When the report fails the SDK's redaction audit, no file exists and the sheet shows the
reason.

The iOS app has no export: on iOS the log is retrieved from a development build's
container. An Android release build offers no such access, so this app adds the share
action.

The SDK installs no log sink of its own. In debug builds `App/SdkLogcat.kt` sets
`Proximiio.logSink` to forward each entry to `android.util.Log`, tagged
`Proximiio/<category>`, at `Proximiio.logLevel` (`INFO` by default). It is installed
before the recording starts, and the recording forwards each line to it, so the SDK's log
reaches both logcat and the file. Release builds install no logcat sink.

```sh
adb logcat -s 'Proximiio/*'
```

## Testing without the venue

There are two ways to see the app move without the venue's anchors: a journey played on
the phone, in debug builds, and a journey played into the sandbox relay.

### A journey played on the phone

Debug builds only. A journey drawn in MapTap is played on the phone in place of the
cloud relay: `JourneyPlaybackProvider` generates the positions locally, with no relay
and no LiveView run. The code is in the `debug` source set. A release build compiles
`src/release/…/DebugPositionSource.kt` instead, so the release APK contains neither the
picker nor the extra names.

**The picker.** The map shows a round button with a walking figure in the top-start
corner. The floor selector is on the other side, and the search bar and `JourneyBar` are
at the bottom. The button opens a sheet that lists the organisation's journeys from
`journeys()`, in the API's order, with distance, duration, waypoint count and levels
from `ProximiioJourneyTimeline`. A journey that fails `validationFailure()` is listed
disabled, with the reason in red. Tapping a playable journey opens its options: speed
(1x, 2x or 5x) and loop. **Play** detaches the relay and attaches the playback.

**The controls.** While a journey plays, the button is replaced by a panel in the same
corner: the journey name, the elapsed and total time, pause or resume, and stop. The
panel reads the provider's `diagnostics` once a second and shows **Finished** when a
journey that does not loop reaches its last waypoint. **Stop** detaches the playback and
attaches the relay for the stored wristband, as at launch.

**Launch extras.** The same playback starts at launch when the intent carries a
`journeyPlayback` extra:

```sh
adb shell am start -S -n io.proximi.blueiot.minimal/.MainActivity \
  --es journeyPlayback '<organisation uuid>:<journey uuid>' \
  --ef journeySpeed 2 \
  --ez journeyLoop true
```

| Extra | What it is |
| --- | --- |
| `journeyPlayback` | The journey id, `<organisation uuid>:<uuid>`, fetched with `fetchJourney(id)`. Only journeys of the token's organisation are found |
| `journeySpeed` | Optional. Journey seconds per real second, `0.5` to `10`; the SDK clamps other values. Default `1` |
| `journeyLoop` | Optional. `true` starts again after the last waypoint. Default `false` |

`-S` stops the running app first, so the extras reach a fresh start. The wristband prompt
still appears on first run. A journey that cannot be fetched attaches nothing; the
controls show the reason, and **Stop** attaches the relay. The picker and the launch
extras attach the provider through the same `DebugPositionSource.playJourney` call.
Changing the wristband (long-press the map) ends the playback and applies the launch
extras again. Android reads intent extras where the iOS app reads `-journeyPlayback`
launch arguments.

Playback runs with the screen locked (`runsInBackground = true`), as the relay does. The
diagnostics log records `journey playback: <name>, <speed>x` or `journey playback
failed: <reason>`.

### A journey played into the sandbox relay

`scripts/journey-run.mjs` plays a journey drawn in MapTap into the Proximi.io sandbox
relay as one wristband's positions. The app receives them through the same relay client
it uses at the venue, in debug and release builds alike, with no code change. The script
calls the LiveView run API at `https://live.proximi.fi`, the same API as the LiveView web
page. It is the same file as in the iOS app.

Prerequisites:

- Node 22 or later. The script has no dependencies.
- A LiveView login: a Proximi.io user account (email and password) of the app's
  organisation.
- In `secrets.properties`, `BLUEIOT_CLOUD_RELAY_URL` set to the sandbox relay host and
  `BLUEIOT_CLOUD_RELAY_TOKEN` set to the sandbox stream token. Both come from your
  Proximi.io contact. Rebuild and install after changing them
  (`./gradlew :app:installDebug`).

```sh
node scripts/journey-run.mjs login --token-file ~/.liveview-token
node scripts/journey-run.mjs list --token-file ~/.liveview-token
node scripts/journey-run.mjs start <journey_id> --token-file ~/.liveview-token \
  --relay sandbox --ground-floor 1 --loop
node scripts/journey-run.mjs status --token-file ~/.liveview-token
node scripts/journey-run.mjs stop <run_id> --token-file ~/.liveview-token
```

| Command | Effect |
| --- | --- |
| `login` | Prompts for the email and the password, the password without echo. Exchanges them for a Proximi.io user token and writes it to `--token-file` with mode 0600. The token is not printed |
| `list` | The organisation's journeys: id, name, waypoint count |
| `start` | Starts a run and prints its run id, walker and tag id. `--walker N` selects the organisation's wristband N on the relay; without it the lowest free walker is used. `--speed X` scales walking and dwelling. `--dry-run` prints the request and sends nothing |
| `status` | The organisation's runs, with state, walker and tag id |
| `stop` | Stops a run. `pause` and `resume` take a run id the same way |

The API accepts only a user token; an application token is refused with HTTP 403.
`--ground-floor` must equal `BLUEIOT_GROUND_FLOOR_NO` in `venue.properties`, `1`; that is
the default.

**Wristband id.** Enter the walker's wristband id in the app's wristband prompt.
LiveView's **Connect your app** card shows it for each walker; `start` and `status` print
it as `tag`. The id of a walker does not change between runs. Long-press the map to
change the stored id.

**Shared relay.** The sandbox relay is shared between organisations. Every app that
follows a walker's id receives its run. A looping run plays until it is stopped, for at
most 12 hours, and is not tied to a LiveView session. Stop it with `stop` after the
test. For the venue, restore the production relay values, rebuild, and enter the
visitor's wristband id.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

Seventy-three tests in fourteen classes. Each covers behaviour that fails without anything
on screen looking wrong. The screens are not tested; they hold no logic.

| Class | Tests | Covers |
| --- | --- | --- |
| `WristbandIdTests` | 6 | Every spelling of a tag id, and the canonical decimal form. An id read one way by the app and another way by the relay matches no tag, and no position arrives |
| `WristbandStoreTests` | 3 | The stored id comes back canonical, a stored foreign spelling names the same tag, and nothing stored means no wristband |
| `JourneyPersistenceTests` | 5 | A visit round-trips with stop order and state; ending clears it; an empty journey and an unreadable value are no visit. A visit that does not survive a launch loses the plan silently |
| `AmenityQueryTests` | 3 | `VenuePoi.nearestByAmenity`: the nearest place per amenity id, kinds taken from the venue data. A wrong read makes a venue with toilets look like a venue without any |
| `VenuePoiTapTests` | 4 | `VenuePoi.tapped` against the feature ids `onFeatureTap` reports. A tap resolved to the room under a POI routes to the wrong place |
| `GeofenceNotifierTests` | 5 | The notification title, body, log line and id. A privacy zone announced on a lock screen is what a privacy zone exists to prevent |
| `BackgroundPositioningTests` | 3 | `LocationPrompt.isOwed` and the background settings of `Venue.configuration`. A flag left at its default stops position updates minutes after the screen locks |
| `DiagnosticsTests` | 2 | No configured secret reaches the log verbatim, and the report is inside the directory the `FileProvider` exposes |
| `VisitRulesTests` | 15 | `StartOrder` (including that the waiting note is replaced or cleared once the first position is handled), `OrderAdvice`, `StopOff`, `GuidanceLine.offersReroute` and `VisitEnding`, and the two library behaviours behind them: `proposeOrder(VISITOR)` returns `null` without a position, and `JourneyNavigator.end()` switches single-route guidance off. An `end()` after `onEnd` leaves every later single route with no instruction and no off-route line |
| `DeviationPromptTests` | 7 | `DeviationPrompt.after`: the events that open, close and keep the deviation prompt, and its sentences |
| `SdkLogcatTests` | 2 | Each SDK log level maps to a logcat priority, and the tag names the category |
| `JourneyPlaybackLaunchTests` | 5 | Debug builds only. The launch extras in each form `adb` sends them |
| `JourneyPickerTests` | 6 | Debug builds only. Picker rows: playable and unplayable journeys, the `validationFailure()` reason, the summary, API order and journeys without an id; the loading, empty and error states; the number formats |
| `JourneyPlaybackSessionTests` | 7 | Debug builds only. The playback controls' states: start, pause, resume, finish, a failed fetch and stop; the launch extras; the options' log line |

`VisitRulesTests` (except `endingAVisitOnceKeepsSingleRouteGuidance`, which has no iOS
twin), `DeviationPromptTests`, `DiagnosticsTests.theLogNeverCarriesAConfiguredSecretVerbatim`,
`JourneyPickerTests` and `JourneyPlaybackSessionTests` keep the iOS test names. The last
three classes are in `src/testDebug`, because the code they test exists in debug builds
only.

JUnit 4, as the SDK uses. Robolectric only where `SharedPreferences`, the `FileProvider`
or a `ProximiioMapSession` is involved; `VisitRulesTests` gives its navigators a
`JourneyRouting` over one corridor and runs them with `kotlinx-coroutines-test`. The wristband spelling rule, the amenity query and the picker
rules are plain JVM tests with no Android in them.

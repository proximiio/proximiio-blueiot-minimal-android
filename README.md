# Proximi.io Blueiot — minimal reference app (Android)

A complete venue app in 2063 lines of Kotlin across thirteen files. It asks
for the visitor's wristband number once, shows the venue map, searches the
venue's places, routes to the one they pick, says which turn to take next, notes
on the lock screen which place they have just walked into, and walks a whole
afternoon of them in order — added to, reordered and detoured from as the
afternoon goes, with the phone in a pocket as often as not. The visitor is
positioned by the venue's own Blueiot anchors, through the Proximi.io cloud relay
— the phone scans nothing.

It exists to be read. Every file is short enough to read in one sitting, and the
comments mark the seams where your own product's code goes.

This is the Android twin of [`proximiio-blueiot-minimal-ios`](https://github.com/proximiio/proximiio-blueiot-minimal-ios),
file for file, with one file more than iOS has. Where the two differ, it is
because Android differs, or because the product asked for something here that iOS
does not do — each place is marked in the file and listed under **Place
notifications**, **In a pocket** and **The diagnostics log** below.

The iOS app is 1334 lines of Swift; this one is 1945 of Kotlin for the same
twelve files and the same behaviour. Two hundred of those are imports — Compose
names one symbol per line where `import SwiftUI` brings the whole framework — and
most of the rest is what SwiftUI hands out for free and Compose does not: a
search list, a form, `.onMove` with its edit button, and a `switch` that may fall
through to a default where Kotlin's `when` over a sealed interface names each
case. The comments are the same comments.

## What it deliberately is NOT

No settings screen. No diagnostics. No staff mode, no engine switches, no event
log, no offline package, no step list, no notification prompts. Nothing reorders
a visit on its own. No navigation library, no dependency injection, no
`ViewModel` — `remember` and `rememberSaveable` hold every piece of state this
app has. Those are all deliberate omissions — every knob is a thing you would
have to read, decide about and maintain.

If you want an instrument that shows all of them at once, that is the full demo
app (`proximiio-blueiot-android`), which is a field-debugging tool for the
Proximi.io team rather than a starting point for a product.

Positioning does carry on with the phone in a pocket or the screen locked. What
that asks of a visitor is one location prompt; what it asks of you is under
**In a pocket** below.

## Fill in the configuration

Three values, all build-time, none editable at runtime:

```sh
cp secrets.example.properties secrets.properties
$EDITOR secrets.properties
```

| Key | What it is |
| --- | --- |
| `PROXIMIIO_APPLICATION_TOKEN` | Your Proximi.io application token (dashboard → organisation → Application token) |
| `BLUEIOT_CLOUD_RELAY_URL` | The Proximi.io cloud relay carrying this venue's wristband positions. A bare host is enough |
| `BLUEIOT_CLOUD_RELAY_TOKEN` | That relay's stream token, sent as `Authorization: Bearer` |

`secrets.properties` is gitignored and is the only place a real *credential* may
live. `secrets.example.properties` is tracked, leaves the token keys empty and is
the file you copy. The build reads whichever of the two is present and falls back
to a Gradle property of the same name, so CI builds with nothing on disk. With
any key empty the app still builds and runs, and says on screen which key is
missing.

The one non-secret value lives in the tracked `venue.properties` — see **Floor
numbers** below. It is a file of its own rather than a line in
`gradle.properties`, because a venue's survey is not a build flag and should not
read as one; it is this repository's `Config/App.xcconfig`.

## Run it

```sh
./gradlew :app:installDebug
```

Or open the folder in Android Studio and press Run. Nothing else is needed: the
Gradle wrapper pins Gradle, and `local.properties` points at your Android SDK
(Android Studio writes it on first open).

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Dependencies are the published artifacts, pinned to exact versions in
`gradle/libs.versions.toml` — the same ones a customer resolves, from
`https://maven.eu.proximi.fi/releases/`, which needs no credentials to read.
There is no `mavenLocal()`.

| | |
| --- | --- |
| `io.proximi.sdk:proximiio` | `6.0.0-beta.6` |
| `io.proximi.sdk:proximiio-blueiot` | `6.0.0-beta.6` |
| `io.proximi.map:proximiio-map` | `6.0.0-beta.8` |
| AGP / Kotlin | `9.3.1` / `2.2.10` (AGP 9's built-in Kotlin) |
| compileSdk / targetSdk / minSdk | `37` / `36` / `26` |

MapLibre arrives through `proximiio-map` and must not be declared here.

## Where things are

Thirteen files, in the iOS app's folders. They are all one Kotlin package
(`io.proximi.blueiot.minimal`): thirteen files do not need a module boundary, and
the folders are there so the two apps read side by side.

| File | What it owns |
| --- | --- |
| `App/MainActivity.kt` | The order of things: wristband → location → SDK → map |
| `App/VenueConfiguration.kt` | The three build-time values |
| `Venue/WristbandId.kt` | The one spelling rule for a band id, and where it is stored |
| `Venue/Venue.kt` | Starting the SDK and attaching the cloud relay to one band |
| `Venue/VenuePoi.kt` | Turning the venue's features into searchable places |
| `Venue/JourneyStore.kt` | Keeping a visit across launches, and turning a picked place into a stop |
| `Venue/GeofenceNotifier.kt` | What a place notification says, and posting it — the one file with no iOS twin |
| `UI/WristbandPrompt.kt` | The first thing the app asks a person for, and the map credits |
| `UI/LocationPrompt.kt` | The other one, and the rule for when it is shown |
| `UI/VenueMapScreen.kt` | Map, search button, route, and where a visit starts |
| `UI/PoiSearchSheet.kt` | The search list — one place, or several |
| `UI/GuidanceLine.kt` | The turn-by-turn sentence, in this app's English |
| `UI/JourneyBar.kt` | The visit: the stop in hand, the plan, adding to it, detours, reordering |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | The app icon — a **placeholder**, see below |

**The icon is a placeholder.** It is an adaptive icon made of a flat colour
(`res/values/colors.xml`) and the letter V drawn as a vector
(`res/drawable/ic_launcher_foreground.xml`). It is there because Android shows
the system's default icon without one, not because anyone chose it. Replace the
foreground drawable with your product's and the background colour with its, or
point `ic_launcher.xml` at whatever you already have. Nothing else in the project
refers to the image.

## Two things worth knowing before you change anything

**Changing the wristband.** A returning visitor is never asked again, but the
number can be changed without reinstalling: **press and hold the map** and the
same prompt comes back as a bottom sheet. There is deliberately no visible
control — a visitor never needs it, and staff are told once. If your product
wants a visible one, `VenueMapScreen.isChangingWristband` is the single switch.
The gesture is MapLibre's own `addOnMapLongClickListener`, registered inside the
`configure` lambda's `onStyleLoaded`, so the map keeps its pan, pinch and rotate.

The same sheet lists the **map credits**. The map hides MapLibre's attribution ⓘ
(`chrome = MapCanvasChrome.BARE` on the `MapOptions` in `VenueMapScreen`), and
hiding it moves the OpenStreetMap (ODbL) and MapLibre credits into the app: they
are `ProximiioMapSession.attributions`, and an app that hides the ⓘ must show
them somewhere reachable from the map — here, behind the long press. MapLibre
strips the leading `©` from each credit on the way in; the app puts it back.

**Floor numbers.** The relay reports the venue engine's floor numbers, and the
SDK turns them into Proximi.io floor ids on its own: an engine floor number *is*
a Proximi.io floor level, and the SDK already syncs every floor with its level.
The app passes no `floorNoMap` — passing one would switch that derivation off —
and a number the venue has no floor for is logged rather than quietly drawn on a
blank level.

One integer is left, in the tracked `venue.properties`, because it is the one
thing the SDK cannot know:

| Key | What it is |
| --- | --- |
| `BLUEIOT_GROUND_FLOOR_NO` | Which floor number the engine calls the ground floor. Proximi.io calls it level `0`; Blueiot LocalSense venues usually start at `1`, and this one does. Empty = `0`, and then this key is not needed at all |

It is the venue's survey rather than a credential, so it is tracked with this
venue's working value, and it goes away the day the deployment is renumbered.

**In a pocket.** Positioning carries on when the screen locks, and it takes four
things — all four, because each one missing looks the same: the dot stops within
minutes of the screen going off, as if the relay had died. The SDK's own
background guide puts the first two like this: *"These are two switches, not one,
and an RTLS integration needs both thrown. `serviceOptions` keeps the process
alive; `runsInBackground` keeps the socket open."*

| | Where | What a missing one looks like |
| --- | --- | --- |
| `serviceOptions` on the SDK configuration | `Venue.configuration` | Android freezes the process within minutes of the last Activity stopping. No socket read, no coroutine tick. `relayOnly` leaves this at `null`, which is foreground-only positioning |
| `runsInBackground = true` on the relay configuration | `Venue.follow` | The service sits there healthy while the SDK pauses the relay provider a second after the screen locks |
| The manifest permissions | `AndroidManifest.xml` | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` and `WAKE_LOCK`. From API 34 the platform refuses `startForeground` without the permission for the service's type, and the SDK derives that type from the grants it holds |
| One location grant | `LocationPrompt` | Without it the SDK cannot hold a `location` foreground service at all, and there is no other type a relay-only app qualifies for |

Those four are also the whole of what the place notifications need: a geofence
note lands on the lock screen because the process that evaluates the geofence is
the one the service keeps alive.

Coarse location is all this app asks for, and it is not asked for the position:
the venue's anchors place the wristband, and the phone's own location never
enters it — the grant is what lets the service exist. `POST_NOTIFICATIONS` is
asked with it on API 33+ so the service's ongoing row is visible; a refusal
leaves the service running invisibly and costs nothing else. Nothing asks for
background location, and **there is no Bluetooth permission anywhere** — the
phone scans nothing, and `ProximiioConfiguration.relayOnly` turns the SDK's own
iBeacon, Eddystone and UWB sources off. Asking a visitor for a radio the app
never turns on would be a question with no honest answer.

The ask is shown once, gated by the app's own flag rather than by Android:
Android has no `notDetermined`, and `shouldShowRequestPermissionRationale` cannot
tell "never asked" from "denied twice". A refusal is an answer, and nobody is
asked twice.

**Doze.** A foreground service exempts the app from most of Doze while it runs,
which is why `ProximiioServiceOptions.holdsWakeLock = true` is set here: the
position source is a socket, a socket is read on the CPU, and in Doze the frame
would wait for the next maintenance window. The lock lives only as long as the
service and the SDK's own 30-minute timeout is the safety net under it. What none
of this exempts you from is what happens after the service stops, and OEM process
management on top of Doze may still reduce delivery. The SDK never asks for
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and neither does this app. Test it with
`adb shell dumpsys deviceidle force-idle`.

**Turn-by-turn.** `session.guidanceRules = RouteFollowRules.VENUE_WALK` in
`VenueMapScreen` is the whole opt-in. The map library then follows the route it
is already drawing and republishes `session.guidance` on every fix; the bottom
bar shows the turn in hand, the metres still to walk to it, and — plainly, once —
that the visitor has arrived. The instruction sentences are the app's, in
`GuidanceLine.instruction`, because `RouteManoeuvre.Kind` carries no display
strings and no library should choose a venue's language for it.

Leaving the route is reported, not acted on: `isOffRoute` latches after three
fixes beyond twelve metres and clears itself on the first fix back inside, so the
bar says so and this app adds no detector and no re-routing of its own.

**Place notifications.** Walk into one of the venue's geofences and a note says
so — *"You are now inside Main Hall."* — and walking out replaces that
same row with *"You have left Main Hall."* The whole of it is
`Venue/GeofenceNotifier.kt`: one channel ("Place updates"), the geofence's name
as the title, one plain sentence as the body, and one notification id per
geofence, so an exit replaces its enter instead of leaving two rows to reconcile.
Tapping one opens the map. **This is the one deliberate divergence from the iOS
app, which posts no notifications at all** — it lists "no notification prompts"
among the things it is not, and this is Android answering a product ask iOS has
not been given. Geofences are drawn in Proximi.io Portal, not here; an area the
venue never named says nothing, because a geofence id means nothing to a visitor.
Privacy zones are never announced, in either direction: a privacy zone exists so
that the visitor's presence inside it is not reported, and a lock screen anyone
can read is the last place to report it. The permission is the
`POST_NOTIFICATIONS` that `LocationPrompt` already asks for on API 33+ — there is
no second ask, and a refusal costs the banners and nothing else.

**A visit.** The list button next to the search opens the same search sheet in
multi-select; the places tapped, in that order, become a `Journey`. From there
`JourneyNavigator` owns every route computation in the walk — it draws and
follows one leg at a time through the same session the map is already using, and
re-routes a leg by itself when the visitor wanders, which a single route does not
do.

The bar shows the stop in hand, what is left (`overview.remainingMeters`, its
ETA, and any leg routing refused), and **Continue** when the visitor has arrived.
Arrival does not advance on its own: somebody stands in front of an exhibit for a
length of time nobody can guess, so the rule is `Manual`.

**Your visit** — the list button on the bar — is where the plan is changed, and
it does four things:

| | |
| --- | --- |
| **+** | Opens the same multi-select search the visit was planned in, and `JourneyNavigator.add` puts each pick after everything still to be walked. The leg in hand is left alone. A place the plan already holds is named on the sheet rather than dropped without a word. The **+** is there when the visit is done too: adding revives it, and the new stop becomes the one being walked to |
| **Drag** | Long-press a row and drag to reorder what is still ahead. The rows *are* `JourneyNavigator.reorderableStops` — the list `move(stopId, toIndex)` indexes into — so the app holds no second copy of which stops may move. SwiftUI hands `.onMove` out for nothing and Compose has no equivalent, so this one gesture is the app's, in `JourneyBar.ReorderableStops` |
| **Save N m by reordering** | A shorter order, measured. The library never applies one and neither does the sheet; a tap does. It is re-measured whenever the stops change, because `apply` ignores a proposal that no longer describes the journey |
| **Show the whole plan on the map** | Draws the rest of the afternoon under the leg in hand. Off by default |

"Stop off" is a detour. Which kinds of place a venue has is read off the venue's
own amenity tags rather than from a list of categories in this app, and what each
kind is *called* comes from the SDK's amenity catalogue — `amenities()` once when
a visit starts, kept as a map for the rest of it. The app keeps no titles of its
own, so an amenity renamed on the server is renamed here without a release.

The visit is written to `SharedPreferences` whenever it changes and restored on
launch, so an afternoon survives the app being closed — `JourneyCodec` encodes it
and each stop carries its own state. `JourneyCodec.decode` throws on text it
cannot read, and `JourneyStore` is where that becomes "no visit" rather than a
crash on launch.

**Following the visitor.** The button at the right of the bottom bar recentres
the map on the wristband. It is one call into the map library's own follow camera
(`ProximiioMapSession.recentre()` plus `followMyFloor()`) — this app writes no
camera of its own. Panning, pinching or rotating the map releases the follow; the
library notices the hand and publishes it through `ProximiioMapSession.cameraMode`,
which is what fills or hollows the button's symbol. It is disabled until there is
a position at all: a button that looks live and does nothing is worse than one
that says so.

## The diagnostics log

**There is not one yet on Android.** The iOS app records everything positioning
sees from its first statement — fixes, floors, the relay coming and going, the
SDK's own warnings, and `scene: background` / `scene: foreground` as the app
leaves and returns to the screen — into a file support can ask a visitor for, and
strips the configured credentials out of it on the way. The Android SDK has no
equivalent of `Proximiio.startDiagnosticsRecording` /
`recordDiagnosticsEvent` yet; it is on the SDK's parity backlog, and this app
will pick it up when it lands.

Two consequences, both marked in the code. `MainActivity` and `Venue` have no
recording to start and no events to record. And `DiagnosticsTests`, which on iOS
proves that the log never carries a configured secret verbatim, is **not ported**
— there is nothing to prove, and a stub that passed would be worse than an absent
file. `BackgroundPositioningTests` says so in its header.

In the meantime, `adb logcat` carries the SDK's own log, and nothing in this app
writes a credential to it.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

Twenty-five of them — iOS's twenty, minus the diagnostics one above, plus one
because Android has two background switches where iOS has one, plus five for the
place notifications iOS does not have — and all five subjects are chosen for the
same reason: they fail without anything on screen looking wrong. A wristband read one way by the app and another way by the
relay matches nothing, and the symptom is a dot that never arrives. A visit that
does not survive a launch loses a visitor's afternoon in silence. An amenity
query that reads the venue's data wrongly makes a venue with toilets look like a
venue without any. A background switch left at its default, or a location ask
that nags or never fires, stops the dot minutes after the screen locks. A
notification sentence naming the wrong place reads perfectly, and a privacy zone
announced on a lock screen is the one thing a privacy zone exists to prevent. The
screens are not tested; a layout that is wrong is a layout you can see.

JUnit 4, as the SDK uses. Robolectric only where `SharedPreferences` needs it —
the wristband spelling rule and the amenity query are plain JVM tests with no
Android in them at all, which is the point.

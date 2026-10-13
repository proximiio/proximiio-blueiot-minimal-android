# Changelog

All notable changes to this app are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- The wristband binding file `io.proximi.sdk.blueiot.binding.xml` is excluded from cloud backup and device transfer, like the SDK token file. A restored copy could not be decrypted on the new phone.

### Added

- **Smooth position** switch in the long-press sheet on the map, in debug and release
  builds. Off draws the position exactly on each update from the venue, without
  smoothing, for testing. The change applies to the map at once. On is the default.
- **Smoothing** section in the long-press sheet: one decimal field per
  `PositionSmoothingTuning` value, each titled with its unit and the map default, and
  **Reset to defaults**. An empty or invalid field uses the default, a comma is read as the
  decimal separator, and the map clamps the value. The values apply at once, only while
  Smooth position is on, and are stored under the iOS keys.
- **Language** section in the long-press sheet: **Map language** (Automatic, English,
  Arabic) sets `MapOptions.language`, and the search titles use the same language. Stored
  under `mapLanguage`; the change applies at once.
- `res/xml/locales_config.xml` declares English, the app's one language, so the map's
  Automatic language is English as on iOS.
- The diagnostics log records the smoothing values and the map language in use.

### Changed

- A visit moves to the next stop once the visitor is 8 m from the stop reached (`JourneyRules.Advance.OnDeparture(8.0)`); **Continue** still moves on at once. A planned stop the visitor walked past counts as visited (`JourneyPassedStopRule.VENUE_WALK`: within 5 m with an accuracy of 8 m or better, then more than 9 m away). Arrival, for a single route and for each stop, is within 2.5 × the fix accuracy, 5–10 m, for 1 s (`RouteFollowRules.ArrivalRadius.FromAccuracy`). The rules are `JourneyRules.VISIT` and `RouteFollowRules.VISIT` in `VisitRules.kt`.
- The map draws the route ahead only, and the path the visitor walked as a trail of the fixes of the last 15 minutes (`MapOptions.Trail.LastMinutes(15.0)`, `#3F69FF`, 6 dp, 30 %).
- The app pins Proximi.io map 6.0.0-beta.20: the blue dot is drawn in the same frame as the follow camera, so it no longer trails the camera, and the follow camera moves on the first fix whatever style is shown.
- The app pins Proximi.io map 6.0.0-beta.19: a detour that is done or skipped no longer keeps its marker on the map (`JourneyStopMarkerStyle.showsFinishedDetours`), the session can clear the walked trail (`clearTrail()`), and a route line can be drawn whole instead of split at progress (`RouteLineStyle.splitsAtProgress`).
- The app pins Proximi.io SDK 6.0.0-beta.23 and map 6.0.0-beta.18: journey stops can be ordered by walking time, numbered stop markers and walked legs are drawn on the map, and a stop walked past can count as visited (`JourneyRules.passedStops`). The SDK adds route durations.
- The app pins Proximi.io map 6.0.0-beta.17 (SDK stays 6.0.0-beta.22): journey stops can be marked done, skipped, reopened or made active, and a visit restored during a detour is tracked from `start()`. The new `JourneyEvent.StopSkipped` leaves the route prompt as it is, like the other stop events.
- The app pins Proximi.io SDK 6.0.0-beta.22 (map stays 6.0.0-beta.16): polygon geofences now fire, each geofence carries its `remoteId`, and level changers attach to the nearest corridor in routing.
- The app pins Proximi.io SDK 6.0.0-beta.21 and map 6.0.0-beta.16: route options and route duration, the runtime service notification, and compass heading on the map.
- The app pins Proximi.io SDK 6.0.0-beta.19: right after a wristband connects, the status reads awaiting the first fix instead of offline or silent.

- The app positions through the SDK's wristband binding (`BlueiotWristbandBinding`,
  relay-api) instead of the BlueIoT cloud relay. The wristband prompt binds the typed
  label, the map shows the session's state and **End visit**, and an ended session shows
  its reason on the prompt. Requires Proximi.io SDK `6.0.0-beta.18`.
- Configuration keys: `BLUEIOT_RELAY_URL` (default in `venue.properties`, the Proximi.io
  sandbox) and `BLUEIOT_RELAY_APP_TOKEN` (in `secrets.properties`).

### Removed

- `BLUEIOT_CLOUD_RELAY_URL`, `BLUEIOT_CLOUD_RELAY_TOKEN` and `BLUEIOT_GROUND_FLOOR_NO`.
  The relay-api sends Proximi.io floor levels, so the app sets no floor mapping.
- The stored wristband id. The SDK stores the session.

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

- The app pins Proximi.io SDK 6.0.0-beta.20 and map 6.0.0-beta.15: translated place and floor titles, and `PositionSmoothingTuning`.
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

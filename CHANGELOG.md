# Changelog

All notable changes to this app are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Smooth position** switch in the long-press sheet on the map, in debug and release
  builds. Off draws the position exactly on each update from the venue, without
  smoothing, for testing. The change applies to the map at once. On is the default.

### Changed

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

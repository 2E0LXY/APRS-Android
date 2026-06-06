# APRS Net – Android

Native Kotlin/Compose Android client for the [Advanced APRS Go Server](https://github.com/2E0LXY/Advanced-APRS-Go-server), connecting to [www.aprsnet.uk](https://www.aprsnet.uk).

[![Release](https://img.shields.io/github/v/release/2E0LXY/APRS-Android)](https://github.com/2E0LXY/APRS-Android/releases)
[![Licence: GPL v3](https://img.shields.io/badge/Licence-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

---

## Features

### Map
- Live osmdroid (OpenStreetMap) map with real-time APRS station markers
- Marker clustering at low zoom levels
- Tap any marker to open a station detail dialog (type, position, distance, bearing, path, comment)
- Send message or add contact directly from the station detail dialog
- **My Location FAB** (bottom-right) — centres and zooms map to your GPS fix; long-press to beacon immediately
- **Filter FAB** (bottom-left) — quick-access overlay panel with type toggles and distance filter
- Station type classification: HAM, Weather, Gliders, Ships, LoRa, MMDVM/Pistar, Objects, Other
- **Live AIS ships** — server subscribes to aisstream.io and relays marine vessel positions in real time
- Distance filter: All / 50 km / 100 km / 250 km / 500 km (haversine from your GPS fix)
- APRS objects and items correctly plotted alongside regular position stations

### Messaging
- SMS-style conversation threads per callsign
- Outgoing bubbles turn green when ACKed by the recipient
- Incoming messages auto-ACKed per APRS spec
- Failed messages retry automatically
- Keyboard correctly pushes message input above it (IME-aware layout)

### Beaconing
- Smart beaconing algorithm — beacons frequently when moving, slows when stationary
- Manual beacon via long-press on the My Location FAB
- Configurable APRS symbol, comment, and status text
- Status text sent as a separate APRS `>` status packet alongside each position beacon
- Foreground service keeps beaconing active when the app is in the background

### Settings
- Member account login (callsign + APRS-IS passcode)
- Appearance (theme)
- Position / Beaconing — mode, symbol, comment, status text
- Station type filters (persisted, immediately applied)
- **Account-synced drop filters** - hide Pi-Star / MMDVM / DMRGateway / ircDDB / D-STAR / APDESK; preferences sync automatically with your member account on `aprsnet.uk` so the same choices appear on the web map
- Notifications / quiet hours
- Status section — live WebSocket state, upstream connection, station count, last beacon, server info
- **Help** — full in-app instructions for every feature
- **Close App** — terminates the app and all background services cleanly

### Stations
- Searchable list of every heard station, sorted by distance from you

### Contacts
- Saved callsigns with aliases

### Sub-screens (accessible from Settings back-navigation)
- Weather — UK Met Office severe weather warnings and CWOP weather stations
- ISS Tracker — live International Space Station position
- Utilities — APRS-IS passcode calculator and Maidenhead locator converter
- Admin — server admin panel (requires admin credentials)

---

## Quick Start

1. Install the APK and open the app
2. Tap **Settings** (gear icon in bottom nav)
3. Enter your callsign and APRS-IS passcode under **Credentials**
4. Set your beaconing mode to **Smart** under **Position / Beaconing**
5. Tap **Save** — the map will populate with live stations within seconds

Receiving works without credentials. Sending messages and beaconing require a valid callsign/passcode.

---

## Map Filter Panel

Tap the funnel icon (bottom-left of the map) to open the quick-filter panel:

| Toggle | Hides / shows |
|---|---|
| HAM | Standard ham/APRS stations |
| WX | CWOP weather stations |
| Ships | AIS maritime vessels |
| Gliders | OGN glider/aircraft trackers |
| LoRa | LoRa-APRS digipeaters and trackers |
| MMDVM | MMDVM / Pistar hotspots |
| Other | Objects, repeaters, digis, unclassified |

Distance chips (All / 50 km / 100 km / 250 km / 500 km) filter stations beyond the selected radius from your GPS fix.

---

## Building

Requires JDK 17 and the Android SDK.

```bash
git clone https://github.com/2E0LXY/APRS-Android
cd APRS-Android
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

For a signed release AAB (Play Store):

```bash
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file=/path/to/aprs-net-release.jks \
  -Pandroid.injected.signing.store.password=<storepass> \
  -Pandroid.injected.signing.key.alias=aprsnet \
  -Pandroid.injected.signing.key.password=<keypass>
```

GitHub Actions builds and publishes both `APRS-Net-Android.aab` (Play Store) and `APRS-Net-Android.apk` (sideload) on every `v*` tag.

---

## Changelog highlights

| Version | Key changes |
|---|---|
| v2.4.6 | Fix ships/objects invisible (APRS object DTI not handled in WebSocket) |
| v2.4.5 | Map filter overlay FAB, distance filter, ship detection improvements, instant filter apply |
| v2.4.4 | Fix station type classification — dual JSON/raw path conflict stamping everything as HAM |
| v2.4.3 | Fix fullCallsign SSID bug (was sending `-9` instead of `2E0LXY-9`); APRS status packets |
| v2.4.2 | Fix IME keyboard covering messages; filter switches now apply immediately |
| v2.4.1 | Fix IME `adjustResize` for message input |
| v2.4.0 | Settings tab replaces Status; map overlay fix (AndroidView z-order); SimpleBackBar |
| v2.3.x | Kotlin/Compose rewrite — settings, station detail dialogs, conversation list, dark glass theme |

---

## Server

Fixed to [www.aprsnet.uk](https://www.aprsnet.uk). See [Advanced-APRS-Go-server](https://github.com/2E0LXY/Advanced-APRS-Go-server) for the server source.

---

## Licence

GNU General Public Licence v3 — © 2026 Daren Loxley 2E0LXY

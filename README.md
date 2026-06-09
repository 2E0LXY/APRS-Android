# APRS Net – Android

Native Kotlin/Jetpack Compose Android client for [aprsnet.uk](https://www.aprsnet.uk).

[![Release](https://img.shields.io/github/v/release/2E0LXY/APRS-Android)](https://github.com/2E0LXY/APRS-Android/releases)
[![Licence: GPL v3](https://img.shields.io/badge/Licence-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

---

## Also available on

| Platform | Repository | Download |
|----------|------------|----------|
| **iOS** | [2E0LXY/APRS-iOS](https://github.com/2E0LXY/APRS-iOS) | [Releases](https://github.com/2E0LXY/APRS-iOS/releases) |
| **Windows / Linux desktop** | [2E0LXY/APRS-Client](https://github.com/2E0LXY/APRS-Client) | [EXE / DEB](https://github.com/2E0LXY/APRS-Client/releases) |
| **Self-host the server** | [2E0LXY/Advanced-APRS-Go-server](https://github.com/2E0LXY/Advanced-APRS-Go-server) | [Install guide](https://github.com/2E0LXY/Advanced-APRS-Go-server#installation-debian-12) |

---

## Features

### Map
- Live osmdroid map with real-time APRS station markers, clustering, trails
- **TOCALL-based station classification** (v2.5.6+) — firmware-accurate detection:
  - `APLRG*` / `APLRT*` / `APLG*` → LoRa iGate/tracker
  - `APZDMR*` / `APDG*` → MMDVM/DMR gateway
  - `APOG*` → OGN receiver
  - Callsign-string heuristics retained as fallback
- Tap any marker to open a station detail dialog (type, position, distance, bearing, path, comment)
- **My Location FAB** — centres map to GPS fix; long-press to beacon immediately
- **Filter FAB** — type toggles and distance filter (50 / 100 / 250 / 500 km)
- Station types: Ham, Weather (CWOP), Ships/AIS, Gliders (OGN), LoRa, MMDVM/DMR, Objects

### AIS Ships
- **Server relay** — server subscribes to aisstream.io and relays live vessel positions
- **Direct connection** (v2.5.7+) — optional `aisstream.io` API key in Settings for an independent direct feed; configure separately from the server key to avoid free-tier conflicts

### Messaging
- SMS-style conversation threads per callsign
- Outgoing bubbles turn green on ACK; incoming messages auto-ACKed per spec
- Atmospheric message backgrounds — 7 selectable styles (v2.5.4+)
- Emoji rendering in message bodies (v2.5.2+)

### Beaconing
- Smart beaconing — frequent when moving, slow when stationary
- Configurable symbol, comment, and APRS status text
- Foreground service keeps beaconing active in the background (v2.5.3+)

### Settings
- Callsign, APRS-IS passcode, SSID
- Member account login — auto-fills passcode and syncs map filter preferences
- **Account-synced drop filters** — hide Pi-Star/MMDVM/D-STAR/APDESK; preferences sync with your aprsnet.uk member account so the same choices appear on the web map and iOS app
- Position mode, beacon comment, symbol
- Station type filters (all 7 types, persisted and immediately applied)
- Direct aisstream.io API key (optional, separate from server key)
- Notifications and quiet hours

### Sub-screens
- Weather (UK Met Office severe weather warnings + CWOP stations)
- ISS Tracker (live position)
- Utilities (passcode calculator, Maidenhead converter)
- Admin (server admin panel)

---

## Quick Start

1. Install the APK — download from [Releases](https://github.com/2E0LXY/APRS-Android/releases)
2. Open the app → **Settings** (gear icon)
3. Enter callsign and APRS-IS passcode under **Credentials**
4. Set beaconing mode to **Smart** under **Position / Beaconing**
5. Tap **Save** — live stations appear within seconds

Receiving works without credentials. Sending and beaconing require a valid callsign/passcode.

---

## Building

Requires JDK 17 and Android SDK.

```bash
git clone https://github.com/2E0LXY/APRS-Android
cd APRS-Android
./gradlew assembleDebug
```

Signed release AAB (Play Store):

```bash
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=<storepass> \
  -Pandroid.injected.signing.key.alias=aprsnet \
  -Pandroid.injected.signing.key.password=<keypass>
```

GitHub Actions publishes `APRS-Net-Android.aab` and `APRS-Net-Android.apk` on every `v*` tag.

---

## Changelog

| Version | Changes |
|---------|---------|
| v2.5.8 | Fix missing AprsApi import after refactor |
| v2.5.7 | Direct aisstream.io AIS — `AisWebSocket`, `aisApiKey` setting, AIS card in Settings |
| v2.5.6 | TOCALL-based LoRa/MMDVM/OGN classification in `PacketParser.classify()` |
| v2.5.5 | Fix AlertDialog and BoxScope imports in MainActivity |
| v2.5.4 | Atmospheric message section backgrounds |
| v2.5.3 | Background reliability + monochrome status bar icon |
| v2.5.2 | Emoji rendering in messages (display-time only) |
| v2.5.1 | Robust passcode auto-fill on member login |
| v2.5.0 | Sync map filter preferences with server member account |
| v2.4.9 | Filter panel diagnostic overlay |
| v2.4.8 | Pi-Star ACK drop fix; SSID source rejection fix |
| v2.4.6 | Fix ships/objects invisible (APRS object DTI not handled) |
| v2.4.5 | Map filter overlay FAB, distance filter, ship detection improvements |
| v2.4.4 | Fix station type classification — dual JSON/raw path conflict |
| v2.4.3 | Fix fullCallsign SSID bug; APRS status packets |
| v2.4.2 | Fix IME keyboard covering messages; filter switches apply immediately |
| v2.4.0 | Settings tab replaces Status tab; map overlay z-order fix |
| v2.3.x | Kotlin/Compose rewrite — dark glass theme, conversation list, dialogs |

---

## Licence

GNU General Public Licence v3 — © 2026 Daren Loxley 2E0LXY

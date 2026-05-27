# APRS Net - Android

A fully native Android client for the
[Advanced APRS Go Server](https://github.com/2E0LXY/Advanced-APRS-Go-server),
talking directly to www.aprsnet.uk.

This is a ground-up native rewrite (v2.x) in Kotlin and Jetpack Compose.
The earlier Capacitor wrapper is preserved at the `wrapper-final` tag.

## Features

- **Live map** - native osmdroid map rendering the same OpenStreetMap
  tiles as the website, with live APRS station markers
- **Messaging** - SMS-style chat with conversation threads; outgoing
  bubbles turn green when the message is ACKed; incoming messages are
  auto-ACKed; failed messages retry automatically
- **Notifications** - chat-style notifications for incoming messages;
  tap to open the conversation, or reply inline from the notification
- **Smart beaconing** - your GPS position is shown on the map and
  beaconed to APRS-IS using the standard smart-beaconing algorithm;
  continues in the background when the app is closed
- **Contacts** - saved callsigns with aliases
- **Stations** - searchable list of every heard station, sorted by
  distance from you
- **Status** - live server status and connection state
- **Weather** - UK Met Office severe weather warnings and CWOP
  weather stations
- **ISS tracker** - live International Space Station position
- **Admin** - sign in with the server admin credentials to view the
  live server configuration
- **Utilities** - APRS-IS passcode calculator and Maidenhead
  locator converter

Everything runs over a single auto-reconnecting WebSocket plus a few
REST calls. All data is persisted locally with Room.

## Built with

- Kotlin + Jetpack Compose
- osmdroid - native OpenStreetMap rendering
- OkHttp - WebSocket + REST
- Room - local persistence
- Coroutines / Flow
- Google Play Services Location - smart beaconing

## Setup

Install the APK, open **Settings** from the top-right menu, and enter
your callsign and APRS-IS passcode. This unlocks message sending and
position beaconing. Receiving works without credentials.

## Server

Dedicated to www.aprsnet.uk - the server is fixed.

## Download

See [Releases](https://github.com/2E0LXY/APRS-Android/releases) for the
APK. Every push to `main` builds a debug APK via GitHub Actions; a
`v*` tag publishes a release.

The APK is debug-signed for sideloading. To install, enable
"install from unknown sources" for your browser or file manager.

## Building

Requires JDK 17 and the Android SDK.

```
git clone https://github.com/2E0LXY/APRS-Android
cd APRS-Android
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Licence

GNU General Public Licence v3 - (c) 2026 Daren Loxley 2E0LXY
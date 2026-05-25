# APRS Net - Android

A fully native Android client for the
[Advanced APRS Go Server](https://github.com/2E0LXY/Advanced-APRS-Go-server).

This is a ground-up native rewrite (v2.x). The earlier Capacitor wrapper is
preserved at the `wrapper-final` tag.

## Built with

- Kotlin + Jetpack Compose
- osmdroid - native map rendering the same OpenStreetMap tiles as the website
- OkHttp WebSocket - single live connection to the server
- Room - local persistence
- Coroutines / Flow

## Features

Stage 1 (current): live map of APRS stations over a native osmdroid map,
fed by the server WebSocket; auto-reconnecting connection with status.

Planned (staged): SMS-style messaging with ACK (chat bubbles that turn green
when delivered), native message notifications with inline reply, contact
list, smart-beaconing GPS that places you on the map, stations list,
status / analytics, weather, ISS tracking, utilities, and full admin.

## Server

Dedicated to www.aprsnet.uk - the server is fixed.

## Download

See [Releases](https://github.com/2E0LXY/APRS-Android/releases) for the APK.
Every push to `main` builds a debug APK via GitHub Actions; a `v*` tag
publishes a release.

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
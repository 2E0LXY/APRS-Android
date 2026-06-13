# Auto-publishing to Play Console (internal testing)

Every push of a `v*` tag now runs `./gradlew publishBundle --track internal`
via [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
after the signed AAB is built. This uploads the bundle straight to the
**Internal testing** track, no manual upload needed.

If the `PLAY_PUBLISHER_KEY` secret isn't set, this step is skipped (logged,
not a failure) — the GitHub Release with the `.aab`/`.apk` attachments still
gets created as before.

## One-time setup

1. **Enable the Play Developer API** (if not already):
   Play Console → Setup → API access → "Enable" / link a Google Cloud
   project (developer account `4856474743825372360`).

2. **Create a service account**:
   - In the linked Google Cloud project, go to IAM & Admin → Service
     Accounts → Create service account (e.g. `aprs-net-ci`).
   - No project-level roles needed — permissions are granted in Play
     Console, not GCP IAM.
   - Create a JSON key for the service account and download it.

3. **Grant Play Console access**:
   - Play Console → Setup → API access → find the new service account
     under "Service accounts" → Manage Play Console permissions.
   - App permissions: grant access to **APRS Net** (`uk.aprsnet.client`).
   - Permissions needed: **Releases** → "Release apps to testing tracks"
     (Edit). Production release permission is *not* required, since CI
     only targets `internal`.

4. **Add the GitHub secret**:
   - Repo → Settings → Secrets and variables → Actions → New repository
     secret.
   - Name: `PLAY_PUBLISHER_KEY`
   - Value: the full contents of the downloaded service account JSON file
     (paste as-is).

5. **First release after setup**:
   - The very first upload to a track via the API must already have at
     least one release present from a manual upload (Play requires this
     for new apps/tracks — already satisfied here, since `34 (2.6.0)` is
     live on Internal testing).

## Manual publish (local)

Not needed in normal use, but if you ever want to publish from your own
machine: save the service account JSON as
`play-publisher-key.json` in the repo root (gitignored), then run:

```sh
./gradlew publishBundle --track internal \
  -Pandroid.injected.signing.store.file=$(pwd)/aprs-net-release.jks \
  -Pandroid.injected.signing.store.password=<...> \
  -Pandroid.injected.signing.key.alias=<...> \
  -Pandroid.injected.signing.key.password=<...>
```

# RDM Radio

(Project folder: BYDRadioCompanion.) Internet radio app for the BYD Song L (DiLink 5.0) and other Android head units.
Kotlin + Jetpack Compose + Media3/ExoPlayer.

## Features
- Internet streaming only (no tuner/antenna needed).
- **Clean header:** menu button + two quick buttons: **Favorites** and **home country** (Egypt).
- **Side menu (☰):** all categories and tools
  - Stations: Favorites, All stations
  - Region: Egypt (home), Regional (Arab world), International
  - Station type: FM broadcasters (their online streams) / Web-only radio
  - Settings: home country, refresh station list, equalizer
  - Tools: add station, import/export, stream diagnostics
- Built-in **Nogoum FM 100.6** (Egypt): `https://audio.nrpstream.com/listen/nogoumfm/radio.mp3`
  (in `app/src/main/assets/stations.json`; merged into existing installs on launch)
- Station directory from [radio-browser.info](https://www.radio-browser.info): Egypt,
  Arabic-language and worldwide top stations, refreshed weekly.
- Favorites, custom stations (country, type, backup URL), JSON import/export, "Open with"
- Stream fixes: browser User-Agent, http↔https redirects, cleartext allowed, automatic fallback
  to backup addresses, 20 s startup timeout; ☰ → Stream diagnostics shows why a station failed
- 7-band equalizer with presets, Loudness switch and automatic volume compensation
- Background playback, reconnect after signal loss, resume after the system kills the app
- Audio focus: pauses for phone calls, ducks under navigation prompts
- Layout adapts to landscape, portrait (rotating screen) and split-screen

## App name and icon
- Name: `app/src/main/res/values/strings.xml` → `app_name` ("RDM Radio").
- Icon: `res/drawable/ic_launcher_foreground.xml` (signal-wave glyph) on the dark background in
  `res/mipmap-anydpi-v26/ic_launcher.xml`. To use your own logo: Android Studio → right-click
  `res` → **New → Image Asset** → Launcher Icons (Adaptive and Legacy), name `ic_launcher`.
  Keep the logo inside the inner ~66% circle so the round mask doesn't cut it.

## Build the APK
Needs Android Studio (or JDK 17 + Android SDK 35) and an internet connection the first time.

1. Open this folder in Android Studio and let Gradle sync.
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   or from a terminal: `./gradlew assembleDebug`
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

For a signed release build, copy `keystore.properties.example` to `keystore.properties`,
fill it in, then run `./gradlew assembleRelease`.

Unit tests: `./gradlew testDebugUnitTest`

## Install on the car
Copy the APK to a USB drive and install it on the head unit, if your DiLink firmware
allows app installs (this varies by market and firmware version). Firmware updates can
remove or block sideloaded apps, so keep the APK and an exported station list on the drive.

After installing:
- Allow the app to run in the background / auto-start in BYD's app management settings, if present.
- Accept the battery-optimization prompt on first launch.

The car needs internet (SIM or hotspot) for streaming and for loading the station list.

## Equalizer
Open it with the equalizer icon in the top bar (or ⋮ → Equalizer).
- **7 bands** (60, 150, 400, 1k, 2.5k, 6k, 15k Hz). Head units report their own band count
  (often 5 or 10); the 7-band curve is mapped onto them by log-frequency interpolation.
- **Volume compensation:** boosts are offset by lowering all bands (same loudness, less
  clipping); cuts and the head unit's own EQ headroom drop are made up with a
  `LoudnessEnhancer` (it has a limiter). **Level match** (default +3 dB) is the per-car
  calibration: toggle Power on/off and raise it until both sound equally loud.
- **Loudness:** `BassBoost`, stronger at low media volume. If the head unit has no BassBoost,
  a +6 dB low-shelf is added to the EQ's lowest bands instead.
- Code: `service/audio/EqMath.kt` (math, unit-tested), `AudioEffectsController.kt` (effects),
  `ui/components/EqualizerPanel.kt` (UI).
- Some head units route audio through a separate amplifier/DSP and ignore Android audio
  effects. The equalizer screen tells you if effects aren't available.

## Station JSON format
Used for `app/src/main/assets/stations.json` (loaded on first launch, ships empty), import and export.
A full example is in `docs/sample-stations.json`; replace the placeholder URLs before importing.

```json
{
  "formatVersion": 2,
  "exportedAtEpochMs": 0,
  "stations": [
    {
      "id": "eg-nogoum-fm",
      "name": "Nogoum FM 100.6",
      "streamUrl": "https://audio.nrpstream.com/listen/nogoumfm/radio.mp3",
      "backupStreamUrls": [],
      "country": "EG",
      "isOfficialFm": true,
      "logoUrl": null
    }
  ]
}
```
Only `id`, `name` and `streamUrl` are required. `isOfficialFm` is only the station-type filter
(FM broadcaster's stream vs web-only). Old exports (with `countryCode` or `frequency`) still import.
To add built-in stations for everyone, add them to `app/src/main/assets/stations.json`.

## Fixing a station that won't play
1. Play it, then open **⋮ → Stream diagnostics** to see the reason and the exact URL tried.
2. **HTTP 403 / refused**: the server blocks apps. The browser User-Agent usually fixes this. If not,
   the station probably needs a token URL from its website player.
3. **404 / address no longer exists**: the station moved. Tap refresh (the directory often has the
   new address), or long-press → remove, and add it again with the new URL from the station's website.
4. **SSL failed**: the app retries over plain http automatically. If the server only has HTTPS with
   a broken certificate, ask the station or use a different stream.
5. **Not an audio stream**: the URL is a web page. Use the direct stream (.mp3/.aac/.m3u8/.pls).

## Project layout
```
app/src/main/java/com/radiocompanion/
├── RadioCompanionApp.kt        Application, shared OkHttp/JSON/DB, image cache limits
├── model/Station.kt            Station (Room row + JSON format), StationBackup
├── data/local/                 Room database + DAO
├── model/StationCategories.kt  region / type categories, stream address candidates
├── data/FmClassifier.kt        Official FM detection
├── data/remote/                radio-browser.info client, .pls/.m3u resolver
├── data/repository/            StationRepository (refresh, add, import/export)
├── data/settings/              DataStore preferences (country, refresh time)
├── service/                    RadioPlaybackService (fallback logic), StreamHttp (headers),
│                               StreamDiagnostics, PlayerConnection
└── ui/                         MainActivity, RadioViewModel, RadioScreen, components, theme
```

## Known limits
- Streaming only: the app never uses the car's FM/AM tuner.
- Reverse-gear and parking-sensor chimes are mixed by the car itself; the app doesn't need to react.

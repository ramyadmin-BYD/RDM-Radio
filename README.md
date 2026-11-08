# RDM Radio – Downloads

Internet radio for the BYD Song L and other DiLink 5.0 head units: Egyptian, Arab and
international stations, prayer times with the Azan, and the Ramadan Imsakiyya.

This repository only publishes the app. It contains no source code.

## Install
1. Open the [latest release](../../releases/latest) on the car (or a USB drive).
2. Download `RDMRadio-<version>.apk` and open it. Allow "Install unknown apps" if Android asks.

Already installed? The app updates itself: ☰ → Check for updates.

## Verify a download
Every release includes:
- `SHA256SUMS`: the SHA-256 of the APK.
- `update.json` + `update.json.sig`: the update manifest the app uses, signed with the
  RDM Radio release key. The app installs an update only if this signature, the APK's
  SHA-256 and its signing certificate all check out.

APK signing certificate (SHA-256):
`63d6409bd33ca9dc8b23e455f944b52c0370e6cbede06c21c6a806562c71da43`

Check on a computer: `apksigner verify --print-certs RDMRadio-<version>.apk`

Designed by Eng. Ramy Abdelaziz.

# Ramadan Imsakiyya generator

Builds `app/src/main/assets/ramadan_1448.json` (Ramadan 2027) from the app's own
prayer-time engine (`app/src/main/java/com/radiocompanion/prayer`).

It is plain Kotlin/JVM, so any Kotlin setup can run it: compile
`tools/imsakiyya/GenerateImsakiyya.kt` together with the `prayer` package and
kotlinx-serialization-json, then run `main` from the repository root.

To use an authority's official timetable instead (for example Dar al-Ifta for Egypt),
replace that city's rows in the JSON by hand. `ImsakiyyaAssetTest` checks the file
on every CI build: every city present, every row complete and in order, and each time
within 5 minutes of the calculation (to catch typos).

If the official start of Ramadan differs from the expected 8 February 2027, change
the `starts` entry for that country (`first` date and `days`) and publish an update.

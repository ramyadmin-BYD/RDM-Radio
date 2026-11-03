package com.radiocompanion.model

/** Category 1: where the station is, relative to the user's home country. */
enum class StationRegion { LOCAL, REGIONAL, INTERNATIONAL }

/** Category 2: how the station broadcasts. */
enum class StationType { OFFICIAL_FM, ONLINE }

/** Arab League members: shown under "Regional" when they aren't the home country. */
val ARAB_COUNTRIES = setOf(
    "EG", "SA", "AE", "KW", "QA", "BH", "OM", "JO", "LB", "IQ", "PS", "SY",
    "YE", "MA", "DZ", "TN", "LY", "SD", "MR", "SO", "DJ", "KM",
)

/** Stations without a country (e.g. added by hand) count as local. */
fun Station.region(homeCountry: String): StationRegion {
    val code = countryCode.uppercase()
    return when {
        code.isBlank() || code == homeCountry.uppercase() -> StationRegion.LOCAL
        code in ARAB_COUNTRIES -> StationRegion.REGIONAL
        else -> StationRegion.INTERNATIONAL
    }
}

val Station.type: StationType
    get() = if (isOfficialFm) StationType.OFFICIAL_FM else StationType.ONLINE

/** All URLs worth trying for this station, best first. */
fun Station.streamCandidates(): List<String> {
    val primary = (listOf(streamUrl) + backupStreamUrls)
        .map { it.trim() }
        .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
    // Last resort: the same address over the other protocol. Fixes servers whose
    // HTTPS certificate is broken/expired, or that dropped plain HTTP.
    val flipped = primary.map {
        if (it.startsWith("https://", true)) "http://" + it.substring(8)
        else "https://" + it.substring(7)
    }
    return (primary + flipped).distinct()
}

package com.radiocompanion.data

/**
 * Guesses whether a directory station is an FM broadcaster's online stream (vs. a
 * web-only station), for the "station type" filter. Radio-Browser has no such field,
 * so this looks for a frequency in the name/tags or a known Egyptian FM brand.
 * Users can correct it per station (long-press), and JSON files set it explicitly.
 * Nothing here involves a tuner: all playback is internet streaming.
 */
object FmClassifier {

    // 87.5–108.0 MHz, e.g. "100.6", "88,2". Not part of a longer number (e.g. "128.0 kbps").
    private val FREQUENCY = Regex("""(?<![\d.])(8[7-9]|9\d|10[0-8])[.,](\d{1,2})(?![\d])""")

    private val BRAND_HINTS = listOf(
        "nogoum", "nile fm", "mega fm", "nagham", "radio hits", "9090", "shaabi",
        "radio masr", "holy quran", "el sharq el awsat",
        "نجوم", "نغم", "ميجا", "راديو مصر", "شعبي", "القرآن الكريم", "البرنامج العام", "الشرق الأوسط",
    )

    fun extractFrequency(text: String): String? =
        FREQUENCY.find(text)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

    fun looksOfficial(name: String, tags: String): Boolean {
        if (extractFrequency(name) != null || extractFrequency(tags) != null) return true
        val haystack = "$name $tags".lowercase()
        return BRAND_HINTS.any { haystack.contains(it) }
    }
}

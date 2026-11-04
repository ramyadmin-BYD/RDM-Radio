package com.radiocompanion.update

import kotlin.math.max

/** Version helpers for the updater (pure Kotlin, unit-tested on the JVM). */
object Versions {

    /** "v1.2.3", "1.2.3", "1.2" → [1, 2, 3] / [1, 2]. Null for non-release names like "dev-12". */
    fun parse(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.')
        if (parts.size > 4) return null
        return parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    /** True if [candidate] is a higher release version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until max(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The release APK among a release's files (never the large debug build). */
    fun pickApk(assetNames: List<String>): String? =
        assetNames.firstOrNull { it.endsWith(".apk", ignoreCase = true) && !it.contains("debug", ignoreCase = true) }

    /** Release notes as short plain text for the car screen. */
    fun plainNotes(markdown: String?, maxChars: Int = 600): String {
        if (markdown.isNullOrBlank()) return ""
        val text = markdown.lineSequence()
            .map { it.trim().trimStart('#', '>', ' ').replace("**", "").replace("__", "").replace("`", "") }
            .map { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.drop(2) else it }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }
}

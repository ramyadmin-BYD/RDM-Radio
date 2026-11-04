package com.radiocompanion.service.audio

/**
 * Presets in the 10-band layout (31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz), in dB.
 * Labels are string resources looked up by [id] in the UI (English/Arabic).
 */
data class EqPreset(val id: String, val gainsDb: List<Float>)

object EqPresets {
    const val CUSTOM_ID = "custom"

    val ALL: List<EqPreset> = listOf(
        EqPreset("flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("car", listOf(2f, 2f, -1f, -1.5f, -1f, 0f, 1f, 2f, 2f, 2f)),
        EqPreset("bass", listOf(6f, 6f, 4.5f, 2f, 0.5f, 0f, 0f, 0f, 0.5f, 1f)),
        EqPreset("vocal", listOf(-3f, -3f, -1.5f, 0f, 1f, 3f, 3f, 2f, 0f, -1f)),
        EqPreset("arabic", listOf(3f, 3f, 2f, 0f, -0.5f, 1f, 2.5f, 2f, 1.5f, 1f)),
        EqPreset("pop", listOf(-1f, -1f, 1f, 3f, 3.5f, 3f, 1.5f, 0f, -0.5f, -1f)),
        EqPreset("rock", listOf(5f, 5f, 3f, 0f, -1.5f, -1.5f, 1f, 3f, 3.5f, 4f)),
        EqPreset("classical", listOf(3.5f, 3.5f, 2f, 1f, 0f, 0f, 0f, 1.5f, 3f, 3.5f)),
        EqPreset("treble", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5.5f, 6f)),
    )

    fun byId(id: String): EqPreset? = ALL.firstOrNull { it.id == id }
}

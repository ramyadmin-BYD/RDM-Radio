package com.radiocompanion.service.audio

/** Presets in the 7-band layout (60, 150, 400, 1k, 2.5k, 6k, 15k Hz), in dB. */
data class EqPreset(val id: String, val label: String, val gainsDb: List<Float>)

object EqPresets {
    const val CUSTOM_ID = "custom"

    val ALL: List<EqPreset> = listOf(
        EqPreset("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("car", "Car cabin", listOf(2f, -1.5f, -1f, 0f, 1f, 2f, 2f)),
        EqPreset("bass", "Bass", listOf(6f, 4.5f, 1.5f, 0f, 0f, 0.5f, 1f)),
        EqPreset("vocal", "News / Talk", listOf(-3f, -1.5f, 1f, 3f, 3f, 1f, -1f)),
        EqPreset("arabic", "Arabic music", listOf(3f, 2f, -0.5f, 1f, 2.5f, 2f, 1f)),
        EqPreset("pop", "Pop", listOf(-1f, 2f, 3.5f, 3.5f, 2f, -0.5f, -1f)),
        EqPreset("rock", "Rock", listOf(5f, 3f, -1f, -2f, 1f, 3f, 4f)),
        EqPreset("classical", "Classical", listOf(3.5f, 2f, 0f, 0f, 0f, 2f, 3.5f)),
        EqPreset("treble", "Treble", listOf(0f, 0f, 0f, 1f, 3f, 5f, 6f)),
    )

    fun byId(id: String): EqPreset? = ALL.firstOrNull { it.id == id }
}

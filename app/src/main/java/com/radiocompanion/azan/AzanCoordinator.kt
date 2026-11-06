package com.radiocompanion.azan

import com.radiocompanion.prayer.Prayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implemented by the radio service so the Azan can step in and hand back. */
interface AzanInterruptible {
    /** Fades the radio out and stops it if it is playing. Returns true if it was playing. */
    suspend fun pauseForAzan(): Boolean
    /** Restarts the station that was playing (fresh live stream, fade in). */
    fun resumeAfterAzan()
}

sealed interface AzanState {
    data object Idle : AzanState
    /** [prayer] is null for the "Test Azan" button. */
    data class Playing(val prayer: Prayer?, val radioWillResume: Boolean) : AzanState
}

/** Process-wide link between [AzanService] and the radio service; both run in the app process. */
class AzanCoordinator {
    private val _state = MutableStateFlow<AzanState>(AzanState.Idle)
    val state: StateFlow<AzanState> = _state.asStateFlow()

    /** The radio service while it is alive (set in its onCreate, cleared in onDestroy). */
    @Volatile var radio: AzanInterruptible? = null

    internal fun set(state: AzanState) { _state.value = state }
}

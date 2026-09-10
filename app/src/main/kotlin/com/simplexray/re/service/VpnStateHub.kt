package com.simplexray.re.service

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VpnRunningState {
    data object Disconnected : VpnRunningState()
    data object Connecting : VpnRunningState()
    data object Connected : VpnRunningState()
    data class Failed(val message: String? = null) : VpnRunningState()
}

object VpnStateHub {
    private val _state = MutableStateFlow<VpnRunningState>(VpnRunningState.Disconnected)
    val state: StateFlow<VpnRunningState> = _state.asStateFlow()

    private val _logFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    fun updateState(newState: VpnRunningState) {
        _state.value = newState
    }

    fun emitLog(line: String) {
        _logFlow.tryEmit(line)
    }
}

package com.memdio.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide event bus for [BufferEvent]s.
 *
 * Producers ([BufferService], [ChunkRotationManager]) call [emit].
 * Consumers (ViewModel, Compose hooks) collect [events].
 *
 * [extraBufferCapacity] = 16 means fast producers never suspend even if the
 * collector is momentarily slow (DROP_OLDEST would be an alternative for UI).
 */
@Singleton
class BufferEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<BufferEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BufferEvent> = _events.asSharedFlow()

    fun emit(event: BufferEvent) {
        _events.tryEmit(event)
    }
}

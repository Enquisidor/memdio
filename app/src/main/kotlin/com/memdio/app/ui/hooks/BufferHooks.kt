package com.memdio.app.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memdio.app.service.BufferEvent
import com.memdio.app.service.BufferService
import com.memdio.app.service.RecordingState
import com.memdio.app.ui.settings.SettingsViewModel

/**
 * Compose hooks for the audio buffer — the Kotlin/Compose equivalent of React hooks.
 *
 * Each hook is a plain @Composable function that encapsulates state collection
 * and side-effect wiring so call-sites stay declarative:
 *
 * ```kotlin
 * val state by useRecordingState()
 * useBufferEvents { event -> … }
 * ```
 */

/** Returns the current [RecordingState] as Compose [State], lifecycle-aware. */
@Composable
fun useRecordingState(): State<RecordingState> =
    BufferService.state.collectAsStateWithLifecycle(initialValue = RecordingState.Idle)

/** Returns `true` while the buffer service is actively recording. */
@Composable
fun useIsRecording(): Boolean = useRecordingState().value.isRecording

/**
 * Runs [onEvent] for every [BufferEvent] emitted by the pipeline.
 *
 * The effect is tied to the Compose lifecycle: collection starts when the
 * composable enters the composition and stops when it leaves — no manual
 * cleanup required, just like React's `useEffect`.
 *
 * @param viewModel source of the event stream (injected automatically by Hilt)
 * @param onEvent called on every event; runs in the composition's coroutine scope
 */
@Composable
fun useBufferEvents(
    viewModel: SettingsViewModel = hiltViewModel(),
    onEvent: suspend (BufferEvent) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.bufferEvents.collect(onEvent)
    }
}

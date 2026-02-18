package com.memdio.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memdio.app.data.model.ExportDestination
import com.memdio.app.data.repository.SettingsRepository
import com.memdio.app.export.BufferExporter
import com.memdio.app.export.DispatchResult
import com.memdio.app.export.ExportDispatcher
import com.memdio.app.export.ExportResult
import com.memdio.app.service.BufferService
import com.memdio.app.service.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val settingsRepo: SettingsRepository,
    private val exporter: BufferExporter,
    private val dispatcher: ExportDispatcher,
) : ViewModel() {

    // ── Recording state (bridged from BufferService companion) ────────────────

    val isRecording: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            BufferService.state.collect { flow.value = it.isRecording }
        }
    }

    val bufferDurationMs: Flow<Long> = BufferService.state.map { state ->
        (state as? RecordingState.Active)?.bufferDurationMs ?: 0L
    }

    val storageBytesUsed: Flow<Long> = BufferService.state.map { state ->
        (state as? RecordingState.Active)?.storageBytesUsed ?: 0L
    }

    // ── Settings (pass-through to DataStore) ──────────────────────────────────

    val bufferDurationMinutes: Flow<Int> = settingsRepo.bufferDurationMinutes
    val audioBitrate: Flow<Int> = settingsRepo.audioBitrate
    val startOnBoot: Flow<Boolean> = settingsRepo.startOnBoot
    val exportDestination: Flow<ExportDestination> = settingsRepo.exportDestination
    val cloudAutoExport: Flow<Boolean> = settingsRepo.cloudAutoExport
    val driveAccountEmail: Flow<String?> = settingsRepo.driveAccountEmail

    fun setBufferDurationMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepo.setBufferDurationMinutes(minutes) }
    }

    fun setAudioBitrate(bitrate: Int) {
        viewModelScope.launch { settingsRepo.setAudioBitrate(bitrate) }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setStartOnBoot(enabled) }
    }

    fun setExportDestination(destination: ExportDestination) {
        viewModelScope.launch { settingsRepo.setExportDestination(destination) }
    }

    fun setCloudAutoExport(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setCloudAutoExport(enabled) }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun triggerExport() {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            when (val exportResult = exporter.export()) {
                is ExportResult.EmptyBuffer -> {
                    _exportState.value = ExportState.EmptyBuffer
                }
                is ExportResult.Failure -> {
                    _exportState.value = ExportState.Error(
                        exportResult.cause.message ?: "Export failed"
                    )
                }
                is ExportResult.Success -> {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        exportResult.outputFile,
                    )
                    when (val dispatchResult = dispatcher.dispatch(exportResult.outputFile, fileUri)) {
                        is DispatchResult.ShareSheetLaunched ->
                            _exportState.value = ExportState.Done(dispatchResult.intent)
                        is DispatchResult.CloudUploadSuccess ->
                            _exportState.value = ExportState.Done()
                        is DispatchResult.CloudUploadFailure ->
                            _exportState.value = ExportState.Error(
                                dispatchResult.cause.message ?: "Upload failed"
                            )
                        is DispatchResult.LocalSaveRequested ->
                            _exportState.value = ExportState.Done()
                    }
                }
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // ── Service control ───────────────────────────────────────────────────────

    fun startRecording() {
        context.startForegroundService(BufferService.startIntent(context))
    }

    fun stopRecording() {
        context.startService(BufferService.stopIntent(context))
    }
}

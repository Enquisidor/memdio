package com.memdio.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memdio.app.data.model.ExportDestination
import com.memdio.app.ui.theme.RecordingGreen
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    // ── Collect state ─────────────────────────────────────────────────────────
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val bufferDurationMs by viewModel.bufferDurationMs.collectAsStateWithLifecycle(0L)
    val storageBytesUsed by viewModel.storageBytesUsed.collectAsStateWithLifecycle(0L)
    val bufferMinutes by viewModel.bufferDurationMinutes.collectAsStateWithLifecycle(60)
    val audioBitrate by viewModel.audioBitrate.collectAsStateWithLifecycle(64_000)
    val startOnBoot by viewModel.startOnBoot.collectAsStateWithLifecycle(false)
    val exportDestination by viewModel.exportDestination.collectAsStateWithLifecycle(ExportDestination.SHARE_SHEET)
    val cloudAutoExport by viewModel.cloudAutoExport.collectAsStateWithLifecycle(false)
    val driveEmail by viewModel.driveAccountEmail.collectAsStateWithLifecycle(null)
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    // ── Permission launcher ───────────────────────────────────────────────────
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (granted) viewModel.startRecording()
        else scope.launch { snackbarHost.showSnackbar("Microphone permission is required") }
    }

    // ── Handle SAF local-save flow ────────────────────────────────────────────
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/aac")
    ) { _ -> viewModel.resetExportState() }

    // ── Side-effects ──────────────────────────────────────────────────────────
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Done -> {
                state.shareIntent?.let { context.startActivity(it) }
                viewModel.resetExportState()
            }
            is ExportState.Error -> {
                snackbarHost.showSnackbar(state.message)
                viewModel.resetExportState()
            }
            is ExportState.EmptyBuffer -> {
                snackbarHost.showSnackbar("Buffer is empty — enable recording first")
                viewModel.resetExportState()
            }
            else -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 1. Status Card ────────────────────────────────────────────────
            StatusCard(isRecording = isRecording, bufferDurationMs = bufferDurationMs)

            Spacer(Modifier.height(4.dp))

            // ── 2. Recording section ──────────────────────────────────────────
            SectionHeader("Recording")
            SettingRow(
                label = "Enable Buffer",
                sublabel = if (isRecording) formatDuration(bufferDurationMs) + " buffered" else "Tap to start",
            ) {
                Switch(
                    checked = isRecording,
                    onCheckedChange = { on ->
                        if (on) {
                            val perms = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            micPermLauncher.launch(perms.toTypedArray())
                        } else {
                            viewModel.stopRecording()
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = RecordingGreen),
                )
            }

            // ── 3. Buffer settings ────────────────────────────────────────────
            SectionHeader("Buffer Settings")
            Column(Modifier.padding(horizontal = 4.dp)) {
                val sliderMinutes by viewModel.bufferDurationMinutes.collectAsStateWithLifecycle(60)
                Text(
                    text = "Keep last ${formatMinutes(sliderMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = sliderMinutes.toFloat(),
                    onValueChange = { viewModel.setBufferDurationMinutes(it.roundToInt()) },
                    valueRange = 15f..180f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingRow(label = "Audio quality", sublabel = if (audioBitrate == 64_000) "Voice (64 kbps)" else "High (128 kbps)") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = if (audioBitrate == 64_000) "Voice" else "High",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Voice (64 kbps)") },
                            onClick = { viewModel.setAudioBitrate(64_000); expanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("High (128 kbps)") },
                            onClick = { viewModel.setAudioBitrate(128_000); expanded = false },
                        )
                    }
                }
            }

            SettingRow(label = "Start on boot") {
                Switch(checked = startOnBoot, onCheckedChange = viewModel::setStartOnBoot)
            }

            // ── 4. Export ─────────────────────────────────────────────────────
            SectionHeader("Export")
            val isExporting = exportState is ExportState.Exporting
            Button(
                onClick = { viewModel.triggerExport() },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RecordingGreen),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Export Buffer")
                }
            }
            if (bufferDurationMs > 0) {
                Text(
                    text = "Exports entire buffer (~${formatDuration(bufferDurationMs)}, ~${formatBytes(storageBytesUsed)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            // ── 5. Cloud Storage ──────────────────────────────────────────────
            SectionHeader("Save destination")
            DestinationOption(
                label = "Share sheet",
                sublabel = "Always available, no sign-in",
                icon = Icons.Default.Share,
                selected = exportDestination == ExportDestination.SHARE_SHEET,
                onClick = { viewModel.setExportDestination(ExportDestination.SHARE_SHEET) },
            )
            DestinationOption(
                label = "Google Drive",
                sublabel = driveEmail ?: "Tap Connect to sign in",
                icon = Icons.Default.CloudUpload,
                selected = exportDestination == ExportDestination.GOOGLE_DRIVE,
                onClick = { viewModel.setExportDestination(ExportDestination.GOOGLE_DRIVE) },
                trailingContent = {
                    if (driveEmail == null) {
                        TextButton(onClick = { /* Launch GoogleSignIn — handled in Activity */ }) {
                            Text("Connect")
                        }
                    }
                },
            )
            DestinationOption(
                label = "Dropbox",
                sublabel = "Connect your Dropbox account",
                icon = Icons.Default.CloudUpload,
                selected = exportDestination == ExportDestination.DROPBOX,
                onClick = { viewModel.setExportDestination(ExportDestination.DROPBOX) },
            )
            DestinationOption(
                label = "Local files",
                sublabel = "Save to device Downloads",
                icon = Icons.Default.FolderOpen,
                selected = exportDestination == ExportDestination.LOCAL_FILES,
                onClick = { viewModel.setExportDestination(ExportDestination.LOCAL_FILES) },
            )

            if (exportDestination in listOf(ExportDestination.GOOGLE_DRIVE, ExportDestination.DROPBOX)) {
                SettingRow(label = "Auto-export on buffer full", sublabel = "Uploads when oldest chunk is evicted") {
                    Switch(checked = cloudAutoExport, onCheckedChange = viewModel::setCloudAutoExport)
                }
            }

            // ── 6. Storage ────────────────────────────────────────────────────
            SectionHeader("Storage")
            Text(
                text = "Buffer using ${formatBytes(storageBytesUsed)} of device storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Composable helpers ─────────────────────────────────────────────────────────

@Composable
private fun StatusCard(isRecording: Boolean, bufferDurationMs: Long) {
    val cardColor by animateColorAsState(
        targetValue = if (isRecording) RecordingGreen else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(600),
        label = "statusCardColor",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isRecording) PulsingDot()
            val label = if (isRecording)
                "Recording — ${formatDuration(bufferDurationMs)} buffered"
            else "Buffer off"
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White)
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingRow(
    label: String,
    sublabel: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(
                    sublabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun DestinationOption(
    label: String,
    sublabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SettingRow(
        label = label,
        sublabel = sublabel,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailingContent?.invoke()
            androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        }
    }
}

// ── Formatting ────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1_000
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatMinutes(minutes: Int): String =
    when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "%.1f MB".format(bytes / 1_048_576.0)
}

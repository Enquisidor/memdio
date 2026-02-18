package com.memdio.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.memdio.app.MainActivity
import com.memdio.app.MemdioApplication.Companion.NOTIFICATION_CHANNEL_ID
import com.memdio.app.MemdioApplication.Companion.NOTIFICATION_ID
import com.memdio.app.R
import com.memdio.app.data.db.ChunkEntity
import com.memdio.app.data.repository.ChunkRepository
import com.memdio.app.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Continuously records audio in 30-second AAC chunks to [filesDir]/buffer/.
 *
 * IMPORTANT: foregroundServiceType="microphone" is declared in AndroidManifest.
 * Omitting that attribute silently prevents mic access on Android 14+.
 *
 * State is exposed via the [companion] [state] StateFlow so the ViewModel
 * can observe it without a ServiceConnection binding.
 */
@AndroidEntryPoint
class BufferService : Service() {

    @Inject lateinit var chunkRepository: ChunkRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var rotationManager: ChunkRotationManager
    @Inject lateinit var eventBus: BufferEventBus

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recorder: MediaRecorder? = null
    private var currentChunkFile: File? = null
    private var isCallInProgress = false
    private var telephonyCallback: Any? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        registerPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> launchRecordLoop()
            ACTION_STOP -> stopRecordingAndSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecordingInternal()
        serviceScope.cancel()
        unregisterPhoneStateListener()
        super.onDestroy()
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    private fun launchRecordLoop() {
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch { recordLoop() }
    }

    /**
     * Runs indefinitely, recording one [CHUNK_DURATION_MS]-long chunk at a time.
     * Pauses during phone calls; cleans up the incomplete chunk on stop.
     */
    private suspend fun recordLoop() {
        while (true) {
            if (isCallInProgress) {
                delay(POLL_INTERVAL_MS)
                continue
            }
            val file = prepareChunkFile()
            val startMs = System.currentTimeMillis()
            currentChunkFile = file
            try {
                startChunk(file)
                publishActiveState()
                delay(CHUNK_DURATION_MS)
                finalizeChunk(file, startMs)
            } catch (_: Exception) {
                // Release and retry on the next iteration; the incomplete file is cleaned up below
                releaseRecorder()
                file.runCatching { delete() }
            }
        }
    }

    private suspend fun startChunk(outputFile: File) {
        val bitrate = settingsRepository.audioBitrate.first()
        recorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(bitrate)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    private suspend fun finalizeChunk(file: File, startMs: Long) {
        releaseRecorder()
        val endMs = System.currentTimeMillis()
        val chunk = ChunkEntity(
            startMs = startMs,
            endMs = endMs,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
        )
        chunkRepository.insert(chunk)
        eventBus.emit(BufferEvent.ChunkRecorded(chunk))
        val limitMs = settingsRepository.bufferDurationMinutes.first() * 60_000L
        rotationManager.enforceLimit(limitMs)
        publishActiveState()
    }

    private fun stopRecordingAndSelf() {
        stopRecordingInternal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecordingInternal() {
        releaseRecorder()
        // The current (incomplete) chunk is not a full segment — delete it
        currentChunkFile?.runCatching { delete() }
        currentChunkFile = null
        _serviceState.value = RecordingState.Idle
    }

    private fun releaseRecorder() {
        recorder?.runCatching { stop(); release() }
        recorder = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prepareChunkFile(): File {
        val dir = File(filesDir, "buffer").also { it.mkdirs() }
        return File(dir, "${System.currentTimeMillis()}.aac")
    }

    private suspend fun publishActiveState() {
        _serviceState.value = RecordingState.Active(
            bufferDurationMs = chunkRepository.getTotalDurationMs(),
            storageBytesUsed = chunkRepository.getTotalSizeBytes(),
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ── Phone-call pause/resume ───────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            tm.registerTelephonyCallback(mainExecutor, cb)
            telephonyCallback = cb
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in API 31", ReplaceWith(""))
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    handleCallState(state)
            }
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            telephonyCallback = listener
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            (telephonyCallback as? PhoneStateListener)?.let {
                tm.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallState(state: Int) {
        isCallInProgress = state != TelephonyManager.CALL_STATE_IDLE
        if (isCallInProgress) {
            releaseRecorder()
            currentChunkFile?.runCatching { delete() }
            currentChunkFile = null
        }
    }

    /** Extracted for testing — allows injection of a fake MediaRecorder. */
    internal open fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

    // ── Companion — singleton state shared with ViewModel ─────────────────────

    companion object {
        const val ACTION_START = "com.memdio.app.action.START_BUFFER"
        const val ACTION_STOP = "com.memdio.app.action.STOP_BUFFER"
        const val CHUNK_DURATION_MS = 30_000L
        const val POLL_INTERVAL_MS = 1_000L

        private val _serviceState = MutableStateFlow<RecordingState>(RecordingState.Idle)

        /** ViewModel collects this without a ServiceConnection. */
        val state: StateFlow<RecordingState> = _serviceState.asStateFlow()

        fun startIntent(ctx: Context) =
            Intent(ctx, BufferService::class.java).apply { action = ACTION_START }

        fun stopIntent(ctx: Context) =
            Intent(ctx, BufferService::class.java).apply { action = ACTION_STOP }
    }
}

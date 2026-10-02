package com.linernotes.app.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class CdConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class ShanlingCdState(
    val connectionState: CdConnectionState = CdConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val currentQueueIndex: Int = -1,
    val currentTrackNumber: Int = 0,
    val totalTracks: Int = 0,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

@Singleton
class ShanlingBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ShanlingSyncLink"

    private val _cdState = MutableStateFlow(ShanlingCdState())
    val cdState: StateFlow<ShanlingCdState> = _cdState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var currentSocket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var connectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var trackSwitchJob: Job? = null
    @Volatile
    private var isTrackSwitching: Boolean = false
    private var seqCounter = AtomicInteger(1)

    private fun nextSeq(): Int = seqCounter.getAndIncrement() and 0xFFFFFF

    @SuppressLint("MissingPermission")
    fun getPairedShanlingDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.filter { device ->
                val name = device.name ?: ""
                name.contains("EC Mini", ignoreCase = true) ||
                        name.contains("Shanling", ignoreCase = true) ||
                        name.contains("ECMini", ignoreCase = true) ||
                        name.contains("EC3", ignoreCase = true) ||
                        name.contains("EC Smart", ignoreCase = true) ||
                        name.contains("EC", ignoreCase = true) ||
                        name.contains("山灵")
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(tag, "Bluetooth permission not granted for bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun getAllPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connectToDevice(targetDevice: BluetoothDevice? = null) {
        if (_cdState.value.connectionState == CdConnectionState.CONNECTING ||
            _cdState.value.connectionState == CdConnectionState.CONNECTED
        ) {
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            _cdState.update { it.copy(connectionState = CdConnectionState.CONNECTING, errorMessage = null) }

            val device = targetDevice ?: getPairedShanlingDevices().firstOrNull() ?: run {
                _cdState.update {
                    it.copy(
                        connectionState = CdConnectionState.FAILED,
                        errorMessage = "未找到已配对的山灵 CD 机，请先在手机系统设置中配对"
                    )
                }
                return@launch
            }

            val deviceName = try { device.name ?: "Shanling CD" } catch (e: Exception) { "Shanling CD" }
            _cdState.update { it.copy(deviceName = deviceName) }

            var socket: BluetoothSocket? = null

            // Try Secure UUID -> Insecure UUID -> Standard SPP
            val connectionUuids = listOf(
                ShanlingSyncLinkProtocol.UUID_SECURE,
                ShanlingSyncLinkProtocol.UUID_INSECURE,
                ShanlingSyncLinkProtocol.UUID_SPP
            )

            for (uuid in connectionUuids) {
                try {
                    Log.d(tag, "Attempting RFCOMM connection to $deviceName with UUID $uuid")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                    if (socket.isConnected) {
                        Log.i(tag, "Successfully connected to $deviceName via $uuid")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed connecting with UUID $uuid: ${e.message}")
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
            }

            if (socket == null || !socket.isConnected) {
                _cdState.update {
                    it.copy(
                        connectionState = CdConnectionState.FAILED,
                        errorMessage = "连接 $deviceName 失败，请确认 CD 机已开启并处于蓝牙连接范围"
                    )
                }
                return@launch
            }

            currentSocket = socket
            inStream = socket.inputStream
            outStream = socket.outputStream

            _cdState.update {
                it.copy(
                    connectionState = CdConnectionState.CONNECTED,
                    errorMessage = null
                )
            }

            // 1. Handshake: SL_LOGIN_REQ
            sendFrame(ShanlingSyncLinkProtocol.SL_LOGIN_REQ)
            delay(100L)

            // 2. Subscribe to timeline: SL_ENABLE_PLAYTIME_NOTIFY_REQ
            sendFrame(ShanlingSyncLinkProtocol.SL_ENABLE_PLAYTIME_NOTIFY_REQ)
            delay(100L)

            // 3. Request initial play status: SL_GET_PLAY_STATUS_REQ
            sendFrame(ShanlingSyncLinkProtocol.SL_GET_PLAY_STATUS_REQ)

            // Start heartbeat loop
            startHeartbeat()

            // Run receive loop
            runReceiveLoop()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var loopCount = 0
            while (isActive && _cdState.value.connectionState == CdConnectionState.CONNECTED) {
                delay(1500L)
                loopCount++
                // Continuous query of play status (track position and play/pause state)
                if (!isTrackSwitching) {
                    refreshPlayStatus()
                }
                // Heartbeat keep-alive every 6 seconds (loopCount % 4 == 0)
                if (loopCount % 4 == 0) {
                    sendFrame(ShanlingSyncLinkProtocol.SL_HEART_BEAT_REQ)
                }
            }
        }
    }

    private fun runReceiveLoop() {
        val input = inStream ?: return
        val headerBuffer = ByteArray(ShanlingSyncLinkProtocol.HEADER_SIZE)

        try {
            while (scope.isActive && currentSocket?.isConnected == true) {
                // Read 12-byte header
                readFully(input, headerBuffer)

                val (msgId, commandId, payloadLen) = ShanlingSyncLinkProtocol.parseHeader(headerBuffer)

                val payload = if (payloadLen > 0) {
                    val p = ByteArray(payloadLen)
                    readFully(input, p)
                    p
                } else {
                    ByteArray(0)
                }

                handleIncomingFrame(commandId, payload)
            }
        } catch (e: Exception) {
            Log.e(tag, "Connection lost in receive loop: ${e.message}")
        } finally {
            disconnect()
        }
    }

    private fun handleIncomingFrame(commandId: Int, payload: ByteArray) {
        when (commandId) {
            ShanlingSyncLinkProtocol.SL_CUR_PLAY_TIME_NOTIFY -> {
                val data = ShanlingSyncLinkProtocol.decodePlayTimeNotify(payload)
                _cdState.update {
                    it.copy(
                        currentPositionMs = data.playtimeSeconds * 1000L,
                        durationMs = if (data.durationSeconds > 0) data.durationSeconds * 1000L else it.durationMs
                    )
                }
            }
            ShanlingSyncLinkProtocol.SL_GET_PLAY_STATUS_RESP,
            ShanlingSyncLinkProtocol.SL_GET_PLAY_STATUS_NOTIFY -> {
                val data = ShanlingSyncLinkProtocol.decodePlayStatus(payload)
                _cdState.update {
                    it.copy(
                        isPlaying = data.isPlaying,
                        currentQueueIndex = data.queueIndex,
                        currentTrackNumber = data.trackNumber,
                        totalTracks = if (data.totalSongs > 0) data.totalSongs else it.totalTracks
                    )
                }
            }
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_RESP,
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_NOTIFY -> {
                refreshPlayStatus()
            }
            ShanlingSyncLinkProtocol.SL_LOGIN_RESP -> {
                Log.i(tag, "Handshake successful with Shanling CD player")
                refreshPlayStatus()
            }
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException("Unexpected end of stream")
            offset += count
        }
    }

    private val sendMutex = Mutex()

    fun sendFrame(commandId: Int, payload: ByteArray = ByteArray(0)) {
        scope.launch {
            sendMutex.withLock {
                try {
                    val frame = ShanlingSyncLinkProtocol.buildFrame(
                        messageId = nextSeq(),
                        commandId = commandId,
                        payload = payload
                    )
                    outStream?.write(frame)
                    outStream?.flush()
                } catch (e: Exception) {
                    Log.w(tag, "Failed sending command $commandId: ${e.message}")
                }
            }
        }
    }

    fun refreshPlayStatus() {
        sendFrame(ShanlingSyncLinkProtocol.SL_GET_PLAY_STATUS_REQ)
    }

    // --- CD Remote Controls ---

    fun play() {
        _cdState.update { it.copy(isPlaying = true) }
        sendFrame(
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
            ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PLAY_SONG)
        )
        scope.launch {
            delay(300L)
            refreshPlayStatus()
        }
    }

    fun pause() {
        _cdState.update { it.copy(isPlaying = false) }
        sendFrame(
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
            ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PAUSE_SONG)
        )
        scope.launch {
            delay(300L)
            refreshPlayStatus()
        }
    }

    fun next() {
        sendFrame(
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
            ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_NEXT_SONG)
        )
        scope.launch {
            delay(300L)
            refreshPlayStatus()
        }
    }

    fun previous() {
        sendFrame(
            ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
            ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PREV_SONG)
        )
        scope.launch {
            delay(300L)
            refreshPlayStatus()
        }
    }

    fun seekTo(seconds: Int) {
        sendFrame(
            ShanlingSyncLinkProtocol.SL_PLAY_SEEK_REQ,
            ShanlingSyncLinkProtocol.encodePlaySeek(seconds)
        )
        scope.launch {
            delay(300L)
            refreshPlayStatus()
        }
    }

    fun playTrack(targetQueueIndex: Int, fromQueueIndex: Int? = null) {
        val validTarget = targetQueueIndex.coerceAtLeast(0)

        // Cancel previous track-switching sequence if in progress
        trackSwitchJob?.cancel()
        trackSwitchJob = scope.launch {
            isTrackSwitching = true
            try {
                // 1. Determine current hardware queue index BEFORE updating _cdState
                val current = if (_cdState.value.currentQueueIndex >= 0) {
                    _cdState.value.currentQueueIndex
                } else if (fromQueueIndex != null && fromQueueIndex >= 0) {
                    fromQueueIndex
                } else {
                    -1
                }

                _cdState.update {
                    it.copy(
                        currentQueueIndex = validTarget,
                        currentTrackNumber = validTarget + 1,
                        isPlaying = true,
                        currentPositionMs = 0L
                    )
                }

                // 2. Direct CD seek command (supported by Android DAP / high-end Shanling CD models)
                sendFrame(
                    ShanlingSyncLinkProtocol.SL_CD_PLAY_REQ,
                    ShanlingSyncLinkProtocol.encodeCdPlayQueue(validTarget)
                )

                // 3. Hardware transport pulse stepping for mechanical CD mechanisms (Shanling EC Mini)
                if (current >= 0 && current != validTarget) {
                    val delta = validTarget - current
                    if (delta > 0) {
                        for (i in 0 until delta) {
                            sendFrame(
                                ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
                                ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_NEXT_SONG)
                            )
                            delay(200L)
                        }
                    } else {
                        // Standard Red Book CD behavior: if current track has played for > 1.5s,
                        // the first PREV resets current track to 00:00, requiring abs(delta) + 1 pulses.
                        val currentPosMs = _cdState.value.currentPositionMs
                        val needExtraPulse = currentPosMs > 1500L
                        val pulses = kotlin.math.abs(delta) + (if (needExtraPulse) 1 else 0)
                        for (i in 0 until pulses) {
                            sendFrame(
                                ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
                                ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PREV_SONG)
                            )
                            delay(200L)
                        }
                    }
                    // Allow optical pickup to finish physical seek before issuing play
                    delay(600L)
                    sendFrame(
                        ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
                        ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PLAY_SONG)
                    )
                } else {
                    // Same track or unknown current track: ensure playback starts
                    sendFrame(
                        ShanlingSyncLinkProtocol.SL_PLAY_CONTROL_REQ,
                        ShanlingSyncLinkProtocol.encodePlayControl(ShanlingSyncLinkProtocol.CONTROL_PLAY_SONG)
                    )
                }

                // Mechanical optical pickup seek delay before polling status
                delay(600L)
            } finally {
                isTrackSwitching = false
            }
            refreshPlayStatus()
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        trackSwitchJob?.cancel()
        trackSwitchJob = null
        isTrackSwitching = false
        try {
            inStream?.close()
            outStream?.close()
            currentSocket?.close()
        } catch (_: Exception) {}
        currentSocket = null
        inStream = null
        outStream = null

        _cdState.update {
            it.copy(
                connectionState = CdConnectionState.DISCONNECTED,
                isPlaying = false,
                currentQueueIndex = -1,
                currentTrackNumber = 0
            )
        }
    }
}

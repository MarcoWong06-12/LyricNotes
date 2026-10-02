package com.linernotes.app.core.bluetooth

import java.util.UUID

/**
 * Shanling SyncLink Bluetooth Protocol Implementation.
 * Reverse-engineered from Shanling Eddict Player V2.3.6 (com.shanling.eddictplayer.Synclink).
 *
 * Wire Framing:
 * [0..2]  messageId (24-bit Little-Endian uint)
 * [3]     priority (8-bit uint, 0x40 = common, 0x00 = immediate)
 * [4..7]  commandId (32-bit Little-Endian uint opcode)
 * [8..11] payloadLength (32-bit Little-Endian uint)
 * [12..]  Protobuf encoded payload bytes
 */
object ShanlingSyncLinkProtocol {

    // Bluetooth Classic RFCOMM UUIDs
    val UUID_SECURE: UUID = UUID.fromString("0000cc0c-0000-1000-8000-00805f9b34fb")
    val UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    const val HEADER_SIZE = 12
    const val PRIORITY_COMMON = 0x40
    const val PRIORITY_IMMEDIATE = 0x00

    // Opcodes
    const val SL_LOGIN_REQ = 257
    const val SL_LOGIN_RESP = 258
    const val SL_GET_PLAY_MODE_REQ = 769
    const val SL_GET_PLAY_MODE_RESP = 770
    const val SL_SET_PLAY_MODE_REQ = 771
    const val SL_SET_PLAY_MODE_RESP = 772
    const val SL_GET_PLAY_INFO_REQ = 779
    const val SL_GET_PLAY_INFO_RESP = 780
    const val SL_GET_PLAY_INFO_NOTIFY = 781
    const val SL_CUR_PLAY_TIME_NOTIFY = 782
    const val SL_DISABLE_PLAYTIME_NOTIFY_REQ = 783
    const val SL_DISABLE_PLAYTIME_NOTIFY_RESP = 784
    const val SL_ENABLE_PLAYTIME_NOTIFY_REQ = 785
    const val SL_ENABLE_PLAYTIME_NOTIFY_RESP = 786
    const val SL_PLAY_CONTROL_REQ = 787
    const val SL_PLAY_CONTROL_RESP = 788
    const val SL_PLAY_CONTROL_NOTIFY = 789
    const val SL_PLAY_SEEK_REQ = 790
    const val SL_PLAY_SEEK_RESP = 791
    const val SL_GET_PLAY_QUEUE_REQ = 801
    const val SL_GET_PLAY_QUEUE_RESP = 802
    const val SL_GET_PLAY_STATUS_REQ = 803
    const val SL_GET_PLAY_STATUS_RESP = 804
    const val SL_GET_PLAY_STATUS_NOTIFY = 805
    const val SL_HEART_BEAT_REQ = 1792
    const val SL_HEART_BEAT_RESP = 1793
    const val SL_CD_PLAY_REQ = 1796

    // Control Types (Protobuf enum ControlType in com.shanling.eddictplayer.Synclink)
    const val CONTROL_PLAY_SONG = 0
    const val CONTROL_NEXT_SONG = 1
    const val CONTROL_PREV_SONG = 2
    const val CONTROL_STOP_SONG = 3
    const val CONTROL_MUTE_SONG = 4
    const val CONTROL_PAUSE_SONG = 5

    data class Frame(
        val messageId: Int,
        val priority: Int,
        val commandId: Int,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return messageId == other.messageId &&
                    priority == other.priority &&
                    commandId == other.commandId &&
                    payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = messageId
            result = 31 * result + priority
            result = 31 * result + commandId
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Builds a 12-byte header frame followed by optional payload.
     */
    fun buildFrame(
        messageId: Int,
        commandId: Int,
        payload: ByteArray = ByteArray(0),
        priority: Int = PRIORITY_COMMON
    ): ByteArray {
        val buffer = ByteArray(HEADER_SIZE + payload.size)
        // Message ID (bytes 0..2, 24-bit LE)
        buffer[0] = (messageId and 0xFF).toByte()
        buffer[1] = ((messageId ushr 8) and 0xFF).toByte()
        buffer[2] = ((messageId ushr 16) and 0xFF).toByte()

        // Priority (byte 3)
        buffer[3] = (priority and 0xFF).toByte()

        // Command ID (bytes 4..7, 32-bit LE)
        buffer[4] = (commandId and 0xFF).toByte()
        buffer[5] = ((commandId ushr 8) and 0xFF).toByte()
        buffer[6] = ((commandId ushr 16) and 0xFF).toByte()
        buffer[7] = ((commandId ushr 24) and 0xFF).toByte()

        // Payload Length (bytes 8..11, 32-bit LE)
        val length = payload.size
        buffer[8] = (length and 0xFF).toByte()
        buffer[9] = ((length ushr 8) and 0xFF).toByte()
        buffer[10] = ((length ushr 16) and 0xFF).toByte()
        buffer[11] = ((length ushr 24) and 0xFF).toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, buffer, HEADER_SIZE, payload.size)
        }
        return buffer
    }

    /**
     * Parses the 12-byte Little-Endian frame header.
     * Returns Triple(messageId, commandId, payloadLength).
     */
    fun parseHeader(header: ByteArray): Triple<Int, Int, Int> {
        require(header.size >= HEADER_SIZE) { "Header must be at least 12 bytes" }
        val messageId = (header[0].toInt() and 0xFF) or
                ((header[1].toInt() and 0xFF) shl 8) or
                ((header[2].toInt() and 0xFF) shl 16)

        val commandId = (header[4].toInt() and 0xFF) or
                ((header[5].toInt() and 0xFF) shl 8) or
                ((header[6].toInt() and 0xFF) shl 16) or
                ((header[7].toInt() and 0xFF) shl 24)

        val payloadLength = (header[8].toInt() and 0xFF) or
                ((header[9].toInt() and 0xFF) shl 8) or
                ((header[10].toInt() and 0xFF) shl 16) or
                ((header[11].toInt() and 0xFF) shl 24)

        return Triple(messageId, commandId, payloadLength)
    }

    // --- Lightweight Protobuf Codec ---

    data class PlayTimeData(val playtimeSeconds: Int, val durationSeconds: Int)
    data class PlayStatusData(
        val isPlaying: Boolean,
        val queueIndex: Int,
        val totalSongs: Int
    ) {
        val trackNumber: Int get() = queueIndex + 1
    }

    /**
     * Decodes PlayTimeNotify Protobuf payload:
     * Field 1 (playtime): varint (in seconds)
     * Field 2 (duration): varint (in seconds)
     */
    fun decodePlayTimeNotify(bytes: ByteArray): PlayTimeData {
        var playtime = 0
        var duration = 0
        var i = 0
        while (i < bytes.size) {
            val (tag, nextI) = readVarint(bytes, i)
            i = nextI
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07L).toInt()

            when (wireType) {
                0 -> { // Varint
                    val (value, afterVal) = readVarint(bytes, i)
                    i = afterVal
                    if (fieldNumber == 1) playtime = value.toInt()
                    if (fieldNumber == 2) duration = value.toInt()
                }
                1 -> i += 8 // 64-bit
                2 -> { // Length-delimited
                    val (len, afterLen) = readVarint(bytes, i)
                    i = afterLen + len.toInt()
                }
                5 -> i += 4 // 32-bit
                else -> break
            }
        }
        return PlayTimeData(playtime, duration)
    }

    /**
     * Decodes PlayStatusResp / PlayStatusNotify Protobuf payload:
     * Field 1 (playstatus): ControlType (0=PLAY_SONG, 5=PAUSE_SONG, 3=STOP_SONG)
     * Field 2 (current_position): 0-based queue index (0 for 1st song, 1 for 2nd song...)
     * Field 3 (total_songs): total number of tracks on CD
     */
    fun decodePlayStatus(bytes: ByteArray): PlayStatusData {
        // In Proto3, default enum value 0 (CONTROL_PLAY_SONG) is omitted over the wire!
        // Default to CONTROL_PLAY_SONG so that omitted field 1 is correctly treated as playing.
        var playStatus = CONTROL_PLAY_SONG
        var currentPosition = 0
        var totalSongs = 0
        var i = 0
        while (i < bytes.size) {
            val (tag, nextI) = readVarint(bytes, i)
            i = nextI
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07L).toInt()

            when (wireType) {
                0 -> { // Varint
                    val (value, afterVal) = readVarint(bytes, i)
                    i = afterVal
                    if (fieldNumber == 1) playStatus = value.toInt()
                    if (fieldNumber == 2) currentPosition = value.toInt()
                    if (fieldNumber == 3) totalSongs = value.toInt()
                }
                1 -> i += 8
                2 -> {
                    val (len, afterLen) = readVarint(bytes, i)
                    i = afterLen + len.toInt()
                }
                5 -> i += 4
                else -> break
            }
        }
        val isPlaying = playStatus != CONTROL_PAUSE_SONG && playStatus != CONTROL_STOP_SONG
        return PlayStatusData(
            isPlaying = isPlaying,
            queueIndex = currentPosition,
            totalSongs = totalSongs
        )
    }

    /**
     * Encodes PlayControlReq (control = 1 for PLAY, 6 for PAUSE, 2 for NEXT, 3 for PREV).
     * Field 1 (control): varint
     */
    fun encodePlayControl(control: Int): ByteArray {
        val out = mutableListOf<Byte>()
        // Tag for field 1, wireType 0: (1 shl 3) | 0 = 0x08
        out.add(0x08.toByte())
        writeVarint(out, control.toLong())
        return out.toByteArray()
    }

    /**
     * Encodes PlaySeekReq (seektime in seconds).
     * Field 1 (seektime): varint
     */
    fun encodePlaySeek(seektimeSeconds: Int): ByteArray {
        val out = mutableListOf<Byte>()
        // Tag for field 1, wireType 0: (1 shl 3) | 0 = 0x08
        out.add(0x08.toByte())
        writeVarint(out, seektimeSeconds.toLong())
        return out.toByteArray()
    }

    /**
     * Encodes CdPlayQueueReq (index = track index).
     * Field 1 (index): varint
     */
    fun encodeCdPlayQueue(trackIndex: Int): ByteArray {
        val out = mutableListOf<Byte>()
        // Tag for field 1, wireType 0: (1 shl 3) | 0 = 0x08
        out.add(0x08.toByte())
        writeVarint(out, trackIndex.toLong())
        return out.toByteArray()
    }

    fun writeVarint(out: MutableList<Byte>, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        out.add((v and 0x7F).toByte())
    }

    fun readVarint(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = offset
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            i++
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 64) break
        }
        return Pair(result, i)
    }
}

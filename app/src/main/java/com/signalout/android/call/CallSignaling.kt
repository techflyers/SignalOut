package com.signalout.android.call

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Call signaling data classes for WebRTC call setup over BLE mesh.
 * These are serialized into BinaryProtocol payloads.
 */

/**
 * Call types
 */
enum class CallType(val value: Byte) {
    VOICE(0x00),
    VIDEO(0x01);

    companion object {
        fun fromValue(value: Byte): CallType = when (value) {
            0x01.toByte() -> VIDEO
            else -> VOICE
        }
    }
}

/**
 * Signaling message types sent as protocol payloads
 */
sealed class CallSignalingMessage {
    abstract val callId: String

    /**
     * Offer to start a call (contains SDP offer)
     */
    data class CallOffer(
        override val callId: String,
        val sdp: String,
        val callType: CallType = CallType.VOICE,
        val callerName: String = ""
    ) : CallSignalingMessage()

    /**
     * Answer accepting a call (contains SDP answer)
     */
    data class CallAnswer(
        override val callId: String,
        val sdp: String
    ) : CallSignalingMessage()

    /**
     * ICE candidate for peer connection establishment
     */
    data class CallIceCandidate(
        override val callId: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : CallSignalingMessage()

    /**
     * Hang up / end call
     */
    data class CallHangup(
        override val callId: String,
        val reason: String = ""
    ) : CallSignalingMessage()

    /**
     * Reject an incoming call
     */
    data class CallReject(
        override val callId: String,
        val reason: String = ""
    ) : CallSignalingMessage()

    /**
     * Peer is busy on another call
     */
    data class CallBusy(
        override val callId: String
    ) : CallSignalingMessage()

    companion object {
        // Sub-type identifiers within the payload
        private const val TYPE_OFFER: Byte = 0x01
        private const val TYPE_ANSWER: Byte = 0x02
        private const val TYPE_ICE: Byte = 0x03
        private const val TYPE_HANGUP: Byte = 0x04
        private const val TYPE_REJECT: Byte = 0x05
        private const val TYPE_BUSY: Byte = 0x06

        fun generateCallId(): String = UUID.randomUUID().toString().uppercase().take(12)

        /**
         * Encode a signaling message into a binary payload
         */
        fun encode(message: CallSignalingMessage): ByteArray {
            val buffer = ByteBuffer.allocate(8192).apply { order(ByteOrder.BIG_ENDIAN) }

            // Call ID (fixed 12 bytes, padded)
            val callIdBytes = message.callId.toByteArray(Charsets.UTF_8).take(12).toByteArray()
            buffer.put(callIdBytes)
            if (callIdBytes.size < 12) {
                buffer.put(ByteArray(12 - callIdBytes.size))
            }

            when (message) {
                is CallOffer -> {
                    buffer.put(TYPE_OFFER)
                    buffer.put(message.callType.value)
                    // Caller name
                    val nameBytes = message.callerName.toByteArray(Charsets.UTF_8)
                    buffer.put(nameBytes.size.coerceAtMost(255).toByte())
                    buffer.put(nameBytes.take(255).toByteArray())
                    // SDP
                    val sdpBytes = message.sdp.toByteArray(Charsets.UTF_8)
                    buffer.putShort(sdpBytes.size.coerceAtMost(65535).toShort())
                    buffer.put(sdpBytes.take(65535).toByteArray())
                }
                is CallAnswer -> {
                    buffer.put(TYPE_ANSWER)
                    val sdpBytes = message.sdp.toByteArray(Charsets.UTF_8)
                    buffer.putShort(sdpBytes.size.coerceAtMost(65535).toShort())
                    buffer.put(sdpBytes.take(65535).toByteArray())
                }
                is CallIceCandidate -> {
                    buffer.put(TYPE_ICE)
                    val midBytes = message.sdpMid.toByteArray(Charsets.UTF_8)
                    buffer.put(midBytes.size.coerceAtMost(255).toByte())
                    buffer.put(midBytes.take(255).toByteArray())
                    buffer.putInt(message.sdpMLineIndex)
                    val candBytes = message.candidate.toByteArray(Charsets.UTF_8)
                    buffer.putShort(candBytes.size.coerceAtMost(65535).toShort())
                    buffer.put(candBytes.take(65535).toByteArray())
                }
                is CallHangup -> {
                    buffer.put(TYPE_HANGUP)
                    val reasonBytes = message.reason.toByteArray(Charsets.UTF_8)
                    buffer.put(reasonBytes.size.coerceAtMost(255).toByte())
                    buffer.put(reasonBytes.take(255).toByteArray())
                }
                is CallReject -> {
                    buffer.put(TYPE_REJECT)
                    val reasonBytes = message.reason.toByteArray(Charsets.UTF_8)
                    buffer.put(reasonBytes.size.coerceAtMost(255).toByte())
                    buffer.put(reasonBytes.take(255).toByteArray())
                }
                is CallBusy -> {
                    buffer.put(TYPE_BUSY)
                }
            }

            val result = ByteArray(buffer.position())
            buffer.rewind()
            buffer.get(result)
            return result
        }

        /**
         * Decode a signaling message from binary payload
         */
        fun decode(data: ByteArray): CallSignalingMessage? {
            try {
                if (data.size < 13) return null
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }

                // Call ID
                val callIdBytes = ByteArray(12)
                buffer.get(callIdBytes)
                val callId = String(callIdBytes, Charsets.UTF_8).trimEnd('\u0000')

                val subType = buffer.get()

                return when (subType) {
                    TYPE_OFFER -> {
                        val callType = CallType.fromValue(buffer.get())
                        val nameLen = buffer.get().toInt() and 0xFF
                        val nameBytes = ByteArray(nameLen)
                        buffer.get(nameBytes)
                        val callerName = String(nameBytes, Charsets.UTF_8)
                        val sdpLen = buffer.getShort().toInt() and 0xFFFF
                        val sdpBytes = ByteArray(sdpLen)
                        buffer.get(sdpBytes)
                        CallOffer(callId, String(sdpBytes, Charsets.UTF_8), callType, callerName)
                    }
                    TYPE_ANSWER -> {
                        val sdpLen = buffer.getShort().toInt() and 0xFFFF
                        val sdpBytes = ByteArray(sdpLen)
                        buffer.get(sdpBytes)
                        CallAnswer(callId, String(sdpBytes, Charsets.UTF_8))
                    }
                    TYPE_ICE -> {
                        val midLen = buffer.get().toInt() and 0xFF
                        val midBytes = ByteArray(midLen)
                        buffer.get(midBytes)
                        val lineIndex = buffer.getInt()
                        val candLen = buffer.getShort().toInt() and 0xFFFF
                        val candBytes = ByteArray(candLen)
                        buffer.get(candBytes)
                        CallIceCandidate(callId, String(midBytes, Charsets.UTF_8), lineIndex, String(candBytes, Charsets.UTF_8))
                    }
                    TYPE_HANGUP -> {
                        val reasonLen = buffer.get().toInt() and 0xFF
                        val reasonBytes = ByteArray(reasonLen)
                        buffer.get(reasonBytes)
                        CallHangup(callId, String(reasonBytes, Charsets.UTF_8))
                    }
                    TYPE_REJECT -> {
                        val reasonLen = buffer.get().toInt() and 0xFF
                        val reasonBytes = ByteArray(reasonLen)
                        buffer.get(reasonBytes)
                        CallReject(callId, String(reasonBytes, Charsets.UTF_8))
                    }
                    TYPE_BUSY -> CallBusy(callId)
                    else -> null
                }
            } catch (e: Exception) {
                return null
            }
        }
    }
}

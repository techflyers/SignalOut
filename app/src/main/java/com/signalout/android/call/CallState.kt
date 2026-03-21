package com.signalout.android.call

/**
 * Represents the current state of a call.
 */
sealed class CallState {
    /** No active call */
    object Idle : CallState()

    /** Outgoing call ringing */
    data class Outgoing(
        val callId: String,
        val peerID: String,
        val peerName: String,
        val isVideo: Boolean
    ) : CallState()

    /** Incoming call ringing */
    data class Incoming(
        val callId: String,
        val peerID: String,
        val callerName: String,
        val isVideo: Boolean,
        val sdp: String = ""
    ) : CallState()

    /** Call is being connected (ICE / DTLS handshake) */
    data class Connecting(
        val callId: String,
        val peerID: String,
        val isVideo: Boolean
    ) : CallState()

    /** Call is active */
    data class Connected(
        val callId: String,
        val peerID: String,
        val peerName: String,
        val isVideo: Boolean,
        val startTimeMs: Long = System.currentTimeMillis(),
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val isLocalVideoEnabled: Boolean = true,
        val isRemoteVideoEnabled: Boolean = true
    ) : CallState()

    /** Call ended */
    data class Ended(
        val reason: String = "Call ended"
    ) : CallState()
}

package com.signalout.android.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages call lifecycle, signaling, and audio routing.
 * 
 * This is the central call orchestrator that:
 * - Handles outgoing/incoming call initiation
 * - Coordinates with WebRTCManager for media
 * - Routes signaling messages to/from the BLE mesh
 * - Controls audio routing (speaker/earpiece/bluetooth)
 */
class CallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CallManager"
        private const val CALL_TIMEOUT_MS = 60_000L // 60 seconds ring timeout

        @Volatile
        private var instance: CallManager? = null

        fun getInstance(context: Context): CallManager {
            return instance ?: synchronized(this) {
                instance ?: CallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timeoutJob: Job? = null

    // Callback to send signaling messages over the mesh
    var onSendSignaling: ((peerID: String, payload: ByteArray) -> Unit)? = null

    // Audio manager for routing
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // WebRTC connection and media stream management
    val webRTCManager by lazy {
        WebRTCManager(context).apply {
            onIceCandidate = { candidate ->
                val state = _callState.value
                val callId = when (state) {
                    is CallState.Outgoing -> state.callId
                    is CallState.Incoming -> state.callId
                    is CallState.Connecting -> state.callId
                    is CallState.Connected -> state.callId
                    else -> null
                }
                val peerID = when (state) {
                    is CallState.Outgoing -> state.peerID
                    is CallState.Incoming -> state.peerID
                    is CallState.Connecting -> state.peerID
                    is CallState.Connected -> state.peerID
                    else -> null
                }
                if (callId != null && peerID != null) {
                    sendSignaling(
                        peerID,
                        CallSignalingMessage.CallIceCandidate(callId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                    )
                }
            }
        }
    }

    // --- Outgoing Call ---

    /**
     * Start an outgoing call to the specified peer.
     */
    fun startCall(peerID: String, peerName: String, isVideo: Boolean) {
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Cannot start call: already in a call")
            return
        }

        val callId = CallSignalingMessage.generateCallId()
        Log.d(TAG, "Starting ${if (isVideo) "video" else "voice"} call to $peerName ($peerID), callId=$callId")

        _callState.value = CallState.Outgoing(
            callId = callId,
            peerID = peerID,
            peerName = peerName,
            isVideo = isVideo
        )

        // Start media capture
        webRTCManager.startMedia(isVideo)

        // Create PeerConnection and Offer
        webRTCManager.createPeerConnection(isIncoming = false) { sdp ->
            val offer = CallSignalingMessage.CallOffer(
                callId = callId,
                sdp = sdp,
                callType = if (isVideo) CallType.VIDEO else CallType.VOICE,
                callerName = peerName
            )
            sendSignaling(peerID, offer)
        }

        // Start timeout timer
        startCallTimeout(callId)

        // Configure audio for call
        setupAudioForCall(isVideo)
    }

    /**
     * Accept an incoming call
     */
    fun acceptCall() {
        val state = _callState.value
        if (state !is CallState.Incoming) {
            Log.w(TAG, "Cannot accept: no incoming call")
            return
        }

        Log.d(TAG, "Accepting call ${state.callId} from ${state.callerName}")
        cancelTimeout()

        _callState.value = CallState.Connecting(
            callId = state.callId,
            peerID = state.peerID,
            isVideo = state.isVideo
        )

        // Start media capture
        webRTCManager.startMedia(state.isVideo)

        // Create PeerConnection, then process the stored offer to generate & send answer
        webRTCManager.createPeerConnection(isIncoming = true) { /* onSdpReady unused here */ }

        webRTCManager.handleRemoteOffer(state.sdp) { answerSdp ->
            val answer = CallSignalingMessage.CallAnswer(
                callId = state.callId,
                sdp = answerSdp
            )
            sendSignaling(state.peerID, answer)

            // Transition to connected once answer is ready
            _callState.value = CallState.Connected(
                callId = state.callId,
                peerID = state.peerID,
                peerName = state.callerName,
                isVideo = state.isVideo
            )
        }

        setupAudioForCall(state.isVideo)
    }

    /**
     * Reject an incoming call
     */
    fun rejectCall() {
        val state = _callState.value
        if (state !is CallState.Incoming) return

        Log.d(TAG, "Rejecting call ${state.callId}")
        sendSignaling(state.peerID, CallSignalingMessage.CallReject(state.callId, "rejected"))
        endCallInternal("Rejected")
    }

    /**
     * End the current call
     */
    fun endCall() {
        val state = _callState.value
        val (callId, peerID) = when (state) {
            is CallState.Outgoing -> state.callId to state.peerID
            is CallState.Incoming -> state.callId to state.peerID
            is CallState.Connecting -> state.callId to state.peerID
            is CallState.Connected -> state.callId to state.peerID
            else -> return
        }

        Log.d(TAG, "Ending call $callId")
        sendSignaling(peerID, CallSignalingMessage.CallHangup(callId))
        endCallInternal("Call ended")
    }

    /**
     * Toggle mute
     */
    fun toggleMute() {
        val state = _callState.value
        if (state is CallState.Connected) {
            val newMuted = !state.isMuted
            _callState.value = state.copy(isMuted = newMuted)
            webRTCManager.setAudioEnabled(!newMuted)
        }
    }

    /**
     * Toggle speaker
     */
    fun toggleSpeaker() {
        val state = _callState.value
        if (state is CallState.Connected) {
            val newSpeaker = !state.isSpeakerOn
            _callState.value = state.copy(isSpeakerOn = newSpeaker)
            audioManager.isSpeakerphoneOn = newSpeaker
        }
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        webRTCManager.switchCamera()
    }

    /**
     * Toggle local video
     */
    fun toggleVideo() {
        val state = _callState.value
        if (state is CallState.Connected) {
            val enabled = !state.isLocalVideoEnabled
            _callState.value = state.copy(isLocalVideoEnabled = enabled)
            webRTCManager.setVideoEnabled(enabled)
        }
    }

    // --- Incoming Signaling ---

    /**
     * Handle incoming signaling message from mesh
     */
    fun handleSignalingMessage(fromPeerID: String, data: ByteArray) {
        val message = CallSignalingMessage.decode(data) ?: return

        when (message) {
            is CallSignalingMessage.CallOffer -> {
                if (_callState.value !is CallState.Idle) {
                    // Already in a call — send busy
                    sendSignaling(fromPeerID, CallSignalingMessage.CallBusy(message.callId))
                    return
                }

                Log.d(TAG, "Incoming ${message.callType} call from ${message.callerName}")
                // Store the offer SDP — don't touch WebRTC until user accepts
                _callState.value = CallState.Incoming(
                    callId = message.callId,
                    peerID = fromPeerID,
                    callerName = message.callerName,
                    isVideo = message.callType == CallType.VIDEO,
                    sdp = message.sdp
                )
                startCallTimeout(message.callId)
            }
            is CallSignalingMessage.CallAnswer -> {
                val state = _callState.value
                if (state is CallState.Outgoing && state.callId == message.callId) {
                    cancelTimeout()
                    
                    // Route to WebRTC
                    webRTCManager.handleRemoteAnswer(message.sdp)
                    
                    _callState.value = CallState.Connected(
                        callId = state.callId,
                        peerID = state.peerID,
                        peerName = state.peerName,
                        isVideo = state.isVideo
                    )
                }
            }
            is CallSignalingMessage.CallIceCandidate -> {
                Log.d(TAG, "Received ICE candidate for ${message.callId}")
                val state = _callState.value
                val activeCallId = when (state) {
                    is CallState.Outgoing -> state.callId
                    is CallState.Incoming -> state.callId
                    is CallState.Connecting -> state.callId
                    is CallState.Connected -> state.callId
                    else -> null
                }
                if (activeCallId == message.callId) {
                    webRTCManager.handleRemoteIceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate)
                }
            }
            is CallSignalingMessage.CallHangup -> {
                endCallInternal(message.reason.ifEmpty { "Peer hung up" })
            }
            is CallSignalingMessage.CallReject -> {
                endCallInternal("Call rejected")
            }
            is CallSignalingMessage.CallBusy -> {
                endCallInternal("Peer is busy")
            }
        }
    }

    // --- Private Helpers ---

    private fun sendSignaling(peerID: String, message: CallSignalingMessage) {
        try {
            val payload = CallSignalingMessage.encode(message)
            onSendSignaling?.invoke(peerID, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send signaling: ${e.message}")
        }
    }

    private fun endCallInternal(reason: String) {
        cancelTimeout()
        _callState.value = CallState.Ended(reason)
        restoreAudio()
        
        webRTCManager.endCall()

        // Auto-reset to idle after brief delay
        scope.launch {
            delay(2000)
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }

    private fun startCallTimeout(callId: String) {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(CALL_TIMEOUT_MS)
            val state = _callState.value
            val isStillRinging = when (state) {
                is CallState.Outgoing -> state.callId == callId
                is CallState.Incoming -> state.callId == callId
                else -> false
            }
            if (isStillRinging) {
                endCallInternal("No answer")
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun setupAudioForCall(isVideo: Boolean = false) {
        try {
            @Suppress("DEPRECATION")
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            // Enable speaker by default for video calls
            audioManager.isSpeakerphoneOn = isVideo
        } catch (e: Exception) {
            Log.e(TAG, "Audio setup error: ${e.message}")
        }
    }

    private fun restoreAudio() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
        } catch (e: Exception) {
            Log.e(TAG, "Audio restore error: ${e.message}")
        }
    }
}

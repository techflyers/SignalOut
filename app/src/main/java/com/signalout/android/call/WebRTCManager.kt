package com.signalout.android.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*

class WebRTCManager(private val context: Context) {
    companion object {
        private const val TAG = "WebRTCManager"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val LOCAL_STREAM_ID = "ARDAMS"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    val eglBaseContext: EglBase.Context by lazy { eglBase.eglBaseContext }
    private val eglBase: EglBase by lazy { EglBase.create() }

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoEnabled = MutableStateFlow(false)
    val localVideoEnabled: StateFlow<Boolean> = _localVideoEnabled.asStateFlow()

    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onLocalStreamReady: ((MediaStream) -> Unit)? = null
    
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun startMedia(isVideo: Boolean) {
        if (localAudioTrack != null || localVideoTrack != null) {
            Log.w(TAG, "Media already started, skipping duplicate creation")
            return
        }

        val factory = peerConnectionFactory ?: return
        
        // Setup Audio
        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)
        
        Log.d(TAG, "Audio track created")

        // Setup Video if requested
        if (isVideo) {
            videoCapturer = createVideoCapturer()
            if (videoCapturer != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
                videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                
                // Typical low-res config for constrained bandwidth
                videoCapturer?.startCapture(320, 240, 15)
                
                localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
                localVideoTrack?.setEnabled(true)
                _localVideoEnabled.value = true
                Log.d(TAG, "Video track created")
            }
        }
    }

    fun createPeerConnection(isIncoming: Boolean, onSdpReady: (String) -> Unit) {
        val factory = peerConnectionFactory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $newState")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidate?.invoke(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                // Remote stream received in legacy semantics
                Log.d(TAG, "Remote stream received")
                val track = stream?.videoTracks?.firstOrNull()
                _remoteVideoTrack.value = track
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(TAG, "Remote track received: ${receiver?.track()?.kind()}")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)
        
        // Add tracks (Unified Plan compatible)
        val mediaStream = factory.createLocalMediaStream(LOCAL_STREAM_ID)
        localAudioTrack?.let { 
            mediaStream.addTrack(it)
            peerConnection?.addTrack(it, listOf(LOCAL_STREAM_ID))
        }
        localVideoTrack?.let { 
            mediaStream.addTrack(it)
            peerConnection?.addTrack(it, listOf(LOCAL_STREAM_ID))
        }
        
        onLocalStreamReady?.invoke(mediaStream)

        if (!isIncoming) {
            val constraints = MediaConstraints()
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        peerConnection?.setLocalDescription(this, it)
                        onSdpReady(it.description)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) { Log.e(TAG, "Offer creation failed: $error") }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    fun handleRemoteOffer(sdp: String, onAnswerReady: (String) -> Unit) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                drainPendingIceCandidates()
                val constraints = MediaConstraints()
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp?.let {
                            peerConnection?.setLocalDescription(this, it)
                            onAnswerReady(it.description)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) { Log.e(TAG, "Answer creation failed: $error") }
                    override fun onSetFailure(error: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) { Log.e(TAG, "Setting remote offer failed: $error") }
        }, sessionDescription)
    }

    fun handleRemoteAnswer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() { drainPendingIceCandidates() }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) { Log.e(TAG, "Setting remote answer failed: $error") }
        }, sessionDescription)
    }

    private fun drainPendingIceCandidates() {
        pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun handleRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        if (peerConnection?.remoteDescription == null) {
            pendingIceCandidates.add(candidate)
        } else {
            peerConnection?.addIceCandidate(candidate)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _localVideoEnabled.value = enabled
        if (enabled) {
            videoCapturer?.startCapture(320, 240, 15)
        } else {
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping capture: ${e.message}")
            }
        }
    }

    /**
     * Switch between front and back camera (if supported)
     */
    fun switchCamera() {
        Log.d(TAG, "Switching camera...")
        val capturer = videoCapturer
        if (capturer is CameraVideoCapturer) {
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    Log.d(TAG, "Camera switched successfully. Front: $isFrontCamera")
                }

                override fun onCameraSwitchError(errorDescription: String) {
                    Log.e(TAG, "Camera switch failed: $errorDescription")
                }
            })
        } else {
            Log.w(TAG, "Cannot switch camera: capturer is null or not a CameraVideoCapturer")
        }
    }

    fun endCall() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture: ${e.message}")
        }
        
        try { videoCapturer?.dispose() } catch (e: Exception) {}
        videoCapturer = null
        
        try { surfaceTextureHelper?.dispose() } catch (e: Exception) {}
        surfaceTextureHelper = null
        
        try { peerConnection?.dispose() } catch (e: Exception) {}
        peerConnection = null
        
        try { localAudioTrack?.dispose() } catch (e: Exception) {}
        localAudioTrack = null
        
        try { localVideoTrack?.dispose() } catch (e: Exception) {}
        localVideoTrack = null
        
        try { audioSource?.dispose() } catch (e: Exception) {}
        audioSource = null
        
        try { videoSource?.dispose() } catch (e: Exception) {}
        videoSource = null
        
        pendingIceCandidates.clear()
        
        _remoteVideoTrack.value = null
        _localVideoEnabled.value = false
        
        // DO NOT dispose PeerConnectionFactory so it can be used for the next call!
    }
}

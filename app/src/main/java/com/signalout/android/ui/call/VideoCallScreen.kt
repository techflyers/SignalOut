package com.signalout.android.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import com.signalout.android.call.CallState
import com.signalout.android.call.CallManager
import com.signalout.android.ui.theme.WhatsAppColors
import org.webrtc.SurfaceViewRenderer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Full-screen video call UI — WhatsApp style.
 * Shows remote video full screen with local video PiP in a draggable corner.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoCallScreen(
    callState: CallState,
    callManager: CallManager,
    onDismiss: () -> Unit
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    // Fade-in/out controls
    var showControls by remember { mutableStateOf(true) }

    // PiP local video position
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }

    // Auto-hide controls disabled by user request
    val isConnected = callState is CallState.Connected
    LaunchedEffect(showControls, isConnected) {
        if (showControls && isConnected) {
            // delay(4000)
            // showControls = false
        }
    }

    val remoteVideoTrack by callManager.webRTCManager.remoteVideoTrack.collectAsState()
    val localVideoEnabled by callManager.webRTCManager.localVideoEnabled.collectAsState()
    val localVideoTrack = callManager.webRTCManager.localVideoTrack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141A))
    ) {
        // Remote video placeholder (full screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A2E35)),
            contentAlignment = Alignment.Center
        ) {
            // When connected, show remote participant avatar or video
            when (callState) {
                is CallState.Connected -> {
                    if (!callState.isRemoteVideoEnabled) {
                        // No remote video — show avatar
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppColors.TealAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = callState.peerName.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = callState.peerName,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Camera off", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    } else {
                        if (remoteVideoTrack != null) {
                            var viewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceViewRenderer(ctx).apply {
                                        init(callManager.webRTCManager.eglBaseContext, null)
                                        setEnableHardwareScaler(true)
                                        setMirror(false)
                                        viewRef = this
                                    }
                                },
                                onRelease = { view ->
                                    view.release()
                                    viewRef = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            DisposableEffect(remoteVideoTrack, viewRef) {
                                val track = remoteVideoTrack
                                val view = viewRef
                                if (track != null && view != null) {
                                    track.addSink(view)
                                }
                                onDispose {
                                    if (track != null && view != null) {
                                        track.removeSink(view)
                                    }
                                }
                            }
                        } else {
                            // Placeholder while waiting for video track
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0D1F26)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Connecting Video...",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
                is CallState.Incoming -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(WhatsAppColors.TealAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = callState.callerName.firstOrNull()?.uppercase() ?: "?",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(callState.callerName, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Incoming video call...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                is CallState.Outgoing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(WhatsAppColors.TealAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = callState.peerName.firstOrNull()?.uppercase() ?: "?",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(callState.peerName, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Calling...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                is CallState.Ended -> {
                    Text(callState.reason, fontSize = 18.sp, color = Color.White.copy(alpha = 0.7f))
                    LaunchedEffect(Unit) {
                        delay(2000)
                        onDismiss()
                    }
                }
                else -> {}
            }
        }

        // Local video PiP (draggable, top-right corner)
        if (isConnected && (callState as? CallState.Connected)?.isLocalVideoEnabled == true) {
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp
            val screenHeightDp = configuration.screenHeightDp

            Box(
                modifier = Modifier
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .padding(top = 48.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .width(100.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A3942))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (localVideoEnabled && localVideoTrack != null) {
                    var viewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(callManager.webRTCManager.eglBaseContext, null)
                                setEnableHardwareScaler(true)
                                setMirror(true) // Mirror local camera
                                viewRef = this
                            }
                        },
                        onRelease = { view ->
                            view.release()
                            viewRef = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    DisposableEffect(localVideoTrack, viewRef) {
                        val track = localVideoTrack
                        val view = viewRef
                        if (track != null && view != null) {
                            track.addSink(view)
                        }
                        onDispose {
                            if (track != null && view != null) {
                                track.removeSink(view)
                            }
                        }
                    }
                } else {
                    Text("You", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
        }

        // Controls overlay (tap to show/hide)
        if (isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                showControls = !showControls
                            }
                        }
                    }
            )
        }

        // Bottom controls
        val showBottomControls = when (callState) {
            is CallState.Connected -> showControls
            is CallState.Incoming, is CallState.Outgoing, is CallState.Connecting -> true
            else -> false
        }

        if (showBottomControls) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                when (callState) {
                    is CallState.Incoming -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { callManager.rejectCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            ) {
                                Icon(Icons.Filled.CallEnd, "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            IconButton(
                                onClick = { callManager.acceptCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(WhatsAppColors.TealAccent, CircleShape)
                            ) {
                                Icon(Icons.Filled.Videocam, "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    is CallState.Connected -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Camera flip
                            VideoControlButton(
                                icon = Icons.Filled.FlipCameraAndroid,
                                label = "Flip",
                                onClick = { callManager.switchCamera() }
                            )

                            // Mute
                            VideoControlButton(
                                icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = if (callState.isMuted) "Unmute" else "Mute",
                                isActive = callState.isMuted,
                                onClick = { callManager.toggleMute() }
                            )

                            // Video toggle
                            VideoControlButton(
                                icon = if (callState.isLocalVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                label = if (callState.isLocalVideoEnabled) "Camera Off" else "Camera On",
                                isActive = !callState.isLocalVideoEnabled,
                                onClick = { callManager.toggleVideo() }
                            )

                            // End call
                            IconButton(
                                onClick = { callManager.endCall() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            ) {
                                Icon(Icons.Filled.CallEnd, "End", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    is CallState.Outgoing, is CallState.Connecting -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { callManager.endCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            ) {
                                Icon(Icons.Filled.CallEnd, "Cancel", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun VideoControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

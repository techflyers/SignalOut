package com.signalout.android.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalout.android.call.CallState
import com.signalout.android.call.CallManager
import com.signalout.android.ui.theme.WhatsAppColors
import kotlinx.coroutines.delay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Full-screen voice call UI composable — WhatsApp style.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceCallScreen(
    callState: CallState,
    callManager: CallManager,
    onDismiss: () -> Unit
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(android.Manifest.permission.RECORD_AUDIO)
    )

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A2E35))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: status text
            Spacer(modifier = Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Peer name
                val peerName = when (callState) {
                    is CallState.Outgoing -> callState.peerName
                    is CallState.Incoming -> callState.callerName
                    is CallState.Connected -> callState.peerName
                    is CallState.Connecting -> "Connecting..."
                    is CallState.Ended -> ""
                    else -> ""
                }

                Text(
                    text = peerName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status text
                val statusText = when (callState) {
                    is CallState.Outgoing -> "Calling..."
                    is CallState.Incoming -> "Incoming call"
                    is CallState.Connecting -> "Connecting..."
                    is CallState.Connected -> {
                        // Show call duration
                        CallDurationText(startTimeMs = callState.startTimeMs)
                        ""
                    }
                    is CallState.Ended -> callState.reason
                    else -> ""
                }

                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Center: Avatar with pulse animation
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Pulse animation for ringing
                val isRinging = callState is CallState.Outgoing || callState is CallState.Incoming
                if (isRinging) {
                    PulseAnimation()
                }

                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(WhatsAppColors.TealAccent),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = when (callState) {
                        is CallState.Outgoing -> callState.peerName.firstOrNull()?.uppercase() ?: "?"
                        is CallState.Incoming -> callState.callerName.firstOrNull()?.uppercase() ?: "?"
                        is CallState.Connected -> callState.peerName.firstOrNull()?.uppercase() ?: "?"
                        else -> "?"
                    }
                    Text(
                        text = initial,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Bottom: Action buttons
            when (callState) {
                is CallState.Incoming -> {
                    // Accept / Reject buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Reject (red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { callManager.rejectCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            ) {
                                Icon(
                                    Icons.Filled.CallEnd,
                                    contentDescription = "Reject",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }

                        // Accept (green)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { callManager.acceptCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(WhatsAppColors.TealAccent, CircleShape)
                            ) {
                                Icon(
                                    Icons.Filled.Call,
                                    contentDescription = "Accept",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Accept", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }

                is CallState.Connected -> {
                    // Active call controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Mute
                        CallControlButton(
                            icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (callState.isMuted) "Unmute" else "Mute",
                            isActive = callState.isMuted,
                            onClick = { callManager.toggleMute() }
                        )

                        // Speaker
                        CallControlButton(
                            icon = if (callState.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown,
                            label = if (callState.isSpeakerOn) "Earpiece" else "Speaker",
                            isActive = callState.isSpeakerOn,
                            onClick = { callManager.toggleSpeaker() }
                        )

                        // End call (red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { callManager.endCall() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            ) {
                                Icon(
                                    Icons.Filled.CallEnd,
                                    contentDescription = "End call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("End", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }

                is CallState.Outgoing, is CallState.Connecting -> {
                    // Cancel call
                    Column(
                        modifier = Modifier.padding(bottom = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { callManager.endCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFFF3B30), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.CallEnd,
                                contentDescription = "Cancel",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                is CallState.Ended -> {
                    // Auto dismiss after showing reason
                    LaunchedEffect(Unit) {
                        delay(2000)
                        onDismiss()
                    }
                    Spacer(Modifier.height(48.dp))
                }

                else -> {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun PulseAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(WhatsAppColors.TealAccent.copy(alpha = alpha))
    )
}

@Composable
private fun CallDurationText(startTimeMs: Long) {
    var durationSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(startTimeMs) {
        while (true) {
            durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
            delay(1000)
        }
    }

    val mm = durationSec / 60
    val ss = durationSec % 60
    Text(
        text = String.format("%02d:%02d", mm, ss),
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.7f)
    )
}

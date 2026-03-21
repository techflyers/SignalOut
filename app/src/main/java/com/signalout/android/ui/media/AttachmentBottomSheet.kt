package com.signalout.android.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WhatsApp-style attachment bottom sheet with circular icon buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onDocumentClick: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onAudioClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactClick: () -> Unit
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // First row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Filled.Description,
                    label = "Document",
                    color = Color(0xFF7C5CE3),
                    onClick = {
                        onDocumentClick()
                        onDismiss()
                    }
                )
                AttachmentOption(
                    icon = Icons.Filled.CameraAlt,
                    label = "Camera",
                    color = Color(0xFFFF3B58),
                    onClick = {
                        onCameraClick()
                        onDismiss()
                    }
                )
                AttachmentOption(
                    icon = Icons.Filled.Image,
                    label = "Gallery",
                    color = Color(0xFFBF59CF),
                    onClick = {
                        onGalleryClick()
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Filled.Headphones,
                    label = "Audio",
                    color = Color(0xFFF97316),
                    onClick = {
                        onAudioClick()
                        onDismiss()
                    }
                )
                AttachmentOption(
                    icon = Icons.Filled.LocationOn,
                    label = "Location",
                    color = Color(0xFF1DA855),
                    onClick = {
                        onLocationClick()
                        onDismiss()
                    }
                )
                AttachmentOption(
                    icon = Icons.Filled.Person,
                    label = "Contact",
                    color = Color(0xFF0096DE),
                    onClick = {
                        onContactClick()
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    }
}

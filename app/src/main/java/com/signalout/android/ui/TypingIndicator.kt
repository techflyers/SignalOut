package com.signalout.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.signalout.android.ui.theme.WhatsAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Text

@Composable
fun TypingIndicatorBubble(modifier: Modifier = Modifier, typingUser: String? = null) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground
    val bubbleColor = if (isDark) WhatsAppColors.DarkIncomingBubble else WhatsAppColors.LightIncomingBubble

    val bubbleShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = 4.dp,
        bottomEnd = 12.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 48.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TypingIndicatorDots(color = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21))
        }
        
        if (typingUser != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val displayUser = if (typingUser.startsWith("@")) typingUser else "@$typingUser"
            Text(
                text = "$displayUser is typing...",
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun TypingIndicatorDots(color: Color) {
    val alphaAnim1 = remember { Animatable(0.2f) }
    val alphaAnim2 = remember { Animatable(0.2f) }
    val alphaAnim3 = remember { Animatable(0.2f) }

    val yAnim1 = remember { Animatable(0f) }
    val yAnim2 = remember { Animatable(0f) }
    val yAnim3 = remember { Animatable(0f) }

    val animationSpec = infiniteRepeatable<Float>(
        animation = tween(400, easing = LinearOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    )

    LaunchedEffect(Unit) {
        launch { alphaAnim1.animateTo(1f, animationSpec) }
        launch { yAnim1.animateTo(-4f, animationSpec) }
    }

    LaunchedEffect(Unit) {
        delay(200)
        launch { alphaAnim2.animateTo(1f, animationSpec) }
        launch { yAnim2.animateTo(-4f, animationSpec) }
    }

    LaunchedEffect(Unit) {
        delay(400)
        launch { alphaAnim3.animateTo(1f, animationSpec) }
        launch { yAnim3.animateTo(-4f, animationSpec) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Dot(color, alphaAnim1.value, yAnim1.value)
        Dot(color, alphaAnim2.value, yAnim2.value)
        Dot(color, alphaAnim3.value, yAnim3.value)
    }
}

@Composable
private fun Dot(color: Color, alpha: Float, yOffset: Float) {
    Box(
        modifier = Modifier
            .offset(y = yOffset.dp)
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

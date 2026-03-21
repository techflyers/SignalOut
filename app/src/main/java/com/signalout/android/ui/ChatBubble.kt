package com.signalout.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalout.android.ui.theme.WhatsAppColors

/**
 * WhatsApp-style chat bubble with speech tail and configurable alignment.
 */
@Composable
fun ChatBubble(
    isOutgoing: Boolean,
    senderName: String? = null,
    senderColor: Color = Color(0xFF06CF9C),
    showTail: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground

    val bubbleColor = if (isOutgoing) {
        if (isDark) WhatsAppColors.DarkOutgoingBubble else WhatsAppColors.LightOutgoingBubble
    } else {
        if (isDark) WhatsAppColors.DarkIncomingBubble else WhatsAppColors.LightIncomingBubble
    }

    val bubbleShape = RoundedCornerShape(
        topStart = if (!isOutgoing && showTail) 4.dp else 12.dp,
        topEnd = if (isOutgoing && showTail) 4.dp else 12.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isOutgoing) 48.dp else 4.dp,
                end = if (isOutgoing) 4.dp else 48.dp,
                top = 1.dp,
                bottom = 1.dp
            ),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .widthIn(min = 80.dp, max = 300.dp)
        ) {
            // Sender name for incoming group messages
            if (!isOutgoing && senderName != null) {
                Text(
                    text = senderName,
                    color = senderColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            content()
        }
    }
}

/**
 * Metadata row inside a chat bubble (timestamp + delivery ticks)
 */
@Composable
fun BubbleMetadata(
    timeText: String,
    deliveryContent: @Composable (() -> Unit)? = null,
    isOutgoing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = timeText,
            fontSize = 11.sp,
            color = if (isOutgoing) {
                Color(0xFF8FCEA4)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
        if (deliveryContent != null) {
            Spacer(modifier = Modifier.width(3.dp))
            deliveryContent()
        }
    }
}

package com.signalout.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.signalout.android.ui.theme.WhatsAppColors

/**
 * WhatsApp-style doodle pattern chat background.
 * Draws subtle repeating icons (chat bubbles, phones, emojis) as a faint pattern.
 */
@Composable
fun WhatsAppChatBackground(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground

    val bgColor = if (isDark) Color(0xFF0B141A) else Color(0xFFECE5DD)
    val patternColor = if (isDark) Color(0xFF172329) else Color(0xFFD6CFC6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDoodlePattern(patternColor)
        }
    }
}

/**
 * Draws a subtle repeating doodle pattern across the canvas.
 */
private fun DrawScope.drawDoodlePattern(color: Color) {
    val spacing = 70f
    val iconSize = 14f
    val stroke = Stroke(width = 1.2f)

    var y = spacing / 2
    var rowIndex = 0
    while (y < size.height + spacing) {
        var x = if (rowIndex % 2 == 0) spacing / 2 else spacing
        while (x < size.width + spacing) {
            val iconType = ((x.toInt() / spacing.toInt()) + (y.toInt() / spacing.toInt())) % 5
            when (iconType.toInt()) {
                0 -> drawSmallBubble(Offset(x, y), iconSize, color, stroke)
                1 -> drawSmallPhone(Offset(x, y), iconSize, color, stroke)
                2 -> drawSmallHeart(Offset(x, y), iconSize, color, stroke)
                3 -> drawSmallStar(Offset(x, y), iconSize, color, stroke)
                4 -> drawSmallCamera(Offset(x, y), iconSize, color, stroke)
            }
            x += spacing
        }
        y += spacing
        rowIndex++
    }
}

private fun DrawScope.drawSmallBubble(center: Offset, size: Float, color: Color, stroke: Stroke) {
    drawCircle(color = color, radius = size * 0.6f, center = center, style = stroke)
    // Small tail
    val path = Path().apply {
        moveTo(center.x - size * 0.4f, center.y + size * 0.3f)
        lineTo(center.x - size * 0.7f, center.y + size * 0.7f)
        lineTo(center.x - size * 0.1f, center.y + size * 0.5f)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawSmallPhone(center: Offset, size: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - size * 0.3f, center.y - size * 0.5f),
        size = androidx.compose.ui.geometry.Size(size * 0.6f, size),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.1f),
        style = stroke
    )
}

private fun DrawScope.drawSmallHeart(center: Offset, size: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.3f)
        cubicTo(center.x - size * 0.6f, center.y - size * 0.1f,
            center.x - size * 0.6f, center.y - size * 0.5f,
            center.x, center.y - size * 0.2f)
        cubicTo(center.x + size * 0.6f, center.y - size * 0.5f,
            center.x + size * 0.6f, center.y - size * 0.1f,
            center.x, center.y + size * 0.3f)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawSmallStar(center: Offset, size: Float, color: Color, stroke: Stroke) {
    val r = size * 0.5f
    val path = Path().apply {
        for (i in 0 until 5) {
            val angle = Math.toRadians((i * 72 - 90).toDouble())
            val px = center.x + (r * Math.cos(angle)).toFloat()
            val py = center.y + (r * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)

            val innerAngle = Math.toRadians((i * 72 - 90 + 36).toDouble())
            val innerR = r * 0.4f
            val ipx = center.x + (innerR * Math.cos(innerAngle)).toFloat()
            val ipy = center.y + (innerR * Math.sin(innerAngle)).toFloat()
            lineTo(ipx, ipy)
        }
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawSmallCamera(center: Offset, size: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - size * 0.5f, center.y - size * 0.2f),
        size = androidx.compose.ui.geometry.Size(size, size * 0.7f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.1f),
        style = stroke
    )
    drawCircle(color = color, radius = size * 0.2f, center = center, style = stroke)
}

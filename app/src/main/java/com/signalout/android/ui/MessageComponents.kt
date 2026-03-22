package com.signalout.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.signalout.android.model.SignaloutMessage
import com.signalout.android.model.DeliveryStatus
import com.signalout.android.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.signalout.android.ui.media.FileMessageItem
import com.signalout.android.model.SignaloutMessageType
import com.signalout.android.R
import com.signalout.android.ui.theme.WhatsAppColors
import androidx.compose.ui.res.stringResource


// VoiceNotePlayer moved to com.signalout.android.ui.media.VoiceNotePlayer

/**
 * Message display components for ChatScreen - WhatsApp-style chat bubbles
 */

@Composable
fun MessagesList(
    messages: List<SignaloutMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: ((Boolean) -> Unit)? = null,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((SignaloutMessage) -> Unit)? = null,
    onCancelTransfer: ((SignaloutMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    isPeerTyping: Boolean = false,
    typingUsers: List<String> = emptyList()
) {
    val listState = rememberLazyListState()

    // Track if this is the first time messages are being loaded
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    var followIncomingMessages by remember { mutableStateOf(true) }

    // Smart scroll: auto-scroll to bottom for initial load, then follow unless user scrolls away
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val isFirstLoad = !hasScrolledToInitialPosition
            if (isFirstLoad || followIncomingMessages) {
                listState.scrollToItem(0)
                if (isFirstLoad) {
                    hasScrolledToInitialPosition = true
                }
            }
        }
    }

    // Track whether user has scrolled away from the latest messages
    val isAtLatest by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            firstVisibleIndex <= 2
        }
    }
    LaunchedEffect(isAtLatest) {
        followIncomingMessages = isAtLatest
        onScrolledUpChanged?.invoke(!isAtLatest)
    }

    // Force scroll to bottom when requested (e.g., when user sends a message)
    LaunchedEffect(forceScrollToBottom) {
        if (messages.isNotEmpty()) {
            followIncomingMessages = true
            listState.scrollToItem(0)
        }
    }

    Box(modifier = modifier) {
        // WhatsApp doodle pattern background
        WhatsAppChatBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true
        ) {
            if (isPeerTyping || typingUsers.isNotEmpty()) {
                item {
                    val displayUser = typingUsers.firstOrNull()
                    TypingIndicatorBubble(
                        modifier = Modifier.padding(bottom = 4.dp),
                        typingUser = displayUser
                    )
                }
            }
            items(
                items = messages.asReversed(),
                key = { it.id }
            ) { message ->
                MessageItem(
                    message = message,
                    messages = messages,
                    currentUserNickname = currentUserNickname,
                    meshService = meshService,
                    onNicknameClick = onNicknameClick,
                    onMessageLongPress = onMessageLongPress,
                    onCancelTransfer = onCancelTransfer,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: SignaloutMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    messages: List<SignaloutMessage> = emptyList(),
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((SignaloutMessage) -> Unit)? = null,
    onCancelTransfer: ((SignaloutMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val isSelf = message.senderPeerID == meshService.myPeerID ||
            message.sender == currentUserNickname ||
            message.sender.startsWith("$currentUserNickname#")

    // System messages get a centered pill style
    if (message.sender == "system") {
        SystemMessageBubble(
            message = message,
            timeFormatter = timeFormatter
        )
        return
    }

    // Wrap all message types in a ChatBubble
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground
    val senderColor = if (isSelf) {
        WhatsAppColors.TealAccent
    } else {
        getPeerColor(message, isDark)
    }

    ChatBubble(
        isOutgoing = isSelf,
        senderName = if (!isSelf) message.sender else null,
        senderColor = senderColor
    ) {
        MessageTextWithClickableNicknames(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            onImageClick = onImageClick,
            isSelf = isSelf,
            modifier = Modifier
        )
    }
}

/**
 * System message displayed as a centered pill
 */
@Composable
private fun SystemMessageBubble(
    message: SignaloutMessage,
    timeFormatter: SimpleDateFormat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            tonalElevation = 1.dp
        ) {
            Text(
                text = "${message.content}  ${timeFormatter.format(message.timestamp)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
    private fun MessageTextWithClickableNicknames(
        message: SignaloutMessage,
        messages: List<SignaloutMessage>,
        currentUserNickname: String,
        meshService: BluetoothMeshService,
        colorScheme: ColorScheme,
        timeFormatter: SimpleDateFormat,
        onNicknameClick: ((String) -> Unit)?,
        onMessageLongPress: ((SignaloutMessage) -> Unit)?,
        onCancelTransfer: ((SignaloutMessage) -> Unit)?,
        onImageClick: ((String, List<String>, Int) -> Unit)?,
        isSelf: Boolean = false,
        modifier: Modifier = Modifier
    ) {
    val timeText = timeFormatter.format(message.timestamp)

    // Image special rendering
    if (message.type == SignaloutMessageType.Image) {
        Column {
            com.signalout.android.ui.media.ImageMessageItem(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onCancelTransfer = onCancelTransfer,
                onImageClick = onImageClick,
                modifier = modifier
            )
            BubbleMetadata(
                timeText = timeText,
                isOutgoing = isSelf,
                deliveryContent = if (isSelf && message.isPrivate) {
                    { WhatsAppDeliveryIcon(status = message.deliveryStatus) }
                } else null
            )
        }
        return
    }

    // Voice note special rendering
    if (message.type == SignaloutMessageType.Audio) {
        Column {
            com.signalout.android.ui.media.AudioMessageItem(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onCancelTransfer = onCancelTransfer,
                modifier = modifier
            )
            BubbleMetadata(
                timeText = timeText,
                isOutgoing = isSelf,
                deliveryContent = if (isSelf && message.isPrivate) {
                    { WhatsAppDeliveryIcon(status = message.deliveryStatus) }
                } else null
            )
        }
        return
    }

    // File special rendering
    if (message.type == SignaloutMessageType.File) {
        val path = message.content.trim()
        val (overrideProgress, _) = when (val st = message.deliveryStatus) {
            is DeliveryStatus.PartiallyDelivered -> {
                if (st.total > 0 && st.reached < st.total) {
                    (st.reached.toFloat() / st.total.toFloat()) to Color(0xFF1E88E5)
                } else null to null
            }
            else -> null to null
        }
        Column(modifier = modifier.fillMaxWidth()) {
            val packet = try {
                val file = java.io.File(path)
                if (file.exists()) {
                    com.signalout.android.model.SignaloutFilePacket(
                        fileName = file.name,
                        fileSize = file.length(),
                        mimeType = com.signalout.android.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                        content = file.readBytes()
                    )
                } else null
            } catch (e: Exception) { null }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (packet != null) {
                        val showCancel = isSelf && (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
                        FileMessageItem(
                            packet = packet,
                            uploadProgress = overrideProgress,
                            onCancelUpload = if (showCancel) { { onCancelTransfer?.invoke(message) } } else null,
                            onFileClick = {}
                        )
                    } else {
                        Text(text = stringResource(R.string.file_unavailable), color = Color.Gray)
                    }
                }
            }
            BubbleMetadata(
                timeText = timeText,
                isOutgoing = isSelf,
                deliveryContent = if (isSelf && message.isPrivate) {
                    { WhatsAppDeliveryIcon(status = message.deliveryStatus) }
                } else null
            )
        }
        return
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)

    if (shouldAnimate) {
        Column {
            MessageWithMatrixAnimation(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onImageClick = onImageClick,
                modifier = modifier
            )
            BubbleMetadata(
                timeText = timeText,
                isOutgoing = isSelf,
                deliveryContent = if (isSelf && message.isPrivate) {
                    { WhatsAppDeliveryIcon(status = message.deliveryStatus) }
                } else null
            )
        }
    } else {
        // Normal text message display - WhatsApp bubble content
        Column {
            val isDark = colorScheme.background == WhatsAppColors.DarkBackground
            val textColor = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)

            val haptic = LocalHapticFeedback.current
            val context = LocalContext.current
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            Text(
                text = MessageSpecialParser.parseMarkdown(message.content, isDark),
                modifier = modifier.pointerInput(message) {
                    detectTapGestures(
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMessageLongPress?.invoke(message)
                        }
                    )
                },
                color = textColor,
                fontSize = 15.sp,
                softWrap = true,
                overflow = TextOverflow.Visible,
                onTextLayout = { result -> textLayoutResult = result }
            )

            BubbleMetadata(
                timeText = timeText,
                isOutgoing = isSelf,
                deliveryContent = if (isSelf && message.isPrivate) {
                    { WhatsAppDeliveryIcon(status = message.deliveryStatus) }
                } else null
            )
        }
    }
}

/**
 * WhatsApp-style delivery status icon (ticks)
 */
@Composable
fun WhatsAppDeliveryIcon(status: DeliveryStatus?) {
    when (status) {
        is DeliveryStatus.Sending -> {
            // Clock icon or single grey tick
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = stringResource(R.string.status_sending),
                modifier = Modifier.size(14.dp),
                tint = Color.Gray
            )
        }
        is DeliveryStatus.Sent -> {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = stringResource(R.string.status_pending),
                modifier = Modifier.size(14.dp),
                tint = Color.Gray
            )
        }
        is DeliveryStatus.Delivered -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = stringResource(R.string.status_sent),
                modifier = Modifier.size(14.dp),
                tint = Color.Gray
            )
        }
        is DeliveryStatus.Read -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = stringResource(R.string.status_delivered),
                modifier = Modifier.size(14.dp),
                tint = WhatsAppColors.BlueTick
            )
        }
        is DeliveryStatus.Failed -> {
            Text(
                text = "!",
                fontSize = 12.sp,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
        is DeliveryStatus.PartiallyDelivered -> {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = stringResource(R.string.status_sent),
                modifier = Modifier.size(14.dp),
                tint = Color.Gray.copy(alpha = 0.6f)
            )
        }
        null -> { /* No status indicator */ }
    }
}

/**
 * Legacy DeliveryStatusIcon - kept for backward compatibility
 */
@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    WhatsAppDeliveryIcon(status = status)
}

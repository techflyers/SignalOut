package com.signalout.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalout.android.R
import com.signalout.android.ui.theme.WhatsAppColors
import com.signalout.android.nostr.GeohashAliasRegistry
import com.signalout.android.nostr.GeohashConversationRegistry

/**
 * Full-screen private chat view — replaces PrivateChatSheet (bottom-sheet overlay).
 * Includes voice and video call buttons in the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    peerID: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val peerDirectMap by viewModel.peerDirect.collectAsStateWithLifecycle()
    val peerSessionStates by viewModel.peerSessionStates.collectAsStateWithLifecycle()
    val favoritePeers by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val peerFingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()
    val isPeerTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()
    val typingUsers by viewModel.typingUsers.collectAsStateWithLifecycle()
    val verifiedFingerprints by viewModel.verifiedFingerprints.collectAsStateWithLifecycle()

    // Start private chat when screen opens
    LaunchedEffect(peerID) {
        viewModel.startPrivateChat(peerID)
    }

    val isNostrPeer = peerID.startsWith("nostr_") || peerID.startsWith("nostr:")

    // Compute display name reactively
    val displayName = peerNicknames[peerID] ?: peerID.take(12)
    val titleText = remember(peerID, peerNicknames) {
        if (isNostrPeer) {
            val gh = GeohashConversationRegistry.get(peerID) ?: "geohash"
            val fullPubkey = GeohashAliasRegistry.get(peerID) ?: ""
            val name = if (fullPubkey.isNotEmpty()) {
                viewModel.geohashViewModel.displayNameForGeohashConversation(fullPubkey, gh)
            } else {
                peerNicknames[peerID] ?: "unknown"
            }
            "#$gh/@$name"
        } else {
            peerNicknames[peerID] ?: peerID.take(12)
        }
    }

    val messages = privateChats[peerID] ?: emptyList()
    val isDirect = peerDirectMap[peerID] == true
    val isConnected = connectedPeers.contains(peerID) || isDirect
    val sessionState = peerSessionStates[peerID]
    val fingerprint = peerFingerprints[peerID]
    val isFavorite = remember(favoritePeers, fingerprint) {
        if (fingerprint != null) favoritePeers.contains(fingerprint) else viewModel.isFavorite(peerID)
    }
    val isVerified = remember(peerID, verifiedFingerprints) {
        viewModel.isPeerVerified(peerID, verifiedFingerprints)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground
    val headerBg = if (isDark) WhatsAppColors.HeaderDark else WhatsAppColors.HeaderLight
    val headerHeight = 56.dp

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var forceScrollToBottom by remember { mutableStateOf(false) }
    var isScrolledUp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Header spacer
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(headerHeight)
            )

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            // Messages list
            MessagesList(
                messages = messages,
                currentUserNickname = nickname,
                meshService = viewModel.meshService,
                modifier = Modifier.weight(1f),
                forceScrollToBottom = forceScrollToBottom,
                onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
                onNicknameClick = { /* handle mention */ },
                onMessageLongPress = { /* handle long press */ },
                onCancelTransfer = { msg -> viewModel.cancelMediaSend(msg.id) },
                onImageClick = { _, _, _ -> /* handle image click */ },
                isPeerTyping = isPeerTyping,
                typingUsers = typingUsers
            )

            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))

            // Input section
            ChatInputSection(
                messageText = messageText,
                onMessageTextChange = { newText ->
                    messageText = newText
                    viewModel.updateMentionSuggestions(newText.text)
                    if (newText.text.isNotEmpty()) {
                        viewModel.sendTypingIndicator(peerID)
                    }
                },
                onSend = {
                    if (messageText.text.trim().isNotEmpty()) {
                        viewModel.sendMessage(messageText.text.trim())
                        messageText = TextFieldValue("")
                        forceScrollToBottom = !forceScrollToBottom
                    }
                },
                onSendVoiceNote = { peer, channel, path ->
                    viewModel.sendVoiceNote(peer, channel, path)
                },
                onSendImageNote = { peer, channel, path ->
                    viewModel.sendImageNote(peer, channel, path)
                },
                onSendFileNote = { peer, channel, path ->
                    viewModel.sendFileNote(peer, channel, path)
                },
                showCommandSuggestions = false,
                commandSuggestions = emptyList(),
                showMentionSuggestions = false,
                mentionSuggestions = emptyList(),
                onCommandSuggestionClick = { },
                onMentionSuggestionClick = { },
                selectedPrivatePeer = peerID,
                currentChannel = null,
                nickname = nickname,
                colorScheme = colorScheme,
                showMediaButtons = true
            )
        }

        // Floating header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .windowInsetsPadding(WindowInsets.statusBars),
            color = headerBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                }

                // Bluetooth avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WhatsAppColors.TealAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = WhatsAppColors.TealAccent
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Name + status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.showSecurityVerificationSheet() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                        if (isVerified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF32D74B)
                            )
                        }
                        if (!isNostrPeer) {
                            Spacer(Modifier.width(4.dp))
                            NoiseSessionIcon(
                                sessionState = sessionState,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    // Status line
                    Text(
                        text = when {
                            isDirect -> "online • direct"
                            isConnected -> "online • relayed"
                            isNostrPeer -> "via Nostr"
                            else -> "offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Voice call button
                if (isDirect) {
                    IconButton(
                        onClick = {
                            com.signalout.android.call.CallManager.getInstance(context)
                                .startCall(peerID, displayName, false)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Voice call",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }

                    // Video call button
                    IconButton(
                        onClick = {
                            com.signalout.android.call.CallManager.getInstance(context)
                                .startCall(peerID, displayName, true)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Video call",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                }

                // Favorite star
                IconButton(
                    onClick = { viewModel.toggleFavorite(peerID) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        modifier = Modifier.size(18.dp),
                        tint = if (isFavorite) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

package com.signalout.android.ui


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.signalout.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalout.android.core.ui.utils.singleOrTripleClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalout.android.ui.theme.WhatsAppColors

/**
 * Header components for ChatScreen - WhatsApp-style design
 */


@Composable
fun TorStatusDot(
    modifier: Modifier = Modifier
) {
    val torProvider = remember { com.signalout.android.net.ArtiTorManager.getInstance() }
    val torStatus by torProvider.statusFlow.collectAsState()

    if (torStatus.mode != com.signalout.android.net.TorMode.OFF) {
        val dotColor = when {
            torStatus.running && torStatus.bootstrapPercent < 100 -> Color(0xFFFF9500)
            torStatus.running && torStatus.bootstrapPercent >= 100 -> WhatsAppColors.OnlineGreen
            else -> Color.Red
        }
        Canvas(modifier = modifier) {
            val radius = size.minDimension / 2
            drawCircle(
                color = dotColor,
                radius = radius,
                center = Offset(size.width / 2, size.height / 2)
            )
        }
    }
}

@Composable
fun NoiseSessionIcon(
    sessionState: String?,
    modifier: Modifier = Modifier
) {
    val (icon, color, contentDescription) = when (sessionState) {
        "uninitialized" -> Triple(
            Icons.Outlined.NoEncryption,
            Color.Gray,
            stringResource(R.string.cd_ready_for_handshake)
        )
        "handshaking" -> Triple(
            Icons.Outlined.Sync,
            Color.Gray,
            stringResource(R.string.cd_handshake_in_progress)
        )
        "established" -> Triple(
            Icons.Filled.Lock,
            WhatsAppColors.TealAccent,
            stringResource(R.string.cd_encrypted)
        )
        else -> {
            Triple(
                Icons.Outlined.Warning,
                Color(0xFFFF4444),
                stringResource(R.string.cd_handshake_failed)
            )
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = color
    )
}

@Composable
fun NicknameEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(value) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable {
                editValue = value
                showDialog = true
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.at_symbol) + value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Change Handle", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    singleLine = true,
                    label = { Text("Nickname") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppColors.TealAccent,
                        focusedLabelColor = WhatsAppColors.TealAccent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editValue.isNotBlank()) {
                        onValueChange(editValue.trim())
                    }
                    showDialog = false
                }) {
                    Text("Save", color = WhatsAppColors.TealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun PeerCounter(
    connectedPeers: List<String>,
    joinedChannels: Set<String>,
    hasUnreadChannels: Map<String, Int>,
    isConnected: Boolean,
    selectedLocationChannel: com.signalout.android.geohash.ChannelID?,
    geohashPeople: List<GeoPerson>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val (peopleCount, countColor) = when (selectedLocationChannel) {
        is com.signalout.android.geohash.ChannelID.Location -> {
            val count = geohashPeople.size
            Pair(count, if (count > 0) WhatsAppColors.OnlineGreen else Color.Gray)
        }
        is com.signalout.android.geohash.ChannelID.Mesh,
        null -> {
            val count = connectedPeers.size
            Pair(count, if (isConnected && count > 0) WhatsAppColors.BlueTick else Color.Gray)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable { onClick() }.padding(end = 4.dp)
    ) {
        // Online dot
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = countColor, radius = size.minDimension / 2)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$peopleCount online",
            style = MaterialTheme.typography.labelSmall,
            color = countColor
        )
    }
}

@Composable
fun ChatHeaderContent(
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onTripleClick: () -> Unit,
    onShowAppInfo: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit
) {
    when {
        currentChannel != null -> {
            ChannelHeader(
                channel = currentChannel,
                onBackClick = onBackClick,
                onLeaveChannel = { viewModel.leaveChannel(currentChannel) },
                onSidebarClick = onSidebarClick
            )
        }
        else -> {
            MainHeader(
                nickname = nickname,
                onNicknameChange = viewModel::setNickname,
                onTitleClick = onShowAppInfo,
                onTripleTitleClick = onTripleClick,
                onSidebarClick = onSidebarClick,
                onLocationChannelsClick = onLocationChannelsClick,
                onLocationNotesClick = onLocationNotesClick,
                viewModel = viewModel
            )
        }
    }
}



@Composable
private fun ChannelHeader(
    channel: String,
    onBackClick: () -> Unit,
    onLeaveChannel: () -> Unit,
    onSidebarClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
        }

        // Channel avatar and name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable { onSidebarClick() }
                .padding(horizontal = 4.dp)
        ) {
            // Channel avatar circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(WhatsAppColors.TealAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "#$channel",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // Right: voice call, video call, leave, overflow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { /* TODO: voice call */ }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Call, contentDescription = "Voice call", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { /* TODO: video call */ }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Videocam, contentDescription = "Video call", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            TextButton(onClick = onLeaveChannel) {
                Text(
                    text = stringResource(R.string.chat_leave),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
    }
}

@Composable
private fun MainHeader(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onTitleClick: () -> Unit,
    onTripleTitleClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
    val hasUnreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
    val geohashPeople by viewModel.geohashPeople.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val bookmarksStore = remember { com.signalout.android.geohash.GeohashBookmarksStore.getInstance(context) }
    val bookmarks by bookmarksStore.bookmarks.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: App brand (logo + name like WhatsApp)
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_brand),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.singleOrTripleClickable(
                    onSingleClick = onTitleClick,
                    onTripleClick = onTripleTitleClick
                )
            )
        }

        // Right section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Unread private messages badge
            if (hasUnreadPrivateMessages.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = stringResource(R.string.cd_unread_private_messages),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { viewModel.openLatestUnreadPrivateChat() },
                    tint = WhatsAppColors.UnreadBadge
                )
            }

            // Location channels button
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 2.dp)) {
                LocationChannelsButton(
                    viewModel = viewModel,
                    onClick = onLocationChannelsClick
                )

                val currentGeohash: String? = when (val sc = selectedLocationChannel) {
                    is com.signalout.android.geohash.ChannelID.Location -> sc.channel.geohash
                    else -> null
                }
                if (currentGeohash != null) {
                    val isBookmarked = bookmarks.contains(currentGeohash)
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(20.dp)
                            .clickable { bookmarksStore.toggle(currentGeohash) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_toggle_bookmark),
                            tint = if (isBookmarked) WhatsAppColors.TealAccent else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Settings icon (replaces Location Notes button)
            IconButton(
                onClick = onTitleClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Tor status
            TorStatusDot(
                modifier = Modifier
                    .size(8.dp)
                    .padding(start = 0.dp, end = 2.dp)
            )

            // PoW status
            PoWStatusIndicator(
                modifier = Modifier,
                style = PoWIndicatorStyle.COMPACT
            )

            Spacer(modifier = Modifier.width(2.dp))

            // Peer counter
            PeerCounter(
                connectedPeers = connectedPeers.filter { it != viewModel.meshService.myPeerID },
                joinedChannels = joinedChannels,
                hasUnreadChannels = hasUnreadChannels,
                isConnected = isConnected,
                selectedLocationChannel = selectedLocationChannel,
                geohashPeople = geohashPeople,
                onClick = onSidebarClick
            )

            // Overflow menu
            IconButton(
                onClick = onSidebarClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LocationChannelsButton(
    viewModel: ChatViewModel,
    onClick: () -> Unit
) {
    val selectedChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
    val teleported by viewModel.isTeleported.collectAsStateWithLifecycle()

    val (badgeText, badgeColor) = when (selectedChannel) {
        is com.signalout.android.geohash.ChannelID.Mesh -> {
            "#mesh" to WhatsAppColors.BlueTick
        }
        is com.signalout.android.geohash.ChannelID.Location -> {
            val geohash = (selectedChannel as com.signalout.android.geohash.ChannelID.Location).channel.geohash
            "#$geohash" to WhatsAppColors.TealAccent
        }
        null -> "#mesh" to WhatsAppColors.BlueTick
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(start = 4.dp, end = 0.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1
            )

            if (teleported) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.PinDrop,
                    contentDescription = stringResource(R.string.cd_teleported),
                    modifier = Modifier.size(12.dp),
                    tint = badgeColor
                )
            }
        }
    }
}

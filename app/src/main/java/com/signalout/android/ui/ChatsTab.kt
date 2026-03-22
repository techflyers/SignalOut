package com.signalout.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalout.android.ui.theme.WhatsAppColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Internal data class for chat list entries.
 */
private data class ChatEntry(
    val peerID: String,
    val displayName: String,
    val lastMessage: String?,
    val lastMessageTime: Date?,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isDirect: Boolean
)

/**
 * WhatsApp-style Chats Tab — shows peers as chat list items.
 * This is the default landing screen (replaces MeshPeerListSheet overlay).
 */
@Composable
fun ChatsTab(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val favoritePeers by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val peerFingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()
    val peerDirect by viewModel.peerDirect.collectAsStateWithLifecycle()

    val peerFavoriteStates = remember(favoritePeers, peerFingerprints, connectedPeers) {
        connectedPeers.associateWith { peerID ->
            val fingerprint = peerFingerprints[peerID]
            fingerprint != null && favoritePeers.contains(fingerprint)
        }
    }

    // Build mapping of connected peerID -> noise key hex
    val noiseHexByPeerID: Map<String, String> = remember(connectedPeers) {
        connectedPeers.associateWith { pid ->
            try {
                viewModel.meshService.getPeerInfo(pid)?.noisePublicKey?.joinToString("") { b -> "%02x".format(b) }
            } catch (_: Exception) { null }
        }.filterValues { it != null }.mapValues { it.value!! }
    }

    val myPeerID = remember { viewModel.meshService.myPeerID }

    // Smart sorting: unread DMs first, most recent DM, favorites, alphabetical
    val sortedPeers = remember(connectedPeers, hasUnreadPrivateMessages, privateChats, peerFavoriteStates, peerNicknames, nickname) {
        connectedPeers.filter { it != myPeerID }.sortedWith(
            compareBy<String> { !hasUnreadPrivateMessages.contains(it) }
                .thenByDescending { privateChats[it]?.maxByOrNull { msg -> msg.timestamp }?.timestamp?.time ?: 0L }
                .thenBy { !(peerFavoriteStates[it] ?: false) }
                .thenBy { (peerNicknames[it] ?: it).lowercase() }
        )
    }

    val offlineFavorites = remember {
        try { com.signalout.android.favorites.FavoritesPersistenceService.shared.getOurFavorites() } catch (_: Exception) { emptyList() }
    }

    // Build chat entries
    val chatEntries = remember(sortedPeers, offlineFavorites, privateChats, hasUnreadPrivateMessages, peerNicknames, nickname, noiseHexByPeerID, peerDirect) {
        val entries = mutableListOf<ChatEntry>()

        // Connected peers
        sortedPeers.forEach { peerID ->
            val displayName = peerNicknames[peerID] ?: peerID.take(12)
            val noiseHex = noiseHexByPeerID[peerID]
            val meshMessages = privateChats[peerID] ?: emptyList()
            val nostrMessages = if (noiseHex != null) privateChats[noiseHex] ?: emptyList() else emptyList()
            val allMessages = (meshMessages + nostrMessages).sortedBy { it.timestamp }
            val lastMsg = allMessages.lastOrNull()

            val meshUnread = hasUnreadPrivateMessages.contains(peerID)
            val nostrUnread = if (noiseHex != null) hasUnreadPrivateMessages.contains(noiseHex) else false
            val unreadCount = meshMessages.count { msg -> msg.sender != nickname && meshUnread } +
                    (if (noiseHex != null) nostrMessages.count { msg -> msg.sender != nickname && nostrUnread } else 0)

            entries.add(ChatEntry(
                peerID = peerID,
                displayName = truncateNickname(splitSuffix(displayName).first),
                lastMessage = lastMsg?.content,
                lastMessageTime = lastMsg?.timestamp,
                unreadCount = if (unreadCount > 0) unreadCount else if (meshUnread || nostrUnread) 1 else 0,
                isOnline = true,
                isDirect = peerDirect[peerID] ?: false
            ))
        }

        // Offline favorites not currently connected
        offlineFavorites.forEach { fav ->
            val favPeerID = fav.peerNoisePublicKey.joinToString("") { b -> "%02x".format(b) }
            val isMappedToConnected = noiseHexByPeerID.values.any { it.equals(favPeerID, ignoreCase = true) }
            if (isMappedToConnected) return@forEach
            // Also skip if already in connected peers
            if (sortedPeers.contains(favPeerID)) return@forEach

            val dn = peerNicknames[favPeerID] ?: fav.peerNickname
            val offlineMessages = privateChats[favPeerID] ?: emptyList()
            val lastMsg = offlineMessages.lastOrNull()
            val hasUnread = hasUnreadPrivateMessages.contains(favPeerID)

            entries.add(ChatEntry(
                peerID = favPeerID,
                displayName = truncateNickname(splitSuffix(dn).first),
                lastMessage = lastMsg?.content,
                lastMessageTime = lastMsg?.timestamp,
                unreadCount = if (hasUnread) offlineMessages.count { msg -> msg.sender != nickname && hasUnread }.coerceAtLeast(1) else 0,
                isOnline = false,
                isDirect = false
            ))
        }

        entries
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy", Locale.getDefault()) }
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }

    if (chatEntries.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Nearby peers will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = chatEntries,
                key = { "chat_${it.peerID}" }
            ) { entry ->
                ChatListItem(
                    entry = entry,
                    colorScheme = colorScheme,
                    timeFormat = timeFormat,
                    dateFormat = dateFormat,
                    today = today,
                    onClick = {
                        viewModel.openFullScreenChat(entry.peerID)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatListItem(
    entry: ChatEntry,
    colorScheme: ColorScheme,
    timeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat,
    today: Date,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bluetooth avatar circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.isOnline) WhatsAppColors.TealAccent.copy(alpha = 0.15f)
                        else colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (entry.isOnline) WhatsAppColors.TealAccent else colorScheme.onSurface.copy(alpha = 0.4f)
                )
                // Online indicator dot
                if (entry.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colorScheme.background)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(WhatsAppColors.OnlineGreen)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + last message
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (entry.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.lastMessage ?: "No Messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.lastMessage != null) colorScheme.onSurface.copy(alpha = 0.6f) else colorScheme.onSurface.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right side: time + unread badge
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (entry.lastMessageTime != null) {
                    val timeStr = if (entry.lastMessageTime.after(today)) {
                        timeFormat.format(entry.lastMessageTime)
                    } else {
                        dateFormat.format(entry.lastMessageTime)
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.unreadCount > 0) WhatsAppColors.TealAccent else colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
                if (entry.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(WhatsAppColors.TealAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (entry.unreadCount > 99) "99+" else entry.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp),
        color = colorScheme.outline.copy(alpha = 0.15f)
    )
}

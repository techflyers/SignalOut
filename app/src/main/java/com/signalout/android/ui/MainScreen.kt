package com.signalout.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalout.android.ui.theme.WhatsAppColors

/**
 * MainScreen — root composable replacing ChatScreen as the entry point.
 * Contains a bottom NavigationBar with Chats (default) and Channels tabs.
 * When a private chat is active (activeChatPeer != null), shows full-screen PrivateChatScreen.
 * Voice/video call overlays are rendered on top as before.
 */
@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    isCallActive: Boolean = false
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val activeChatPeer by viewModel.activeChatPeer.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground
    val headerBg = if (isDark) WhatsAppColors.HeaderDark else WhatsAppColors.HeaderLight

    // If a private chat is being viewed full-screen, show it on top
    if (activeChatPeer != null) {
        PrivateChatScreen(
            peerID = activeChatPeer!!,
            viewModel = viewModel,
            onBack = { viewModel.closeFullScreenChat() }
        )
        return
    }

    // Main tabbed layout
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) WhatsAppColors.DarkSurface else colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHATS,
                    onClick = { viewModel.selectTab(MainTab.CHATS) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == MainTab.CHATS) Icons.Filled.Chat else Icons.Outlined.Chat,
                            contentDescription = "Chats"
                        )
                    },
                    label = {
                        Text(
                            text = "Chats",
                            fontWeight = if (selectedTab == MainTab.CHATS) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WhatsAppColors.TealAccent,
                        selectedTextColor = WhatsAppColors.TealAccent,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.6f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.6f),
                        indicatorColor = WhatsAppColors.TealAccent.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHANNELS,
                    onClick = { viewModel.selectTab(MainTab.CHANNELS) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == MainTab.CHANNELS) Icons.Filled.Tag else Icons.Outlined.Tag,
                            contentDescription = "Channels"
                        )
                    },
                    label = {
                        Text(
                            text = "Channels",
                            fontWeight = if (selectedTab == MainTab.CHANNELS) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WhatsAppColors.TealAccent,
                        selectedTextColor = WhatsAppColors.TealAccent,
                        unselectedIconColor = colorScheme.onSurface.copy(alpha = 0.6f),
                        unselectedTextColor = colorScheme.onSurface.copy(alpha = 0.6f),
                        indicatorColor = WhatsAppColors.TealAccent.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.CHATS -> {
                    // Top header for Chats tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        // WhatsApp-style header
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars),
                            color = headerBg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "SignalOut",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Settings button
                                    IconButton(
                                        onClick = { viewModel.showAppInfo() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Settings",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Tor status
                                    TorStatusDot(
                                        modifier = Modifier.size(8.dp)
                                    )
                                    // PoW status
                                    PoWStatusIndicator(
                                        modifier = Modifier,
                                        style = PoWIndicatorStyle.COMPACT
                                    )
                                }
                            }
                        }

                        // Chat list content
                        ChatsTab(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                MainTab.CHANNELS -> {
                    // Channels tab uses the existing ChatScreen
                    ChatScreen(viewModel = viewModel, isCallActive = isCallActive)
                }
            }
        }
    }

    // Sheets that should still work in both tabs
    ChatGlobalDialogs(viewModel = viewModel)
}

/**
 * Global dialogs/sheets that should not be scoped to a specific tab
 * (About sheet, verification sheets etc.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatGlobalDialogs(viewModel: ChatViewModel) {
    val showAppInfo by viewModel.showAppInfo.collectAsStateWithLifecycle()
    val showVerificationSheet by viewModel.showVerificationSheet.collectAsStateWithLifecycle()
    val showSecurityVerificationSheet by viewModel.showSecurityVerificationSheet.collectAsStateWithLifecycle()
    val aboutNickname by viewModel.nickname.collectAsStateWithLifecycle()

    // About sheet
    var showDebugSheet by remember { mutableStateOf(false) }
    AboutSheet(
        isPresented = showAppInfo,
        onDismiss = { viewModel.hideAppInfo() },
        nickname = aboutNickname,
        onNicknameChange = viewModel::setNickname,
        onShowDebug = { showDebugSheet = true }
    )
    if (showDebugSheet) {
        com.signalout.android.ui.debug.DebugSettingsSheet(
            isPresented = showDebugSheet,
            onDismiss = { showDebugSheet = false },
            meshService = viewModel.meshService
        )
    }

    if (showVerificationSheet) {
        VerificationSheet(
            isPresented = showVerificationSheet,
            onDismiss = viewModel::hideVerificationSheet,
            viewModel = viewModel
        )
    }

    if (showSecurityVerificationSheet) {
        SecurityVerificationSheet(
            isPresented = showSecurityVerificationSheet,
            onDismiss = viewModel::hideSecurityVerificationSheet,
            viewModel = viewModel
        )
    }
}

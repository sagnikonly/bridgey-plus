package dev.bridgey.android.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bridgey.android.BridgeyFeature
import dev.bridgey.android.ConnectedDevice
import dev.bridgey.android.FileTransferState
import dev.bridgey.android.MacActionIds
import dev.bridgey.android.MacMediaState
import dev.bridgey.android.PairingCoordinator
import dev.bridgey.android.PairingState
import dev.bridgey.android.QuickActions
import dev.bridgey.android.R
import dev.bridgey.android.RemoteBatteryStatus
import dev.bridgey.android.screen.ScreenShareState
import dev.bridgey.android.linkHost
import dev.bridgey.core.discovery.DiscoveredPeer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeState: HomeUiState,
    peers: List<DiscoveredPeer>,
    trustedIds: Set<String>,
    connections: List<ConnectedDevice>,
    remoteBattery: RemoteBatteryStatus?,
    clipboardStatus: String?,
    lastClipboardPreview: String?,
    clipboardPaused: Boolean,
    pingStatus: String?,
    phoneRinging: Boolean,
    macRinging: Boolean,
    screenState: ScreenShareState,
    enabledFeatures: Map<BridgeyFeature, Boolean>,
    pairing: PairingCoordinator,
    onPickFiles: () -> Unit,
    onShareScreen: () -> Unit,
    onStopScreen: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val media by pairing.quickActions.media.collectAsState()
    val macActionsState by pairing.quickActions.macActions.collectAsState()
    val quickStatus by pairing.quickActions.status.collectAsState()
    var showMacSheet by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showMoreExpanded by remember { mutableStateOf(false) }

    val dimAlpha = if (homeState.isConnected) 1f else 0.45f

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Top Mac status strip
        item(key = "status_strip") {
            MacStatusStrip(
                connectedName = homeState.connectedMacName,
                isConnected = homeState.isConnected,
                battery = remoteBattery,
                syncing = !clipboardPaused && enabledFeatures[BridgeyFeature.CLIPBOARD] != false,
                onClick = { showMacSheet = true },
            )
        }

        // 2. Adaptive top slot (Offline / Transferring / IncomingLink / Idle)
        item(key = "adaptive_top") {
            AnimatedContent(
                targetState = homeState.topSlot,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "adaptive_top_slot",
            ) { slot ->
                when (slot) {
                    is HomeTopSlot.Transferring -> {
                        TransferProgressBanner(
                            transfer = slot.activeTransfer,
                            currentIndex = slot.currentIndex,
                            totalCount = slot.totalCount,
                            onCancel = { pairing.cancelFileTransfer(slot.activeTransfer.id) },
                        )
                    }
                    is HomeTopSlot.IncomingLink -> {
                        IncomingLinkBanner(
                            url = slot.url,
                            onOpen = pairing.quickActions::openLink,
                            onDismiss = pairing.quickActions::dismissLink,
                        )
                    }
                    is HomeTopSlot.Offline -> {
                        OfflineReconnectCard(
                            connecting = slot.connecting,
                            peers = peers,
                            trustedIds = trustedIds,
                            pairing = pairing,
                        )
                    }
                    HomeTopSlot.Idle -> Spacer(Modifier.height(0.dp))
                }
            }
        }

        // 3. Primary hero card: Send files
        item(key = "send_files_hero") {
            SendFilesHeroCard(
                enabled = homeState.isConnected && enabledFeatures[BridgeyFeature.FILES] != false,
                alpha = dimAlpha,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPickFiles()
                },
            )
        }

        // 4. Ambient Clipboard status card
        if (enabledFeatures[BridgeyFeature.CLIPBOARD] != false) {
            item(key = "clipboard_card") {
                ClipboardStatusCard(
                    enabled = homeState.isConnected,
                    alpha = dimAlpha,
                    paused = clipboardPaused,
                    lastPreview = lastClipboardPreview,
                    status = clipboardStatus,
                    onTogglePause = { pairing.setClipboardSyncPaused(!clipboardPaused) },
                    onSendNow = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        pairing.sendClipboard()
                    },
                )
            }
        }

        // 5. Adaptive Now Playing card (only when music is active)
        if (homeState.showNowPlaying) {
            item(key = "now_playing") {
                NowPlayingCard(
                    media = media,
                    actions = pairing.quickActions,
                )
            }
        }

        // 6. Quick controls row: Lock · Sleep · Mute · Volume
        if (homeState.isConnected && enabledFeatures[BridgeyFeature.MAC_ACTIONS] == true) {
            item(key = "quick_controls") {
                val muted = macActionsState?.volume?.muted == true
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickControlTile(
                            icon = Icons.Rounded.Lock,
                            label = "Lock",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pairing.quickActions.runMacAction(MacActionIds.LOCK)
                            },
                        )
                        QuickControlTile(
                            icon = Icons.Rounded.Bedtime,
                            label = "Sleep",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pairing.quickActions.runMacAction(MacActionIds.SLEEP)
                            },
                        )
                        QuickControlTile(
                            icon = if (muted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            label = if (muted) "Unmute" else "Mute",
                            active = muted,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pairing.quickActions.runMacAction(MacActionIds.VOLUME_MUTE)
                            },
                        )
                        QuickControlTile(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            label = "Volume",
                            active = showVolumeSlider,
                            modifier = Modifier.weight(1f),
                            onClick = { showVolumeSlider = !showVolumeSlider },
                        )
                    }

                    AnimatedVisibility(
                        visible = showVolumeSlider,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        val vol = macActionsState?.volume
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                var level by remember(vol?.level) { mutableFloatStateOf((vol?.level ?: 50).toFloat()) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Mac volume", style = MaterialTheme.typography.titleSmall)
                                    Text("${level.toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = level,
                                    onValueChange = { level = it },
                                    valueRange = 0f..100f,
                                    onValueChangeFinished = {
                                        pairing.quickActions.runMacAction(MacActionIds.VOLUME_SET, level.toInt())
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. More (collapsed bar -> expands rare actions)
        item(key = "more_bar") {
            MoreActionsSection(
                expanded = showMoreExpanded,
                onToggle = { showMoreExpanded = !showMoreExpanded },
                enabled = homeState.isConnected,
                alpha = dimAlpha,
                pingEnabled = enabledFeatures[BridgeyFeature.PING] != false,
                findEnabled = enabledFeatures[BridgeyFeature.FIND_DEVICE] != false,
                linksEnabled = enabledFeatures[BridgeyFeature.LINKS] == true,
                screenEnabled = enabledFeatures[BridgeyFeature.SCREEN] == true || screenState.active,
                macActionsEnabled = enabledFeatures[BridgeyFeature.MAC_ACTIONS] == true,
                macActionsState = macActionsState,
                ringing = phoneRinging || macRinging,
                screenActive = screenState.active,
                onPing = pairing::sendPing,
                onRing = { if (phoneRinging || macRinging) pairing.stopFinding() else pairing.findMac() },
                onSendLink = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    pairing.quickActions.sendLink(text)
                },
                onScreen = { if (screenState.active) onStopScreen() else onShareScreen() },
                onRunMacAction = { pairing.quickActions.runMacAction(it) },
            )
        }

        // Subtle feedback status line (ping / quick action / screen)
        val feedback = listOfNotNull(
            pingStatus,
            quickStatus,
            screenState.macName?.takeIf { screenState.active }?.let { "Sharing screen to $it" },
        ).firstOrNull()
        if (feedback != null) {
            item(key = "feedback_line") {
                Text(
                    feedback,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }

    if (showMacSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMacSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MacQuickSheet(
                homeState = homeState,
                connections = connections,
                peers = peers,
                trustedIds = trustedIds,
                pairing = pairing,
                onTurnOff = {
                    showMacSheet = false
                    onTurnOff()
                },
                onClose = { showMacSheet = false },
            )
        }
    }
}

@Composable
private fun MacStatusStrip(
    connectedName: String?,
    isConnected: Boolean,
    battery: RemoteBatteryStatus?,
    syncing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveIconWell(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            size = 44.dp,
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                Icons.Rounded.LaptopMac,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = connectedName ?: "No Mac connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isConnected) ExpressiveConnectedGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                )
                val statusParts = buildList {
                    add(if (isConnected) "Connected" else "Offline")
                    if (isConnected && battery != null) {
                        add("${battery.level}%${if (battery.isCharging) " ⚡" else ""}")
                    }
                    if (isConnected && syncing) add("Syncing")
                }
                Text(
                    statusParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = "Mac options",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransferProgressBanner(
    transfer: FileTransferState,
    currentIndex: Int,
    totalCount: Int,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val direction = if (transfer.status.startsWith("Receiving", ignoreCase = true)) {
                    "Receiving from Mac"
                } else {
                    "Sending to Mac"
                }
                val title = if (totalCount > 1) "$direction · $currentIndex of $totalCount" else direction
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                transfer.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val progress = transfer.progressPercent
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun IncomingLinkBanner(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpressiveIconWell(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("Link from Mac", style = MaterialTheme.typography.titleSmall)
                Text(
                    linkHost(url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Open")
            }
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun OfflineReconnectCard(
    connecting: Boolean,
    peers: List<DiscoveredPeer>,
    trustedIds: Set<String>,
    pairing: PairingCoordinator,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (connecting) "Connecting to your Mac…" else "Looking for your Mac",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (peers.isEmpty()) {
                    "Keep Bridgey open on your Mac on the same Wi-Fi network."
                } else {
                    "Tap your Mac below to reconnect."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            peers.forEach { peer ->
                val trusted = peer.deviceIdHint in trustedIds
                Surface(
                    onClick = { peer.host?.let { pairing.pair(it, peer.port ?: 42_458, peer.deviceNameHint) } },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.LaptopMac, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(peer.deviceNameHint, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (trusted) "Paired · tap to reconnect" else "New Mac · tap to pair",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SendFilesHeroCard(
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleIconWell(
                containerColor = MaterialTheme.colorScheme.primary,
                size = 54.dp,
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Send files",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Tap, or share from any app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ClipboardStatusCard(
    enabled: Boolean,
    alpha: Float,
    paused: Boolean,
    lastPreview: String?,
    status: String?,
    onTogglePause: () -> Unit,
    onSendNow: () -> Unit,
) {
    Surface(
        onClick = onTogglePause,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpressiveIconWell(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                size = 44.dp,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (paused) "Clipboard paused" else "Clipboard syncing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtext = when {
                    paused -> "Tap to resume auto-sync"
                    !lastPreview.isNullOrBlank() -> "Last: $lastPreview"
                    !status.isNullOrBlank() -> status
                    else -> "Copies sync automatically"
                }
                Text(
                    subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                onClick = onSendNow,
                enabled = enabled,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    "Send now",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    media: MacMediaState,
    actions: QuickActions,
) {
    val haptics = LocalHapticFeedback.current
    var showPlayerVolume by remember { mutableStateOf(false) }

    // Signature Material 3 Expressive shape morph: squircle when playing, circle when paused
    val playCorner by animateDpAsState(
        targetValue = if (media.playing) 20.dp else 32.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "play_morph",
    )

    val artwork = remember(media.artwork) {
        runCatching {
            val bytes = Base64.decode(media.artwork ?: return@runCatching null, Base64.DEFAULT)
            if (bytes.size > 16384) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..128 || bounds.outHeight !in 1..128) return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork != null) {
                        Image(
                            artwork,
                            contentDescription = "Album artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        media.title.ifBlank { media.player },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOf(media.artist, media.player).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showPlayerVolume = !showPlayerVolume }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Player volume",
                        tint = if (showPlayerVolume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (media.duration > 0) {
                var position by remember(media.position, media.duration) {
                    mutableFloatStateOf(media.position.coerceAtMost(media.duration).toFloat())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        formatDuration(position.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = position,
                        onValueChange = { position = it },
                        valueRange = 0f..media.duration.toFloat(),
                        onValueChangeFinished = { actions.request(BridgeyFeature.MEDIA, "seek", position.toInt().toString()) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                    Text(
                        formatDuration(media.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.request(BridgeyFeature.MEDIA, "previous")
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(20.dp))
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.request(BridgeyFeature.MEDIA, "toggle")
                    },
                    shape = RoundedCornerShape(playCorner),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(width = 72.dp, height = 58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (media.playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        actions.request(BridgeyFeature.MEDIA, "next")
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }

            AnimatedVisibility(visible = showPlayerVolume) {
                var volume by remember(media.volume) { mutableFloatStateOf(media.volume.toFloat()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Player", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..100f,
                        onValueChangeFinished = { actions.request(BridgeyFeature.MEDIA, "volume", volume.toInt().toString()) },
                        modifier = Modifier.weight(1f),
                    )
                    Text("${volume.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuickControlTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreActionsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    alpha: Float,
    pingEnabled: Boolean,
    findEnabled: Boolean,
    linksEnabled: Boolean,
    screenEnabled: Boolean,
    macActionsEnabled: Boolean,
    macActionsState: dev.bridgey.android.MacActionsState?,
    ringing: Boolean,
    screenActive: Boolean,
    onPing: () -> Unit,
    onRing: () -> Unit,
    onSendLink: () -> Unit,
    onScreen: () -> Unit,
    onRunMacAction: (String) -> Unit,
) {
    val summary = buildList {
        if (pingEnabled) add("Ping")
        if (findEnabled) add("Ring")
        if (linksEnabled) add("Send link")
        if (screenEnabled) add("Share screen")
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("More", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    summary.ifBlank { "Extra tools" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pingEnabled) {
                            MoreActionChip(Icons.Rounded.Sensors, "Ping Mac", enabled, onPing)
                        }
                        if (findEnabled) {
                            MoreActionChip(
                                if (ringing) Icons.Rounded.Stop else Icons.Rounded.NotificationsActive,
                                if (ringing) "Stop ring" else "Ring Mac",
                                enabled,
                                onRing,
                            )
                        }
                        if (linksEnabled) {
                            MoreActionChip(Icons.Rounded.Link, "Send copied link", enabled, onSendLink)
                        }
                        if (screenEnabled) {
                            MoreActionChip(
                                if (screenActive) Icons.Rounded.Stop else Icons.Rounded.Cast,
                                if (screenActive) "Stop sharing" else "Share screen",
                                enabled,
                                onScreen,
                            )
                        }
                        if (macActionsEnabled && macActionsState != null) {
                            if (macActionsState.has(MacActionIds.DISPLAY_SLEEP)) {
                                MoreActionChip(Icons.Rounded.DesktopWindows, "Display off", enabled) {
                                    onRunMacAction(MacActionIds.DISPLAY_SLEEP)
                                }
                            }
                            if (macActionsState.has(MacActionIds.SCREENSAVER)) {
                                MoreActionChip(Icons.Rounded.DesktopWindows, "Screen saver", enabled) {
                                    onRunMacAction(MacActionIds.SCREENSAVER)
                                }
                            }
                            (macActionsState.apps + macActionsState.shortcuts).forEach { entry ->
                                MoreActionChip(Icons.Rounded.LaptopMac, entry.title, enabled) {
                                    onRunMacAction(entry.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MacQuickSheet(
    homeState: HomeUiState,
    connections: List<ConnectedDevice>,
    peers: List<DiscoveredPeer>,
    trustedIds: Set<String>,
    pairing: PairingCoordinator,
    onTurnOff: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mac connection", style = MaterialTheme.typography.titleLarge)

        if (connections.isNotEmpty()) {
            SegmentedGroup {
                connections.forEachIndexed { idx, device ->
                    val selected = device.id == homeState.connectedMacId
                    SegmentedCard(
                        index = idx,
                        count = connections.size,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            pairing.selectDevice(device.id)
                            onClose()
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.LaptopMac, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleSmall)
                                val bat = device.link.battery?.let { "${it.level}%" } ?: "Connected"
                                Text(bat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                pairing.disconnectByUser(device.id)
                                onClose()
                            }) { Text("Disconnect") }
                        }
                    }
                }
            }
        }

        val visiblePeers = peers.filter { peer -> connections.none { it.id == peer.deviceIdHint } }
        if (visiblePeers.isNotEmpty()) {
            Text("Nearby Macs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SegmentedGroup {
                visiblePeers.forEachIndexed { idx, peer ->
                    SegmentedCard(
                        index = idx,
                        count = visiblePeers.size,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            peer.host?.let { pairing.pair(it, peer.port ?: 42_458, peer.deviceNameHint) }
                            onClose()
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(peer.deviceNameHint, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (peer.deviceIdHint in trustedIds) "Paired · tap to connect" else "New · tap to pair",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }

        if (homeState.connectedMacId != null && homeState.connectedMacId in trustedIds) {
            TextButton(
                onClick = {
                    pairing.forget(homeState.connectedMacId)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Forget ${homeState.connectedMacName}", color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedButton(
            onClick = onTurnOff,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Turn off Bridgey")
        }
    }
}

private fun formatDuration(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

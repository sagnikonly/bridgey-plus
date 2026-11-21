package dev.bridgey.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bridgey.android.BridgeyFeature
import dev.bridgey.android.BridgeySettings
import dev.bridgey.android.BridgeySettingsState
import dev.bridgey.android.ConnectedDevice
import dev.bridgey.android.ConnectionHealthCard
import dev.bridgey.android.ConnectionHealthSnapshot
import dev.bridgey.android.R
import dev.bridgey.android.SharedFolder
import dev.bridgey.android.TrustedDevice

enum class SettingsSubPage {
    ROOT,
    PHONE_NAME,
    DEVICES_AND_OVERRIDES,
    CONNECTION_HEALTH,
    DIAGNOSTICS_AND_RESET,
}

enum class SettingsPreset(val label: String, val icon: ImageVector) {
    EVERYTHING("Everything", Icons.Rounded.AutoAwesome),
    ESSENTIALS("Essentials", Icons.Rounded.Bolt),
    PRIVACY("Privacy", Icons.Rounded.Shield),
}

data class SettingsPermissionStates(
    val appNotifications: Boolean,
    val notificationAccess: Boolean,
    val phone: Boolean,
)

private data class OutcomeSettingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String,
    val checked: Boolean,
    val needsPermission: Boolean = false,
    val onFixPermission: (() -> Unit)? = null,
    val onToggle: (Boolean) -> Unit,
)

private data class AdvancedSettingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: String,
    val page: SettingsSubPage,
)

@Composable
internal fun SettingsScreen(
    subPage: SettingsSubPage,
    onSubPageChange: (SettingsSubPage) -> Unit,
    state: BridgeySettingsState,
    trustedDevices: List<TrustedDevice>,
    connections: List<ConnectedDevice>,
    permissions: SettingsPermissionStates,
    connectionHealth: ConnectionHealthSnapshot,
    batteryOptimizationIgnored: Boolean,
    onDeviceNameChanged: (String) -> Unit,
    onGlobalFeatureChanged: (BridgeyFeature, Boolean) -> Unit,
    onDeviceFeatureChanged: (String, BridgeyFeature, Boolean) -> Unit,
    onNotificationApplicationChanged: (String, Boolean) -> Unit,
    onDirectCallsChanged: (Boolean) -> Unit,
    onForget: (String) -> Unit,
    onAppNotifications: () -> Unit,
    onNotificationForwarding: () -> Unit,
    onPhonePermissions: () -> Unit,
    onAddSharedFolder: (Uri) -> Unit,
    onRemoveSharedFolder: (SharedFolder) -> Unit,
    onSharedFolderUploadsChanged: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            val forward = targetState != SettingsSubPage.ROOT
            val spec = tween<androidx.compose.ui.unit.IntOffset>(240, easing = FastOutSlowInEasing)
            if (forward) {
                (slideInHorizontally(spec) { it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(spec) { -it / 6 } + fadeOut(tween(140)))
            } else {
                (slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(spec) { it / 4 } + fadeOut(tween(140)))
            }
        },
        label = "settings_subpage",
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            SettingsSubPage.ROOT -> SettingsRoot(
                state = state,
                permissions = permissions,
                onGlobalFeatureChanged = onGlobalFeatureChanged,
                onDirectCallsChanged = onDirectCallsChanged,
                onNotificationForwarding = onNotificationForwarding,
                onPhonePermissions = onPhonePermissions,
                onOpenSubPage = onSubPageChange,
            )
            SettingsSubPage.PHONE_NAME -> PhoneNameSubScreen(
                currentName = state.deviceName,
                onSave = {
                    onDeviceNameChanged(it)
                    onSubPageChange(SettingsSubPage.ROOT)
                },
                onBack = { onSubPageChange(SettingsSubPage.ROOT) },
            )
            SettingsSubPage.DEVICES_AND_OVERRIDES -> DevicesAndOverridesSubScreen(
                state = state,
                trustedDevices = trustedDevices,
                connections = connections,
                onGlobalFeatureChanged = onGlobalFeatureChanged,
                onDeviceFeatureChanged = onDeviceFeatureChanged,
                onNotificationApplicationChanged = onNotificationApplicationChanged,
                onForget = onForget,
                onAddSharedFolder = onAddSharedFolder,
                onRemoveSharedFolder = onRemoveSharedFolder,
                onSharedFolderUploadsChanged = onSharedFolderUploadsChanged,
                onBack = { onSubPageChange(SettingsSubPage.ROOT) },
            )
            SettingsSubPage.CONNECTION_HEALTH -> ConnectionHealthSubScreen(
                connectionHealth = connectionHealth,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                permissions = permissions,
                onAppNotifications = onAppNotifications,
                onBack = { onSubPageChange(SettingsSubPage.ROOT) },
            )
            SettingsSubPage.DIAGNOSTICS_AND_RESET -> DiagnosticsAndResetSubScreen(
                onExportDiagnostics = onExportDiagnostics,
                onResetLocalData = {
                    onResetLocalData()
                    onSubPageChange(SettingsSubPage.ROOT)
                },
                onBack = { onSubPageChange(SettingsSubPage.ROOT) },
            )
        }
    }
}

@Composable
private fun SettingsRoot(
    state: BridgeySettingsState,
    permissions: SettingsPermissionStates,
    onGlobalFeatureChanged: (BridgeyFeature, Boolean) -> Unit,
    onDirectCallsChanged: (Boolean) -> Unit,
    onNotificationForwarding: () -> Unit,
    onPhonePermissions: () -> Unit,
    onOpenSubPage: (SettingsSubPage) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val clipboardOn = state.globalFeatures[BridgeyFeature.CLIPBOARD] != false
    val filesOn = state.globalFeatures[BridgeyFeature.FILES] != false
    val controlMacOn = state.globalFeatures[BridgeyFeature.MEDIA] != false ||
        state.globalFeatures[BridgeyFeature.MAC_ACTIONS] != false
    val notificationsOn = state.globalFeatures[BridgeyFeature.NOTIFICATIONS] != false
    val callsOn = state.globalFeatures[BridgeyFeature.CALLS] != false && state.directCallsEnabled

    val activePreset: SettingsPreset? = when {
        clipboardOn && filesOn && controlMacOn && notificationsOn && callsOn -> SettingsPreset.EVERYTHING
        clipboardOn && filesOn && controlMacOn && !notificationsOn && !callsOn -> SettingsPreset.ESSENTIALS
        !clipboardOn && filesOn && !notificationsOn && !callsOn -> SettingsPreset.PRIVACY
        else -> null
    }

    val outcomeItems = listOf(
        OutcomeSettingItem(
            id = "clipboard",
            title = "Share my clipboard with Mac",
            subtitle = "Copies sync both ways",
            keywords = "clipboard copy paste sync",
            checked = clipboardOn,
            onToggle = { onGlobalFeatureChanged(BridgeyFeature.CLIPBOARD, it) },
        ),
        OutcomeSettingItem(
            id = "files",
            title = "Send and receive files",
            subtitle = "Includes folders",
            keywords = "files folders transfer browse share",
            checked = filesOn,
            onToggle = { enabled ->
                onGlobalFeatureChanged(BridgeyFeature.FILES, enabled)
                onGlobalFeatureChanged(BridgeyFeature.FOLDERS, enabled)
                onGlobalFeatureChanged(BridgeyFeature.FILES_BROWSE, enabled)
            },
        ),
        OutcomeSettingItem(
            id = "control_mac",
            title = "Control Mac from this phone",
            subtitle = "Music, volume, lock, sleep",
            keywords = "music spotify media volume lock sleep remote mac actions",
            checked = controlMacOn,
            onToggle = { enabled ->
                onGlobalFeatureChanged(BridgeyFeature.MEDIA, enabled)
                onGlobalFeatureChanged(BridgeyFeature.MAC_ACTIONS, enabled)
            },
        ),
        OutcomeSettingItem(
            id = "notifications",
            title = "Show my notifications on Mac",
            subtitle = "Forward Android alerts to Mac",
            keywords = "notifications alerts forwarding permission",
            checked = notificationsOn && permissions.notificationAccess,
            needsPermission = !permissions.notificationAccess,
            onFixPermission = onNotificationForwarding,
            onToggle = { enabled ->
                if (enabled && !permissions.notificationAccess) {
                    onNotificationForwarding()
                } else {
                    onGlobalFeatureChanged(BridgeyFeature.NOTIFICATIONS, enabled)
                }
            },
        ),
        OutcomeSettingItem(
            id = "calls",
            title = "Let Mac control my calls",
            subtitle = "Answer and end from Mac",
            keywords = "calls phone answer decline dial permission",
            checked = callsOn && permissions.phone,
            needsPermission = state.globalFeatures[BridgeyFeature.CALLS] != false && !permissions.phone,
            onFixPermission = onPhonePermissions,
            onToggle = { enabled ->
                onGlobalFeatureChanged(BridgeyFeature.CALLS, enabled)
                onDirectCallsChanged(enabled)
            },
        ),
    )

    val advancedItems = listOf(
        AdvancedSettingItem(
            id = "phone_name",
            title = "Phone name",
            subtitle = state.deviceName,
            keywords = "device name phone rename",
            page = SettingsSubPage.PHONE_NAME,
        ),
        AdvancedSettingItem(
            id = "mac_overrides",
            title = "Mac and per-device overrides",
            subtitle = "Switch or forget Mac, folders, apps",
            keywords = "mac paired device forget switch folders notification apps screen ping ring",
            page = SettingsSubPage.DEVICES_AND_OVERRIDES,
        ),
        AdvancedSettingItem(
            id = "health",
            title = "Connection health",
            subtitle = "Battery optimization, network",
            keywords = "connection health latency ping wifi battery optimization permissions",
            page = SettingsSubPage.CONNECTION_HEALTH,
        ),
        AdvancedSettingItem(
            id = "diagnostics",
            title = "Diagnostics and reset",
            subtitle = "Export logs or reset Bridgey",
            keywords = "diagnostics logs export reset wipe clear data",
            page = SettingsSubPage.DIAGNOSTICS_AND_RESET,
        ),
    )

    val trimmed = query.trim().lowercase()
    val filteredOutcomes = if (trimmed.isEmpty()) outcomeItems else outcomeItems.filter {
        trimmed in it.title.lowercase() || trimmed in it.subtitle.lowercase() || trimmed in it.keywords
    }
    val filteredAdvanced = if (trimmed.isEmpty()) advancedItems else advancedItems.filter {
        trimmed in it.title.lowercase() || trimmed in it.subtitle.lowercase() || trimmed in it.keywords
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }

        // Pill search bar
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Search settings", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { query = "" },
                        )
                    }
                }
            }
        }

        // Expressive connected preset pills
        if (trimmed.isEmpty()) {
            item {
                val haptics = LocalHapticFeedback.current
                val presets = SettingsPreset.entries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    presets.forEachIndexed { index, preset ->
                        val selected = activePreset == preset
                        val bgColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "preset_bg_${preset.name}",
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "preset_fg_${preset.name}",
                        )
                        val innerCorner by animateDpAsState(
                            targetValue = if (selected) 22.dp else 8.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "preset_corner_${preset.name}",
                        )
                        val shape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 22.dp,
                                bottomStart = 22.dp,
                                topEnd = innerCorner,
                                bottomEnd = innerCorner,
                            )
                            presets.lastIndex -> RoundedCornerShape(
                                topStart = innerCorner,
                                bottomStart = innerCorner,
                                topEnd = 22.dp,
                                bottomEnd = 22.dp,
                            )
                            else -> RoundedCornerShape(innerCorner)
                        }
                        Surface(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (preset) {
                                    SettingsPreset.EVERYTHING -> {
                                        onGlobalFeatureChanged(BridgeyFeature.CLIPBOARD, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FILES, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FOLDERS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FILES_BROWSE, true)
                                        onGlobalFeatureChanged(BridgeyFeature.MEDIA, true)
                                        onGlobalFeatureChanged(BridgeyFeature.MAC_ACTIONS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.LINKS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.PING, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FIND_DEVICE, true)
                                        onGlobalFeatureChanged(BridgeyFeature.BATTERY, true)
                                        onGlobalFeatureChanged(BridgeyFeature.NOTIFICATIONS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.CALLS, true)
                                    }
                                    SettingsPreset.ESSENTIALS -> {
                                        onGlobalFeatureChanged(BridgeyFeature.CLIPBOARD, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FILES, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FOLDERS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.MEDIA, true)
                                        onGlobalFeatureChanged(BridgeyFeature.MAC_ACTIONS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.NOTIFICATIONS, false)
                                        onGlobalFeatureChanged(BridgeyFeature.CALLS, false)
                                        onDirectCallsChanged(false)
                                    }
                                    SettingsPreset.PRIVACY -> {
                                        onGlobalFeatureChanged(BridgeyFeature.CLIPBOARD, false)
                                        onGlobalFeatureChanged(BridgeyFeature.FILES, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FOLDERS, true)
                                        onGlobalFeatureChanged(BridgeyFeature.FILES_BROWSE, false)
                                        onGlobalFeatureChanged(BridgeyFeature.NOTIFICATIONS, false)
                                        onGlobalFeatureChanged(BridgeyFeature.CALLS, false)
                                        onDirectCallsChanged(false)
                                        onGlobalFeatureChanged(BridgeyFeature.SCREEN, false)
                                    }
                                }
                            },
                            shape = shape,
                            color = bgColor,
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    preset.icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else contentColor,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Share with Mac segmented card group
        if (filteredOutcomes.isNotEmpty()) {
            item {
                Text(
                    "Share with Mac",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                SegmentedGroup {
                    filteredOutcomes.forEachIndexed { idx, item ->
                        SegmentedCard(index = idx, count = filteredOutcomes.size) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (item.needsPermission) "Needs permission" else item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.needsPermission) ExpressiveWarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (item.needsPermission && item.onFixPermission != null) {
                                    Text(
                                        "Fix",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = item.onFixPermission)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Switch(
                                    checked = item.checked,
                                    onCheckedChange = item.onToggle,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Advanced segmented card group
        if (filteredAdvanced.isNotEmpty()) {
            item {
                Text(
                    "Advanced",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                SegmentedGroup {
                    filteredAdvanced.forEachIndexed { idx, item ->
                        SegmentedCard(
                            index = idx,
                            count = filteredAdvanced.size,
                            onClick = { onOpenSubPage(item.page) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PhoneNameSubScreen(
    currentName: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var edited by remember(currentName) { mutableStateOf(currentName) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubScreenHeader("Phone name", onBack)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This name appears on your Mac when pairing and connecting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it.take(64) },
                    label = { Text("Device name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSave(edited) },
                    enabled = edited.trim().isNotEmpty() && edited.trim() != currentName,
                ) { Text("Save name") }
            }
        }
    }
}

@Composable
private fun DevicesAndOverridesSubScreen(
    state: BridgeySettingsState,
    trustedDevices: List<TrustedDevice>,
    connections: List<ConnectedDevice>,
    onGlobalFeatureChanged: (BridgeyFeature, Boolean) -> Unit,
    onDeviceFeatureChanged: (String, BridgeyFeature, Boolean) -> Unit,
    onNotificationApplicationChanged: (String, Boolean) -> Unit,
    onForget: (String) -> Unit,
    onAddSharedFolder: (Uri) -> Unit,
    onRemoveSharedFolder: (SharedFolder) -> Unit,
    onSharedFolderUploadsChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onAddSharedFolder)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SubScreenHeader("Mac & overrides", onBack) }

        item {
            Text("All individual features", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        item {
            SegmentedGroup {
                BridgeyFeature.entries.forEachIndexed { idx, feature ->
                    SegmentedCard(index = idx, count = BridgeyFeature.entries.size) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(feature.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Switch(
                                checked = state.globalFeatures[feature] != false,
                                onCheckedChange = { onGlobalFeatureChanged(feature, it) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.shared_folders_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.shared_folders_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.sharedFolders.isEmpty()) {
                        Text(stringResource(R.string.shared_folders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.sharedFolders.forEach { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(folder.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            TextButton(onClick = { onRemoveSharedFolder(folder) }) { Text(stringResource(R.string.shared_folders_remove)) }
                        }
                    }
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        enabled = state.sharedFolders.size < BridgeySettings.MAX_SHARED_FOLDERS,
                    ) { Text(stringResource(R.string.shared_folders_add)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.shared_folders_uploads), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.shared_folders_uploads_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.sharedFolderUploadsEnabled, onCheckedChange = onSharedFolderUploadsChanged)
                    }
                }
            }
        }

        if (state.notificationApplications.isNotEmpty()) {
            item {
                Text("Notification apps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            val apps = state.notificationApplications.entries.sortedBy { "${it.value.lowercase()}\u0000${it.key}" }
            item {
                SegmentedGroup {
                    apps.forEachIndexed { idx, app ->
                        SegmentedCard(index = idx, count = apps.size) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.value, style = MaterialTheme.typography.titleSmall)
                                    Text(app.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = app.key !in state.disabledNotificationPackages,
                                    onCheckedChange = { onNotificationApplicationChanged(app.key, it) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Paired Macs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (trustedDevices.isEmpty()) {
            item { Text("No paired Macs yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            itemsIndexed(trustedDevices, key = { _, d -> d.id }) { _, device ->
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.id.take(8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BridgeyFeature.entries.forEach { feature ->
                            val globalOn = state.globalFeatures[feature] != false
                            val offOnMac = connections.firstOrNull { it.id == device.id }?.link?.remoteFeatures?.get(feature) == false
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(feature.title, style = MaterialTheme.typography.bodyMedium)
                                    if (offOnMac) Text("Off on ${device.name}", style = MaterialTheme.typography.labelSmall, color = ExpressiveWarningAmber)
                                }
                                Switch(
                                    checked = globalOn && state.deviceFeatures[device.id]?.get(feature) != false,
                                    enabled = globalOn,
                                    onCheckedChange = { onDeviceFeatureChanged(device.id, feature, it) },
                                )
                            }
                        }
                        TextButton(onClick = { onForget(device.id) }) {
                            Text("Forget ${device.name}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ConnectionHealthSubScreen(
    connectionHealth: ConnectionHealthSnapshot,
    batteryOptimizationIgnored: Boolean,
    permissions: SettingsPermissionStates,
    onAppNotifications: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SubScreenHeader("Connection health", onBack) }
        item { ConnectionHealthCard(connectionHealth, batteryOptimizationIgnored) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bridgey notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (permissions.appNotifications) "Enabled for background sync & transfers" else "Recommended for reliable background sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onAppNotifications) {
                        Text(if (permissions.appNotifications) "Manage" else "Allow")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsAndResetSubScreen(
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubScreenHeader("Diagnostics & reset", onBack)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Diagnostics log", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Export a bounded event log without clipboard text, notification content, file names, addresses, or device identifiers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onExportDiagnostics) { Text("Share diagnostics file") }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.reset_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.reset_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { confirmingReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.reset_button)) }
            }
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.reset_confirm_deleted))
                    Text(stringResource(R.string.reset_confirm_kept))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingReset = false
                        onResetLocalData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.reset_cancel)) } },
        )
    }
}

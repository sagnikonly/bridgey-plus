package dev.bridgey.android

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.collectAsState
import dev.bridgey.android.ui.ActivityScreen
import dev.bridgey.android.ui.BridgeyExpressiveTheme
import dev.bridgey.android.ui.HomeScreen
import dev.bridgey.android.ui.SettingsPermissionStates
import dev.bridgey.android.ui.SettingsScreen
import dev.bridgey.android.ui.SettingsSubPage
import dev.bridgey.android.ui.computeHomeState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bridgey.android.screen.ScreenShare
import dev.bridgey.android.screen.ScreenShareState
import dev.bridgey.android.screen.ScreenStopReason
import dev.bridgey.core.discovery.DiscoveredPeer
import dev.bridgey.core.discovery.NsdDiscoveryService
import java.io.File

private val BridgeyPurple = Color(0xFF6046B6)
private val BridgeyPurpleDark = Color(0xFFD0BCFF)
private val BridgeyLightScheme = lightColorScheme(
    primary = BridgeyPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF24105A),
    secondaryContainer = Color(0xFFE8E2F1),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF2EDF5),
    background = Color(0xFFFAF8FF),
)
private val BridgeyDarkScheme = darkColorScheme(
    primary = BridgeyPurpleDark,
    primaryContainer = Color(0xFF493092),
    secondaryContainer = Color(0xFF49454F),
)

private enum class PermissionPrompt {
    BridgeyNotifications,
    NotificationForwarding,
    DirectCalls,
    ScreenNotifications,
}

private data class SharedContent(
    val text: String? = null,
    val files: List<Uri> = emptyList(),
)

class MainActivity : ComponentActivity() {
    private lateinit var discovery: NsdDiscoveryService
    private lateinit var pairing: PairingCoordinator
    private lateinit var bridgeySettings: BridgeySettings
    private var notificationAccessEnabled by mutableStateOf(false)
    private var appNotificationsEnabled by mutableStateOf(false)
    private var callPermissionsGranted by mutableStateOf(false)
    private var sharedContent by mutableStateOf<SharedContent?>(null)
    private var showOnboarding by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissionState()
    }
    private val callPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        callPermissionsGranted = hasCallIntegrationPermissions()
        bridgeySettings.setDirectCallsEnabled(callPermissionsGranted)
        BridgeyNotificationListenerService.callPermissionsChanged()
    }
    private val screenConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            (application as BridgeyApplication).screenShare.consentGranted(this, result.resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bridgey = application as BridgeyApplication
        if (!bridgey.isPrimaryUser) {
            setContent { BridgeyTheme { UnsupportedProfile() } }
            return
        }
        bridgey.enableBridgey()
        showOnboarding = !getSharedPreferences(BridgeyApplication.ONBOARDING_PREFERENCES, MODE_PRIVATE)
            .getBoolean("completed", false)
        pairing = bridgey.pairing
        discovery = bridgey.discovery
        bridgeySettings = bridgey.settings
        sharedContent = intent.toSharedContent()
        startForegroundService(Intent(this, BridgeyConnectionService::class.java))
        setContent {
            BridgeyTheme {
                BridgeyApp(
                    discovery = discovery,
                    pairing = pairing,
                    settings = bridgey.settings,
                    screenShare = bridgey.screenShare,
                    onRequestScreenShare = ::requestScreenShare,
                    onDeviceNameChanged = bridgey::updateDeviceName,
                    appNotificationsEnabled = appNotificationsEnabled,
                    notificationAccessEnabled = notificationAccessEnabled,
                    callPermissionsGranted = callPermissionsGranted,
                    onTurnOff = {
                        startService(Intent(this, BridgeyConnectionService::class.java).setAction(BridgeyConnectionService.ACTION_TURN_OFF))
                        finishAndRemoveTask()
                    },
                    onRequestAppNotifications = ::requestAppNotifications,
                    onOpenAppNotificationSettings = ::openAppNotificationSettings,
                    onOpenNotificationAccessSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestDirectCalls = ::requestDirectCalls,
                    onOpenAppDetailsSettings = ::openAppDetailsSettings,
                    onExportDiagnostics = ::exportDiagnostics,
                    onResetLocalData = {
                        bridgey.resetLocalData()
                        showOnboarding = true
                    },
                    sharedContent = sharedContent,
                    onSharedContentHandled = ::clearSharedContent,
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = {
                        getSharedPreferences(BridgeyApplication.ONBOARDING_PREFERENCES, MODE_PRIVATE)
                            .edit().putBoolean("completed", true).apply()
                        showOnboarding = false
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::pairing.isInitialized) {
            pairing.syncClipboardIfChanged()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedContent = intent.toSharedContent()
    }

    private fun Intent.toSharedContent(): SharedContent? {
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val streams = when (action) {
            Intent.ACTION_SEND_MULTIPLE -> streamUris()
            else -> listOfNotNull(streamUri())
        }.distinct()
        val sharedText = getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() && streams.isEmpty() }
        return SharedContent(text = sharedText, files = streams)
            .takeIf { it.text != null || it.files.isNotEmpty() }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun refreshPermissionState() {
        appNotificationsEnabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        notificationAccessEnabled = NotificationAccess.isEnabled(this)
        callPermissionsGranted = hasCallIntegrationPermissions()
        if (
            ::bridgeySettings.isInitialized && bridgeySettings.state.value.directCallsEnabled &&
            !hasCallIntegrationPermissions()
        ) {
            bridgeySettings.setDirectCallsEnabled(false)
            BridgeyNotificationListenerService.callPermissionsChanged()
        }
    }

    private fun clearSharedContent() {
        sharedContent = null
        // Prevent a handled share intent from being replayed after configuration changes.
        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun requestAppNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings()
        }
    }

    private fun requestDirectCalls() {
        if (hasCallIntegrationPermissions()) {
            bridgeySettings.setDirectCallsEnabled(true)
            BridgeyNotificationListenerService.callPermissionsChanged()
        } else {
            callPermissionLauncher.launch(CALL_INTEGRATION_PERMISSIONS)
        }
    }

    private fun requestScreenShare() {
        // Android's own consent dialog, every time; Bridgey never keeps or reuses the result.
        runCatching { screenConsentLauncher.launch((application as BridgeyApplication).screenShare.consentIntent()) }
    }

    private fun hasCallIntegrationPermissions(): Boolean =
        CALL_INTEGRATION_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun openAppNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun openAppDetailsSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun exportDiagnostics() {
        val report = pairing.diagnosticsReport()
        val diagnosticsDirectory = File(cacheDir, "diagnostics")
        val reportFile = runCatching {
            check(diagnosticsDirectory.exists() || diagnosticsDirectory.mkdirs())
            File(diagnosticsDirectory, "Bridgey-Diagnostics.json").apply {
                writeText(report, Charsets.UTF_8)
            }
        }.getOrElse {
            android.widget.Toast.makeText(this, "Could not create diagnostics file", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val reportUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            reportFile,
        )
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Bridgey diagnostics")
                putExtra(Intent.EXTRA_STREAM, reportUri)
                clipData = ClipData.newUri(contentResolver, "Bridgey diagnostics", reportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Export Bridgey diagnostics",
        ))
    }
}

@Composable
private fun BridgeyTheme(content: @Composable () -> Unit) {
    BridgeyExpressiveTheme(content = content)
}

@Composable
private fun UnsupportedProfile() = Surface(Modifier.fillMaxSize()) {
    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Bridgey", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Already active in the main phone profile", style = MaterialTheme.typography.titleLarge)
        Text("Open Bridgey from the main profile to avoid showing this phone twice.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BridgeyApp(
    discovery: NsdDiscoveryService,
    pairing: PairingCoordinator,
    settings: BridgeySettings,
    screenShare: ScreenShare,
    onRequestScreenShare: () -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    appNotificationsEnabled: Boolean,
    notificationAccessEnabled: Boolean,
    callPermissionsGranted: Boolean,
    onTurnOff: () -> Unit,
    onRequestAppNotifications: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onRequestDirectCalls: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    sharedContent: SharedContent?,
    onSharedContentHandled: () -> Unit,
    showOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
) {
    val peers by discovery.peers.collectAsStateWithLifecycle()
    val pairingState by pairing.state.collectAsStateWithLifecycle()
    val connectionHealth by pairing.connectionHealth.collectAsStateWithLifecycle()
    val trustedIds by pairing.trustedDeviceIds.collectAsStateWithLifecycle()
    val clipboardStatus by pairing.clipboardStatus.collectAsStateWithLifecycle()
    val lastClipboardPreview by pairing.lastClipboardPreview.collectAsStateWithLifecycle()
    val clipboardPaused by pairing.clipboardSyncPaused.collectAsStateWithLifecycle()
    val activityEvents by pairing.activityEvents.collectAsStateWithLifecycle()
    val fileTransfers by pairing.fileTransfers.collectAsStateWithLifecycle()
    val phoneRinging by pairing.phoneRinging.collectAsStateWithLifecycle()
    val macRinging by pairing.macRinging.collectAsStateWithLifecycle()
    val remoteBattery by pairing.remoteBattery.collectAsStateWithLifecycle()
    val pingStatus by pairing.pingStatus.collectAsStateWithLifecycle()
    val trustedDevices by pairing.trustedDevices.collectAsStateWithLifecycle()
    val remoteFeatures by pairing.remoteFeatures.collectAsStateWithLifecycle()
    val connections by pairing.connections.collectAsStateWithLifecycle()
    val settingsState by settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val screenState by screenShare.state.collectAsStateWithLifecycle()
    val incomingLink by pairing.quickActions.receivedLink.collectAsState()
    val media by pairing.quickActions.media.collectAsState()
    var permissionPrompt by remember { mutableStateOf<PermissionPrompt?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Home, 1 = Activity, 2 = Settings
    var settingsSubPage by remember { mutableStateOf(SettingsSubPage.ROOT) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pairing.sendFiles(uris)
    }

    BackHandler(enabled = selectedTab != 0 || settingsSubPage != SettingsSubPage.ROOT) {
        when {
            selectedTab == 2 && settingsSubPage != SettingsSubPage.ROOT -> settingsSubPage = SettingsSubPage.ROOT
            else -> selectedTab = 0
        }
    }

    val onAppNotifications: () -> Unit = {
        if (appNotificationsEnabled) onOpenAppNotificationSettings()
        else permissionPrompt = PermissionPrompt.BridgeyNotifications
    }
    val onNotificationForwarding: () -> Unit = {
        if (notificationAccessEnabled) onOpenNotificationAccessSettings()
        else permissionPrompt = PermissionPrompt.NotificationForwarding
    }
    val enabledFeatures = BridgeyFeature.entries.associateWith { feature ->
        settings.isEnabled(feature, (pairingState as? PairingState.Connected)?.deviceId) &&
            remoteFeatures[feature] != false
    }
    val homeState = computeHomeState(
        pairingState = pairingState,
        nearbyPeerCount = peers.size,
        fileTransfers = fileTransfers,
        incomingLink = incomingLink,
        media = media,
        mediaEnabled = enabledFeatures[BridgeyFeature.MEDIA] == true,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Schedule, contentDescription = "Activity") },
                    label = { Text("Activity") },
                    colors = navColors,
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        if (selectedTab == 2) settingsSubPage = SettingsSubPage.ROOT
                        selectedTab = 2
                    },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = navColors,
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                homeState = homeState,
                peers = peers,
                trustedIds = trustedIds,
                connections = connections,
                remoteBattery = remoteBattery,
                clipboardStatus = clipboardStatus,
                lastClipboardPreview = lastClipboardPreview,
                clipboardPaused = clipboardPaused,
                pingStatus = pingStatus,
                phoneRinging = phoneRinging,
                macRinging = macRinging,
                screenState = screenState,
                enabledFeatures = enabledFeatures,
                pairing = pairing,
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                onShareScreen = {
                    if (appNotificationsEnabled) onRequestScreenShare()
                    else permissionPrompt = PermissionPrompt.ScreenNotifications
                },
                onStopScreen = { screenShare.stop(ScreenStopReason.USER) },
                onTurnOff = onTurnOff,
                modifier = Modifier.padding(padding),
            )
            1 -> ActivityScreen(
                fileTransfers = fileTransfers,
                events = activityEvents,
                pairing = pairing,
                modifier = Modifier.padding(padding),
            )
            else -> SettingsScreen(
                subPage = settingsSubPage,
                onSubPageChange = { settingsSubPage = it },
                state = settingsState,
                trustedDevices = trustedDevices,
                connections = connections,
                permissions = SettingsPermissionStates(appNotificationsEnabled, notificationAccessEnabled, callPermissionsGranted),
                connectionHealth = connectionHealth,
                batteryOptimizationIgnored = batteryOptimizationIgnored(LocalContext.current),
                onDeviceNameChanged = onDeviceNameChanged,
                onGlobalFeatureChanged = { feature, enabled ->
                    settings.setGlobal(feature, enabled)
                    if (feature == BridgeyFeature.FIND_DEVICE && !enabled) pairing.stopFinding()
                    if (feature == BridgeyFeature.CALLS && !enabled) {
                        settings.setDirectCallsEnabled(false)
                        BridgeyNotificationListenerService.callPermissionsChanged()
                    }
                },
                onDeviceFeatureChanged = { deviceId, feature, enabled ->
                    settings.setForDevice(deviceId, feature, enabled)
                    if (
                        feature == BridgeyFeature.FIND_DEVICE && !enabled &&
                        connections.any { it.id == deviceId }
                    ) pairing.stopFinding()
                },
                onNotificationApplicationChanged = { packageName, enabled ->
                    settings.setNotificationApplicationEnabled(packageName, enabled)
                    BridgeyNotificationListenerService.filterChanged(packageName, enabled)
                },
                onDirectCallsChanged = { enabled ->
                    if (enabled) permissionPrompt = PermissionPrompt.DirectCalls
                    else {
                        settings.setDirectCallsEnabled(false)
                        BridgeyNotificationListenerService.callPermissionsChanged()
                    }
                },
                onForget = pairing::forget,
                onAppNotifications = onAppNotifications,
                onNotificationForwarding = onNotificationForwarding,
                onPhonePermissions = {
                    if (callPermissionsGranted) onOpenAppDetailsSettings()
                    else permissionPrompt = PermissionPrompt.DirectCalls
                },
                onAddSharedFolder = { settings.shareFolder(context.contentResolver, it) },
                onRemoveSharedFolder = { settings.unshareFolder(context.contentResolver, it) },
                onSharedFolderUploadsChanged = settings::setSharedFolderUploadsEnabled,
                onExportDiagnostics = onExportDiagnostics,
                onResetLocalData = {
                    onResetLocalData()
                    settingsSubPage = SettingsSubPage.ROOT
                    selectedTab = 0
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.welcome_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.welcome_body))
                    Text(stringResource(R.string.welcome_permissions), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = onOnboardingComplete) {
                    Text(stringResource(R.string.welcome_continue))
                }
            },
        )
    }

    when (val state = pairingState) {
        is PairingState.Verification -> AlertDialog(
            onDismissRequest = pairing::cancel,
            title = { Text("Verify this device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Check that this code also appears on ${state.peerName}.")
                    Text(state.code, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { Button(onClick = pairing::confirm) { Text("Codes match") } },
            dismissButton = { TextButton(onClick = pairing::cancel) { Text("Cancel") } },
        )
        is PairingState.Failed -> AlertDialog(
            onDismissRequest = pairing::cancel,
            title = { Text("Couldn’t connect") },
            text = { Text(state.message) },
            confirmButton = { Button(onClick = pairing::cancel) { Text("OK") } },
        )
        else -> Unit
    }

    permissionPrompt?.let { prompt ->
        val isForwarding = prompt == PermissionPrompt.NotificationForwarding
        val isDirectCalls = prompt == PermissionPrompt.DirectCalls
        val isScreen = prompt == PermissionPrompt.ScreenNotifications
        AlertDialog(
            onDismissRequest = { permissionPrompt = null },
            title = {
                Text(
                    when {
                        isForwarding -> "Forward Android notifications?"
                        isDirectCalls -> "Allow call status and controls?"
                        isScreen -> stringResource(R.string.screen_notifications_title)
                        else -> "Allow Bridgey notifications?"
                    },
                )
            },
            text = {
                Text(
                    when {
                        isForwarding -> "This optional access lets Bridgey read notification titles and text and send them only to your paired Mac over the encrypted local connection."
                        isScreen -> stringResource(R.string.screen_notifications_body)
                        isDirectCalls -> "These optional Phone permissions let an authenticated paired Mac start calls, distinguish ringing from active calls, and answer, decline, or hang up. Bridgey does not read contacts or call history."
                        else -> "Bridgey uses notifications to keep the connection visible and show file transfers, received files, and Find Device. It does not use notifications for advertising."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    permissionPrompt = null
                    when {
                        isForwarding -> onOpenNotificationAccessSettings()
                        isDirectCalls -> onRequestDirectCalls()
                        else -> onRequestAppNotifications()
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { permissionPrompt = null }) { Text("Not now") } },
        )
    }

    sharedContent?.let { content ->
        val isConnected = pairingState is PairingState.Connected
        val requiredFeature = if (content.files.isNotEmpty()) BridgeyFeature.FILES else BridgeyFeature.CLIPBOARD
        val featureAvailable = settings.isEnabled(
            requiredFeature,
            (pairingState as? PairingState.Connected)?.deviceId,
        ) && remoteFeatures[requiredFeature] != false
        val summary = when {
            content.files.size == 1 -> "Send the selected file to your Mac?"
            content.files.isNotEmpty() -> "Send ${content.files.size} selected files to your Mac?"
            else -> "Send the shared text to your Mac?"
        }
        val link = if (content.files.isEmpty()) content.text?.let(::sharedWebLink) else null
        val sendLink = link != null && isConnected && pairing.isFeatureAvailable(BridgeyFeature.LINKS)
        AlertDialog(
            onDismissRequest = onSharedContentHandled,
            title = { Text("Share with Bridgey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sendLink && link != null) {
                        Text(stringResource(R.string.share_link_summary, linkHost(link)))
                        Text(stringResource(R.string.share_link_detail), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text(summary)
                    when {
                        !isConnected -> Text(
                            "Waiting for a trusted Mac to reconnect.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        !featureAvailable -> Text(
                            "This feature is turned off on one of your devices.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                if (sendLink && link != null) {
                    Button(onClick = {
                        pairing.quickActions.sendLink(link)
                        onSharedContentHandled()
                    }) { Text(stringResource(R.string.share_link_send)) }
                } else Button(
                    enabled = isConnected && featureAvailable,
                    onClick = {
                        content.text?.let { pairing.sendText(it) }
                        if (content.files.isNotEmpty()) pairing.sendFiles(content.files)
                        onSharedContentHandled()
                    },
                ) { Text("Send") }
            },
            dismissButton = {
                Row {
                    if (sendLink) {
                        TextButton(enabled = featureAvailable, onClick = {
                            content.text?.let { pairing.sendText(it) }
                            onSharedContentHandled()
                        }) { Text(stringResource(R.string.share_link_as_text)) }
                    }
                    TextButton(onClick = onSharedContentHandled) { Text("Cancel") }
                }
            },
        )
    }
}


private val CALL_INTEGRATION_PERMISSIONS = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
)

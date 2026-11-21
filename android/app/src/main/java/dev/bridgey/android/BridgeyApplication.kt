package dev.bridgey.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UserManager
import android.provider.Settings
import dev.bridgey.android.screen.ScreenShare
import dev.bridgey.core.discovery.LocalDiscoveryIdentity
import dev.bridgey.core.discovery.NsdDiscoveryService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BridgeyApplication : Application() {
    var isPrimaryUser: Boolean = true
        private set
    lateinit var pairing: PairingCoordinator
        private set
    lateinit var discovery: NsdDiscoveryService
        private set
    lateinit var settings: BridgeySettings
        private set
    internal lateinit var savedMacActions: SavedMacActions
        private set
    lateinit var screenShare: ScreenShare
        private set
    var isBridgeyEnabled: Boolean = false
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastBatteryIntent: Intent? = null
    private var batteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            lastBatteryIntent = intent
            publishBattery(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isPrimaryUser = getSystemService(UserManager::class.java).isSystemUser
        if (!isPrimaryUser) return
        val preferences = getSharedPreferences("bridgey", MODE_PRIVATE)
        val deviceId = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("device_id", it).apply()
        }
        val systemDeviceName = Settings.Global.getString(contentResolver, "device_name") ?: "Android device"
        settings = BridgeySettings(this, systemDeviceName)
        val deviceName = settings.state.value.deviceName
        pairing = PairingCoordinator(this, deviceId, deviceName, settings = settings)
        screenShare = ScreenShare(this, pairing)
        pairing.screenListener = screenShare
        discovery = NsdDiscoveryService(this, LocalDiscoveryIdentity(deviceId, deviceName))
        applicationScope.launch {
            combine(discovery.peers, pairing.trustedDeviceIds, pairing.connections) { peers, trustedIds, connections ->
                Triple(peers, trustedIds, connections.map(ConnectedDevice::id).toSet())
            }.collect { (peers, trustedIds, connectedIds) ->
                if (!isBridgeyEnabled) return@collect
                // Each trusted device is dialled on its own, also while other devices are connected.
                autoDialTargets(peers, { it.deviceIdHint }, deviceId, trustedIds, connectedIds).forEach { (peer, peerId) ->
                    peer.host?.let { pairing.reconnect(it, peer.port ?: 42_458, peer.deviceNameHint, peerId) }
                }
            }
        }
        applicationScope.launch {
            settings.state.collect { lastBatteryIntent?.let(::publishBattery) }
        }
        savedMacActions = SavedMacActions(this)
        applicationScope.launch {
            combine(pairing.state, pairing.quickActions.macActions, pairing.trustedDeviceIds) { state, actions, trusted ->
                Triple(state, actions, trusted)
            }.collect { (state, actions, trusted) ->
                savedMacActions.retainOnly(trusted)
                if (state is PairingState.Connected && actions != null) savedMacActions.save(state.deviceId, actions)
            }
        }
        applicationScope.launch {
            combine(pairing.state, pairing.quickActions.macActions, pairing.remoteFeatures, settings.state, pairing.trustedDevices) {
                _, _, _, _, _ -> Unit
            }.collect { updateMacWidgets(this@BridgeyApplication) }
        }
        applicationScope.launch {
            combine(pairing.remoteBattery, pairing.quickActions.media, pairing.clipboardSyncPaused) { _, _, _ -> Unit }
                .collect { updateMacWidgets(this@BridgeyApplication) }
        }
        applicationScope.launch {
            pairing.remoteFeatures.collect { features ->
                if (features[BridgeyFeature.BATTERY] != false) lastBatteryIntent?.let(::publishBattery)
            }
        }
        if (preferences.getBoolean(PREFERENCE_ENABLED, true)) enableBridgey()
    }

    fun enableBridgey() {
        if (!isPrimaryUser) return
        getSharedPreferences("bridgey", MODE_PRIVATE).edit().putBoolean(PREFERENCE_ENABLED, true).apply()
        if (isBridgeyEnabled) return
        isBridgeyEnabled = true
        pairing.start()
        discovery.start()
        if (!batteryReceiverRegistered) {
            lastBatteryIntent = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
        }
        lastBatteryIntent?.let(::publishBattery)
        updateMacWidgets(this)
    }

    fun disableBridgey() {
        if (!isPrimaryUser) return
        getSharedPreferences("bridgey", MODE_PRIVATE).edit().putBoolean(PREFERENCE_ENABLED, false).commit()
        if (!isBridgeyEnabled) return
        isBridgeyEnabled = false
        if (batteryReceiverRegistered) {
            runCatching { unregisterReceiver(batteryReceiver) }
            batteryReceiverRegistered = false
        }
        discovery.stop()
        pairing.pause()
        updateMacWidgets(this)
    }

    /**
     * Settings, Reset Bridgey on this phone: deletes trust records, settings and the first-run flag
     * (docs/upgrade-policy.md). The device ID, signing key, received files and granted permissions stay.
     */
    fun resetLocalData() {
        pairing.forgetAllDevices()
        settings.reset()
        getSharedPreferences(ONBOARDING_PREFERENCES, MODE_PRIVATE).edit().clear().commit()
        val name = settings.state.value.deviceName
        pairing.updateDeviceName(name)
        discovery.updateDeviceName(name)
        BridgeyNotificationListenerService.callPermissionsChanged()
    }

    fun updateDeviceName(value: String) {
        settings.setDeviceName(value)
        val name = settings.state.value.deviceName
        pairing.updateDeviceName(name)
        discovery.updateDeviceName(name)
    }

    private fun publishBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        pairing.sendBattery(percent, charging)
    }

    companion object {
        private const val PREFERENCE_ENABLED = "bridgey_enabled"
        internal const val ONBOARDING_PREFERENCES = "bridgey_onboarding"
    }
}

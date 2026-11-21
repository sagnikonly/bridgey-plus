package dev.bridgey.android.ui

import dev.bridgey.android.FileTransferState
import dev.bridgey.android.MacMediaState
import dev.bridgey.android.PairingState

sealed interface HomeTopSlot {
    data class Offline(val connecting: Boolean, val peerCount: Int) : HomeTopSlot
    data class Transferring(
        val activeTransfer: FileTransferState,
        val currentIndex: Int,
        val totalCount: Int,
    ) : HomeTopSlot
    data class IncomingLink(val url: String) : HomeTopSlot
    data object Idle : HomeTopSlot
}

data class HomeUiState(
    val isConnected: Boolean,
    val connectedMacName: String?,
    val connectedMacId: String?,
    val topSlot: HomeTopSlot,
    val showNowPlaying: Boolean,
)

/**
 * Pure reducer that decides what occupies Home's adaptive top slot and whether
 * the Now Playing card should be visible.
 *
 * Priority order for the adaptive top slot:
 * 1. Offline (Mac disconnected / connecting)
 * 2. Active file transfer in progress
 * 3. Incoming web link from Mac
 * 4. Idle (no banner)
 */
fun computeHomeState(
    pairingState: PairingState,
    nearbyPeerCount: Int,
    fileTransfers: Map<String, FileTransferState>,
    incomingLink: String?,
    media: MacMediaState,
    mediaEnabled: Boolean,
): HomeUiState {
    val connected = pairingState as? PairingState.Connected
    if (connected == null) {
        return HomeUiState(
            isConnected = false,
            connectedMacName = null,
            connectedMacId = null,
            topSlot = HomeTopSlot.Offline(
                connecting = pairingState is PairingState.Connecting,
                peerCount = nearbyPeerCount,
            ),
            showNowPlaying = false,
        )
    }

    val activeTransfers = fileTransfers.values
        .filter { it.active }
        .sortedBy { it.startedAtMillis }

    val topSlot: HomeTopSlot = when {
        activeTransfers.isNotEmpty() -> {
            val allRecentBatch = fileTransfers.values.count { it.active }
            HomeTopSlot.Transferring(
                activeTransfer = activeTransfers.first(),
                currentIndex = 1,
                totalCount = allRecentBatch,
            )
        }
        !incomingLink.isNullOrBlank() -> HomeTopSlot.IncomingLink(incomingLink)
        else -> HomeTopSlot.Idle
    }

    val playerActive = mediaEnabled &&
        media.player in setOf("Mac", "Music", "Spotify") &&
        media.detail.isEmpty() &&
        (media.playing || media.title.isNotEmpty())

    return HomeUiState(
        isConnected = true,
        connectedMacName = connected.peerName,
        connectedMacId = connected.deviceId,
        topSlot = topSlot,
        showNowPlaying = playerActive,
    )
}

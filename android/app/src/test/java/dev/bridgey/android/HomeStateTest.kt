package dev.bridgey.android

import dev.bridgey.android.ui.HomeTopSlot
import dev.bridgey.android.ui.computeHomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    @Test fun offlineStateShowsReconnectAndHidesNowPlaying() {
        val state = computeHomeState(
            pairingState = PairingState.Idle,
            nearbyPeerCount = 1,
            fileTransfers = emptyMap(),
            incomingLink = "https://example.com",
            media = MacMediaState(player = "Spotify", title = "Song", playing = true),
            mediaEnabled = true,
        )
        assertFalse(state.isConnected)
        assertEquals(HomeTopSlot.Offline(connecting = false, peerCount = 1), state.topSlot)
        assertFalse(state.showNowPlaying)
    }

    @Test fun activeTransferTakesPriorityOverIncomingLink() {
        val transfer = FileTransferState(
            id = "t1",
            name = "Quarterly-report.pdf",
            status = "Sending",
            progressPercent = 45,
            active = true,
            startedAtMillis = 100L,
        )
        val state = computeHomeState(
            pairingState = PairingState.Connected("mac-1", "MacBook Air"),
            nearbyPeerCount = 0,
            fileTransfers = mapOf("t1" to transfer),
            incomingLink = "https://example.com",
            media = MacMediaState(),
            mediaEnabled = true,
        )
        assertTrue(state.isConnected)
        assertTrue(state.topSlot is HomeTopSlot.Transferring)
        val slot = state.topSlot as HomeTopSlot.Transferring
        assertEquals("Quarterly-report.pdf", slot.activeTransfer.name)
        assertEquals(1, slot.totalCount)
    }

    @Test fun incomingLinkAppearsWhenNoTransferIsActive() {
        val doneTransfer = FileTransferState(
            id = "t1",
            name = "old.pdf",
            status = "Sent",
            progressPercent = 100,
            active = false,
            startedAtMillis = 100L,
        )
        val state = computeHomeState(
            pairingState = PairingState.Connected("mac-1", "MacBook Air"),
            nearbyPeerCount = 0,
            fileTransfers = mapOf("t1" to doneTransfer),
            incomingLink = "https://bridgey.ai/docs",
            media = MacMediaState(),
            mediaEnabled = true,
        )
        assertEquals(HomeTopSlot.IncomingLink("https://bridgey.ai/docs"), state.topSlot)
    }

    @Test fun nowPlayingAppearsWhenSpotifyIsActiveAndDisappearsWhenStopped() {
        val playing = computeHomeState(
            pairingState = PairingState.Connected("mac-1", "MacBook Air"),
            nearbyPeerCount = 0,
            fileTransfers = emptyMap(),
            incomingLink = null,
            media = MacMediaState(player = "Spotify", title = "Midnight Drive", artist = "Neon Harbor", playing = true, detail = ""),
            mediaEnabled = true,
        )
        assertTrue(playing.showNowPlaying)
        assertEquals(HomeTopSlot.Idle, playing.topSlot)

        val stopped = computeHomeState(
            pairingState = PairingState.Connected("mac-1", "MacBook Air"),
            nearbyPeerCount = 0,
            fileTransfers = emptyMap(),
            incomingLink = null,
            media = MacMediaState(player = "Off", title = "", playing = false),
            mediaEnabled = true,
        )
        assertFalse(stopped.showNowPlaying)
    }
}

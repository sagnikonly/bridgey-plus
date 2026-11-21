package dev.bridgey.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bridgey.android.ActivityEvent
import dev.bridgey.android.ActivityEventKind
import dev.bridgey.android.FileTransferState
import dev.bridgey.android.PairingCoordinator
import dev.bridgey.android.R
import java.text.DateFormat
import java.util.Date

@Composable
fun ActivityScreen(
    fileTransfers: Map<String, FileTransferState>,
    events: List<ActivityEvent>,
    pairing: PairingCoordinator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activeTransfers = fileTransfers.values.filter { it.active }.sortedByDescending { it.startedAtMillis }
    val finishedTransfers = fileTransfers.values.filter { !it.active }.sortedByDescending { it.startedAtMillis }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Activity",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                if (finishedTransfers.isNotEmpty() || events.isNotEmpty()) {
                    TextButton(onClick = {
                        pairing.clearTransferHistory()
                        pairing.clearActivityEvents()
                    }) { Text("Clear") }
                }
            }
        }

        if (activeTransfers.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "In progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeTransfers.size > 1) {
                        TextButton(onClick = { pairing.cancelFileTransfer() }) {
                            Text(stringResource(R.string.transfers_cancel_all))
                        }
                    }
                }
            }
            itemsIndexed(activeTransfers, key = { _, t -> "active.${t.id}" }) { index, transfer ->
                SegmentedCard(index = index, count = activeTransfers.size) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExpressiveIconWell(containerColor = MaterialTheme.colorScheme.primaryContainer, size = 40.dp) {
                                Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(transfer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(transfer.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            TextButton(onClick = { pairing.cancelFileTransfer(transfer.id) }) { Text("Cancel") }
                        }
                        val pct = transfer.progressPercent
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                strokeCap = StrokeCap.Round,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }

        if (finishedTransfers.isNotEmpty()) {
            item {
                Text(
                    "Files",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(finishedTransfers, key = { _, t -> "done.${t.id}" }) { index, transfer ->
                SegmentedCard(index = index, count = finishedTransfers.size) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExpressiveIconWell(size = 40.dp) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(transfer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(transfer.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (transfer.retryable) {
                            TextButton(onClick = { pairing.retryFileTransfer(transfer.id) }) { Text("Retry") }
                        }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item {
                Text(
                    "Clipboard & events",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(events, key = { _, e -> e.id }) { index, event ->
                val icon = when (event.kind) {
                    ActivityEventKind.CLIPBOARD_SENT, ActivityEventKind.CLIPBOARD_RECEIVED -> Icons.Rounded.ContentCopy
                    ActivityEventKind.LINK -> Icons.Rounded.Link
                    ActivityEventKind.PING -> Icons.Rounded.Sensors
                }
                SegmentedCard(
                    index = index,
                    count = events.size,
                    onClick = event.payloadText?.let { text ->
                        {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("Bridgey", text))
                            pairing.sendText(text)
                            Toast.makeText(context, "Copied & sent to Mac", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExpressiveIconWell(size = 40.dp) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                event.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.timestampMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (activeTransfers.isEmpty() && finishedTransfers.isEmpty() && events.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExpressiveIconWell(size = 56.dp, shape = CircleShape) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Text("No activity yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Files you send or receive, copied clipboard items, and links will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

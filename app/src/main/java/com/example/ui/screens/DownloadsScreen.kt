package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DownloadTaskEntity
import com.example.domain.model.DownloadStatus
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatSpeed
import com.example.ui.theme.LocalBrandColors

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTaskEntity>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_vault),
                    contentDescription = "No active downloads",
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.downloads_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.downloads_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .testTag("downloads_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPause = { onPause(task.id) },
                        onResume = { onResume(task.id) },
                        onCancel = { onCancel(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val brandColors = LocalBrandColors.current
    val status = task.downloadStatus
    val percent = if (task.totalBytes > 0L) {
        ((task.downloadedBytes * 100L) / task.totalBytes).coerceIn(0L, 100L)
    } else 0L

    GlassSurfaceCard(modifier = Modifier.testTag("download_task_card_${task.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPillBadge(
                text = when (status) {
                    DownloadStatus.QUEUED -> "QUEUED"
                    DownloadStatus.DOWNLOADING -> "${percent}%"
                    DownloadStatus.PAUSED -> "PAUSED"
                    DownloadStatus.FAILED -> "ERROR"
                    DownloadStatus.COMPLETED -> "DONE"
                    DownloadStatus.CANCELLED -> "CANCELLED"
                },
                containerColor = when (status) {
                    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    DownloadStatus.PAUSED -> brandColors.warning.copy(alpha = 0.18f)
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when (status) {
                    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                    DownloadStatus.PAUSED -> brandColors.warning
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = task.fileName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (task.totalBytes > 0L) {
            LinearProgressIndicator(
                progress = { task.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (status == DownloadStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val totalStr = if (task.totalBytes > 0L) formatFileSize(task.totalBytes) else "Streaming"
            Text(
                text = "${formatFileSize(task.downloadedBytes)} / $totalStr • ${task.qualityLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status == DownloadStatus.DOWNLOADING && task.speedBytesPerSec > 0L) {
                Text(
                    text = formatSpeed(task.speedBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!task.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (!task.supportsRange && status == DownloadStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Server does not advertise HTTP Range support; pause restarts from 0%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    FilledTonalButton(
                        onClick = onPause,
                        enabled = task.supportsRange,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("pause_download_button_${task.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pause))
                    }
                }
                DownloadStatus.PAUSED -> {
                    FilledTonalButton(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("resume_download_button_${task.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_resume))
                    }
                }
                DownloadStatus.FAILED -> {
                    FilledTonalButton(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("retry_download_button_${task.id}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
                else -> Unit
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_download_button_${task.id}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

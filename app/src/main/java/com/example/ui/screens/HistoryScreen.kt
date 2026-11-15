package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadHistoryEntity
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.LocalBrandColors
import com.example.ui.viewmodel.HistoryFilter

@Composable
fun HistoryScreen(
    items: List<DownloadHistoryEntity>,
    totalCount: Int,
    searchQuery: String,
    selectedFilter: HistoryFilter,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (HistoryFilter) -> Unit,
    onOpenItem: (DownloadHistoryEntity) -> Unit,
    onShareItem: (DownloadHistoryEntity) -> Unit,
    onDeleteItem: (DownloadHistoryEntity, Boolean) -> Unit,
    onImportCreatorExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteItem by remember { mutableStateOf<DownloadHistoryEntity?>(null) }
    var deleteDeviceFile by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (totalCount == 0) {
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
                    contentDescription = "Empty download history",
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onImportCreatorExport,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_empty_import_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_import_exported))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .testTag("history_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = onImportCreatorExport,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_search_input"),
                            placeholder = { Text("Search downloads by title or file...") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onSearchChanged("") },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HistoryFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { onFilterChanged(filter) },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (items.isEmpty()) {
                    item {
                        GlassSurfaceCard {
                            Text(
                                text = "No downloaded files match \"$searchQuery\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(items, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onOpen = { onOpenItem(item) },
                            onShare = { onShareItem(item) },
                            onDelete = {
                                deleteDeviceFile = true
                                pendingDeleteItem = item
                            }
                        )
                    }
                }
            }
        }
    }

    val toDelete = pendingDeleteItem
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            modifier = Modifier.testTag("delete_confirm_dialog"),
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.delete_confirm_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = deleteDeviceFile,
                            onCheckedChange = { deleteDeviceFile = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Also delete saved file from device storage",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(toDelete, deleteDeviceFile)
                        pendingDeleteItem = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteItem = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: DownloadHistoryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val brandColors = LocalBrandColors.current
    GlassSurfaceCard(modifier = Modifier.testTag("history_item_card_${item.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brandColors.heroGlowBrush),
                contentAlignment = Alignment.Center
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = "Thumbnail for ${item.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.VideoFile,
                        contentDescription = "Video file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPillBadge(
                        text = item.qualityLabel,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatFileSize(item.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatTimestamp(item.downloadedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("history_open_button_${item.id}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_open))
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("history_share_button_${item.id}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_share))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_delete_button_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${item.title}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

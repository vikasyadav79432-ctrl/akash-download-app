package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AnalyzedVideo
import com.example.domain.model.UrlKind
import com.example.domain.model.VideoQualityOption
import com.example.ui.components.GlassSurfaceCard
import com.example.ui.components.GradientActionButton
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalBrandColors
import com.example.ui.viewmodel.AnalyzeUiState

private const val OFFICIAL_CC_SAMPLE_URL =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

@Composable
fun AnalyzeScreen(
    urlInput: String,
    detectedUrlKind: UrlKind?,
    analyzeState: AnalyzeUiState,
    selectedQuality: VideoQualityOption?,
    hasYouTubeApiKey: Boolean,
    onUrlChanged: (String) -> Unit,
    onPasteClick: () -> Unit,
    onAnalyzeClick: (String?) -> Unit,
    onClearClick: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onStartDownload: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onImportExportedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandColors = LocalBrandColors.current
    var showAttachCreatorUrlDialog by remember { mutableStateOf(false) }
    var creatorUrlDraft by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Premium Header Banner
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, brandColors.cardBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(brandColors.heroGlowBrush)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_icon_fg),
                            contentDescription = "Video Downloader Pro logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.dashboard_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPillBadge(
                            text = if (hasYouTubeApiKey) "API v3" else "oEmbed",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Filled.VerifiedUser
                        )
                    }
                }
            }

            // URL Input & Action Card
            GlassSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.url_input_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (detectedUrlKind != null) {
                        UrlKindBadge(urlKind = detectedUrlKind)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("url_input_field"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.url_input_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "URL icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = onClearClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_url_inline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear URL"
                                )
                            }
                        }
                    },
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.labelMedium,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onAnalyzeClick(null) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Paste & Clear Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPasteClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("paste_link_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_paste))
                    }

                    OutlinedButton(
                        onClick = onClearClick,
                        enabled = urlInput.isNotEmpty() || analyzeState !is AnalyzeUiState.Idle,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("clear_url_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_clear))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GradientActionButton(
                    text = if (analyzeState is AnalyzeUiState.Loading) "Analyzing Media..." else stringResource(R.string.action_analyze),
                    icon = Icons.Filled.Search,
                    enabled = analyzeState !is AnalyzeUiState.Loading,
                    onClick = { onAnalyzeClick(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analyze_video_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Helper action chips for quick authorized media & creator import
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onAnalyzeClick(OFFICIAL_CC_SAMPLE_URL) },
                        label = { Text(stringResource(R.string.action_try_cc_sample)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("try_cc_sample_button")
                    )

                    AssistChip(
                        onClick = onImportExportedClick,
                        label = { Text(stringResource(R.string.action_import_exported)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("quick_import_creator_button")
                    )
                }
            }

            // Loading State
            AnimatedVisibility(
                visible = analyzeState is AnalyzeUiState.Loading,
                enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                GlassSurfaceCard(modifier = Modifier.testTag("analyze_loading_card")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Column {
                            Text(
                                text = "Inspecting Video Metadata",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Querying official API & verifying authorized stream headers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error State
            AnimatedVisibility(
                visible = analyzeState is AnalyzeUiState.Error,
                enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                val errMsg = (analyzeState as? AnalyzeUiState.Error)?.message.orEmpty()
                GlassSurfaceCard(
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.testTag("analyze_error_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Could Not Analyze Link",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Analyzed Video Result State
            AnimatedVisibility(
                visible = analyzeState is AnalyzeUiState.Success,
                enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                val video = (analyzeState as? AnalyzeUiState.Success)?.video
                if (video != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AnalyzedVideoMetadataCard(video = video)

                        if (video.availableQualities.isNotEmpty()) {
                            QualitySelectionCard(
                                qualities = video.availableQualities,
                                selectedQuality = selectedQuality,
                                onSelectQuality = onSelectQuality,
                                onStartDownload = onStartDownload
                            )
                        }

                        if (video.isYouTube) {
                            YouTubeComplianceAndCreatorCard(
                                video = video,
                                onOpenYouTube = { onOpenExternalUrl(video.officialWatchUrl) },
                                onOpenStudio = { onOpenExternalUrl("https://studio.youtube.com/") },
                                onOpenTakeout = {
                                    onOpenExternalUrl("https://takeout.google.com/settings/takeout/custom/youtube")
                                },
                                onImportExported = onImportExportedClick,
                                onAttachCreatorUrl = {
                                    creatorUrlDraft = ""
                                    showAttachCreatorUrlDialog = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showAttachCreatorUrlDialog) {
        AlertDialog(
            onDismissRequest = { showAttachCreatorUrlDialog = false },
            title = { Text("Attach Authorized Creator URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a direct HTTPS download link to your own creator-hosted MP4/WebM export or authorized multi-quality JSON manifest.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = creatorUrlDraft,
                        onValueChange = { creatorUrlDraft = it },
                        placeholder = { Text("https://cdn.yoursite.com/video_1080p.mp4") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("creator_url_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAttachCreatorUrlDialog = false
                        if (creatorUrlDraft.isNotBlank()) {
                            onAnalyzeClick(creatorUrlDraft.trim())
                        }
                    }
                ) {
                    Text("Probe URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttachCreatorUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UrlKindBadge(urlKind: UrlKind) {
    val brandColors = LocalBrandColors.current
    val (label, bg, fg) = when (urlKind) {
        UrlKind.YOUTUBE_SHORT -> Triple(
            "YouTube Short",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.secondary
        )
        UrlKind.YOUTUBE_VIDEO -> Triple(
            "YouTube Video",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary
        )
        UrlKind.AUTHORIZED_DIRECT_VIDEO -> Triple(
            "Direct Media URL",
            brandColors.success.copy(alpha = 0.18f),
            brandColors.success
        )
        UrlKind.INVALID -> Triple(
            "Check URL",
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.error
        )
    }
    StatusPillBadge(text = label, containerColor = bg, contentColor = fg)
}

@Composable
private fun AnalyzedVideoMetadataCard(video: AnalyzedVideo) {
    val brandColors = LocalBrandColors.current
    GlassSurfaceCard(modifier = Modifier.testTag("analyzed_video_card")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "Thumbnail for ${video.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brandColors.heroGlowBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoFile,
                        contentDescription = "Authorized video preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Type Badge top-left
            StatusPillBadge(
                text = when {
                    video.isShort -> "SHORTS"
                    video.isYouTube -> "VIDEO"
                    else -> "AUTHORIZED"
                },
                containerColor = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                icon = Icons.Filled.Movie,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )

            // Duration Badge bottom-right
            if (!video.durationFormatted.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = video.durationFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = video.channelName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPillBadge(
                text = video.metadataSource,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Filled.Info
            )
            if (!video.licenseLabel.isNullOrBlank()) {
                StatusPillBadge(
                    text = video.licenseLabel,
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun QualitySelectionCard(
    qualities: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption?,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onStartDownload: () -> Unit
) {
    val brandColors = LocalBrandColors.current
    GlassSurfaceCard(
        borderColor = brandColors.success.copy(alpha = 0.45f),
        modifier = Modifier.testTag("quality_selection_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.HighQuality,
                contentDescription = null,
                tint = brandColors.success
            )
            Text(
                text = stringResource(R.string.quality_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            qualities.forEach { option ->
                val isSelected = selectedQuality?.id == option.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectQuality(option) }
                        .testTag("quality_option_${option.label.replace(' ', '_')}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectQuality(option) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildString {
                                    append(option.format)
                                    append(" • ")
                                    append(formatFileSize(option.sizeBytes))
                                    if (option.supportsRange) append(" • Pause/Resume")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected quality",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GradientActionButton(
            text = stringResource(R.string.action_start_download),
            icon = Icons.Filled.CloudDownload,
            enabled = selectedQuality != null,
            onClick = onStartDownload,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_download_button")
        )
    }
}

@Composable
private fun YouTubeComplianceAndCreatorCard(
    video: AnalyzedVideo,
    onOpenYouTube: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenTakeout: () -> Unit,
    onImportExported: () -> Unit,
    onAttachCreatorUrl: () -> Unit
) {
    val brandColors = LocalBrandColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Official Viewing & Compliance Card
        GlassSurfaceCard(
            borderColor = brandColors.warning.copy(alpha = 0.5f),
            modifier = Modifier.testTag("youtube_compliance_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(brandColors.warning.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = brandColors.warning,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.youtube_official_notice_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = video.restrictionReason ?: stringResource(R.string.youtube_official_notice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            GradientActionButton(
                text = stringResource(R.string.action_open_youtube),
                icon = Icons.Filled.PlayCircleFilled,
                onClick = onOpenYouTube,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_youtube_button")
            )
        }

        // Creator-Owned Export Workflow Card
        GlassSurfaceCard(modifier = Modifier.testTag("creator_export_card")) {
            Text(
                text = stringResource(R.string.creator_export_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.creator_export_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenStudio,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("youtube_studio_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_youtube_studio), maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenTakeout,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("google_takeout_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_google_takeout), maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onAttachCreatorUrl,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("attach_creator_url_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Creator URL", maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onImportExported,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("import_exported_mp4_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import MP4", maxLines = 1)
                }
            }
        }
    }
}

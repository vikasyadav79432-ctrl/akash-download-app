package com.example

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.DownloadHistoryEntity
import com.example.ui.screens.AnalyzeScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.VideoDownloaderProTheme
import com.example.ui.viewmodel.MainViewModel

const val ROUTE_ANALYZE = "analyze"
const val ROUTE_DOWNLOADS = "downloads"
const val ROUTE_HISTORY = "history"
const val ROUTE_SETTINGS = "settings"

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(application)
            )
            VideoDownloaderProApp(viewModel = viewModel)
        }
    }
}

@Composable
fun VideoDownloaderProApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val detectedUrlKind by viewModel.detectedUrlKind.collectAsStateWithLifecycle()
    val analyzeState by viewModel.analyzeState.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val filteredHistory by viewModel.filteredHistory.collectAsStateWithLifecycle()
    val totalHistoryCount by viewModel.totalHistoryCount.collectAsStateWithLifecycle()
    val historySearch by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // SAF Custom Folder Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            val displayName = DocumentsContract.getTreeDocumentId(treeUri)
                .substringAfter(':')
                .ifBlank { treeUri.lastPathSegment ?: "Selected Folder" }
            viewModel.setCustomStorageFolder(treeUri.toString(), displayName)
        }
    }

    // Creator Export MP4 Picker (zero-permission SAF OpenDocument)
    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCreatorExport(uri)
        }
    }

    // Notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with download regardless of notification preference
        viewModel.startSelectedDownload {
            navController.navigate(ROUTE_DOWNLOADS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Legacy Write Storage permission launcher (Android 7-9 only)
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startSelectedDownload {
                navController.navigate(ROUTE_DOWNLOADS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else {
            viewModel.emitMessage("Storage permission is required on Android 9 and below to save to Downloads.")
        }
    }

    val startAuthorizedDownloadWithPermissions: () -> Unit = {
        val navigateToDownloads: () -> Unit = {
            navController.navigate(ROUTE_DOWNLOADS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val hasWrite = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasWrite) {
                legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.startSelectedDownload(navigateToDownloads)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.startSelectedDownload(navigateToDownloads)
            }
        } else {
            viewModel.startSelectedDownload(navigateToDownloads)
        }
    }

    val tabs = remember {
        listOf(
            NavTab(ROUTE_ANALYZE, R.string.nav_analyze, Icons.Filled.Search, Icons.Outlined.Search),
            NavTab(ROUTE_DOWNLOADS, R.string.nav_downloads, Icons.Filled.Download, Icons.Outlined.Download),
            NavTab(ROUTE_HISTORY, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
            NavTab(ROUTE_SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    VideoDownloaderProTheme(themeMode = settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected,
                                            activeCount = activeDownloads.size
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected,
                                            activeCount = activeDownloads.size
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_rail_${tab.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_ANALYZE
                        ) {
                            composable(ROUTE_ANALYZE) {
                                AnalyzeScreen(
                                    urlInput = urlInput,
                                    detectedUrlKind = detectedUrlKind,
                                    analyzeState = analyzeState,
                                    selectedQuality = selectedQuality,
                                    hasYouTubeApiKey = viewModel.hasYouTubeApiKey,
                                    onUrlChanged = viewModel::onUrlInputChanged,
                                    onPasteClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val pasted = clipboard?.primaryClip
                                            ?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                        viewModel.pasteUrl(pasted)
                                    },
                                    onAnalyzeClick = viewModel::analyzeUrl,
                                    onClearClick = viewModel::clearInput,
                                    onSelectQuality = viewModel::selectQuality,
                                    onStartDownload = startAuthorizedDownloadWithPermissions,
                                    onOpenExternalUrl = { url ->
                                        openExternalUrl(context, url) { msg -> viewModel.emitMessage(msg) }
                                    },
                                    onImportExportedClick = {
                                        importVideoLauncher.launch(arrayOf("video/*"))
                                    }
                                )
                            }

                            composable(ROUTE_DOWNLOADS) {
                                DownloadsScreen(
                                    tasks = activeDownloads,
                                    onPause = viewModel::pauseDownload,
                                    onResume = viewModel::resumeDownload,
                                    onCancel = viewModel::cancelDownload
                                )
                            }

                            composable(ROUTE_HISTORY) {
                                HistoryScreen(
                                    items = filteredHistory,
                                    totalCount = totalHistoryCount,
                                    searchQuery = historySearch,
                                    selectedFilter = historyFilter,
                                    onSearchChanged = viewModel::onHistorySearchChanged,
                                    onFilterChanged = viewModel::onHistoryFilterChanged,
                                    onOpenItem = { item ->
                                        openDownloadedVideo(context, item) { msg -> viewModel.emitMessage(msg) }
                                    },
                                    onShareItem = { item ->
                                        shareDownloadedVideo(context, item) { msg -> viewModel.emitMessage(msg) }
                                    },
                                    onDeleteItem = viewModel::deleteHistoryItem,
                                    onImportCreatorExport = {
                                        importVideoLauncher.launch(arrayOf("video/*"))
                                    }
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    settings = settings,
                                    hasYouTubeApiKey = viewModel.hasYouTubeApiKey,
                                    onThemeModeChanged = viewModel::setThemeMode,
                                    onSelectDefaultStorage = viewModel::setDefaultStorageLocation,
                                    onPickCustomFolder = { folderPickerLauncher.launch(null) },
                                    onWifiOnlyChanged = viewModel::setWifiOnlyDownloads
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: NavTab,
    selected: Boolean,
    activeCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == ROUTE_DOWNLOADS && activeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(activeCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
    }
}

private fun openExternalUrl(context: Context, url: String, onError: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        onError("No browser or YouTube app available to open this link.")
    }
}

private fun openDownloadedVideo(
    context: Context,
    item: DownloadHistoryEntity,
    onError: (String) -> Unit
) {
    val uri = Uri.parse(item.contentUri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Play Video"))
    }.onFailure {
        onError("No compatible video player found for this file.")
    }
}

private fun shareDownloadedVideo(
    context: Context,
    item: DownloadHistoryEntity,
    onError: (String) -> Unit
) {
    val uri = Uri.parse(item.contentUri)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share ${item.title}"))
    }.onFailure {
        onError("Unable to open share sheet for this video.")
    }
}

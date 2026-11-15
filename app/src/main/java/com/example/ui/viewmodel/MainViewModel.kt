package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.PreferencesRepository
import com.example.data.repository.DownloadEngineRepository
import com.example.data.repository.MediaAnalyzerRepository
import com.example.domain.model.AnalyzedVideo
import com.example.domain.model.AppSettings
import com.example.domain.model.ParsedUrlResult
import com.example.domain.model.ThemeMode
import com.example.domain.model.UrlKind
import com.example.domain.model.VideoQualityOption
import com.example.domain.parser.VideoUrlParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalyzeUiState {
    data object Idle : AnalyzeUiState
    data object Loading : AnalyzeUiState
    data class Success(val video: AnalyzedVideo) : AnalyzeUiState
    data class Error(val message: String) : AnalyzeUiState
}

enum class HistoryFilter(val label: String) {
    ALL("All"),
    SHORTS("Shorts"),
    HD_PLUS("HD / 4K")
}

class MainViewModel(
    private val analyzerRepository: MediaAnalyzerRepository,
    private val downloadEngineRepository: DownloadEngineRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _detectedUrlKind = MutableStateFlow<UrlKind?>(null)
    val detectedUrlKind: StateFlow<UrlKind?> = _detectedUrlKind.asStateFlow()

    private val _analyzeState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val analyzeState: StateFlow<AnalyzeUiState> = _analyzeState.asStateFlow()

    private val _selectedQuality = MutableStateFlow<VideoQualityOption?>(null)
    val selectedQuality: StateFlow<VideoQualityOption?> = _selectedQuality.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val hasYouTubeApiKey: Boolean = analyzerRepository.hasConfiguredYouTubeApiKey()

    val settings: StateFlow<AppSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val activeDownloads: StateFlow<List<DownloadTaskEntity>> = downloadEngineRepository.activeTasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<DownloadHistoryEntity>> = combine(
        downloadEngineRepository.historyFlow,
        _historySearchQuery,
        _historyFilter
    ) { items, query, filter ->
        items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.channelName.contains(query, ignoreCase = true) ||
                item.fileName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SHORTS -> item.isShort
                HistoryFilter.HD_PLUS -> item.qualityLabel.contains("720") ||
                    item.qualityLabel.contains("1080") ||
                    item.qualityLabel.contains("1440") ||
                    item.qualityLabel.contains("2160") ||
                    item.qualityLabel.contains("HD") ||
                    item.qualityLabel.contains("4K")
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalHistoryCount: StateFlow<Int> = downloadEngineRepository.historyFlow
        .combine(MutableStateFlow(Unit)) { list, _ -> list.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun onUrlInputChanged(newValue: String) {
        _urlInput.value = newValue
        _detectedUrlKind.value = if (newValue.isBlank()) {
            null
        } else {
            when (val parsed = VideoUrlParser.parse(newValue)) {
                is ParsedUrlResult.ValidYouTube ->
                    if (parsed.isShort) UrlKind.YOUTUBE_SHORT else UrlKind.YOUTUBE_VIDEO
                is ParsedUrlResult.ValidDirectMedia -> UrlKind.AUTHORIZED_DIRECT_VIDEO
                is ParsedUrlResult.Invalid -> UrlKind.INVALID
            }
        }
        if (_analyzeState.value is AnalyzeUiState.Error) {
            _analyzeState.value = AnalyzeUiState.Idle
        }
    }

    fun clearInput() {
        _urlInput.value = ""
        _detectedUrlKind.value = null
        _analyzeState.value = AnalyzeUiState.Idle
        _selectedQuality.value = null
    }

    fun pasteUrl(pastedText: String?) {
        if (pastedText.isNullOrBlank()) {
            _events.tryEmit("Clipboard is empty. Copy a video URL first.")
            return
        }
        onUrlInputChanged(pastedText.trim())
        _events.tryEmit("Link pasted from clipboard")
    }

    fun analyzeUrl(customUrl: String? = null) {
        val target = (customUrl ?: _urlInput.value).trim()
        if (customUrl != null) {
            onUrlInputChanged(target)
        }
        if (target.isEmpty()) {
            _analyzeState.value = AnalyzeUiState.Error(
                "Please paste a YouTube video, Shorts, or authorized media URL first."
            )
            return
        }

        viewModelScope.launch {
            _analyzeState.value = AnalyzeUiState.Loading
            _selectedQuality.value = null
            val result = analyzerRepository.analyzeUrl(target)
            result.fold(
                onSuccess = { video ->
                    _analyzeState.value = AnalyzeUiState.Success(video)
                    _selectedQuality.value = video.availableQualities.lastOrNull()
                },
                onFailure = { error ->
                    _analyzeState.value = AnalyzeUiState.Error(
                        error.message ?: "Unable to analyze this URL."
                    )
                }
            )
        }
    }

    fun selectQuality(option: VideoQualityOption) {
        _selectedQuality.value = option
    }

    fun startSelectedDownload(onStarted: () -> Unit = {}) {
        val successState = _analyzeState.value as? AnalyzeUiState.Success ?: return
        val quality = _selectedQuality.value ?: run {
            _events.tryEmit("Select an available authorized quality first.")
            return
        }
        viewModelScope.launch {
            val result = downloadEngineRepository.startDownload(
                video = successState.video,
                quality = quality,
                settings = settings.value
            )
            result.fold(
                onSuccess = {
                    _events.tryEmit("Download started (${quality.label})")
                    onStarted()
                },
                onFailure = { err ->
                    _events.tryEmit(err.message ?: "Could not start download.")
                }
            )
        }
    }

    fun pauseDownload(taskId: String) {
        downloadEngineRepository.pauseDownload(taskId)
    }

    fun resumeDownload(taskId: String) {
        viewModelScope.launch {
            val result = downloadEngineRepository.resumeDownload(taskId, settings.value)
            result.onFailure { err ->
                _events.tryEmit(err.message ?: "Unable to resume download.")
            }
        }
    }

    fun cancelDownload(taskId: String) {
        downloadEngineRepository.cancelDownload(taskId)
        _events.tryEmit("Download cancelled")
    }

    fun importCreatorExport(uri: Uri) {
        viewModelScope.launch {
            val result = downloadEngineRepository.importCreatorExportedVideo(uri)
            result.fold(
                onSuccess = { item ->
                    _events.tryEmit("Imported \"${item.title}\" to History")
                },
                onFailure = { err ->
                    _events.tryEmit(err.message ?: "Failed to import creator video")
                }
            )
        }
    }

    fun deleteHistoryItem(item: DownloadHistoryEntity, deleteFromStorage: Boolean) {
        viewModelScope.launch {
            downloadEngineRepository.deleteHistoryItem(item, deleteFromStorage)
            _events.tryEmit("Deleted \"${item.title}\"")
        }
    }

    fun onHistorySearchChanged(query: String) {
        _historySearchQuery.value = query
    }

    fun onHistoryFilterChanged(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultStorageLocation() {
        viewModelScope.launch {
            preferencesRepository.setDefaultMediaStoreStorage()
            _events.tryEmit("Download folder reset to Android Downloads")
        }
    }

    fun setCustomStorageFolder(uri: String, displayName: String) {
        viewModelScope.launch {
            preferencesRepository.setCustomFolder(uri, displayName)
            _events.tryEmit("Custom folder set: $displayName")
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setWifiOnlyDownloads(enabled) }
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getInstance(application)
            val dao = db.downloadDao()
            return MainViewModel(
                analyzerRepository = MediaAnalyzerRepository(),
                downloadEngineRepository = DownloadEngineRepository(application, dao),
                preferencesRepository = PreferencesRepository(application)
            ) as T
        }
    }
}

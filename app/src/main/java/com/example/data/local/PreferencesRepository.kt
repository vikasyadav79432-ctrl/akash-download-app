package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppSettings
import com.example.domain.model.StorageLocationMode
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vdp_settings")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val CUSTOM_FOLDER_URI = stringPreferencesKey("custom_folder_uri")
        val CUSTOM_FOLDER_NAME = stringPreferencesKey("custom_folder_name")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK

        val storage = prefs[Keys.STORAGE_MODE]
            ?.let { runCatching { StorageLocationMode.valueOf(it) }.getOrNull() }
            ?: StorageLocationMode.MEDIA_STORE_DOWNLOADS

        AppSettings(
            themeMode = theme,
            storageLocationMode = storage,
            customFolderUri = prefs[Keys.CUSTOM_FOLDER_URI],
            customFolderName = prefs[Keys.CUSTOM_FOLDER_NAME],
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultMediaStoreStorage() {
        context.dataStore.edit {
            it[Keys.STORAGE_MODE] = StorageLocationMode.MEDIA_STORE_DOWNLOADS.name
        }
    }

    suspend fun setCustomFolder(uri: String, displayName: String) {
        context.dataStore.edit {
            it[Keys.STORAGE_MODE] = StorageLocationMode.CUSTOM_SAF_FOLDER.name
            it[Keys.CUSTOM_FOLDER_URI] = uri
            it[Keys.CUSTOM_FOLDER_NAME] = displayName
        }
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }
}

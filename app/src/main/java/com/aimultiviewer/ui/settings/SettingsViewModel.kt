package com.aimultiviewer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aimultiviewer.App
import com.aimultiviewer.data.SettingsStore

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store: SettingsStore = (app as App).settingsStore

    fun snapshot() = SettingsSnapshot(
        cloudEnabled = store.cloudEnabled,
        baseUrl = store.baseUrl,
        apiKey = store.apiKey,
        model = store.model,
        wikiAutoExport = store.wikiAutoExport
    )

    fun save(snapshot: SettingsSnapshot) {
        store.cloudEnabled = snapshot.cloudEnabled
        store.baseUrl = snapshot.baseUrl.ifBlank { SettingsStore.DEFAULT_BASE_URL }
        store.apiKey = snapshot.apiKey.trim()
        store.model = snapshot.model.ifBlank { SettingsStore.DEFAULT_MODEL }
        store.wikiAutoExport = snapshot.wikiAutoExport
    }
}

data class SettingsSnapshot(
    val cloudEnabled: Boolean,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val wikiAutoExport: Boolean = true
)

package com.aimultiviewer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aimultiviewer.App
import com.aimultiviewer.data.AiProvider
import com.aimultiviewer.data.SettingsStore

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store: SettingsStore = (app as App).settingsStore

    fun snapshot() = SettingsSnapshot(
        cloudEnabled = store.cloudEnabled,
        provider = store.aiProvider,
        customBaseUrl = store.customBaseUrl,
        apiKeys = AiProvider.entries.associateWith { store.apiKeyFor(it) },
        models = AiProvider.entries.associateWith { store.modelFor(it) },
        wikiAutoExport = store.wikiAutoExport
    )

    fun save(snapshot: SettingsSnapshot) {
        store.cloudEnabled = snapshot.cloudEnabled
        store.aiProvider = snapshot.provider
        store.customBaseUrl = snapshot.customBaseUrl.ifBlank { SettingsStore.DEFAULT_BASE_URL }
        snapshot.apiKeys.forEach { (p, key) -> store.setApiKeyFor(p, key.trim()) }
        snapshot.models.forEach { (p, model) ->
            store.setModelFor(p, model.ifBlank { p.defaultModel })
        }
        store.wikiAutoExport = snapshot.wikiAutoExport
    }
}

data class SettingsSnapshot(
    val cloudEnabled: Boolean,
    val provider: AiProvider,
    val customBaseUrl: String,
    val apiKeys: Map<AiProvider, String>,
    val models: Map<AiProvider, String>,
    val wikiAutoExport: Boolean = true
)

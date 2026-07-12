package com.aimultiviewer

import android.app.Application
import com.aimultiviewer.data.DocumentStore
import com.aimultiviewer.data.SettingsStore
import com.aimultiviewer.data.ai.AiRepository
import com.aimultiviewer.data.ai.LlmClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    lateinit var documentStore: DocumentStore
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var aiRepository: AiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        documentStore = DocumentStore(this)
        settingsStore = SettingsStore(this)
        aiRepository = AiRepository(settingsStore, LlmClient(settingsStore))
    }
}

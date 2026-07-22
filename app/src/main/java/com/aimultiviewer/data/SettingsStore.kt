package com.aimultiviewer.data

import android.content.Context

/**
 * AI 제공자 프리셋. 모두 OpenAI 호환 Chat Completions 엔드포인트를 제공하므로
 * base URL과 기본 모델만 다르다. (Claude는 Anthropic의 OpenAI SDK 호환 레이어 사용)
 */
enum class AiProvider(
    val label: String,
    val presetBaseUrl: String,
    val defaultModel: String,
    val keyHint: String
) {
    OPENAI("ChatGPT", "https://api.openai.com/v1", "gpt-4o-mini", "sk-..."),
    CLAUDE("Claude", "https://api.anthropic.com/v1", "claude-sonnet-4-20250514", "sk-ant-..."),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", "AIza..."),
    GROK("Grok", "https://api.x.ai/v1", "grok-3", "xai-..."),
    CUSTOM("직접 입력", "", "", "");

    companion object {
        fun fromName(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: OPENAI
    }
}

/**
 * 앱 설정 저장(SharedPreferences).
 * API 키·모델은 제공자별로 따로 저장되어 제공자를 전환해도 유지된다.
 * 보안 강화 시 EncryptedSharedPreferences로 교체 권장.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var cloudEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var aiProvider: AiProvider
        get() = AiProvider.fromName(prefs.getString(KEY_PROVIDER, null))
        set(v) = prefs.edit().putString(KEY_PROVIDER, v.name).apply()

    fun apiKeyFor(p: AiProvider): String =
        prefs.getString("${KEY_API_KEY}_${p.name}", null)
            ?: (if (p == AiProvider.OPENAI) prefs.getString(KEY_API_KEY, "") else "") ?: ""

    fun setApiKeyFor(p: AiProvider, v: String) =
        prefs.edit().putString("${KEY_API_KEY}_${p.name}", v).apply()

    fun modelFor(p: AiProvider): String =
        prefs.getString("${KEY_MODEL}_${p.name}", null)?.takeIf { it.isNotBlank() }
            ?: (if (p == AiProvider.OPENAI) prefs.getString(KEY_MODEL, null) else null)
            ?: p.defaultModel

    fun setModelFor(p: AiProvider, v: String) =
        prefs.edit().putString("${KEY_MODEL}_${p.name}", v).apply()

    /** CUSTOM 제공자용 Base URL */
    var customBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(v) = prefs.edit().putString(KEY_BASE_URL, v).apply()

    // ---- LlmClient가 사용하는 유효 설정 (현재 선택된 제공자 기준) ----
    val baseUrl: String
        get() = aiProvider.let { if (it == AiProvider.CUSTOM) customBaseUrl else it.presetBaseUrl }

    val apiKey: String get() = apiKeyFor(aiProvider)

    val model: String get() = modelFor(aiProvider)

    /** 열람 문서를 llm-wiki 마크다운으로 자동 수집 (기본 켜짐) */
    var wikiAutoExport: Boolean
        get() = prefs.getBoolean(KEY_WIKI_EXPORT, true)
        set(v) = prefs.edit().putBoolean(KEY_WIKI_EXPORT, v).apply()

    val isCloudReady: Boolean
        get() = cloudEnabled && apiKey.isNotBlank()

    companion object {
        private const val KEY_ENABLED = "cloud_enabled"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_WIKI_EXPORT = "wiki_auto_export"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}

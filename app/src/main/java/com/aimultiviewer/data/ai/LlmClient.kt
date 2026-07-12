package com.aimultiviewer.data.ai

import com.aimultiviewer.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 호환 Chat Completions 클라이언트(클라우드 모드).
 * base_url 만 바꾸면 OpenAI / Azure / 로컬 OpenAI 호환 서버 등에 연결 가능.
 */
class LlmClient(private val settings: SettingsStore) {

    suspend fun chat(system: String, user: String): String = withContext(Dispatchers.IO) {
        val url = URL(settings.baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

        val payload = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }

        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""

        if (code !in 200..299) {
            throw RuntimeException("LLM 요청 실패 ($code): ${body.take(300)}")
        }

        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}

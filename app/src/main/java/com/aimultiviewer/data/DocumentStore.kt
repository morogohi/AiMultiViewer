package com.aimultiviewer.data

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.Document
import org.json.JSONArray
import org.json.JSONObject

/**
 * 가져온 문서 목록을 영속화(SharedPreferences에 JSON).
 * SAF로 받은 persistable URI 권한 덕분에 앱 재시작 후에도 접근 가능.
 */
class DocumentStore(context: Context) {
    private val prefs = context.getSharedPreferences("documents", Context.MODE_PRIVATE)

    fun load(): List<Document> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Document(
                        id = o.getString("id"),
                        uri = o.getString("uri"),
                        name = o.getString("name"),
                        format = runCatching { DocFormat.valueOf(o.getString("format")) }
                            .getOrDefault(DocFormat.UNKNOWN),
                        importedAt = o.getLong("importedAt")
                    )
                )
            }
        }
    }

    fun save(docs: List<Document>) {
        val arr = JSONArray()
        docs.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("uri", d.uri)
                    .put("name", d.name)
                    .put("format", d.format.name)
                    .put("importedAt", d.importedAt)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(doc: Document): List<Document> {
        val current = load().filterNot { it.uri == doc.uri }
        val updated = listOf(doc) + current
        save(updated)
        return updated
    }

    fun remove(id: String): List<Document> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        return updated
    }

    companion object {
        private const val KEY = "doc_list"
    }
}

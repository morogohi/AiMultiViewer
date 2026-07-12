package com.aimultiviewer.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.aimultiviewer.App
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.Document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as App).documentStore

    private val _documents = MutableStateFlow(store.load())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()

    fun onDocumentPicked(uri: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = queryName(uri)
        // 확장자 우선, 실패 시 MIME 타입으로 판별 (Drive의 Google 문서 등)
        var format = DocFormat.fromName(name)
        if (format == DocFormat.UNKNOWN) {
            format = DocFormat.fromMime(context.contentResolver.getType(uri))
        }
        val doc = Document(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            name = name,
            format = format,
            importedAt = System.currentTimeMillis()
        )
        _documents.value = store.add(doc)
    }

    fun remove(id: String) {
        _documents.value = store.remove(id)
    }

    private fun queryName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment ?: "문서"
    }
}

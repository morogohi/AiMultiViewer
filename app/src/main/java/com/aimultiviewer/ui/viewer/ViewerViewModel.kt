package com.aimultiviewer.ui.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimultiviewer.App
import com.aimultiviewer.data.parser.ParserRegistry
import com.aimultiviewer.data.wiki.WikiExporter
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.Document
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerUiState(
    val document: Document? = null,
    val content: DocumentContent? = null,
    val loading: Boolean = true,
    val aiTitle: String = "",
    val aiOutput: String = "",
    val aiLoading: Boolean = false,
    /** 편집 저장 결과 알림 (null=없음) */
    val editMessage: String? = null
)

class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app as App
    private val ai = appCtx.aiRepository

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    val otherDocuments: List<Document>
        get() = appCtx.documentStore.load().filter { it.id != _state.value.document?.id }

    fun load(documentId: String) {
        val doc = appCtx.documentStore.load().firstOrNull { it.id == documentId }
        if (doc == null) {
            _state.value = ViewerUiState(loading = false, aiOutput = "문서를 찾을 수 없습니다.")
            return
        }
        _state.value = ViewerUiState(document = doc, loading = true)
        viewModelScope.launch {
            // 과거 버전에서 UNKNOWN으로 저장된 문서는 열 때 재판별 (신규 포맷 지원 대응)
            val format = if (doc.format == DocFormat.UNKNOWN) {
                val byName = DocFormat.fromName(doc.name)
                if (byName != DocFormat.UNKNOWN) byName
                else DocFormat.fromMime(appCtx.contentResolver.getType(Uri.parse(doc.uri)))
            } else doc.format
            val content = ParserRegistry.parse(appCtx, Uri.parse(doc.uri), format)
            _state.value = _state.value.copy(content = content, loading = false)

            // llm-wiki 자동 수집: 열람한 문서를 Obsidian 마크다운으로 변환해 축적
            if (appCtx.settingsStore.wikiAutoExport && content.plainText.isNotBlank()) {
                launch {
                    runCatching { WikiExporter(appCtx).export(doc.copy(format = format), content) }
                }
            }
        }
    }

    private fun runAi(title: String, block: suspend (String) -> String) {
        val text = _state.value.content?.plainText.orEmpty()
        _state.value = _state.value.copy(aiTitle = title, aiLoading = true, aiOutput = "")
        viewModelScope.launch {
            val result = runCatching { block(text) }
                .getOrElse { "오류: ${it.message}" }
            _state.value = _state.value.copy(aiOutput = result, aiLoading = false)
        }
    }

    fun summarize() = runAi("문서 요약") { ai.summarize(it) }
    fun minutes() = runAi("회의록 생성") { ai.minutes(it) }
    fun analyzeTechDoc() = runAi("기술문서 분석") { ai.analyzeTechDoc(it) }
    fun ask(question: String) = runAi("질의응답: $question") { ai.ask(question, it) }

    fun compareWith(otherId: String) {
        val other = appCtx.documentStore.load().firstOrNull { it.id == otherId } ?: return
        val current = _state.value.content?.plainText.orEmpty()
        _state.value = _state.value.copy(aiTitle = "문서 비교", aiLoading = true, aiOutput = "")
        viewModelScope.launch {
            val otherContent = ParserRegistry.parse(appCtx, Uri.parse(other.uri), other.format)
            val result = runCatching { ai.compare(current, otherContent.plainText) }
                .getOrElse { "오류: ${it.message}" }
            _state.value = _state.value.copy(aiOutput = result, aiLoading = false)
        }
    }

    fun isParsedText(): Boolean {
        val c = _state.value.content ?: return false
        return _state.value.document?.format != DocFormat.PDF || c.plainText.isNotBlank()
    }

    /** TXT/Markdown 등 일반 텍스트 문서만 편집 허용 (그 외 포맷은 뷰어 전용) */
    val isEditable: Boolean
        get() = _state.value.document?.format in setOf(DocFormat.TXT, DocFormat.MARKDOWN)

    /** 편집한 텍스트를 원본 파일에 저장 */
    fun saveText(newText: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    appCtx.contentResolver.openOutputStream(Uri.parse(doc.uri), "wt")
                        ?.use { it.write(newText.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("파일 스트림을 열 수 없습니다")
                }
            }
            _state.value = if (result.isSuccess) {
                _state.value.copy(
                    content = _state.value.content?.copy(plainText = newText),
                    editMessage = "저장되었습니다."
                )
            } else {
                _state.value.copy(
                    editMessage = "저장 실패 (읽기 전용 문서일 수 있습니다. 문서를 다시 추가해 주세요): " +
                        result.exceptionOrNull()?.message.orEmpty()
                )
            }
        }
    }

    fun clearEditMessage() {
        _state.value = _state.value.copy(editMessage = null)
    }
}

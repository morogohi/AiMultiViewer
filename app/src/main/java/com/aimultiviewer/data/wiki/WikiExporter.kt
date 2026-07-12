package com.aimultiviewer.data.wiki

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.aimultiviewer.data.ai.Summarizer
import com.aimultiviewer.domain.model.Document
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 열람한 문서를 Obsidian(llm-wiki) 호환 마크다운으로 변환해
 * 기기 공용 폴더 Documents/llm-wiki/ 에 자동 축적한다.
 *
 * - 파일명은 원본 이름 기반 슬러그 → 같은 문서를 다시 열면 갱신(중복 없음)
 * - YAML 프런트매터 + 온디바이스 자동 요약 + 전문(全文) 포함
 * - PC의 llm-wiki-research 볼트로는 sync_wiki_from_device.ps1 로 수집
 */
class WikiExporter(private val context: Context) {

    companion object {
        const val RELATIVE_DIR = "Documents/llm-wiki/"
        /** 전문이 이보다 길면 잘라서 저장 (Obsidian 성능 보호) */
        private const val MAX_BODY = 200_000
    }

    suspend fun export(doc: Document, content: DocumentContent): String? =
        withContext(Dispatchers.IO) {
            if (content.plainText.isBlank()) return@withContext null
            val markdown = buildMarkdown(doc, content)
            val fileName = slugFileName(doc.name)
            writeToDocuments(fileName, markdown.toByteArray(Charsets.UTF_8))
        }

    private fun buildMarkdown(doc: Document, content: DocumentContent): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date())
        val today = now.substring(0, 10)
        val title = doc.name.substringBeforeLast('.')
        val summary = Summarizer.summarize(content.plainText, maxSentences = 6)
        val body = content.plainText.take(MAX_BODY)
        val truncated = content.plainText.length > MAX_BODY

        return buildString {
            appendLine("---")
            appendLine("title: \"${title.replace("\"", "'")}\"")
            appendLine("type: source")
            appendLine("format: ${doc.format.label}")
            appendLine("source: AiMultiViewer")
            appendLine("original: \"${doc.name.replace("\"", "'")}\"")
            appendLine("collected: $now")
            appendLine("updated: $today")
            appendLine("tags: [aimultiviewer, auto-collected]")
            appendLine("status: draft")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            appendLine("## 자동 요약")
            appendLine()
            appendLine(summary)
            appendLine()
            appendLine("## 전문")
            appendLine()
            appendLine(body)
            if (truncated) {
                appendLine()
                appendLine("> ⚠ 본문이 길어 일부만 저장되었습니다 (원본 ${content.plainText.length}자).")
            }
        }
    }

    /** 파일 시스템에 안전한 이름으로 변환 (확장자는 .md 로 교체, 원본 확장자는 이름에 포함). */
    private fun slugFileName(name: String): String {
        val base = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return "$base.md"
    }

    private fun writeToDocuments(fileName: String, bytes: ByteArray): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(fileName, bytes)
        } else {
            // API 26~28: 앱 전용 외부 저장소에 기록
            val dir = File(context.getExternalFilesDir(null), "llm-wiki")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName)
            f.writeBytes(bytes)
            f.absolutePath
        }
    }

    private fun writeViaMediaStore(fileName: String, bytes: ByteArray): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")

        // 이미 내보낸 파일이면 갱신 (같은 문서 재열람 시 중복 방지)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(RELATIVE_DIR, fileName),
            null
        )?.use { c ->
            if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }

        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
        }) ?: return null

        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: return null
        return RELATIVE_DIR + fileName
    }
}

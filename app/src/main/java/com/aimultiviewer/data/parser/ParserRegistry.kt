package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent

/**
 * 모든 파서를 등록하고 포맷에 맞는 파서로 위임한다.
 */
object ParserRegistry {
    private val parsers: List<DocumentParser> = listOf(
        TxtParser(),
        MarkdownParser(),
        PdfParser(),
        DocxParser(),
        PptxParser(),
        XlsxParser(),
        OdfParser(),
        HwpxParser(),
        HwpParser(),
        GoogleDocParser(),
        ImageParser(),
        // DOC(구 바이너리)은 후속 마일스톤에서 네이티브 파서로 대체
        UnsupportedBinaryParser()
    )

    suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent {
        val parser = parsers.firstOrNull { it.supports(format) }
            ?: return DocumentContent(
                plainText = "",
                warning = "지원하지 않는 형식입니다: ${format.label}"
            )
        return parser.parse(context, uri, format)
    }
}

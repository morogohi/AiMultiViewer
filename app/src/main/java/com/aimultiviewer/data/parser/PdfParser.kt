package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 텍스트 추출은 PDFBox-Android로 수행한다.
 * 실제 페이지 렌더링은 UI에서 android.graphics.pdf.PdfRenderer 로 처리한다(원본 Uri 전달).
 */
class PdfParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.PDF

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            var text = ""
            var pages = 0
            var warning: String? = null
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    PDDocument.load(input).use { doc ->
                        pages = doc.numberOfPages
                        text = PDFTextStripper().getText(doc)
                    }
                }
            } catch (t: Throwable) {
                warning = "PDF 텍스트 추출 실패(스캔 이미지 PDF일 수 있음): ${t.message}"
            }
            DocumentContent(
                plainText = text,
                isMarkdown = false,
                renderableUri = uri.toString(),
                pageCount = pages,
                warning = warning
            )
        }
}

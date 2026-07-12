package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Google Docs/Sheets/Slides는 Drive가 노출하는 가상 파일이라 바이트 스트림이 없다.
 * SAF의 openTypedAssetFileDescriptor 로 PDF로 내보내 캐시에 저장한 뒤,
 * PDF 뷰어 렌더 + PDFBox 텍스트 추출로 판독한다.
 */
class GoogleDocParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.GOOGLE

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val cached = File(context.cacheDir, "gdoc_${uri.toString().hashCode()}.pdf")
            try {
                if (!cached.exists() || cached.length() == 0L) {
                    val afd = context.contentResolver
                        .openTypedAssetFileDescriptor(uri, "application/pdf", null)
                        ?: return@withContext DocumentContent(
                            plainText = "",
                            warning = "Google 문서를 PDF로 내보낼 수 없습니다. Drive 앱이 설치되어 있는지 확인해주세요."
                        )
                    afd.use { fd ->
                        fd.createInputStream().use { input ->
                            cached.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                }
            } catch (t: Throwable) {
                cached.delete()
                return@withContext DocumentContent(
                    plainText = "",
                    warning = "Google 문서 내보내기 실패: ${t.message}\n" +
                        "네트워크 연결과 Drive 앱 로그인 상태를 확인해주세요."
                )
            }

            var text = ""
            var pages = 0
            var warning: String? = null
            try {
                cached.inputStream().use { input ->
                    PDDocument.load(input).use { doc ->
                        pages = doc.numberOfPages
                        text = PDFTextStripper().getText(doc)
                    }
                }
            } catch (t: Throwable) {
                warning = "내보낸 PDF의 텍스트 추출에 실패했습니다: ${t.message}"
            }
            DocumentContent(
                plainText = text,
                renderableUri = Uri.fromFile(cached).toString(),
                pageCount = pages,
                warning = warning
            )
        }
}

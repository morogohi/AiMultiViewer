package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * DOCX = ZIP(OOXML). word/document.xml 의 본문 텍스트를 추출한다.
 * (외부 라이브러리 없이 ZIP + XML 경량 파싱)
 */
class DocxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.DOCX

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            var documentXml: String? = null
            context.contentResolver.openInputStream(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            documentXml = zip.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            val xml = documentXml
            if (xml == null) {
                DocumentContent(plainText = "", warning = "DOCX 본문(word/document.xml)을 찾지 못했습니다.")
            } else {
                DocumentContent(plainText = XmlTextExtractor.extract(xml))
            }
        }
}

package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenDocument(ODT/ODS/ODP) = ZIP. content.xml 의 본문 텍스트를 추출한다.
 * Google Docs/LibreOffice에서 내보낸 문서를 판독할 수 있다.
 */
class OdfParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.ODF

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) { it == "content.xml" }
            val xml = entries["content.xml"]?.toString(Charsets.UTF_8)
            if (xml == null) {
                DocumentContent(plainText = "", warning = "OpenDocument 본문(content.xml)을 찾지 못했습니다.")
            } else {
                DocumentContent(plainText = XmlTextExtractor.extract(xml))
            }
        }
}

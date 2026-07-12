package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * HWPX = ZIP(OWPML, 한글 표준 개방형 포맷).
 * Contents/section*.xml 들의 본문 텍스트를 순서대로 추출한다.
 */
class HwpxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.HWPX

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val sections = sortedMapOf<String, String>()
            context.contentResolver.openInputStream(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.startsWith("Contents/section") && name.endsWith(".xml")) {
                            sections[name] = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            if (sections.isEmpty()) {
                DocumentContent(plainText = "", warning = "HWPX 본문(Contents/section*.xml)을 찾지 못했습니다.")
            } else {
                val text = sections.values.joinToString("\n\n") { XmlTextExtractor.extract(it) }
                DocumentContent(plainText = text)
            }
        }
}

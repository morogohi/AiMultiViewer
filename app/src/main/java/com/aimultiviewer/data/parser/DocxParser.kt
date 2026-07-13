package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.aimultiviewer.domain.model.DocBlock
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.aimultiviewer.domain.model.toPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * DOCX = ZIP(OOXML WordprocessingML).
 * word/document.xml 을 스트리밍 파싱해 제목/문단/불릿/표 구조를 보존한 블록을 산출한다.
 * <w:t> 텍스트만 수집하므로 필드코드·도형 좌표 등 비표시 데이터가 섞이지 않는다.
 */
class DocxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.DOCX

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) { it == "word/document.xml" }
            val xml = entries["word/document.xml"]?.toString(Charsets.UTF_8)
                ?: return@withContext DocumentContent(
                    plainText = "",
                    warning = "DOCX 본문(word/document.xml)을 찾지 못했습니다."
                )
            val blocks = parseDocument(xml)
            DocumentContent(plainText = blocks.toPlainText(), blocks = blocks)
        }

    private fun parseDocument(xml: String): List<DocBlock> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val blocks = mutableListOf<DocBlock>()

        var fallbackDepth = 0          // mc:Fallback 내부는 중복 콘텐츠 → 무시
        var tableDepth = 0
        var tableRows: MutableList<List<String>>? = null
        var rowCells: MutableList<String>? = null
        var cellText: StringBuilder? = null

        var paraText: StringBuilder? = null
        var headingLevel = 0
        var bullet = false
        var inT = false
        var skipText = 0               // w:instrText/w:delText 등

        fun local(name: String) = name.substringAfter(':')

        fun endParagraph() {
            val text = paraText?.toString()?.trim() ?: return
            paraText = null
            if (tableDepth > 0) {
                cellText?.let { if (it.isNotEmpty()) it.append('\n'); it.append(text) }
                return
            }
            if (text.isEmpty()) return
            blocks.add(
                when {
                    headingLevel in 1..4 -> DocBlock.Heading(text, headingLevel)
                    bullet -> DocBlock.Para(text, bullet = true)
                    else -> DocBlock.Para(text)
                }
            )
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "Fallback" -> fallbackDepth++
                    "instrText", "delText", "fldData" -> skipText++
                    "p" -> if (fallbackDepth == 0) {
                        paraText = StringBuilder(); headingLevel = 0; bullet = false
                    }
                    "pStyle" -> if (fallbackDepth == 0) {
                        val v = parser.getAttributeValue(null, "w:val") ?: ""
                        Regex("""(?i)heading\s*(\d)|^제목\s*(\d)""").find(v)?.let { m ->
                            headingLevel = (m.groupValues[1] + m.groupValues[2])
                                .toIntOrNull()?.coerceIn(1, 4) ?: 0
                        }
                    }
                    "outlineLvl" -> if (fallbackDepth == 0 && headingLevel == 0) {
                        parser.getAttributeValue(null, "w:val")?.toIntOrNull()
                            ?.let { headingLevel = (it + 1).coerceIn(1, 4) }
                    }
                    "numPr" -> if (fallbackDepth == 0) bullet = true
                    "t" -> if (fallbackDepth == 0 && skipText == 0) inT = true
                    "tab" -> if (fallbackDepth == 0) paraText?.append('\t')
                    "br", "cr" -> if (fallbackDepth == 0) paraText?.append('\n')
                    "tbl" -> if (fallbackDepth == 0) {
                        tableDepth++
                        if (tableDepth == 1) tableRows = mutableListOf()
                    }
                    "tr" -> if (fallbackDepth == 0 && tableDepth == 1) rowCells = mutableListOf()
                    "tc" -> if (fallbackDepth == 0 && tableDepth == 1) cellText = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inT) paraText?.append(parser.text)
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "Fallback" -> fallbackDepth = (fallbackDepth - 1).coerceAtLeast(0)
                    "instrText", "delText", "fldData" -> skipText = (skipText - 1).coerceAtLeast(0)
                    "t" -> inT = false
                    "p" -> if (fallbackDepth == 0) endParagraph()
                    "tc" -> if (fallbackDepth == 0 && tableDepth == 1) {
                        rowCells?.add(cellText?.toString()?.trim() ?: "")
                        cellText = null
                    }
                    "tr" -> if (fallbackDepth == 0 && tableDepth == 1) {
                        rowCells?.let { tableRows?.add(it) }
                        rowCells = null
                    }
                    "tbl" -> if (fallbackDepth == 0) {
                        tableDepth--
                        if (tableDepth == 0) {
                            tableRows?.takeIf { it.isNotEmpty() }
                                ?.let { blocks.add(DocBlock.Table(it)) }
                            tableRows = null
                        }
                    }
                }
            }
        }
        return blocks
    }
}

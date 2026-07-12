package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * XLSX = ZIP(OOXML SpreadsheetML).
 * xl/sharedStrings.xml + xl/worksheets/sheetN.xml 을 파싱해 시트별 셀 텍스트를 표 형태로 추출한다.
 */
class XlsxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.XLSX

    private val sheetRegex = Regex("""xl/worksheets/sheet(\d+)\.xml""")

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) {
                it == "xl/sharedStrings.xml" || it == "xl/workbook.xml" || sheetRegex.matches(it)
            }
            val sheets = entries.filterKeys { sheetRegex.matches(it) }
            if (sheets.isEmpty()) {
                return@withContext DocumentContent(
                    plainText = "",
                    warning = "XLSX 시트(xl/worksheets/*.xml)를 찾지 못했습니다."
                )
            }

            val sharedStrings = entries["xl/sharedStrings.xml"]
                ?.let { parseSharedStrings(it.toString(Charsets.UTF_8)) }
                ?: emptyList()
            val sheetNames = entries["xl/workbook.xml"]
                ?.let { parseSheetNames(it.toString(Charsets.UTF_8)) }
                ?: emptyList()

            val text = buildString {
                sheets.toList()
                    .sortedBy { (name, _) -> sheetRegex.find(name)!!.groupValues[1].toInt() }
                    .forEachIndexed { idx, (name, bytes) ->
                        val no = sheetRegex.find(name)!!.groupValues[1].toInt()
                        val title = sheetNames.getOrNull(no - 1) ?: "Sheet$no"
                        if (idx > 0) append("\n\n")
                        append("── 시트: $title ──\n")
                        append(parseSheet(bytes.toString(Charsets.UTF_8), sharedStrings))
                    }
            }
            DocumentContent(plainText = text.trim())
        }

    /** sharedStrings.xml: <si> 항목마다 내부 <t> 텍스트를 이어붙인다(서식 분할 run 대응). */
    private fun parseSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        var current: StringBuilder? = null
        var inT = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) current?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> {
                        result.add(current?.toString() ?: "")
                        current = null
                    }
                }
            }
        }
        return result
    }

    private fun parseSheetNames(xml: String): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                result.add(parser.getAttributeValue(null, "name") ?: "Sheet${result.size + 1}")
            }
        }
        return result
    }

    /** 시트 XML을 행 단위 텍스트로 변환. 셀은 탭으로 구분한다. */
    private fun parseSheet(xml: String, sharedStrings: List<String>): String {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val lines = mutableListOf<String>()
        var row: MutableList<String>? = null
        var cellType: String? = null
        var cellValue: StringBuilder? = null
        var inV = false
        var inInlineT = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue = StringBuilder()
                    }
                    "v" -> inV = true
                    "t" -> if (cellType == "inlineStr") inInlineT = true
                }
                XmlPullParser.TEXT -> if (inV || inInlineT) cellValue?.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inInlineT = false
                    "c" -> {
                        val raw = cellValue?.toString() ?: ""
                        val resolved = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        row?.add(resolved)
                        cellType = null
                        cellValue = null
                    }
                    "row" -> {
                        val r = row
                        if (r != null && r.any { it.isNotBlank() }) {
                            lines.add(r.joinToString("\t").trimEnd())
                        }
                        row = null
                    }
                }
            }
        }
        return lines.joinToString("\n")
    }
}

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
 * XLSX = ZIP(OOXML SpreadsheetML).
 * 시트별 셀을 열 위치(r="B3")까지 보존해 표 블록으로 산출한다 → 뷰어에서 그리드로 렌더.
 */
class XlsxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.XLSX

    private val sheetRegex = Regex("""xl/worksheets/sheet(\d+)\.xml""")

    companion object {
        private const val MAX_ROWS_PER_SHEET = 400
        private const val MAX_COLS = 40
    }

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

            val blocks = mutableListOf<DocBlock>()
            sheets.toList()
                .sortedBy { (name, _) -> sheetRegex.find(name)!!.groupValues[1].toInt() }
                .forEach { (name, bytes) ->
                    val no = sheetRegex.find(name)!!.groupValues[1].toInt()
                    val title = sheetNames.getOrNull(no - 1) ?: "Sheet$no"
                    val (rows, truncated) = parseSheetRows(bytes.toString(Charsets.UTF_8), sharedStrings)
                    if (rows.isEmpty()) return@forEach
                    blocks.add(DocBlock.Heading("시트: $title", 3))
                    blocks.add(DocBlock.Table(rows))
                    if (truncated) blocks.add(DocBlock.Note("… 표가 길어 일부만 표시합니다 (최대 ${MAX_ROWS_PER_SHEET}행)"))
                }
            if (blocks.isEmpty()) {
                return@withContext DocumentContent(plainText = "", warning = "표시할 셀 데이터가 없습니다.")
            }
            DocumentContent(plainText = blocks.toPlainText(), blocks = blocks)
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

    /** "B3" → 열 인덱스 1 */
    private fun colIndex(ref: String?): Int {
        if (ref == null) return -1
        var col = 0
        for (c in ref) {
            if (c.isLetter()) col = col * 26 + (c.uppercaseChar() - 'A' + 1) else break
        }
        return col - 1
    }

    /** 시트 XML → 행렬(빈 셀은 "") */
    private fun parseSheetRows(xml: String, sharedStrings: List<String>): Pair<List<List<String>>, Boolean> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val rows = mutableListOf<List<String>>()
        var truncated = false

        var cells: MutableMap<Int, String>? = null
        var nextCol = 0
        var cellCol = 0
        var cellType: String? = null
        var cellValue: StringBuilder? = null
        var inV = false
        var inInlineT = false

        loop@ while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { cells = mutableMapOf(); nextCol = 0 }
                    "c" -> {
                        val r = colIndex(parser.getAttributeValue(null, "r"))
                        cellCol = if (r >= 0) r else nextCol
                        nextCol = cellCol + 1
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
                            else -> formatNumber(raw)
                        }
                        if (resolved.isNotBlank() && cellCol < MAX_COLS) {
                            cells?.put(cellCol, resolved)
                        }
                        cellType = null
                        cellValue = null
                    }
                    "row" -> {
                        val c = cells
                        if (!c.isNullOrEmpty()) {
                            if (rows.size >= MAX_ROWS_PER_SHEET) { truncated = true; break@loop }
                            val width = (c.keys.max() + 1).coerceAtMost(MAX_COLS)
                            rows.add(List(width) { c[it] ?: "" })
                        }
                        cells = null
                    }
                }
            }
        }
        return rows to truncated
    }

    /** "1155000000.0000001" 같은 부동소수 잔재 정리 */
    private fun formatNumber(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        if (raw.length > 15 || raw.contains('.')) {
            val rounded = Math.round(d * 10000.0) / 10000.0
            return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
                rounded.toLong().toString()
            } else rounded.toString()
        }
        return raw
    }
}

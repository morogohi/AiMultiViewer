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
 * PPTX = ZIP(OOXML PresentationML).
 * 슬라이드별로 제목 플레이스홀더/본문 문단/표를 구조 보존 블록으로 산출한다.
 * 발표자 노트는 Note 블록으로 첨부.
 */
class PptxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.PPTX

    private val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val notesRegex = Regex("""ppt/notesSlides/notesSlide(\d+)\.xml""")

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) {
                slideRegex.matches(it) || notesRegex.matches(it)
            }
            if (entries.isEmpty()) {
                return@withContext DocumentContent(
                    plainText = "",
                    warning = "PPTX 슬라이드(ppt/slides/*.xml)를 찾지 못했습니다."
                )
            }

            val slides = entries.filterKeys { slideRegex.matches(it) }
                .toList()
                .sortedBy { (name, _) -> slideRegex.find(name)!!.groupValues[1].toInt() }
            val notes = entries.filterKeys { notesRegex.matches(it) }
                .mapKeys { (name, _) -> notesRegex.find(name)!!.groupValues[1].toInt() }
                .mapValues { (_, bytes) -> XmlTextExtractor.extract(bytes.toString(Charsets.UTF_8)) }

            val blocks = mutableListOf<DocBlock>()
            slides.forEach { (name, bytes) ->
                val no = slideRegex.find(name)!!.groupValues[1].toInt()
                blocks.add(DocBlock.Heading("슬라이드 $no", 3))
                blocks.addAll(parseSlide(bytes.toString(Charsets.UTF_8)))
                notes[no]?.takeIf { it.isNotBlank() }?.let {
                    blocks.add(DocBlock.Note("[발표자 노트] $it"))
                }
            }
            DocumentContent(plainText = blocks.toPlainText(), blocks = blocks)
        }

    /**
     * 슬라이드 XML → 블록.
     * - p:sp 안 p:ph type="title|ctrTitle" 이면 그 도형의 문단은 제목
     * - a:tbl → 표, a:p → 문단(a:t 텍스트 수집)
     */
    private fun parseSlide(xml: String): List<DocBlock> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val blocks = mutableListOf<DocBlock>()

        var shapeDepth = 0
        var titleShapeDepth = -1       // 제목 도형이면 그 sp의 depth
        var inTable = false
        var tableRows: MutableList<List<String>>? = null
        var rowCells: MutableList<String>? = null
        var cellText: StringBuilder? = null
        var paraText: StringBuilder? = null
        var inT = false

        fun local(name: String) = name.substringAfter(':')

        fun endParagraph() {
            val text = paraText?.toString()?.trim() ?: return
            paraText = null
            if (text.isEmpty()) return
            if (inTable) {
                cellText?.let { if (it.isNotEmpty()) it.append('\n'); it.append(text) }
            } else if (titleShapeDepth >= 0) {
                blocks.add(DocBlock.Heading(text, 2))
            } else {
                blocks.add(DocBlock.Para(text, bullet = true))
            }
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "sp" -> shapeDepth++
                    "ph" -> {
                        val type = parser.getAttributeValue(null, "type")
                        if ((type == "title" || type == "ctrTitle") && titleShapeDepth < 0) {
                            titleShapeDepth = shapeDepth
                        }
                    }
                    "tbl" -> { inTable = true; tableRows = mutableListOf() }
                    "tr" -> if (inTable) rowCells = mutableListOf()
                    "tc" -> if (inTable) cellText = StringBuilder()
                    "p" -> paraText = StringBuilder()
                    "t" -> inT = true
                    "br" -> paraText?.append('\n')
                }
                XmlPullParser.TEXT -> if (inT) paraText?.append(parser.text)
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "t" -> inT = false
                    "p" -> endParagraph()
                    "tc" -> if (inTable) {
                        rowCells?.add(cellText?.toString()?.trim() ?: "")
                        cellText = null
                    }
                    "tr" -> if (inTable) {
                        rowCells?.let { tableRows?.add(it) }
                        rowCells = null
                    }
                    "tbl" -> {
                        inTable = false
                        tableRows?.takeIf { it.isNotEmpty() }?.let { blocks.add(DocBlock.Table(it)) }
                        tableRows = null
                    }
                    "sp" -> {
                        if (shapeDepth == titleShapeDepth) titleShapeDepth = -1
                        shapeDepth--
                    }
                }
            }
        }
        return blocks
    }
}

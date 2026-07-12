package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PPTX = ZIP(OOXML). ppt/slides/slideN.xml 의 텍스트를 슬라이드 번호 순으로 추출한다.
 * 발표자 노트(ppt/notesSlides)도 함께 추출한다.
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

            val text = buildString {
                slides.forEachIndexed { idx, (name, bytes) ->
                    val no = slideRegex.find(name)!!.groupValues[1].toInt()
                    if (idx > 0) append("\n\n")
                    append("── 슬라이드 $no ──\n")
                    append(XmlTextExtractor.extract(bytes.toString(Charsets.UTF_8)))
                    val note = notes[no]
                    if (!note.isNullOrBlank()) {
                        append("\n[노트] ").append(note)
                    }
                }
            }
            DocumentContent(plainText = text.trim())
        }
}

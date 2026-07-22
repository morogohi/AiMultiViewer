package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocBlock
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.aimultiviewer.domain.model.RichRun
import com.aimultiviewer.domain.model.toPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DOCX = ZIP(OOXML WordprocessingML).
 * 문단·표 구조에 더해 글자 서식(크기/굵기/기울임/색), 문단 정렬, 삽입 이미지까지
 * 보존해 원본 워드 문서에 가까운 렌더링 블록을 산출한다.
 */
class DocxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.DOCX

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) { name ->
                name == "word/document.xml" ||
                    name == "word/_rels/document.xml.rels" ||
                    (name.startsWith("word/media/") && !name.endsWith(".emf") && !name.endsWith(".wmf"))
            }
            val xml = entries["word/document.xml"]?.toString(Charsets.UTF_8)
                ?: return@withContext DocumentContent(
                    plainText = "",
                    warning = "DOCX 본문(word/document.xml)을 찾지 못했습니다."
                )

            val rels = parseRels(entries["word/_rels/document.xml.rels"])
            val mediaDir = File(context.cacheDir, "docx_media/${uri.toString().hashCode()}")
            val mediaCache = mutableMapOf<String, String>()
            fun mediaPath(zipName: String): String? {
                mediaCache[zipName]?.let { return it }
                val bytes = entries[zipName] ?: return null
                if (bytes.size > 12_000_000) return null
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val f = File(mediaDir, zipName.substringAfterLast('/'))
                val ok = f.exists() || runCatching { f.writeBytes(bytes) }.isSuccess
                if (!ok) return null
                mediaCache[zipName] = f.absolutePath
                return f.absolutePath
            }

            val root = XmlDom.parse(xml)
                ?: return@withContext DocumentContent(plainText = "", warning = "DOCX XML 파싱 실패")
            pruneFallback(root)

            val blocks = mutableListOf<DocBlock>()
            val body = root.find("body")
            if (body != null) {
                for (child in body.children) {
                    when (child.name) {
                        "p" -> paragraphBlocks(child, rels, ::mediaPath, blocks)
                        "tbl" -> tableBlock(child)?.let { blocks.add(it) }
                    }
                }
            }
            DocumentContent(plainText = blocks.toPlainText(), blocks = blocks)
        }

    /** mc:Fallback(중복 콘텐츠) 하위 트리 제거 */
    private fun pruneFallback(node: XmlNode) {
        node.children.removeAll { it.name == "Fallback" }
        node.children.forEach { pruneFallback(it) }
    }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        val root = bytes?.let { XmlDom.parse(it.toString(Charsets.UTF_8)) } ?: return emptyMap()
        return root.findAll("Relationship").mapNotNull { rel ->
            val id = rel.attr("Id") ?: return@mapNotNull null
            val target = rel.attr("Target") ?: return@mapNotNull null
            id to "word/" + target.removePrefix("/word/").removePrefix("../")
        }.toMap()
    }

    /** 문단 → Heading | RichPara(+이미지) 블록들 */
    private fun paragraphBlocks(
        p: XmlNode,
        rels: Map<String, String>,
        mediaPath: (String) -> String?,
        blocks: MutableList<DocBlock>
    ) {
        val pPr = p.child("pPr")

        // 제목 스타일
        var headingLevel = 0
        pPr?.child("pStyle")?.attr("val")?.let { v ->
            Regex("""(?i)heading\s*(\d)|^제목\s*(\d)""").find(v)?.let { m ->
                headingLevel = (m.groupValues[1] + m.groupValues[2]).toIntOrNull()?.coerceIn(1, 4) ?: 0
            }
        }
        if (headingLevel == 0) {
            pPr?.child("outlineLvl")?.attr("val")?.toIntOrNull()
                ?.let { headingLevel = (it + 1).coerceIn(1, 4) }
        }

        val bullet = pPr?.find("numPr") != null
        val align = when (pPr?.child("jc")?.attr("val")) {
            "center" -> 3
            "right", "end" -> 2
            "both", "distribute", "justify" -> 0
            else -> 1
        }

        // 런 수집 (하이퍼링크/스마트태그 안의 런 포함, 문서 순서 유지)
        val runs = mutableListOf<RichRun>()
        collectRuns(p, runs)

        if (headingLevel in 1..4) {
            val text = runs.joinToString("") { it.text }.trim()
            if (text.isNotEmpty()) blocks.add(DocBlock.Heading(text, headingLevel))
        } else if (runs.any { it.text.isNotBlank() }) {
            val finalRuns = if (bullet) {
                val style = runs.first()
                mutableListOf(style.copy(text = "•  ")) + runs
            } else runs
            blocks.add(DocBlock.RichPara(finalRuns.toList(), align))
        }

        // 삽입 이미지 (a:blip r:embed → word/media/*)
        for (blip in p.findAll("blip")) {
            val rId = blip.attr("embed") ?: continue
            val target = rels[rId] ?: continue
            mediaPath(target)?.let { blocks.add(DocBlock.Image(it)) }
        }
    }

    /** 문단 하위에서 w:r 런을 문서 순서대로 수집 (w:pPr는 제외, r 내부로는 안 내려감) */
    private fun collectRuns(node: XmlNode, out: MutableList<RichRun>) {
        for (child in node.children) {
            when (child.name) {
                "pPr" -> {}
                "r" -> runToRich(child)?.let { out.add(it) }
                else -> collectRuns(child, out)
            }
        }
    }

    private fun runToRich(r: XmlNode): RichRun? {
        val text = buildString {
            for (child in r.children) {
                when (child.name) {
                    "t" -> append(child.allText())
                    "tab" -> append('\t')
                    "br", "cr" -> append('\n')
                }
            }
        }
        if (text.isEmpty()) return null

        val rPr = r.child("rPr")
        fun flag(name: String): Boolean {
            val n = rPr?.child(name) ?: return false
            val v = n.attr("val")
            return v == null || v !in setOf("false", "0", "none")
        }
        val sizePt = rPr?.child("sz")?.attr("val")?.toFloatOrNull()?.div(2f)
        val color = rPr?.child("color")?.attr("val")
            ?.takeIf { it.length == 6 && !it.equals("auto", true) }
            ?.toIntOrNull(16)
        return RichRun(
            text = text,
            sizePt = sizePt?.coerceIn(4f, 96f),
            bold = flag("b"),
            italic = flag("i"),
            colorRgb = color?.takeIf { it != 0 }
        )
    }

    /** 표 → Table 블록 (셀은 문단 단위 평문) */
    private fun tableBlock(tbl: XmlNode): DocBlock.Table? {
        val rows = tbl.childrenNamed("tr").map { tr ->
            tr.childrenNamed("tc").map { tc ->
                tc.findAll("p").joinToString("\n") { p ->
                    p.findAll("t").joinToString("") { it.allText() }
                }.trim()
            }
        }.filter { it.isNotEmpty() }
        return if (rows.isEmpty()) null else DocBlock.Table(rows)
    }
}

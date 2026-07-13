package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import com.aimultiviewer.domain.model.DocBlock
import com.aimultiviewer.domain.model.DocFormat
import com.aimultiviewer.domain.model.DocumentContent
import com.aimultiviewer.domain.model.Slide
import com.aimultiviewer.domain.model.SlideDeck
import com.aimultiviewer.domain.model.SlideElement
import com.aimultiviewer.domain.model.SlidePara
import com.aimultiviewer.domain.model.SlideRun
import com.aimultiviewer.domain.model.toPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PPTX = ZIP(OOXML PresentationML).
 * 슬라이드의 도형 위치/크기(xfrm), 텍스트 서식(크기/굵기/색), 이미지, 표를 추출해
 * 시각적 슬라이드 캔버스 모델(SlideDeck)을 만든다.
 * plainText/blocks 도 함께 산출해 AI·위키 기능이 그대로 동작하게 한다.
 */
class PptxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.PPTX

    private val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val relsRegex = Regex("""ppt/slides/_rels/slide(\d+)\.xml\.rels""")
    private val notesRegex = Regex("""ppt/notesSlides/notesSlide(\d+)\.xml""")

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) { name ->
                name == "ppt/presentation.xml" ||
                    slideRegex.matches(name) || relsRegex.matches(name) ||
                    notesRegex.matches(name) ||
                    (name.startsWith("ppt/media/") && !name.endsWith(".emf") && !name.endsWith(".wmf"))
            }
            val slideEntries = entries.filterKeys { slideRegex.matches(it) }
            if (slideEntries.isEmpty()) {
                return@withContext DocumentContent(
                    plainText = "",
                    warning = "PPTX 슬라이드(ppt/slides/*.xml)를 찾지 못했습니다."
                )
            }

            // 슬라이드 크기
            var slideW = 12192000L
            var slideH = 6858000L
            entries["ppt/presentation.xml"]?.let { bytes ->
                XmlDom.parse(bytes.toString(Charsets.UTF_8))?.find("sldSz")?.let { sz ->
                    sz.attr("cx")?.toLongOrNull()?.let { slideW = it }
                    sz.attr("cy")?.toLongOrNull()?.let { slideH = it }
                }
            }

            // 참조 이미지 캐시 추출
            val mediaDir = File(context.cacheDir, "pptx_media/${uri.toString().hashCode()}")
            mediaDir.mkdirs()
            val mediaPaths = mutableMapOf<String, String>()   // zip 경로 → 캐시 파일 경로
            fun mediaPath(zipName: String): String? {
                mediaPaths[zipName]?.let { return it }
                val bytes = entries[zipName] ?: return null
                if (bytes.size > 12_000_000) return null
                val f = File(mediaDir, zipName.substringAfterLast('/'))
                if (!f.exists()) runCatching { f.writeBytes(bytes) }.onFailure { return null }
                mediaPaths[zipName] = f.absolutePath
                return f.absolutePath
            }

            val notes = entries.filterKeys { notesRegex.matches(it) }
                .mapKeys { (name, _) -> notesRegex.find(name)!!.groupValues[1].toInt() }
                .mapValues { (_, bytes) ->
                    XmlDom.parse(bytes.toString(Charsets.UTF_8))
                        ?.findAll("t")?.joinToString(" ") { it.allText() }?.trim() ?: ""
                }

            val slides = mutableListOf<Slide>()
            val blocks = mutableListOf<DocBlock>()
            for ((name, bytes) in slideEntries.toList()
                .sortedBy { (n, _) -> slideRegex.find(n)!!.groupValues[1].toInt() }) {
                val no = slideRegex.find(name)!!.groupValues[1].toInt()
                val rels = parseRels(entries["ppt/slides/_rels/slide$no.xml.rels"])
                val root = XmlDom.parse(bytes.toString(Charsets.UTF_8)) ?: continue
                val spTree = root.find("spTree") ?: continue

                val elements = mutableListOf<SlideElement>()
                parseShapeTree(spTree, slideW, slideH, rels, ::mediaPath, elements, null)
                val note = notes[no]?.takeIf { it.isNotBlank() }
                slides.add(Slide(no, elements, note))

                // 텍스트 블록(AI/위키용)
                blocks.add(DocBlock.Heading("슬라이드 $no", 3))
                elements.forEach { el ->
                    when (el) {
                        is SlideElement.TextBox -> el.paragraphs.forEach { p ->
                            val t = p.text.trim()
                            if (t.isNotEmpty()) blocks.add(DocBlock.Para(t, bullet = p.bullet))
                        }
                        is SlideElement.TableBox ->
                            if (el.rows.isNotEmpty()) blocks.add(DocBlock.Table(el.rows))
                        is SlideElement.Picture -> {}
                    }
                }
                note?.let { blocks.add(DocBlock.Note("[발표자 노트] $it")) }
            }

            DocumentContent(
                plainText = blocks.toPlainText(),
                blocks = blocks,
                slideDeck = SlideDeck(slideW, slideH, slides)
            )
        }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        val root = bytes?.let { XmlDom.parse(it.toString(Charsets.UTF_8)) } ?: return emptyMap()
        return root.findAll("Relationship").mapNotNull { rel ->
            val id = rel.attr("Id") ?: return@mapNotNull null
            val target = rel.attr("Target") ?: return@mapNotNull null
            id to target.removePrefix("../").let { if (it.startsWith("ppt/")) it else "ppt/$it" }
        }.toMap()
    }

    /**
     * spTree/grpSp 하위 도형을 순회한다.
     * [groupMap]: 그룹 내부 좌표(EMU) → 슬라이드 좌표(EMU) 변환. null이면 항등.
     */
    private fun parseShapeTree(
        tree: XmlNode,
        slideW: Long,
        slideH: Long,
        rels: Map<String, String>,
        mediaPath: (String) -> String?,
        out: MutableList<SlideElement>,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?
    ) {
        var autoY = 0.05f
        for (node in tree.children) {
            when (node.name) {
                "sp" -> parseSp(node, slideW, slideH, groupMap, autoY)?.let {
                    out.add(it)
                    if (it.h > 0f) autoY = (it.y + it.h + 0.02f).coerceAtMost(0.9f)
                }
                "pic" -> parsePic(node, slideW, slideH, rels, mediaPath, groupMap)?.let { out.add(it) }
                "graphicFrame" -> parseGraphicFrame(node, slideW, slideH, groupMap)?.let { out.add(it) }
                "grpSp" -> {
                    val xfrm = node.child("grpSpPr")?.child("xfrm") ?: continue
                    val off = xfrm.child("off")
                    val ext = xfrm.child("ext")
                    val chOff = xfrm.child("chOff") ?: off
                    val chExt = xfrm.child("chExt") ?: ext
                    val ox = off?.attr("x")?.toLongOrNull() ?: 0L
                    val oy = off?.attr("y")?.toLongOrNull() ?: 0L
                    val ex = ext?.attr("cx")?.toLongOrNull() ?: slideW
                    val ey = ext?.attr("cy")?.toLongOrNull() ?: slideH
                    val cox = chOff?.attr("x")?.toLongOrNull() ?: 0L
                    val coy = chOff?.attr("y")?.toLongOrNull() ?: 0L
                    val cex = (chExt?.attr("cx")?.toLongOrNull() ?: ex).coerceAtLeast(1)
                    val cey = (chExt?.attr("cy")?.toLongOrNull() ?: ey).coerceAtLeast(1)
                    val outerMap = groupMap
                    val map: (Long, Long, Long, Long) -> LongArray = { x, y, w, h ->
                        val mx = ox + (x - cox) * ex / cex
                        val my = oy + (y - coy) * ey / cey
                        val mw = w * ex / cex
                        val mh = h * ey / cey
                        outerMap?.invoke(mx, my, mw, mh) ?: longArrayOf(mx, my, mw, mh)
                    }
                    parseShapeTree(node, slideW, slideH, rels, mediaPath, out, map)
                }
            }
        }
    }

    /** xfrm(off/ext) → 슬라이드 비율 좌표 [x,y,w,h]. 없으면 null */
    private fun readXfrm(
        xfrm: XmlNode?,
        slideW: Long,
        slideH: Long,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?
    ): FloatArray? {
        val off = xfrm?.child("off") ?: return null
        val ext = xfrm.child("ext") ?: return null
        var x = off.attr("x")?.toLongOrNull() ?: return null
        var y = off.attr("y")?.toLongOrNull() ?: return null
        var w = ext.attr("cx")?.toLongOrNull() ?: return null
        var h = ext.attr("cy")?.toLongOrNull() ?: return null
        groupMap?.invoke(x, y, w, h)?.let { m -> x = m[0]; y = m[1]; w = m[2]; h = m[3] }
        return floatArrayOf(
            x.toFloat() / slideW, y.toFloat() / slideH,
            w.toFloat() / slideW, h.toFloat() / slideH
        )
    }

    private fun parseSp(
        sp: XmlNode,
        slideW: Long,
        slideH: Long,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?,
        autoY: Float
    ): SlideElement.TextBox? {
        val txBody = sp.child("txBody") ?: return null
        val phType = sp.child("nvSpPr")?.find("ph")?.attr("type")
        val isTitle = phType == "title" || phType == "ctrTitle"

        val rect = readXfrm(sp.child("spPr")?.child("xfrm"), slideW, slideH, groupMap)
            ?: if (isTitle) floatArrayOf(0.05f, 0.04f, 0.9f, 0.14f)
            else floatArrayOf(0.05f, autoY.coerceAtLeast(0.2f), 0.9f, 0.6f)

        val paragraphs = mutableListOf<SlidePara>()
        for (p in txBody.childrenNamed("p")) {
            val pPr = p.child("pPr")
            val lvl = pPr?.attr("lvl")?.toIntOrNull() ?: 0
            val algn = pPr?.attr("algn")
            val bullet = pPr?.child("buChar") != null || pPr?.child("buAutoNum") != null
            val runs = mutableListOf<SlideRun>()
            for (child in p.children) {
                when (child.name) {
                    "r", "fld" -> {
                        val text = child.child("t")?.allText() ?: continue
                        if (text.isEmpty()) continue
                        val rPr = child.child("rPr")
                        runs.add(
                            SlideRun(
                                text = text,
                                sizePt = rPr?.attr("sz")?.toFloatOrNull()?.div(100f)
                                    ?: if (isTitle) 24f else null,
                                bold = rPr?.attr("b") == "1",
                                italic = rPr?.attr("i") == "1",
                                colorRgb = rPr?.find("srgbClr")?.attr("val")
                                    ?.toIntOrNull(16)
                            )
                        )
                    }
                    "br" -> runs.add(SlideRun("\n"))
                }
            }
            if (runs.any { it.text.isNotBlank() }) {
                paragraphs.add(SlidePara(runs, bullet, lvl, algn))
            }
        }
        if (paragraphs.isEmpty()) return null
        return SlideElement.TextBox(rect[0], rect[1], rect[2], rect[3], paragraphs)
    }

    private fun parsePic(
        pic: XmlNode,
        slideW: Long,
        slideH: Long,
        rels: Map<String, String>,
        mediaPath: (String) -> String?,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?
    ): SlideElement.Picture? {
        val embed = pic.child("blipFill")?.child("blip")?.attr("embed") ?: return null
        val target = rels[embed] ?: return null
        val path = mediaPath(target) ?: return null
        val rect = readXfrm(pic.child("spPr")?.child("xfrm"), slideW, slideH, groupMap)
            ?: floatArrayOf(0.1f, 0.2f, 0.8f, 0.6f)
        return SlideElement.Picture(rect[0], rect[1], rect[2], rect[3], path)
    }

    private fun parseGraphicFrame(
        frame: XmlNode,
        slideW: Long,
        slideH: Long,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?
    ): SlideElement.TableBox? {
        val tbl = frame.find("tbl") ?: return null
        val rows = tbl.childrenNamed("tr").map { tr ->
            tr.childrenNamed("tc").map { tc ->
                tc.findAll("t").joinToString(" ") { it.allText() }.trim()
            }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        val rect = readXfrm(frame.child("xfrm"), slideW, slideH, groupMap)
            ?: floatArrayOf(0.05f, 0.2f, 0.9f, 0.6f)
        return SlideElement.TableBox(rect[0], rect[1], rect[2], rect[3], rows)
    }
}

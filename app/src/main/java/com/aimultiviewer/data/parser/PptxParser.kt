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
import kotlin.math.roundToInt

/**
 * PPTX = ZIP(OOXML PresentationML).
 * 테마 색·슬라이드/레이아웃/마스터 배경·도형 채우기·텍스트 서식·이미지·표를 추출해
 * 원본에 가까운 슬라이드 캔버스(SlideDeck)를 만든다.
 */
class PptxParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.PPTX

    private val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val slideRelsRegex = Regex("""ppt/slides/_rels/slide(\d+)\.xml\.rels""")
    private val notesRegex = Regex("""ppt/notesSlides/notesSlide(\d+)\.xml""")

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val entries = ZipUtils.readEntries(context, uri) { name ->
                name == "ppt/presentation.xml" ||
                    name == "ppt/_rels/presentation.xml.rels" ||
                    name.startsWith("ppt/theme/") ||
                    name.startsWith("ppt/slideLayouts/") ||
                    name.startsWith("ppt/slideMasters/") ||
                    slideRegex.matches(name) || slideRelsRegex.matches(name) ||
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

            var slideW = 12192000L
            var slideH = 6858000L
            entries["ppt/presentation.xml"]?.let { bytes ->
                XmlDom.parse(bytes.toString(Charsets.UTF_8))?.find("sldSz")?.let { sz ->
                    sz.attr("cx")?.toLongOrNull()?.let { slideW = it }
                    sz.attr("cy")?.toLongOrNull()?.let { slideH = it }
                }
            }

            val theme = loadTheme(entries)
            val mediaDir = File(context.cacheDir, "pptx_media/${uri.toString().hashCode()}")
            mediaDir.mkdirs()
            val mediaPaths = mutableMapOf<String, String>()
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

            // slideN → layout zip path (rels)
            val layoutCache = mutableMapOf<String, XmlNode?>()
            val masterCache = mutableMapOf<String, XmlNode?>()
            fun loadXml(path: String): XmlNode? =
                entries[path]?.let { XmlDom.parse(it.toString(Charsets.UTF_8)) }

            val slides = mutableListOf<Slide>()
            val blocks = mutableListOf<DocBlock>()
            for ((name, bytes) in slideEntries.toList()
                .sortedBy { (n, _) -> slideRegex.find(n)!!.groupValues[1].toInt() }) {
                val no = slideRegex.find(name)!!.groupValues[1].toInt()
                val rels = parseRels(entries["ppt/slides/_rels/slide$no.xml.rels"], "ppt/slides/")
                val root = XmlDom.parse(bytes.toString(Charsets.UTF_8)) ?: continue
                val spTree = root.find("spTree") ?: continue

                val layoutPath = rels.values.firstOrNull {
                    it.contains("slideLayout") && it.endsWith(".xml")
                }
                val layout = layoutPath?.let { p ->
                    layoutCache.getOrPut(p) { loadXml(p) }
                }
                val layoutRels = layoutPath?.let { p ->
                    val relsName = p.substringBeforeLast('/') + "/_rels/" +
                        p.substringAfterLast('/') + ".rels"
                    parseRels(entries[relsName], p.substringBeforeLast('/') + "/")
                }.orEmpty()
                val masterPath = layoutRels.values.firstOrNull {
                    it.contains("slideMaster") && it.endsWith(".xml")
                }
                val master = masterPath?.let { p ->
                    masterCache.getOrPut(p) { loadXml(p) }
                }

                val bg = resolveBackground(root, layout, master, theme)
                val elements = mutableListOf<SlideElement>()
                parseShapeTree(spTree, slideW, slideH, rels, theme, ::mediaPath, elements, null)
                val note = notes[no]?.takeIf { it.isNotBlank() }
                slides.add(Slide(no, elements, note, bg))

                blocks.add(DocBlock.Heading("슬라이드 $no", 3))
                elements.forEach { el ->
                    when (el) {
                        is SlideElement.TextBox -> el.paragraphs.forEach { p ->
                            val t = p.text.trim()
                            if (t.isNotEmpty()) blocks.add(DocBlock.Para(t, bullet = p.bullet))
                        }
                        is SlideElement.TableBox ->
                            if (el.rows.isNotEmpty()) blocks.add(DocBlock.Table(el.rows))
                        is SlideElement.Picture, is SlideElement.Shape -> {}
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

    // ---- Theme / colors ----

    private data class Theme(
        val scheme: Map<String, Int> = emptyMap()
    ) {
        fun color(name: String?): Int? = name?.let { scheme[it.lowercase()] }
    }

    private fun loadTheme(entries: Map<String, ByteArray>): Theme {
        val themeBytes = entries.entries
            .firstOrNull { it.key.startsWith("ppt/theme/theme") && it.key.endsWith(".xml") }
            ?.value ?: return Theme()
        val root = XmlDom.parse(themeBytes.toString(Charsets.UTF_8)) ?: return Theme()
        val schemeNode = root.find("clrScheme") ?: return Theme()
        val map = mutableMapOf<String, Int>()
        for (child in schemeNode.children) {
            val rgb = extractColor(child, Theme()) ?: continue
            map[child.name.lowercase()] = rgb
        }
        // 별칭
        map["tx1"] = map["dk1"] ?: map["tx1"] ?: 0x000000
        map["tx2"] = map["dk2"] ?: map["tx2"] ?: 0x000000
        map["bg1"] = map["lt1"] ?: map["bg1"] ?: 0xFFFFFF
        map["bg2"] = map["lt2"] ?: map["bg2"] ?: 0xFFFFFF
        return Theme(map)
    }

    /** 노드 하위(또는 자기)에서 srgbClr / schemeClr / sysClr 색 추출 */
    private fun extractColor(node: XmlNode?, theme: Theme): Int? {
        if (node == null) return null
        node.find("srgbClr")?.let { sc ->
            sc.attr("val")?.toIntOrNull(16)?.let { return applyLum(it, sc) }
        }
        node.find("sysClr")?.let { sc ->
            sc.attr("lastClr")?.toIntOrNull(16)?.let { return applyLum(it, sc) }
        }
        node.find("schemeClr")?.let { sc ->
            val base = theme.color(sc.attr("val")) ?: return@let
            return applyLum(base, sc)
        }
        for (c in node.children) {
            extractColor(c, theme)?.let { return it }
        }
        return null
    }

    /** lumMod(%) / lumOff(%) / tint / shade 적용 */
    private fun applyLum(rgb: Int, node: XmlNode): Int {
        var r = (rgb shr 16) and 0xFF
        var g = (rgb shr 8) and 0xFF
        var b = rgb and 0xFF
        fun toFloat(v: Int) = v / 255f
        fun fromFloat(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt()

        node.child("lumMod")?.attr("val")?.toIntOrNull()?.let { mod ->
            val f = mod / 100_000f
            r = fromFloat(toFloat(r) * f)
            g = fromFloat(toFloat(g) * f)
            b = fromFloat(toFloat(b) * f)
        }
        node.child("lumOff")?.attr("val")?.toIntOrNull()?.let { off ->
            val f = off / 100_000f
            r = fromFloat(toFloat(r) + f)
            g = fromFloat(toFloat(g) + f)
            b = fromFloat(toFloat(b) + f)
        }
        node.child("tint")?.attr("val")?.toIntOrNull()?.let { t ->
            val f = t / 100_000f
            r = fromFloat(toFloat(r) * f + (1 - f))
            g = fromFloat(toFloat(g) * f + (1 - f))
            b = fromFloat(toFloat(b) * f + (1 - f))
        }
        node.child("shade")?.attr("val")?.toIntOrNull()?.let { s ->
            val f = s / 100_000f
            r = fromFloat(toFloat(r) * f)
            g = fromFloat(toFloat(g) * f)
            b = fromFloat(toFloat(b) * f)
        }
        return (r shl 16) or (g shl 8) or b
    }

    private fun resolveBackground(
        slide: XmlNode,
        layout: XmlNode?,
        master: XmlNode?,
        theme: Theme
    ): Int? {
        fun from(node: XmlNode?): Int? {
            val bg = node?.find("bg") ?: return null
            // bgPr/solidFill or bgRef
            bg.find("solidFill")?.let { return extractColor(it, theme) }
            bg.find("bgRef")?.let { ref ->
                // theme fill style 참조 — 색만 추출 시도
                return extractColor(ref, theme) ?: theme.color(ref.attr("val"))
            }
            bg.find("gradFill")?.let { gf ->
                // 첫 번째 그라데이션 스탑 색 사용
                gf.find("gsLst")?.children?.firstOrNull()?.let { return extractColor(it, theme) }
            }
            return extractColor(bg, theme)
        }
        return from(slide) ?: from(layout) ?: from(master) ?: theme.color("bg1") ?: theme.color("lt1")
    }

    // ---- Rels / geometry ----

    private fun parseRels(bytes: ByteArray?, baseDir: String): Map<String, String> {
        val root = bytes?.let { XmlDom.parse(it.toString(Charsets.UTF_8)) } ?: return emptyMap()
        return root.findAll("Relationship").mapNotNull { rel ->
            val id = rel.attr("Id") ?: return@mapNotNull null
            val target = rel.attr("Target") ?: return@mapNotNull null
            val resolved = when {
                target.startsWith("/") -> target.removePrefix("/")
                target.startsWith("../") -> {
                    // baseDir = ppt/slides/ → ../slideLayouts/x.xml → ppt/slideLayouts/x.xml
                    val parts = (baseDir.trimEnd('/') + "/" + target).split('/')
                    val stack = ArrayDeque<String>()
                    for (p in parts) when (p) {
                        "", "." -> {}
                        ".." -> if (stack.isNotEmpty()) stack.removeLast()
                        else -> stack.addLast(p)
                    }
                    stack.joinToString("/")
                }
                target.startsWith("ppt/") -> target
                else -> baseDir.trimEnd('/') + "/" + target
            }
            id to resolved
        }.toMap()
    }

    private fun parseShapeTree(
        tree: XmlNode,
        slideW: Long,
        slideH: Long,
        rels: Map<String, String>,
        theme: Theme,
        mediaPath: (String) -> String?,
        out: MutableList<SlideElement>,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?
    ) {
        var autoY = 0.05f
        for (node in tree.children) {
            when (node.name) {
                "sp" -> {
                    parseSp(node, slideW, slideH, theme, groupMap, autoY).forEach { el ->
                        out.add(el)
                        if (el is SlideElement.TextBox && el.h > 0f) {
                            autoY = (el.y + el.h + 0.02f).coerceAtMost(0.9f)
                        }
                    }
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
                    parseShapeTree(node, slideW, slideH, rels, theme, mediaPath, out, map)
                }
            }
        }
    }

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

    /** sp → Shape(채우기) 및/또는 TextBox. 텍스트 없는 장식 도형도 포함. */
    private fun parseSp(
        sp: XmlNode,
        slideW: Long,
        slideH: Long,
        theme: Theme,
        groupMap: ((Long, Long, Long, Long) -> LongArray)?,
        autoY: Float
    ): List<SlideElement> {
        val result = mutableListOf<SlideElement>()
        val spPr = sp.child("spPr")
        val phType = sp.child("nvSpPr")?.find("ph")?.attr("type")
        val isTitle = phType == "title" || phType == "ctrTitle"

        val rect = readXfrm(spPr?.child("xfrm"), slideW, slideH, groupMap)
            ?: if (isTitle) floatArrayOf(0.05f, 0.04f, 0.9f, 0.14f)
            else floatArrayOf(0.05f, autoY.coerceAtLeast(0.2f), 0.9f, 0.6f)

        val fill = extractFill(spPr, theme)
        val stroke = extractStroke(spPr, theme)
        val kind = when (spPr?.find("prstGeom")?.attr("prst")) {
            "ellipse", "circle" -> SlideElement.Shape.Kind.ELLIPSE
            "roundRect" -> SlideElement.Shape.Kind.ROUND_RECT
            else -> SlideElement.Shape.Kind.RECT
        }

        val txBody = sp.child("txBody")
        val paragraphs = if (txBody != null) parseParagraphs(txBody, isTitle, theme) else emptyList()

        if (paragraphs.isNotEmpty()) {
            result.add(
                SlideElement.TextBox(
                    rect[0], rect[1], rect[2], rect[3],
                    paragraphs, fillRgb = fill
                )
            )
        } else if (fill != null || stroke != null) {
            // 텍스트 없는 장식 도형
            result.add(
                SlideElement.Shape(
                    rect[0], rect[1], rect[2], rect[3],
                    kind = kind,
                    fillRgb = fill,
                    strokeRgb = stroke?.first,
                    strokePt = stroke?.second ?: 0f
                )
            )
        }
        return result
    }

    private fun extractFill(spPr: XmlNode?, theme: Theme): Int? {
        if (spPr == null) return null
        // noFill은 직접 자식일 때만 채우기 없음 (ln/noFill 과 혼동 금지)
        if (spPr.children.any { it.name == "noFill" }) return null
        spPr.child("solidFill")?.let { return extractColor(it, theme) }
        // 그라데이션 → 첫 스탑 색으로 근사
        spPr.child("gradFill")?.find("gsLst")?.children?.firstOrNull()?.let {
            return extractColor(it, theme)
        }
        // 스타일 참조 없이 spPr에 직접 지정된 경우만
        return null
    }

    private fun extractStroke(spPr: XmlNode?, theme: Theme): Pair<Int, Float>? {
        val ln = spPr?.child("ln") ?: return null
        if (ln.children.any { it.name == "noFill" }) return null
        val color = extractColor(ln, theme) ?: return null
        val wEmu = ln.attr("w")?.toLongOrNull() ?: 12700L
        val pt = wEmu / 12700f
        return color to pt
    }

    private fun parseParagraphs(txBody: XmlNode, isTitle: Boolean, theme: Theme): List<SlidePara> {
        val paragraphs = mutableListOf<SlidePara>()
        // lstStyle 기본 크기 (lvl1pPr/defRPr)
        val defSize = txBody.child("lstStyle")
            ?.find("lvl1pPr")?.find("defRPr")?.attr("sz")?.toFloatOrNull()?.div(100f)
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
                        val color = rPr?.let { extractColor(it, theme) }
                        runs.add(
                            SlideRun(
                                text = text,
                                sizePt = rPr?.attr("sz")?.toFloatOrNull()?.div(100f)
                                    ?: defSize
                                    ?: if (isTitle) 28f else null,
                                bold = rPr?.attr("b") == "1",
                                italic = rPr?.attr("i") == "1",
                                colorRgb = color
                            )
                        )
                    }
                    "br" -> runs.add(SlideRun("\n"))
                }
            }
            // endParaRPr만 있고 텍스트 없는 빈 문단은 스킵
            if (runs.any { it.text.isNotBlank() }) {
                paragraphs.add(SlidePara(runs, bullet, lvl, algn))
            }
        }
        return paragraphs
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

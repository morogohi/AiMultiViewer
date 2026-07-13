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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * HWP 5.0(구 바이너리, 한컴오피스) 텍스트 추출 파서.
 *
 * HWP 5.x = CFB(OLE Compound File) 컨테이너:
 *  - FileHeader 스트림: 시그니처/버전/압축·암호화 플래그
 *  - BodyText/Section{N} 스트림: raw-deflate 압축된 레코드 시퀀스
 *  - HWPTAG_PARA_TEXT(67) 레코드의 UTF-16LE 페이로드가 본문 (표 셀 텍스트 포함)
 *
 * 외부 라이브러리 없이 최소 CFB 리더 + 레코드 파서로 구현.
 * (알고리즘은 실제 문서 기반 프로토타입으로 사전 검증됨)
 */
class HwpParser : DocumentParser {
    override fun supports(format: DocFormat) = format == DocFormat.HWP

    override suspend fun parse(context: Context, uri: Uri, format: DocFormat): DocumentContent =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext DocumentContent(plainText = "", warning = "파일을 열 수 없습니다.")
            try {
                val mediaDir = File(context.cacheDir, "hwp_media/${uri.toString().hashCode()}")
                parseHwp(bytes, mediaDir)
            } catch (e: Exception) {
                DocumentContent(
                    plainText = "",
                    warning = "HWP 파싱 실패 (${e.message ?: e.javaClass.simpleName}). " +
                        "한/글에서 HWPX로 저장 후 열면 확실하게 지원됩니다."
                )
            }
        }

    /** DocInfo에서 읽은 글자모양 */
    private class CharShape(
        val sizePt: Float,
        val bold: Boolean,
        val italic: Boolean,
        val colorRgb: Int
    )

    private fun parseHwp(bytes: ByteArray, mediaDir: File): DocumentContent {
        val cfb = CfbReader(bytes)
        val header = cfb.readStream("FileHeader")
            ?: return DocumentContent(plainText = "", warning = "HWP FileHeader가 없습니다. 손상되었거나 HWP 형식이 아닙니다.")
        val signature = String(header, 0, 17, Charsets.US_ASCII)
        if (!signature.startsWith("HWP Document File")) {
            return DocumentContent(plainText = "", warning = "HWP 5.0 시그니처가 아닙니다.")
        }
        val flags = ByteBuffer.wrap(header, 36, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val compressed = flags and 1 != 0
        if (flags and 2 != 0) {
            return DocumentContent(plainText = "", warning = "암호화된 HWP 문서는 지원하지 않습니다.")
        }
        if (flags and 4 != 0) {
            return DocumentContent(plainText = "", warning = "배포용(DRM) HWP 문서는 지원하지 않습니다.")
        }

        // DocInfo: 글자모양(크기/굵기/기울임/색), 문단모양(정렬)
        val charShapes = mutableListOf<CharShape>()
        val paraAligns = mutableListOf<Int>()
        cfb.readStream("DocInfo")?.let { raw ->
            val info = if (compressed) inflateRaw(raw) else raw
            parseRecords(info) { tag, _, payload ->
                when (tag) {
                    HWPTAG_CHAR_SHAPE -> if (payload.size >= 56) {
                        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        val sizePt = bb.getInt(42) / 100f
                        val attr = bb.getInt(46)
                        val colorRef = bb.getInt(52)
                        val rgb = ((colorRef and 0xFF) shl 16) or
                            (colorRef and 0xFF00) or
                            ((colorRef ushr 16) and 0xFF)
                        charShapes.add(
                            CharShape(
                                sizePt = sizePt.coerceIn(4f, 96f),
                                bold = attr and 2 != 0,
                                italic = attr and 1 != 0,
                                colorRgb = rgb
                            )
                        )
                    }
                    HWPTAG_PARA_SHAPE -> if (payload.size >= 4) {
                        val attr1 = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        paraAligns.add((attr1 ushr 2) and 7)
                    }
                }
            }
        }

        // BinData: 삽입 이미지 스트림 → 캐시 파일 추출 (id → 경로)
        val images = extractBinDataImages(cfb, compressed, mediaDir)

        val blocks = mutableListOf<DocBlock>()
        for (sectionName in cfb.streamsUnder("BodyText").sortedBy {
            it.removePrefix("Section").toIntOrNull() ?: 0
        }) {
            val raw = cfb.readStream("BodyText", sectionName) ?: continue
            val data = if (compressed) inflateRaw(raw) else raw
            blocks.addAll(parseSection(data, charShapes, paraAligns, images))
        }
        if (blocks.isEmpty()) {
            return DocumentContent(plainText = "", warning = "본문 텍스트를 찾지 못했습니다.")
        }
        return DocumentContent(plainText = blocks.toPlainText(), blocks = blocks)
    }

    /** BinData 스토리지의 "BIN%04X.ext" 스트림들을 캐시 파일로 추출한다. */
    private fun extractBinDataImages(
        cfb: CfbReader,
        compressed: Boolean,
        mediaDir: File
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val nameRegex = Regex("""(?i)BIN([0-9A-F]{4})\.(\w+)""")
        for (name in cfb.streamsUnder("BinData")) {
            val m = nameRegex.matchEntire(name) ?: continue
            val ext = m.groupValues[2].lowercase()
            if (ext !in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")) continue
            val id = m.groupValues[1].toIntOrNull(16) ?: continue
            val raw = cfb.readStream("BinData", name) ?: continue
            val bytes = if (compressed) {
                runCatching { inflateRaw(raw) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: raw
            } else raw
            if (bytes.size > 20_000_000) continue
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val f = File(mediaDir, name.lowercase())
            val written = f.exists() || runCatching { f.writeBytes(bytes) }.isSuccess
            if (written) result[id] = f.absolutePath
        }
        return result
    }

    /** 서식 적용 대기 중인 본문 문단 */
    private class PendingPara(
        val chars: String,
        val rawPos: IntArray,
        val align: Int
    )

    /**
     * 섹션 레코드를 순회하며 문단(서식 포함)/표/이미지 구조를 복원한다.
     * - PARA_TEXT 뒤에 오는 PARA_CHAR_SHAPE(위치→글자모양 id)로 서식 런을 나눈다
     * - CTRL_HEADER('tbl ') → TABLE(행×열) → LIST_HEADER(셀 주소) → 하위 PARA_TEXT를 셀에 배치
     * - SHAPE_COMPONENT_PICTURE(85)의 binItem으로 BinData 이미지를 본문 위치에 삽입
     * - 머리말/꼬리말/각주(head/foot/fn/en) 하위 문단은 본문에서 제외
     */
    private fun parseSection(
        data: ByteArray,
        charShapes: List<CharShape>,
        paraAligns: List<Int>,
        images: Map<Int, String>
    ): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()

        var table: Array<Array<StringBuilder>>? = null
        var tableCtrlLevel = -1
        var cellRow = -1
        var cellCol = -1
        var cellSeq = 0
        var pendingTableLevel = -1
        var skipCtrlLevel = -1     // head/foot 등 제외 대상 컨트롤의 레벨
        var curAlign = 1           // 현재 문단 정렬 (PARA_HEADER에서 갱신)
        var pending: PendingPara? = null
        val usedImages = mutableSetOf<Int>()

        fun emitPending(runsSpec: List<Pair<Int, Int>>?) {
            val p = pending ?: return
            pending = null
            val text = p.chars
            if (text.isBlank()) return

            val runs = mutableListOf<RichRun>()
            if (runsSpec.isNullOrEmpty()) {
                runs.add(RichRun(text.trim('\n')))
            } else {
                for ((i, spec) in runsSpec.withIndex()) {
                    val (startRaw, shapeId) = spec
                    val endRaw = if (i + 1 < runsSpec.size) runsSpec[i + 1].first else Int.MAX_VALUE
                    val seg = buildString {
                        for (j in text.indices) {
                            if (p.rawPos[j] in startRaw until endRaw) append(text[j])
                        }
                    }
                    if (seg.isEmpty()) continue
                    val cs = charShapes.getOrNull(shapeId)
                    runs.add(
                        RichRun(
                            text = seg,
                            sizePt = cs?.sizePt,
                            bold = cs?.bold ?: false,
                            italic = cs?.italic ?: false,
                            colorRgb = cs?.colorRgb?.takeIf { it != 0 }
                        )
                    )
                }
            }
            // 마지막 런의 꼬리 개행 제거
            while (runs.isNotEmpty()) {
                val last = runs.last()
                val trimmed = last.text.trimEnd('\n')
                if (trimmed == last.text) break
                if (trimmed.isEmpty()) runs.removeAt(runs.lastIndex)
                else { runs[runs.lastIndex] = last.copy(text = trimmed); break }
            }
            if (runs.isEmpty()) return
            blocks.add(DocBlock.RichPara(runs, p.align))
        }

        fun flushTable() {
            val t = table ?: return
            val rows = t.map { r -> r.map { it.toString().trim() } }
            val compact = compactTable(rows)
            if (compact.isNotEmpty()) blocks.add(DocBlock.Table(compact))
            table = null
            tableCtrlLevel = -1
            cellRow = -1; cellCol = -1; cellSeq = 0
        }

        parseRecords(data) { tag, level, payload ->
            // 제외 컨트롤(머리말 등) 종료 감지
            if (skipCtrlLevel >= 0 && level <= skipCtrlLevel) skipCtrlLevel = -1

            when (tag) {
                HWPTAG_PARA_HEADER -> {
                    emitPending(null)
                    if (table != null && level <= tableCtrlLevel) flushTable()
                    if (payload.size >= 10) {
                        val shapeId = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                            .getShort(8).toInt() and 0xFFFF
                        curAlign = paraAligns.getOrElse(shapeId) { 1 }
                    }
                }
                HWPTAG_CTRL_HEADER -> {
                    emitPending(null)
                    if (payload.size >= 4) {
                        val ctrlId = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        when (ctrlId) {
                            CTRL_TBL -> {
                                flushTable()
                                pendingTableLevel = level
                            }
                            in EXCLUDED_CTRLS -> if (skipCtrlLevel < 0) skipCtrlLevel = level
                            else -> if (table != null && level <= tableCtrlLevel) flushTable()
                        }
                    }
                }
                HWPTAG_TABLE -> if (pendingTableLevel >= 0 && payload.size >= 8) {
                    val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                    val rows = bb.getShort(4).toInt() and 0xFFFF
                    val cols = bb.getShort(6).toInt() and 0xFFFF
                    if (rows in 1..500 && cols in 1..64) {
                        table = Array(rows) { Array(cols) { StringBuilder() } }
                        tableCtrlLevel = pendingTableLevel
                    }
                    pendingTableLevel = -1
                }
                HWPTAG_LIST_HEADER -> {
                    val t = table
                    if (t != null && level > tableCtrlLevel && payload.size >= 12) {
                        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        val col = bb.getShort(8).toInt() and 0xFFFF
                        val row = bb.getShort(10).toInt() and 0xFFFF
                        if (row < t.size && col < t[0].size) {
                            cellRow = row; cellCol = col
                        } else {
                            cellRow = (cellSeq / t[0].size).coerceAtMost(t.size - 1)
                            cellCol = cellSeq % t[0].size
                        }
                        cellSeq++
                    }
                }
                HWPTAG_SHAPE_PICTURE -> {
                    if (skipCtrlLevel < 0 && payload.size >= 70) {
                        val binItem = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                            .getShort(68).toInt() and 0xFFFF
                        val path = images[binItem]
                            ?: images.entries.firstOrNull { it.key !in usedImages }?.value
                        if (path != null) {
                            usedImages.add(images.entries.first { it.value == path }.key)
                            emitPending(null)
                            blocks.add(DocBlock.Image(path))
                        }
                    }
                }
                HWPTAG_PARA_TEXT -> {
                    if (skipCtrlLevel >= 0 && level > skipCtrlLevel) return@parseRecords
                    val t = table
                    if (t != null && cellRow >= 0 && level > tableCtrlLevel) {
                        val text = decodeParaText(payload).trim('\n').trim()
                        if (text.isNotEmpty()) {
                            val sb = t[cellRow][cellCol]
                            if (sb.isNotEmpty()) sb.append('\n')
                            sb.append(text)
                        }
                    } else {
                        if (t != null) flushTable()
                        emitPending(null)
                        val (chars, rawPos) = decodeParaTextWithPos(payload)
                        if (chars.isNotBlank()) {
                            pending = PendingPara(chars, rawPos, curAlign)
                        }
                    }
                }
                HWPTAG_PARA_CHAR_SHAPE -> {
                    if (pending != null && payload.size >= 8) {
                        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        val spec = mutableListOf<Pair<Int, Int>>()
                        var off = 0
                        while (off + 8 <= payload.size) {
                            spec.add(bb.getInt(off) to bb.getInt(off + 4))
                            off += 8
                        }
                        emitPending(spec)
                    }
                }
            }
        }
        emitPending(null)
        flushTable()
        return blocks
    }

    /** 본문 디코딩 + 각 문자별 원시 위치(WCHAR 단위, 컨트롤=8칸) 추적 */
    private fun decodeParaTextWithPos(payload: ByteArray): Pair<String, IntArray> {
        val sb = StringBuilder()
        val poss = mutableListOf<Int>()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        var raw = 0
        while (bb.remaining() >= 2) {
            val ch = bb.short.toInt() and 0xFFFF
            if (ch < 32) {
                when (ch) {
                    in EXTENDED_CTRL, in INLINE_CTRL -> {
                        val skip = minOf(14, bb.remaining())
                        bb.position(bb.position() + skip)
                        raw += 8
                        continue
                    }
                    10, 13 -> { sb.append('\n'); poss.add(raw) }
                    24 -> { sb.append('-'); poss.add(raw) }
                    30, 31 -> { sb.append(' '); poss.add(raw) }
                }
                raw += 1
                continue
            }
            sb.append(ch.toChar())
            poss.add(raw)
            raw += 1
        }
        return sb.toString() to poss.toIntArray()
    }

    /** 병합으로 비어 있는 행/열을 제거해 읽기 좋은 표로 압축 */
    private fun compactTable(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return rows
        val keptRows = rows.filter { r -> r.any { it.isNotBlank() } }
        if (keptRows.isEmpty()) return emptyList()
        val colCount = keptRows[0].size
        val keptCols = (0 until colCount).filter { c -> keptRows.any { it[c].isNotBlank() } }
        return keptRows.map { r -> keptCols.map { r[it] } }
    }

    private fun inflateRaw(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && inflater.needsInput()) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }

    private inline fun parseRecords(
        data: ByteArray,
        onRecord: (tag: Int, level: Int, payload: ByteArray) -> Unit
    ) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (bb.remaining() >= 4) {
            val hdr = bb.int
            val tag = hdr and 0x3FF
            val level = (hdr ushr 10) and 0x3FF
            var size = (hdr ushr 20) and 0xFFF
            if (size == 0xFFF) {
                if (bb.remaining() < 4) break
                size = bb.int
            }
            if (size < 0 || size > bb.remaining()) break
            val payload = ByteArray(size)
            bb.get(payload)
            onRecord(tag, level, payload)
        }
    }

    /** UTF-16LE 본문 디코딩. 코드 32 미만은 HWP 제어문자. */
    private fun decodeParaText(payload: ByteArray): String {
        val sb = StringBuilder()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        while (bb.remaining() >= 2) {
            val ch = bb.short.toInt() and 0xFFFF
            if (ch < 32) {
                when (ch) {
                    in EXTENDED_CTRL, in INLINE_CTRL -> {
                        // 컨트롤은 자신 포함 8 WCHAR(16바이트) 차지 → 나머지 14바이트 스킵
                        val skip = minOf(14, bb.remaining())
                        bb.position(bb.position() + skip)
                    }
                    10, 13 -> sb.append('\n')
                    24 -> sb.append('-')
                    30, 31 -> sb.append(' ')
                }
                continue
            }
            sb.append(ch.toChar())
        }
        return sb.toString()
    }

    companion object {
        private const val HWPTAG_BIN_DATA = 18
        private const val HWPTAG_CHAR_SHAPE = 21
        private const val HWPTAG_PARA_SHAPE = 25
        private const val HWPTAG_PARA_HEADER = 66
        private const val HWPTAG_PARA_TEXT = 67
        private const val HWPTAG_PARA_CHAR_SHAPE = 68
        private const val HWPTAG_CTRL_HEADER = 71
        private const val HWPTAG_LIST_HEADER = 72
        private const val HWPTAG_TABLE = 77
        private const val HWPTAG_SHAPE_PICTURE = 85

        private fun ctrlId(a: Char, b: Char, c: Char, d: Char) =
            (a.code shl 24) or (b.code shl 16) or (c.code shl 8) or d.code

        private val CTRL_TBL = ctrlId('t', 'b', 'l', ' ')

        /** 머리말/꼬리말/각주/미주 — 본문에서 제외 */
        private val EXCLUDED_CTRLS = setOf(
            ctrlId('h', 'e', 'a', 'd'),
            ctrlId('f', 'o', 'o', 't'),
            ctrlId('f', 'n', ' ', ' '),
            ctrlId('e', 'n', ' ', ' ')
        )

        private val EXTENDED_CTRL = setOf(1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23)
        private val INLINE_CTRL = setOf(4, 5, 6, 7, 8, 9, 19, 20)
    }
}

/**
 * 최소 CFB(Compound File Binary, OLE2) 리더.
 * FAT/miniFAT 체인과 디렉터리 트리를 해석해 스토리지/스트림을 읽는다.
 */
internal class CfbReader(private val data: ByteArray) {

    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<DirEntry>
    private val miniStream: ByteArray

    internal class DirEntry(
        val name: String,
        val type: Int,          // 1=storage, 2=stream, 5=root
        val startSector: Int,
        val size: Long,
        val childId: Int,
        val leftId: Int,
        val rightId: Int
    )

    init {
        require(data.size >= 512) { "CFB 헤더가 없습니다" }
        val sig = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )
        require(data.copyOf(8).contentEquals(sig)) { "CFB 시그니처 불일치" }
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        sectorSize = 1 shl bb.getShort(30).toInt()
        miniSectorSize = 1 shl bb.getShort(32).toInt()
        val numFatSectors = bb.getInt(44)
        val firstDirSector = bb.getInt(48)
        miniCutoff = bb.getInt(56)
        val firstMiniFatSector = bb.getInt(60)
        val numMiniFatSectors = bb.getInt(64)
        val firstDifatSector = bb.getInt(68)

        // DIFAT: 헤더 내 109개 + 체인 섹터
        val fatSectorIds = mutableListOf<Int>()
        for (i in 0 until 109) {
            val sid = bb.getInt(76 + i * 4)
            if (sid >= 0) fatSectorIds.add(sid)
        }
        var difatSid = firstDifatSector
        while (difatSid >= 0 && fatSectorIds.size < numFatSectors) {
            val base = sectorOffset(difatSid)
            val perSector = sectorSize / 4 - 1
            for (i in 0 until perSector) {
                val sid = bb.getInt(base + i * 4)
                if (sid >= 0) fatSectorIds.add(sid)
            }
            difatSid = bb.getInt(base + perSector * 4)
        }

        // FAT 로드
        val fatList = IntArray(fatSectorIds.size * (sectorSize / 4))
        var idx = 0
        for (sid in fatSectorIds) {
            val base = sectorOffset(sid)
            for (i in 0 until sectorSize / 4) fatList[idx++] = bb.getInt(base + i * 4)
        }
        fat = fatList

        // 디렉터리 로드
        val dirBytes = readChain(firstDirSector, Long.MAX_VALUE)
        val list = mutableListOf<DirEntry>()
        var off = 0
        while (off + 128 <= dirBytes.size) {
            val ebb = ByteBuffer.wrap(dirBytes, off, 128).order(ByteOrder.LITTLE_ENDIAN)
            val nameLen = (ebb.getShort(off + 64).toInt() and 0xFFFF).coerceIn(0, 64)
            val name = if (nameLen >= 2)
                String(dirBytes, off, nameLen - 2, Charsets.UTF_16LE)
            else ""
            list.add(
                DirEntry(
                    name = name,
                    type = dirBytes[off + 66].toInt(),
                    startSector = ebb.getInt(off + 116),
                    size = ebb.getInt(off + 120).toLong() and 0xFFFFFFFFL,
                    childId = ebb.getInt(off + 76),
                    leftId = ebb.getInt(off + 68),
                    rightId = ebb.getInt(off + 72)
                )
            )
            off += 128
        }
        entries = list

        // miniFAT + 미니 스트림(루트 엔트리 체인)
        miniFat = if (firstMiniFatSector >= 0 && numMiniFatSectors > 0) {
            val mfBytes = readChain(firstMiniFatSector, Long.MAX_VALUE)
            IntArray(mfBytes.size / 4) {
                ByteBuffer.wrap(mfBytes, it * 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            }
        } else IntArray(0)
        val root = entries.firstOrNull { it.type == 5 }
        miniStream = if (root != null && root.startSector >= 0)
            readChain(root.startSector, root.size) else ByteArray(0)
    }

    private fun sectorOffset(sid: Int) = (sid + 1) * sectorSize

    private fun readChain(start: Int, maxSize: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var sid = start
        var guard = 0
        while (sid >= 0 && guard++ < fat.size + 2) {
            val offset = sectorOffset(sid)
            if (offset + sectorSize > data.size) {
                out.write(data, offset, (data.size - offset).coerceAtLeast(0)); break
            }
            out.write(data, offset, sectorSize)
            sid = fat.getOrElse(sid) { -2 }
        }
        val bytes = out.toByteArray()
        return if (maxSize < bytes.size) bytes.copyOf(maxSize.toInt()) else bytes
    }

    private fun readMiniChain(start: Int, size: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var sid = start
        var guard = 0
        while (sid >= 0 && guard++ < miniFat.size + 2) {
            val offset = sid * miniSectorSize
            if (offset + miniSectorSize > miniStream.size) break
            out.write(miniStream, offset, miniSectorSize)
            sid = miniFat.getOrElse(sid) { -2 }
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    private fun readEntry(e: DirEntry): ByteArray =
        if (e.size < miniCutoff) readMiniChain(e.startSector, e.size)
        else readChain(e.startSector, e.size)

    /** 트리에서 자식들 나열 (레드블랙 트리를 단순 순회) */
    private fun childrenOf(parent: DirEntry): List<DirEntry> {
        if (parent.childId < 0) return emptyList()
        val result = mutableListOf<DirEntry>()
        val stack = ArrayDeque<Int>()
        stack.addLast(parent.childId)
        val seen = mutableSetOf<Int>()
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (id < 0 || id >= entries.size || !seen.add(id)) continue
            val e = entries[id]
            result.add(e)
            stack.addLast(e.leftId)
            stack.addLast(e.rightId)
        }
        return result
    }

    private fun findRootChild(name: String): DirEntry? {
        val root = entries.firstOrNull { it.type == 5 } ?: return null
        return childrenOf(root).firstOrNull { it.name == name }
    }

    /** 루트 바로 아래 스트림 읽기 */
    fun readStream(name: String): ByteArray? =
        findRootChild(name)?.takeIf { it.type == 2 }?.let { readEntry(it) }

    /** 스토리지 하위 스트림 읽기 */
    fun readStream(storage: String, name: String): ByteArray? {
        val st = findRootChild(storage)?.takeIf { it.type == 1 } ?: return null
        return childrenOf(st).firstOrNull { it.name == name && it.type == 2 }?.let { readEntry(it) }
    }

    /** 스토리지 하위 스트림 이름 목록 */
    fun streamsUnder(storage: String): List<String> {
        val st = findRootChild(storage)?.takeIf { it.type == 1 } ?: return emptyList()
        return childrenOf(st).filter { it.type == 2 }.map { it.name }
    }
}

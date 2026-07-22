package com.aimultiviewer.ui.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimultiviewer.domain.model.DocBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 구조화 블록(제목/문단/표/이미지) 렌더러.
 * [pageStyle]이면 상용 뷰어처럼 회색 배경 위 흰색 종이(페이지) 카드에 그린다.
 * 서식 문단(RichPara)은 글자 크기/굵기/기울임/색/정렬을 원본대로 반영한다.
 */
@Composable
fun StructuredDocView(
    blocks: List<DocBlock>,
    modifier: Modifier = Modifier,
    pageStyle: Boolean = false
) {
    if (pageStyle) {
        LazyColumn(
            modifier = modifier.background(Color(0xFFE8EAED)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
        ) {
            items(blocks.size) { i ->
                // 흰 종이가 이어져 보이도록 블록마다 흰 배경 + 좌우 여백
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 22.dp)
                        .padding(
                            top = if (i == 0) 30.dp else 0.dp,
                            bottom = if (i == blocks.size - 1) 30.dp else 0.dp
                        )
                ) {
                    RenderBlock(blocks[i])
                }
            }
        }
    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
            items(blocks.size) { i -> RenderBlock(blocks[i]) }
        }
    }
}

@Composable
private fun RenderBlock(b: DocBlock) {
    when (b) {
        is DocBlock.Heading -> HeadingBlock(b)
        is DocBlock.Para -> ParaBlock(b)
        is DocBlock.RichPara -> RichParaBlock(b)
        is DocBlock.Table -> TableBlock(b)
        is DocBlock.Image -> ImageBlock(b)
        is DocBlock.Note -> NoteBlock(b)
    }
}

@Composable
private fun RichParaBlock(b: DocBlock.RichPara) {
    val annotated = buildAnnotatedString {
        for (run in b.runs) {
            pushStyle(
                SpanStyle(
                    fontSize = run.sizePt?.sp ?: 12.sp,
                    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.underline) TextDecoration.Underline else TextDecoration.None,
                    color = run.colorRgb?.let { Color(0xFF000000L or it.toLong()) }
                        ?: Color(0xFF1F1F1F)
                )
            )
            append(run.text)
            pop()
        }
    }
    val maxPt = b.runs.mapNotNull { it.sizePt }.maxOrNull() ?: 12f
    Text(
        text = annotated,
        textAlign = when (b.align) {
            2 -> TextAlign.End
            3 -> TextAlign.Center
            0 -> TextAlign.Justify
            else -> TextAlign.Start
        },
        lineHeight = (maxPt * 1.55f * b.spacing.coerceIn(0.8f, 2f)).sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (maxPt * 0.18f * b.spacing).dp)
    )
}

@Composable
private fun ImageBlock(b: DocBlock.Image) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, b.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(b.path) }.getOrNull()
        }
    }
    bitmap?.let {
        val frac = (b.widthFraction ?: 1f).coerceIn(0.2f, 1f)
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth(frac)
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun HeadingBlock(b: DocBlock.Heading) {
    val style = when (b.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text = b.text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (b.level <= 3) 18.dp else 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun ParaBlock(b: DocBlock.Para) {
    Text(
        text = if (b.bullet) "•  ${b.text}" else b.text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 3.dp, horizontal = if (b.bullet) 6.dp else 0.dp)
    )
}

@Composable
private fun NoteBlock(b: DocBlock.Note) {
    Text(
        text = b.text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    )
}

@Composable
private fun TableBlock(b: DocBlock.Table) {
    val colWidths = remember(b) { computeColumnWidths(b.rows) }
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val labelBg = Color(0xFFE8F4F8)

    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor)
    ) {
        b.rows.forEachIndexed { rowIdx, row ->
            Row {
                for (c in colWidths.indices) {
                    val cell = row.getOrNull(c) ?: ""
                    val isHeaderRow = rowIdx == 0
                    val isLabelCol = c == 0 && colWidths.size >= 2 && cell.length <= 12
                    Box(
                        modifier = Modifier
                            .width(colWidths[c].dp)
                            .then(
                                when {
                                    isHeaderRow -> Modifier.background(headerBg)
                                    isLabelCol -> Modifier.background(labelBg)
                                    else -> Modifier
                                }
                            )
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = if (isHeaderRow || isLabelCol) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 8
                        )
                    }
                }
            }
        }
    }
}

/** 열 너비(dp): 한글 2배 가중, 최소 폭을 넓혀 양식 표가 세로로 깨지지 않게 */
private fun computeColumnWidths(rows: List<List<String>>): List<Int> {
    val colCount = rows.maxOf { it.size }
    return List(colCount) { c ->
        var maxUnits = 0
        for (r in rows) {
            val cell = r.getOrNull(c) ?: continue
            for (line in cell.lineSequence()) {
                val units = line.sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 as Int }
                if (units > maxUnits) maxUnits = units
            }
        }
        val minW = if (c == 0 && colCount >= 2) 88 else 72
        (maxUnits * 8 + 20).coerceIn(minW, 280)
    }
}

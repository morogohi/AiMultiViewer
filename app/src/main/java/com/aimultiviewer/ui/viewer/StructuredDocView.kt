package com.aimultiviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimultiviewer.domain.model.DocBlock

/**
 * 구조화 블록(제목/문단/표) 렌더러.
 * 표는 열 너비를 내용 길이에 맞춰 계산한 그리드로, 가로 스크롤을 지원한다.
 */
@Composable
fun StructuredDocView(blocks: List<DocBlock>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(blocks.size) { i ->
            when (val b = blocks[i]) {
                is DocBlock.Heading -> HeadingBlock(b)
                is DocBlock.Para -> ParaBlock(b)
                is DocBlock.Table -> TableBlock(b)
                is DocBlock.Note -> NoteBlock(b)
            }
        }
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
                    Box(
                        modifier = Modifier
                            .width(colWidths[c].dp)
                            .then(
                                if (rowIdx == 0) Modifier.background(headerBg) else Modifier
                            )
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = if (rowIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 6
                        )
                    }
                }
            }
        }
    }
}

/** 열 너비(dp): 열의 최장 내용 기준 60~240dp, 한글은 글자당 더 넓게 */
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
        (maxUnits * 7 + 14).coerceIn(56, 240)
    }
}

package com.aimultiviewer.ui.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimultiviewer.domain.model.SlideDeck
import com.aimultiviewer.domain.model.SlideElement
import com.aimultiviewer.domain.model.SlidePara
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PPTX 슬라이드를 원본 위치/크기/서식 그대로 재현하는 캔버스 뷰어.
 * 각 슬라이드는 원본 종횡비의 흰색 카드로 그려지고 도형은 비율 좌표로 배치된다.
 */
@Composable
fun SlideDeckViewer(deck: SlideDeck, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.background(Color(0xFFE8EAED)),
        contentPadding = PaddingValues(10.dp)
    ) {
        items(deck.slides.size) { i ->
            val slide = deck.slides[i]
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Text(
                    text = "슬라이드 ${slide.number} / ${deck.slides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5F6368),
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                )
                SlideCanvas(deck, i)
                slide.note?.let { note ->
                    Text(
                        text = "발표자 노트: $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6368),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFFF1F3F4))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideCanvas(deck: SlideDeck, index: Int) {
    val slide = deck.slides[index]
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(deck.aspectRatio)
            .background(Color.White)
            .border(1.dp, Color(0xFFDADCE0))
    ) {
        val cardW = maxWidth
        val cardH = maxHeight
        // 글꼴 스케일: 슬라이드 pt 크기 → 화면 dp 크기
        val fontScale = cardW.value / deck.widthPt

        for (el in slide.elements) {
            val x = cardW * el.x
            val y = cardH * el.y
            val w = cardW * el.w.coerceAtLeast(0.01f)
            val h = cardH * el.h.coerceAtLeast(0.01f)
            when (el) {
                is SlideElement.Picture -> SlidePicture(
                    path = el.path,
                    modifier = Modifier.offset(x, y).size(w, h)
                )
                is SlideElement.TextBox -> Column(Modifier.offset(x, y).size(w, h)) {
                    el.paragraphs.forEach { p -> SlideParaText(p, fontScale) }
                }
                is SlideElement.TableBox -> SlideTable(
                    rows = el.rows,
                    fontScale = fontScale,
                    modifier = Modifier.offset(x, y).size(w, h)
                )
            }
        }
    }
}

@Composable
private fun SlideParaText(p: SlidePara, fontScale: Float) {
    val defaultPt = 14f
    val annotated: AnnotatedString = buildAnnotatedString {
        if (p.bullet) append("• ")
        for (run in p.runs) {
            pushStyle(
                SpanStyle(
                    fontSize = ((run.sizePt ?: defaultPt) * fontScale).sp,
                    fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    color = run.colorRgb?.let { Color(0xFF000000L or it.toLong()) }
                        ?: Color(0xFF202124)
                )
            )
            append(run.text)
            pop()
        }
    }
    val maxPt = p.runs.mapNotNull { it.sizePt }.maxOrNull() ?: defaultPt
    Text(
        text = annotated,
        textAlign = when (p.align) {
            "ctr" -> TextAlign.Center
            "r" -> TextAlign.End
            else -> TextAlign.Start
        },
        lineHeight = (maxPt * fontScale * 1.25f).sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (p.level * 10).dp)
    )
}

@Composable
private fun SlidePicture(path: String, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SlideTable(rows: List<List<String>>, fontScale: Float, modifier: Modifier) {
    val cols = rows.maxOf { it.size }
    Column(modifier.border(0.5.dp, Color(0xFFBDC1C6))) {
        rows.forEachIndexed { rIdx, row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until cols) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (rIdx == 0) Color(0xFFF1F3F4) else Color.White)
                            .border(0.25.dp, Color(0xFFBDC1C6))
                            .padding(2.dp)
                    ) {
                        Text(
                            text = row.getOrNull(c) ?: "",
                            fontSize = (10f * fontScale).sp,
                            lineHeight = (12f * fontScale).sp,
                            fontWeight = if (rIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = Color(0xFF202124)
                        )
                    }
                }
            }
        }
    }
}

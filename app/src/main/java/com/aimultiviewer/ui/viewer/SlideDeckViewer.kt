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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
 * PPTX 슬라이드를 원본 위치/크기/서식/배경/도형 채우기까지 재현하는 캔버스 뷰어.
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
    val bg = slide.backgroundRgb?.let { Color(0xFF000000L or it.toLong()) } ?: Color.White
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(deck.aspectRatio)
            .background(bg)
            .border(1.dp, Color(0xFFDADCE0))
    ) {
        val cardW = maxWidth
        val cardH = maxHeight
        val fontScale = cardW.value / deck.widthPt

        // 도형 → 이미지 → 표 → 텍스트 순으로 겹침 (텍스트가 최상단)
        val ordered = slide.elements.sortedBy {
            when (it) {
                is SlideElement.Shape -> 0
                is SlideElement.Picture -> 1
                is SlideElement.TableBox -> 2
                is SlideElement.TextBox -> 3
            }
        }

        for (el in ordered) {
            val x = cardW * el.x
            val y = cardH * el.y
            val w = cardW * el.w.coerceAtLeast(0.005f)
            val h = cardH * el.h.coerceAtLeast(0.005f)
            val boxMod = Modifier.offset(x, y).size(w, h)
            when (el) {
                is SlideElement.Shape -> ShapeBox(el, boxMod)
                is SlideElement.Picture -> SlidePicture(el.path, boxMod)
                is SlideElement.TextBox -> {
                    val fill = el.fillRgb?.let { Color(0xFF000000L or it.toLong()) }
                    Column(
                        boxMod.then(
                            if (fill != null) Modifier.background(fill) else Modifier
                        ).padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        el.paragraphs.forEach { p -> SlideParaText(p, fontScale) }
                    }
                }
                is SlideElement.TableBox -> SlideTable(el.rows, fontScale, boxMod)
            }
        }
    }
}

@Composable
private fun ShapeBox(el: SlideElement.Shape, modifier: Modifier) {
    val fill = el.fillRgb?.let { Color(0xFF000000L or it.toLong()) } ?: Color.Transparent
    val stroke = el.strokeRgb?.let { Color(0xFF000000L or it.toLong()) }
    val shape = when (el.kind) {
        SlideElement.Shape.Kind.ELLIPSE -> CircleShape
        SlideElement.Shape.Kind.ROUND_RECT -> RoundedCornerShape(12)
        SlideElement.Shape.Kind.RECT -> RectangleShape
    }
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .then(
                if (stroke != null && el.strokePt > 0f)
                    Modifier.border(el.strokePt.dp.coerceAtLeast(0.5.dp), stroke, shape)
                else Modifier
            )
    )
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
            contentScale = ContentScale.FillBounds
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

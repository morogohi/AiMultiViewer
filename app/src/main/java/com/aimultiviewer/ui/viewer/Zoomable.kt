package com.aimultiviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 두 손가락 핀치만 가로채서 확대율을 갱신하는 제스처.
 * Initial 패스에서 처리하므로 한 손가락 스크롤은 그대로 하위 뷰(LazyColumn 등)로 전달된다.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

@Composable
private fun BoxScope.ZoomBadge(scale: Float, onReset: () -> Unit) {
    if (scale != 1f) {
        Text(
            text = "${(scale * 100).roundToInt()}%  ×",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * 텍스트 문서용 확대: 핀치하면 글자·요소 크기가 커지고 내용이 화면 폭에 맞게
 * 다시 흐른다(리플로우). 가로 스크롤 없이 읽기 편한 확대 방식.
 */
@Composable
fun ZoomableDoc(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    val base = LocalDensity.current
    Box(modifier.fillMaxSize().pinchToZoom { z -> scale = (scale * z).coerceIn(0.7f, 2.5f) }) {
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * scale, base.fontScale)
        ) {
            content()
        }
        ZoomBadge(scale) { scale = 1f }
    }
}

/**
 * PDF·슬라이드처럼 페이지 비율이 고정된 콘텐츠용 확대:
 * 핀치하면 캔버스 폭 자체가 커지고 가로 스크롤로 이동한다.
 */
@Composable
fun ZoomableCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    BoxWithConstraints(
        modifier.fillMaxSize().pinchToZoom { z -> scale = (scale * z).coerceIn(1f, 4f) }
    ) {
        val baseWidth = maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState(), enabled = scale > 1f)
        ) {
            Box(Modifier.width(baseWidth * scale)) {
                content()
            }
        }
        ZoomBadge(scale) { scale = 1f }
    }
}

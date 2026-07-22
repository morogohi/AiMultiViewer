package com.aimultiviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 두 손가락 핀치/이동만 가로채는 제스처. Initial 패스에서 처리하므로
 * 한 손가락 스크롤은 그대로 하위 뷰(LazyColumn 등)로 전달된다.
 */
private fun Modifier.pinchGesture(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid()
                if (zoom != 1f || pan != Offset.Zero) onGesture(centroid, pan, zoom)
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
 * 문서 화면을 렌더링된 그대로(이미지처럼) 확대·이동하는 줌.
 * 글자 재배치 없이 픽셀 단위로 커지므로 확대 중 글자가 깨지지 않는다.
 * - 두 손가락 핀치: 확대(1~4배), 두 손가락 드래그: 이동
 * - 한 손가락: 평소처럼 세로 스크롤
 */
@Composable
fun ZoomableDoc(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(Offset.Zero) }

    fun clampOffset(o: Offset, s: Float): Offset {
        val minX = boxSize.x * (1f - s)
        val minY = boxSize.y * (1f - s)
        return Offset(o.x.coerceIn(minX, 0f), o.y.coerceIn(minY, 0f))
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .pinchGesture { centroid, pan, zoom ->
                val newScale = (scale * zoom).coerceIn(1f, 4f)
                val applied = newScale / scale
                // 핀치 중심이 화면에서 고정되도록 오프셋 보정 + 두 손가락 이동
                val newOffset = Offset(
                    centroid.x - (centroid.x - offset.x) * applied + pan.x,
                    centroid.y - (centroid.y - offset.y) * applied + pan.y
                )
                scale = newScale
                offset = clampOffset(newOffset, newScale)
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    boxSize = Offset(size.width, size.height)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            content()
        }
        ZoomBadge(scale) { scale = 1f; offset = Offset.Zero }
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
        modifier.fillMaxSize().pinchGesture { _, _, z -> scale = (scale * z).coerceIn(1f, 4f) }
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

package com.aimultiviewer.ui.viewer

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이미지 뷰어: 핀치 줌(1x~6x), 팬(드래그 이동), 더블탭 확대/원복.
 * 하단에 OCR 판독 텍스트 패널(접기/펼치기)을 함께 보여준다.
 */
@Composable
fun ImageViewer(uriString: String, ocrText: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }

    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var ocrExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(Color(0xFF202124))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        val maxX = size.width * (newScale - 1f) / 2f
                        val maxY = size.height * (newScale - 1f) / 2f
                        offset = if (newScale == 1f) Offset.Zero
                        else Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val s = 3f
                                val maxX = size.width * (s - 1f) / 2f
                                val maxY = size.height * (s - 1f) / 2f
                                scale = s
                                offset = Offset(
                                    ((size.width / 2f - tap.x) * s).coerceIn(-maxX, maxX),
                                    ((size.height / 2f - tap.y) * s).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    )
                }
        ) {
            when (val bmp = bitmap) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            if (scale > 1f) {
                Text(
                    text = "${"%.1f".format(scale)}x",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (ocrText.isNotBlank()) {
            Surface(tonalElevation = 2.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { ocrExpanded = !ocrExpanded }
                ) {
                    HorizontalDivider()
                    Text(
                        text = if (ocrExpanded) "▼ 판독된 텍스트 (OCR)" else "▲ 판독된 텍스트 (OCR) — 탭하여 펼치기",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    if (ocrExpanded) {
                        Text(
                            text = ocrText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

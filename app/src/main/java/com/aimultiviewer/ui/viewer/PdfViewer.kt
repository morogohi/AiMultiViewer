package com.aimultiviewer.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@androidx.compose.runtime.Composable
fun PdfViewer(uriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uri = remember(uriString) { Uri.parse(uriString) }

    val pages by produceState<List<Bitmap>?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            renderPages(context, uri)
        }
    }

    when (val p = pages) {
        null -> androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        else -> if (p.isEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("PDF를 표시할 수 없습니다.") }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(p) { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

private fun renderPages(context: android.content.Context, uri: Uri, maxPages: Int = 50): List<Bitmap> {
    val result = mutableListOf<Bitmap>()
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return result
    pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val count = minOf(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                renderer.openPage(i).use { page ->
                    val scale = 2
                    val width = page.width * scale
                    val height = page.height * scale
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bmp)
                }
            }
        }
    }
    return result
}

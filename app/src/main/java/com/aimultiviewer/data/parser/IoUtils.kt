package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

internal object IoUtils {

    fun readBytes(context: Context, uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "스트림을 열 수 없습니다: $uri" }
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** 한글 문서 대응: UTF-8(BOM) → UTF-8 → EUC-KR(CP949) 순으로 추정 디코딩. */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = runCatching { strictUtf8(bytes) }.getOrNull()
        if (utf8 != null) return utf8
        return runCatching { String(bytes, Charset.forName("EUC-KR")) }
            .getOrElse { String(bytes, Charsets.UTF_8) }
    }

    private fun strictUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
}

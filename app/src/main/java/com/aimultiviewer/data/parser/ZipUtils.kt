package com.aimultiviewer.data.parser

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

internal object ZipUtils {

    /** ZIP을 한 번 순회하며 [predicate]에 맞는 엔트리들을 (이름 → 바이트) 맵으로 읽는다. */
    fun readEntries(
        context: Context,
        uri: Uri,
        predicate: (String) -> Boolean
    ): Map<String, ByteArray> {
        val result = sortedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && predicate(entry.name)) {
                        result[entry.name] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return result
    }
}

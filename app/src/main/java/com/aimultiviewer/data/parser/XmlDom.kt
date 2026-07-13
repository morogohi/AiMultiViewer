package com.aimultiviewer.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * 경량 XML DOM. 네임스페이스 접두어(p:, a:, r:)는 요소명에서 제거하고
 * 속성은 원래 이름 그대로 보관한다 (r:embed 등).
 */
internal class XmlNode(
    val name: String,
    private val attrs: Map<String, String>
) {
    val children = mutableListOf<XmlNode>()
    var text: String = ""

    fun attr(local: String): String? =
        attrs[local] ?: attrs.entries.firstOrNull { it.key.substringAfter(':') == local }?.value

    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    fun childrenNamed(name: String): List<XmlNode> = children.filter { it.name == name }

    /** 전체 하위 트리에서 이름이 일치하는 첫 노드 (깊이 우선) */
    fun find(name: String): XmlNode? {
        for (c in children) {
            if (c.name == name) return c
            c.find(name)?.let { return it }
        }
        return null
    }

    /** 전체 하위 트리에서 이름이 일치하는 모든 노드 */
    fun findAll(name: String, out: MutableList<XmlNode> = mutableListOf()): List<XmlNode> {
        for (c in children) {
            if (c.name == name) out.add(c)
            c.findAll(name, out)
        }
        return out
    }

    /** 하위의 모든 텍스트를 이어붙인다 */
    fun allText(): String = buildString { collectText(this) }

    private fun collectText(sb: StringBuilder) {
        if (text.isNotEmpty()) sb.append(text)
        for (c in children) c.collectText(sb)
    }
}

internal object XmlDom {
    fun parse(xml: String): XmlNode? {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val stack = ArrayDeque<XmlNode>()
        var root: XmlNode? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val attrs = buildMap {
                        for (i in 0 until parser.attributeCount) {
                            put(parser.getAttributeName(i), parser.getAttributeValue(i))
                        }
                    }
                    val node = XmlNode(parser.name.substringAfter(':'), attrs)
                    stack.lastOrNull()?.children?.add(node) ?: run { root = node }
                    stack.addLast(node)
                }
                XmlPullParser.TEXT -> stack.lastOrNull()?.let { it.text += parser.text }
                XmlPullParser.END_TAG -> stack.removeLastOrNull()
            }
        }
        return root
    }
}

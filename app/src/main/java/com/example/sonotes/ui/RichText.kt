package com.example.sonotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONArray
import org.json.JSONObject

const val BULLET = "• "

private val numberedRegex = Regex("^(\\d{1,2})\\. ")

data class CharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: Int = 0
)

data class RichContent(
    val text: String = "",
    val styles: List<CharStyle> = emptyList()
)

data class EditorState(
    val value: TextFieldValue = TextFieldValue(""),
    val content: RichContent = RichContent(),
    val typing: CharStyle = CharStyle()
)

fun RichContent.replace(start: Int, end: Int, insert: String, style: CharStyle): RichContent {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    val safeStyles = if (styles.size == text.length) styles else List(text.length) { CharStyle() }
    val newText = text.substring(0, safeStart) + insert + text.substring(safeEnd)
    val newStyles = safeStyles.subList(0, safeStart) + List(insert.length) { style } + safeStyles.subList(safeEnd, safeStyles.size)
    return RichContent(newText, newStyles)
}

fun RichContent.toAnnotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    val safeStyles = if (styles.size == text.length) styles else List(text.length) { CharStyle() }
    var i = 0
    while (i < safeStyles.size) {
        val s = safeStyles[i]
        var j = i
        while (j < safeStyles.size && safeStyles[j] == s) j++
        if (s != CharStyle()) {
            addStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    color = if (s.color != 0) Color(s.color) else Color.Unspecified
                ),
                i,
                j
            )
        }
        i = j
    }
}

fun RichContent.toJson(): String {
    val runs = JSONArray()
    val safeStyles = if (styles.size == text.length) styles else List(text.length) { CharStyle() }
    var i = 0
    while (i < safeStyles.size) {
        val s = safeStyles[i]
        var j = i
        while (j < safeStyles.size && safeStyles[j] == s) j++
        if (s != CharStyle()) {
            runs.put(
                JSONArray()
                    .put(i)
                    .put(j)
                    .put(if (s.bold) 1 else 0)
                    .put(if (s.italic) 1 else 0)
                    .put(s.color)
            )
        }
        i = j
    }
    return JSONObject().put("t", text).put("s", runs).toString()
}

fun richContentFromBody(body: String): RichContent {
    val plain = RichContent(body, List(body.length) { CharStyle() })
    if (!body.startsWith("{")) return plain
    return try {
        val obj = JSONObject(body)
        val text = obj.getString("t")
        val runs = obj.getJSONArray("s")
        val styles = MutableList(text.length) { CharStyle() }
        for (k in 0 until runs.length()) {
            val r = runs.getJSONArray(k)
            val style = CharStyle(r.getInt(2) == 1, r.getInt(3) == 1, r.getInt(4))
            for (x in r.getInt(0) until minOf(r.getInt(1), text.length)) styles[x] = style
        }
        RichContent(text, styles)
    } catch (_: Exception) {
        plain
    }
}

private fun RichContent.styleAt(sel: TextRange): CharStyle {
    val safeStyles = if (styles.size == text.length) styles else List(text.length) { CharStyle() }
    if (safeStyles.isEmpty()) return CharStyle()
    return if (!sel.collapsed) {
        safeStyles[sel.min.coerceIn(0, safeStyles.lastIndex)]
    } else if (sel.start > 0) {
        safeStyles[(sel.start - 1).coerceIn(0, safeStyles.lastIndex)]
    } else {
        CharStyle()
    }
}

private fun listPrefixLen(line: String): Int =
    if (line.startsWith(BULLET)) BULLET.length else numberedRegex.find(line)?.value?.length ?: 0

private fun EditorState.rebuild(newPrefix: (Int, String, Int) -> String): EditorState {
    val text = content.text
    val safeStyles = if (content.styles.size == text.length) content.styles else List(text.length) { CharStyle() }
    val lines = text.split("\n")
    val sb = StringBuilder()
    val st = ArrayList<CharStyle>(safeStyles.size + 16)
    val maps = ArrayList<IntArray>(lines.size)
    var oldPos = 0
    var changed = false
    lines.forEachIndexed { i, line ->
        val oldLen = listPrefixLen(line)
        val prefix = newPrefix(i, line, oldLen)
        if (prefix != line.substring(0, oldLen)) changed = true
        maps.add(intArrayOf(oldPos, oldLen, sb.length, prefix.length))
        sb.append(prefix).append(line, oldLen, line.length)
        repeat(prefix.length) { st.add(CharStyle()) }
        st.addAll(safeStyles.subList(oldPos + oldLen, oldPos + line.length))
        if (i < lines.lastIndex) {
            sb.append('\n')
            if (oldPos + line.length < safeStyles.size) {
                st.add(safeStyles[oldPos + line.length])
            } else {
                st.add(CharStyle())
            }
        }
        oldPos += line.length + 1
    }
    if (!changed) return this
    val newText = sb.toString()
    fun map(pos: Int): Int {
        if (maps.isEmpty()) return 0
        var m = maps[0]
        for (x in maps) {
            if (x[0] <= pos) m = x else break
        }
        val off = pos - m[0]
        return if (off >= m[1]) m[2] + m[3] + off - m[1] else m[2] + minOf(off, m[3])
    }
    val sel = value.selection
    val newSel = TextRange(map(sel.start), map(sel.end))
    return copy(value = TextFieldValue(newText, newSel), content = RichContent(newText, st))
}

fun EditorState.renumber(): EditorState {
    if (!content.text.contains(". ")) return this
    var prev = 0
    return rebuild { _, line, oldLen ->
        if (numberedRegex.containsMatchIn(line)) {
            prev += 1
            "$prev. "
        } else {
            prev = 0
            line.substring(0, oldLen)
        }
    }
}

fun EditorState.edit(new: TextFieldValue): EditorState {
    val old = value.text
    val nw = new.text
    val safeStyles = if (content.styles.size == old.length) content.styles else List(old.length) { CharStyle() }
    if (old == nw) {
        val newTyping = if (new.selection != value.selection) content.styleAt(new.selection) else typing
        return copy(value = new, typing = newTyping)
    }
    val cap = (if (nw.length >= old.length) value.selection.min else new.selection.min).coerceIn(0, minOf(old.length, nw.length))
    var p = 0
    while (p < cap && p < old.length && p < nw.length && old[p] == nw[p]) p++
    var s = 0
    val maxS = minOf(old.length - p, nw.length - p)
    while (s < maxS && old[old.length - 1 - s] == nw[nw.length - 1 - s]) s++

    val insertedLen = (nw.length - s - p).coerceAtLeast(0)
    val styleHead = safeStyles.subList(0, p.coerceIn(0, safeStyles.size))
    val styleTail = safeStyles.subList((old.length - s).coerceIn(0, safeStyles.size), safeStyles.size)
    val newStyles = styleHead + List(insertedLen) { typing } + styleTail

    val base = EditorState(new, RichContent(nw, newStyles), typing)

    val removed = old.length - s - p
    if (insertedLen == 1 && nw.getOrNull(p) == '\n' && removed == 0) {
        val lineStart = nw.lastIndexOf('\n', (p - 1).coerceAtLeast(0)) + 1
        if (lineStart in 0..p) {
            val line = nw.substring(lineStart, p)
            val prefix = if (line.startsWith(BULLET)) BULLET else numberedRegex.find(line)?.value
            if (prefix != null) {
                val plain = CharStyle()
                return if (line == prefix) {
                    val updated = base.content.replace(lineStart, p + 1, "", plain)
                    EditorState(TextFieldValue(updated.text, TextRange(lineStart)), updated, typing).renumber()
                } else {
                    val add = if (prefix == BULLET) BULLET else "1. "
                    val updated = base.content.replace(p + 1, p + 1, add, plain)
                    EditorState(
                        TextFieldValue(updated.text, TextRange(p + 1 + add.length)),
                        updated,
                        typing
                    ).renumber()
                }
            }
        }
    }
    return base.renumber()
}

fun EditorState.allSelected(check: (CharStyle) -> Boolean): Boolean {
    val sel = value.selection
    val safeStyles = if (content.styles.size == content.text.length) content.styles else List(content.text.length) { CharStyle() }
    if (sel.collapsed) return check(typing)
    val start = sel.min.coerceIn(0, safeStyles.size)
    val end = sel.max.coerceIn(start, safeStyles.size)
    if (start == end) return check(typing)
    return safeStyles.subList(start, end).all(check)
}

fun EditorState.shownColor(): Int {
    val sel = value.selection
    val safeStyles = if (content.styles.size == content.text.length) content.styles else List(content.text.length) { CharStyle() }
    if (safeStyles.isEmpty()) return typing.color
    return if (sel.collapsed) {
        typing.color
    } else {
        safeStyles[sel.min.coerceIn(0, safeStyles.lastIndex)].color
    }
}

fun EditorState.applyStyle(transform: (CharStyle) -> CharStyle): EditorState {
    val sel = value.selection
    if (sel.collapsed) return copy(typing = transform(typing))
    val safeStyles = if (content.styles.size == content.text.length) content.styles else List(content.text.length) { CharStyle() }
    val start = sel.min.coerceIn(0, safeStyles.size)
    val end = sel.max.coerceIn(start, safeStyles.size)
    val newStyles = safeStyles.mapIndexed { i, s ->
        if (i in start until end) transform(s) else s
    }
    return copy(content = content.copy(styles = newStyles))
}

fun EditorState.currentList(): Int {
    val text = content.text
    if (text.isEmpty()) return 0
    val cursor = value.selection.min.coerceIn(0, text.length)
    val from = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
    val to = text.indexOf('\n', from).let { if (it == -1) text.length else it }
    if (from > to || from !in 0..text.length || to !in 0..text.length) return 0
    val line = text.substring(from, to)
    return when {
        line.startsWith(BULLET) -> 1
        numberedRegex.containsMatchIn(line) -> 2
        else -> 0
    }
}

fun EditorState.toggleList(numbered: Boolean): EditorState {
    val text = content.text
    if (text.isEmpty()) return this
    val sel = value.selection
    val selMin = sel.min.coerceIn(0, text.length)
    val selMax = sel.max.coerceIn(0, text.length)
    val first = text.substring(0, selMin).count { it == '\n' }
    val last = text.substring(0, selMax).count { it == '\n' }
    val lines = text.split("\n")
    if (lines.isEmpty() || first > lines.lastIndex) return this
    val safeLast = last.coerceIn(first, lines.lastIndex)
    val kind: (String) -> Boolean = {
        if (numbered) numberedRegex.containsMatchIn(it) else it.startsWith(BULLET)
    }
    val remove = (first..safeLast).all { kind(lines[it]) }
    return rebuild { i, line, oldLen ->
        if (i in first..safeLast) {
            if (remove) "" else if (numbered) "1. " else BULLET
        } else {
            line.substring(0, oldLen)
        }
    }.renumber()
}

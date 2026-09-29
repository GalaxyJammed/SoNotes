package com.example.sonotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

const val BULLET = "• "
const val TODO_UNCHECKED = "[ ] "
const val TODO_CHECKED = "[x] "

private val numberedRegex = Regex("^(\\d{1,2})\\. ")

private data class LineParseResult(
    val cleanText: String,
    val headerLevel: Int,
    val isChecked: Boolean?,
    val prefixLen: Int
)

data class CharStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val color: Int = 0
)

data class RichContent(
    val text: String = "",
    val styles: List<CharStyle> = emptyList()
)

fun RichContent.safeStyles(): List<CharStyle> = when {
    styles.size == text.length -> styles
    styles.size < text.length -> styles + List(text.length - styles.size) { CharStyle() }
    else -> styles.subList(0, text.length)
}

data class EditorState(
    val value: TextFieldValue = TextFieldValue(""),
    val content: RichContent = RichContent(),
    val typing: CharStyle = CharStyle()
)

fun RichContent.replace(start: Int, end: Int, insert: String, style: CharStyle): RichContent {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    val sStyles = safeStyles()
    val newText = text.substring(0, safeStart) + insert + text.substring(safeEnd)
    val newStyles = sStyles.subList(0, safeStart) + List(insert.length) { style } + sStyles.subList(safeEnd, sStyles.size)
    return RichContent(newText, newStyles)
}

fun RichContent.toAnnotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    val sStyles = safeStyles()

    var i = 0
    while (i < sStyles.size) {
        val s = sStyles[i]
        var j = i
        while (j < sStyles.size && sStyles[j] == s) j++
        if (s != CharStyle()) {
            addStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                    textDecoration = if (s.strikethrough) TextDecoration.LineThrough else null,
                    color = if (s.color != 0) Color(s.color) else Color.Unspecified
                ),
                i,
                j
            )
        }
        i = j
    }

    if (text.isNotEmpty()) {
        val codeBlockRegex = Regex("```[\\s\\S]*?```")
        codeBlockRegex.findAll(text).forEach { m ->
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x18888888)
                ),
                m.range.first,
                m.range.last + 1
            )
        }

        val lines = text.split('\n')
        var lineStart = 0

        lines.forEach { line ->
            val lineEnd = lineStart + line.length

            if (line.startsWith("# ")) {
                addStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), lineStart, lineEnd)
            } else if (line.startsWith("## ")) {
                addStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), lineStart, lineEnd)
            } else if (line.startsWith("### ")) {
                addStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold), lineStart, lineEnd)
            }

            if (line.startsWith("[x] ") || line.startsWith("[X] ")) {
                addStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = Color.Gray
                    ),
                    lineStart + 4,
                    lineEnd
                )
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF388E3C)),
                    lineStart,
                    (lineStart + 4).coerceAtMost(lineEnd)
                )
            } else if (line.startsWith("[ ] ")) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1976D2)),
                    lineStart,
                    (lineStart + 4).coerceAtMost(lineEnd)
                )
            }

            lineStart = lineEnd + 1
        }
    }
}

fun RichContent.toCleanAnnotated(offsetShift: Int = 0): AnnotatedString = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString
    val lines = text.split('\n')
    val sStyles = safeStyles()

    var isFirst = true
    var origLineStart = 0

    lines.forEach { rawLine ->
        val lineEnd = origLineStart + rawLine.length

        val line = rawLine.replace(Regex("!\\[(.*?)\\]\\((.*?)\\)"), "").trimEnd()

        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            origLineStart = lineEnd + 1
            return@forEach
        }

        if (!isFirst) append("\n")
        isFirst = false

        val isInlineCode = line.startsWith("```") && line.endsWith("```") && line.length >= 6
        val cleanLineText = if (isInlineCode) line.substring(3, line.length - 3) else line

        val parsed = when {
            cleanLineText.startsWith("# ") -> LineParseResult(cleanLineText.substring(2), 1, null, 2)
            cleanLineText.startsWith("## ") -> LineParseResult(cleanLineText.substring(3), 2, null, 3)
            cleanLineText.startsWith("### ") -> LineParseResult(cleanLineText.substring(4), 3, null, 4)
            cleanLineText.startsWith("[x] ") || cleanLineText.startsWith("[X] ") -> LineParseResult("☑ " + cleanLineText.substring(4), 0, true, 4)
            cleanLineText.startsWith("[ ] ") -> LineParseResult("☐ " + cleanLineText.substring(4), 0, false, 4)
            else -> LineParseResult(cleanLineText, 0, null, 0)
        }

        val cleanText = parsed.cleanText
        val headerLevel = parsed.headerLevel
        val isChecked = parsed.isChecked
        val prefixLen = parsed.prefixLen

        val lineContentStart = length
        append(cleanText)
        val lineContentEnd = length

        if (isChecked != null) {
            addStringAnnotation(
                tag = "TODO_TOGGLE",
                annotation = (offsetShift + origLineStart).toString(),
                start = lineContentStart,
                end = lineContentEnd
            )
        }

        if (isInlineCode) {
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x18888888)
                ),
                lineContentStart,
                lineContentEnd
            )
        }

        val rawOffset = origLineStart + prefixLen + (if (isInlineCode) 3 else 0)
        for (charIdx in 0 until cleanText.length) {
            val origIdx = (rawOffset + charIdx).coerceIn(0, sStyles.lastIndex.coerceAtLeast(0))
            val s = sStyles.getOrNull(origIdx) ?: CharStyle()
            if (s != CharStyle()) {
                val cPos = lineContentStart + charIdx
                addStyle(
                    SpanStyle(
                        fontWeight = if (s.bold) FontWeight.Bold else null,
                        fontStyle = if (s.italic) FontStyle.Italic else null,
                        textDecoration = if (s.strikethrough) TextDecoration.LineThrough else null,
                        color = if (s.color != 0) Color(s.color) else Color.Unspecified
                    ),
                    cPos,
                    cPos + 1
                )
            }
        }

        when (headerLevel) {
            1 -> addStyle(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), lineContentStart, lineContentEnd)
            2 -> addStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), lineContentStart, lineContentEnd)
            3 -> addStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold), lineContentStart, lineContentEnd)
        }

        if (isChecked == true) {
            addStyle(
                SpanStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = Color.Gray
                ),
                lineContentStart,
                lineContentEnd
            )
        } else if (isChecked == false) {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1976D2)),
                lineContentStart,
                (lineContentStart + 2).coerceAtMost(lineContentEnd)
            )
        }

        origLineStart = lineEnd + 1
    }
}

fun RichContent.toJson(): String {
    val runs = JSONArray()
    val sStyles = safeStyles()
    var i = 0
    while (i < sStyles.size) {
        val s = sStyles[i]
        var j = i
        while (j < sStyles.size && sStyles[j] == s) j++
        if (s != CharStyle()) {
            runs.put(
                JSONArray()
                    .put(i)
                    .put(j)
                    .put(if (s.bold) 1 else 0)
                    .put(if (s.italic) 1 else 0)
                    .put(s.color)
                    .put(if (s.strikethrough) 1 else 0)
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
            val strikethrough = if (r.length() > 5) r.getInt(5) == 1 else false
            val style = CharStyle(r.getInt(2) == 1, r.getInt(3) == 1, strikethrough, r.getInt(4))
            for (x in r.getInt(0) until minOf(r.getInt(1), text.length)) styles[x] = style
        }
        RichContent(text, styles)
    } catch (_: Exception) {
        plain
    }
}

private fun listPrefixLen(line: String): Int =
    if (line.startsWith(BULLET)) BULLET.length else numberedRegex.find(line)?.value?.length ?: 0

private fun EditorState.rebuild(newPrefix: (Int, String, Int) -> String): EditorState {
    val text = content.text
    val sStyles = content.safeStyles()
    val lines = text.split("\n")
    val sb = StringBuilder()
    val st = ArrayList<CharStyle>(sStyles.size + 16)
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
        st.addAll(sStyles.subList(oldPos + oldLen, oldPos + line.length))
        if (i < lines.lastIndex) {
            sb.append('\n')
            if (oldPos + line.length < sStyles.size) {
                st.add(sStyles[oldPos + line.length])
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
    val sStyles = content.safeStyles()
    if (old == nw) {
        return copy(value = new)
    }
    val cap = (if (nw.length >= old.length) value.selection.min else new.selection.min).coerceIn(0, minOf(old.length, nw.length))
    var p = 0
    while (p < cap && p < old.length && p < nw.length && old[p] == nw[p]) p++
    var s = 0
    val maxS = minOf(old.length - p, nw.length - p)
    while (s < maxS && old[old.length - 1 - s] == nw[nw.length - 1 - s]) s++

    val insertedLen = (nw.length - s - p).coerceAtLeast(0)
    val styleHead = sStyles.subList(0, p.coerceIn(0, sStyles.size))
    val styleTail = sStyles.subList((old.length - s).coerceIn(0, sStyles.size), sStyles.size)
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
            } else if (line.startsWith("[ ] ") || line.startsWith("[x] ") || line.startsWith("[X] ")) {
                val add = "[ ] "
                val plain = CharStyle()
                val updated = base.content.replace(p + 1, p + 1, add, plain)
                return EditorState(
                    TextFieldValue(updated.text, TextRange(p + 1 + add.length)),
                    updated,
                    typing
                )
            }
        }
    }
    return base.renumber()
}

fun EditorState.allSelected(check: (CharStyle) -> Boolean): Boolean {
    val sel = value.selection
    val sStyles = content.safeStyles()
    if (sel.collapsed) return check(typing)
    val start = sel.min.coerceIn(0, sStyles.size)
    val end = sel.max.coerceIn(start, sStyles.size)
    if (start == end) return check(typing)
    return sStyles.subList(start, end).all(check)
}

fun EditorState.shownColor(): Int {
    val sel = value.selection
    val sStyles = content.safeStyles()
    if (sStyles.isEmpty()) return typing.color
    return if (sel.collapsed) {
        typing.color
    } else {
        sStyles[sel.min.coerceIn(0, sStyles.lastIndex)].color
    }
}

fun EditorState.applyStyle(transform: (CharStyle) -> CharStyle): EditorState {
    val sel = value.selection
    if (sel.collapsed) return copy(typing = transform(typing))
    val sStyles = content.safeStyles()
    val start = sel.min.coerceIn(0, sStyles.size)
    val end = sel.max.coerceIn(start, sStyles.size)
    val newStyles = sStyles.mapIndexed { i, s ->
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
        line.startsWith("[ ] ") || line.startsWith("[x] ") || line.startsWith("[X] ") -> 3
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

fun EditorState.toggleHeader(level: Int): EditorState {
    val text = content.text
    val sel = value.selection
    val cursor = sel.min.coerceIn(0, text.length)
    val lineStart = if (text.isEmpty()) 0 else text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
    val lineEnd = if (text.isEmpty()) 0 else text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
    val line = if (text.isEmpty()) "" else text.substring(lineStart, lineEnd)

    val targetPrefix = when (level) {
        1 -> "# "
        2 -> "## "
        3 -> "### "
        else -> ""
    }

    val currentPrefix = when {
        line.startsWith("### ") -> "### "
        line.startsWith("## ") -> "## "
        line.startsWith("# ") -> "# "
        else -> ""
    }

    val newPrefix = if (currentPrefix == targetPrefix) "" else targetPrefix
    val lineWithoutPrefix = line.removePrefix(currentPrefix)
    val newLine = newPrefix + lineWithoutPrefix

    val updatedContent = content.replace(lineStart, lineEnd, newLine, typing)
    val newCursor = (lineStart + newPrefix.length + (cursor - lineStart - currentPrefix.length)).coerceIn(lineStart, updatedContent.text.length)
    return copy(
        value = TextFieldValue(updatedContent.text, TextRange(newCursor)),
        content = updatedContent
    )
}

fun EditorState.toggleTodo(): EditorState {
    val text = content.text
    if (text.isEmpty()) {
        val newContent = content.replace(0, 0, "[ ] ", typing)
        return copy(value = TextFieldValue("[ ] ", TextRange(4)), content = newContent)
    }
    val sel = value.selection
    val cursor = sel.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
    val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    val (newLine, cursorShift) = when {
        line.startsWith("[ ] ") -> line.substring(4) to -4
        line.startsWith("[x] ") || line.startsWith("[X] ") -> line.substring(4) to -4
        else -> "[ ] $line" to 4
    }

    val updatedContent = content.replace(lineStart, lineEnd, newLine, typing)
    val newCursor = (cursor + cursorShift).coerceIn(lineStart, updatedContent.text.length)
    return copy(
        value = TextFieldValue(updatedContent.text, TextRange(newCursor)),
        content = updatedContent
    )
}

fun EditorState.toggleCheckAtPosition(cursorPos: Int): EditorState {
    val text = content.text
    if (text.isEmpty()) return this
    val pos = cursorPos.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)) + 1
    val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
    if (lineEnd - lineStart < 4) return this
    val prefix = text.substring(lineStart, (lineStart + 4).coerceAtMost(text.length))

    if (prefix == "[ ] ") {
        val updatedContent = content.replace(lineStart, lineStart + 4, "[x] ", typing)
        return copy(value = TextFieldValue(updatedContent.text, value.selection), content = updatedContent)
    } else if (prefix == "[x] " || prefix == "[X] ") {
        val updatedContent = content.replace(lineStart, lineStart + 4, "[ ] ", typing)
        return copy(value = TextFieldValue(updatedContent.text, value.selection), content = updatedContent)
    }
    return this
}

fun EditorState.toggleCodeBlock(): EditorState {
    val text = content.text
    if (text.isEmpty()) {
        val insert = "```code```"
        val updatedContent = content.replace(0, 0, insert, typing)
        return copy(
            value = TextFieldValue(updatedContent.text, TextRange(3, 7)),
            content = updatedContent
        )
    }

    val sel = value.selection
    val start = sel.min.coerceIn(0, text.length)
    val end = sel.max.coerceIn(start, text.length)

    val codeBlockRegex = Regex("```[\\s\\S]*?```")
    val matches = codeBlockRegex.findAll(text).toList()

    val containingMatch = matches.find { m ->
        val mStart = m.range.first
        val mEnd = m.range.last + 1
        (start in mStart..mEnd) || (end in mStart..mEnd) || (mStart in start..end)
    }

    if (containingMatch != null) {
        val mRange = containingMatch.range
        val fullMatchText = containingMatch.value
        val unwrapped = fullMatchText.removePrefix("```").removeSuffix("```")

        val updatedContent = content.replace(mRange.first, mRange.last + 1, unwrapped, typing)
        val newSel = TextRange(mRange.first, mRange.first + unwrapped.length)
        return copy(
            value = TextFieldValue(updatedContent.text, newSel),
            content = updatedContent
        )
    }

    val selectedText = if (start == end) "" else text.substring(start, end)
    val wrapped = "```$selectedText```"
    val updatedContent = content.replace(start, end, wrapped, typing)
    val newSel = if (start == end) TextRange(start + 3) else TextRange(start, start + wrapped.length)
    return copy(
        value = TextFieldValue(updatedContent.text, newSel),
        content = updatedContent
    )
}

fun EditorState.insertTable(rows: Int = 2, cols: Int = 2): EditorState {
    val pos = value.selection.min.coerceIn(0, content.text.length)
    val sb = StringBuilder()
    if (pos > 0 && content.text.getOrNull(pos - 1) != '\n') sb.append("\n")
    sb.append("\n")
    for (c in 1..cols) {
        sb.append("| Header $c ")
    }
    sb.append("|\n")
    for (c in 1..cols) {
        sb.append("| --- ")
    }
    sb.append("|\n")
    for (r in 1..rows - 1) {
        for (c in 1..cols) {
            sb.append("| Cell $r-$c ")
        }
        sb.append("|\n")
    }
    sb.append("\n")

    val tableStr = sb.toString()
    val updatedContent = content.replace(pos, pos, tableStr, typing)
    val newCursor = (pos + tableStr.length).coerceAtMost(updatedContent.text.length)
    return copy(
        value = TextFieldValue(updatedContent.text, TextRange(newCursor)),
        content = updatedContent
    )
}

fun EditorState.insertImage(path: String, alt: String = "Attachment"): EditorState {
    val pos = value.selection.min.coerceIn(0, content.text.length)
    val tag = "\n![$alt]($path)\n"
    val updatedContent = content.replace(pos, pos, tag, typing)
    val newCursor = (pos + tag.length).coerceAtMost(updatedContent.text.length)
    return copy(
        value = TextFieldValue(updatedContent.text, TextRange(newCursor)),
        content = updatedContent
    )
}

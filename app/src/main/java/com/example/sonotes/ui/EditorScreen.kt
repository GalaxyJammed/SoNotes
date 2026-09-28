package com.example.sonotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Note
import com.example.sonotes.data.normalizeForSearch
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val palette = listOf(
    0,
    Color(0xFFD32F2F).toArgb(),
    Color(0xFFF57C00).toArgb(),
    Color(0xFF388E3C).toArgb(),
    Color(0xFF1976D2).toArgb(),
    Color(0xFF7B1FA2).toArgb()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(noteId: Long, folderId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).noteDao() }
    val scope = rememberCoroutineScope()
    val saveLock = remember { Mutex() }
    var title by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(EditorState()) }
    var inkJson by remember { mutableStateOf("") }
    var isStylusMode by remember { mutableStateOf(false) }
    var stylusLanguage by remember { mutableStateOf(RecognitionLanguage.GREEK) }
    var loaded by remember { mutableStateOf(noteId == -1L) }
    var currentId by remember { mutableLongStateOf(noteId) }
    var lastSaved by remember { mutableStateOf("") }
    var colorMenu by remember { mutableStateOf(false) }
    val undoStack = remember { ArrayList<EditorState>() }
    val redoStack = remember { ArrayList<EditorState>() }
    val lastChange = remember { longArrayOf(0L) }



    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            dao.getById(noteId)?.let {
                title = it.title
                inkJson = it.inkJson
                val c = richContentFromBody(it.body)
                editor = EditorState(TextFieldValue(c.text), c)
                lastSaved = it.title + "\u0000" + c.toJson() + "\u0000" + it.inkJson
            }
            loaded = true
        }
    }

    suspend fun persist() {
        if (!loaded) return
        val text = editor.content.text
        if (currentId == -1L && title.isBlank() && text.isBlank() && inkJson.isBlank()) return
        val body = editor.content.toJson()
        val key = title + "\u0000" + body + "\u0000" + inkJson
        if (key == lastSaved) return
        val now = System.currentTimeMillis()
        val search = normalizeForSearch("$title ${text.replace("•", " ")}")
        if (currentId == -1L) {
            currentId = dao.insert(
                Note(
                    title = title,
                    body = body,
                    inkJson = inkJson,
                    searchText = search,
                    folderId = folderId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            dao.getById(currentId)?.let {
                dao.update(it.copy(title = title, body = body, inkJson = inkJson, searchText = search, updatedAt = now))
            }
        }
        lastSaved = key
    }

    fun saveNow() {
        scope.launch { saveLock.withLock { persist() } }
    }

    fun saveAndExit() {
        scope.launch {
            saveLock.withLock { persist() }
            onBack()
        }
    }

    LaunchedEffect(title, editor.content, inkJson) {
        if (!loaded) return@LaunchedEffect
        delay(700)
        saveNow()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { saveNow() }

    BackHandler { saveAndExit() }

    fun commit(new: EditorState, structural: Boolean) {
        val old = editor
        if (new.content != old.content) {
            val now = System.currentTimeMillis()
            if (structural || now - lastChange[0] > 1000 || undoStack.isEmpty()) {
                undoStack.add(old)
                if (undoStack.size > 200) undoStack.removeAt(0)
            }
            lastChange[0] = now
            redoStack.clear()
        }
        editor = new
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(editor)
        editor = undoStack.removeAt(undoStack.lastIndex)
        lastChange[0] = 0L
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(editor)
        editor = redoStack.removeAt(redoStack.lastIndex)
        lastChange[0] = 0L
    }

    val content = editor.content
    val transformation = remember(content) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString) =
                TransformedText(content.toAnnotated(), OffsetMapping.Identity)
        }
    }
    val shownColor = if (loaded) editor.shownColor() else 0
    val listKind = editor.currentList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (currentId == -1L) "New note" else "Edit note")
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            onClick = {
                                stylusLanguage = if (stylusLanguage == RecognitionLanguage.GREEK) RecognitionLanguage.ENGLISH else RecognitionLanguage.GREEK
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stylusLanguage.flag,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { saveAndExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Top-right Undo and Redo buttons
                    IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    // Mode toggle: Text vs Stylus Canvas
                    IconButton(onClick = { isStylusMode = !isStylusMode }) {
                        Icon(
                            if (isStylusMode) Icons.Default.Edit else Icons.Default.Create,
                            contentDescription = if (isStylusMode) "Switch to Text Editor" else "Switch to Stylus Canvas"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .imePadding()
        ) {
            if (!isStylusMode) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Rich Text Note Editor Layer (Always visible underneath)
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                ) {
                    val minHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = editor.value,
                            onValueChange = { nv ->
                                val old = editor
                                val next = old.edit(nv)
                                val big = abs(next.content.text.length - old.content.text.length) > 1
                                commit(next, big)
                            },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = transformation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minHeight)
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (editor.value.text.isEmpty()) {
                                        Text(
                                            "Start writing or switch to Stylus overlay mode…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                // Transparent Stylus Handwriting Overlay (Renders on top when isStylusMode is true)
                if (isStylusMode) {
                    val textBeforeCursor = remember(editor.content.text, editor.value.selection) {
                        val selMin = editor.value.selection.min.coerceIn(0, editor.content.text.length)
                        editor.content.text.substring(0, selMin)
                    }
                    StylusCanvas(
                        selectedLanguage = stylusLanguage,
                        preContext = textBeforeCursor,
                        onTextRecognized = { recognizedText ->
                            val currentText = editor.content.text
                            val sel = editor.value.selection
                            val minPos = sel.min.coerceIn(0, currentText.length)
                            val maxPos = sel.max.coerceIn(0, currentText.length)
                            val newText = currentText.substring(0, minPos) + recognizedText + currentText.substring(maxPos)
                            val newSel = TextRange(minPos + recognizedText.length)
                            val newFieldValue = TextFieldValue(newText, newSel)
                            commit(editor.edit(newFieldValue), true)
                        },
                        onSubstituteCandidate = { oldInsertedText, newCandidateText ->
                            val currentText = editor.content.text
                            val sel = editor.value.selection
                            val pos = (sel.min - oldInsertedText.length).coerceIn(0, currentText.length)
                            if (currentText.substring(pos).startsWith(oldInsertedText)) {
                                val replacedText = currentText.substring(0, pos) + newCandidateText + currentText.substring(pos + oldInsertedText.length)
                                val newSel = TextRange(pos + newCandidateText.length)
                                commit(editor.edit(TextFieldValue(replacedText, newSel)), true)
                            }
                        },
                        onCloseOverlay = {
                            isStylusMode = false
                        }
                    )
                }
            }
            Surface(
                    tonalElevation = 3.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalIconToggleButton(
                            checked = editor.allSelected { it.bold },
                            onCheckedChange = { on ->
                                commit(editor.applyStyle { it.copy(bold = on) }, true)
                            }
                        ) { Icon(Icons.Default.FormatBold, contentDescription = "Bold") }
                        FilledTonalIconToggleButton(
                            checked = editor.allSelected { it.italic },
                            onCheckedChange = { on ->
                                commit(editor.applyStyle { it.copy(italic = on) }, true)
                            }
                        ) { Icon(Icons.Default.FormatItalic, contentDescription = "Italic") }
                        FilledTonalIconToggleButton(
                            checked = listKind == 1,
                            onCheckedChange = { commit(editor.toggleList(false), true) }
                        ) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet list") }
                        FilledTonalIconToggleButton(
                            checked = listKind == 2,
                            onCheckedChange = { commit(editor.toggleList(true), true) }
                        ) { Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered list") }
                        Box {
                            IconButton(onClick = { colorMenu = true }) {
                                Icon(
                                    Icons.Default.FormatColorText,
                                    contentDescription = "Text color",
                                    tint = if (shownColor != 0) Color(shownColor) else LocalContentColor.current
                                )
                            }
                            DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                                palette.forEach { c ->
                                    DropdownMenuItem(
                                        text = {
                                            if (c == 0) {
                                                Text("Default")
                                            } else {
                                                Box(Modifier.size(24.dp).background(Color(c), CircleShape))
                                            }
                                        },
                                        onClick = {
                                            commit(editor.applyStyle { it.copy(color = c) }, true)
                                            colorMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

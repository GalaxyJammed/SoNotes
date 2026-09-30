package com.example.sonotes.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.sonotes.data.AppDatabase
import com.example.sonotes.data.Note
import com.example.sonotes.data.normalizeForSearch
import java.io.File
import java.io.FileOutputStream
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

data class ParsedTable(
    val startIndex: Int,
    val endIndex: Int,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class ParsedImage(
    val alt: String,
    val filePath: String,
    val width: Int = 160,
    val height: Int = 120,
    val rotation: Float = 0f,
    val startIndex: Int,
    val endIndex: Int,
    val rawUrl: String
)

sealed class DocBlock {
    data class TextBlock(val text: String, val startIndex: Int, val endIndex: Int) : DocBlock()
    data class TableBlock(val table: ParsedTable) : DocBlock()
    data class ImageBlock(val image: ParsedImage) : DocBlock()
}

fun parseMarkdownImages(text: String, tableRanges: List<IntRange> = emptyList()): List<ParsedImage> {
    if (!text.contains("![")) return emptyList()
    val regex = Regex("!\\[(.*?)\\]\\((.*?)\\)")
    val results = mutableListOf<ParsedImage>()
    regex.findAll(text).forEach { match ->
        val range = match.range
        val start = range.first
        val end = range.last + 1
        val insideTable = tableRanges.any { start >= it.first && end <= it.last }
        if (!insideTable) {
            val alt = match.groupValues[1]
            val rawUrl = match.groupValues[2]
            val uri = Uri.parse(rawUrl)
            val w = uri.getQueryParameter("w")?.toIntOrNull() ?: 160
            val h = uri.getQueryParameter("h")?.toIntOrNull() ?: 120
            val r = uri.getQueryParameter("r")?.toFloatOrNull() ?: 0f
            results.add(
                ParsedImage(
                    alt = alt,
                    filePath = rawUrl,
                    width = w,
                    height = h,
                    rotation = r,
                    startIndex = start,
                    endIndex = end,
                    rawUrl = rawUrl
                )
            )
        }
    }
    return results
}

private sealed interface SpecialBlock {
    val startIndex: Int
    val endIndex: Int
}
private data class TableItem(val table: ParsedTable) : SpecialBlock {
    override val startIndex = table.startIndex
    override val endIndex = table.endIndex
}
private data class ImageItem(val image: ParsedImage) : SpecialBlock {
    override val startIndex = image.startIndex
    override val endIndex = image.endIndex
}

fun parseDocBlocks(text: String): List<DocBlock> {
    val tables = parseMarkdownTables(text)
    val tableRanges = tables.map { it.startIndex until it.endIndex }
    val images = parseMarkdownImages(text, tableRanges)

    if (tables.isEmpty() && images.isEmpty()) {
        return listOf(DocBlock.TextBlock(text, 0, text.length))
    }

    val specialBlocks: List<SpecialBlock> = (tables.map { TableItem(it) } + images.map { ImageItem(it) })
        .sortedBy { it.startIndex }

    val blocks = mutableListOf<DocBlock>()
    var currentIndex = 0

    specialBlocks.forEach { special ->
        if (special.startIndex > currentIndex) {
            val segmentText = text.substring(currentIndex, special.startIndex)
            blocks.add(DocBlock.TextBlock(segmentText, currentIndex, special.startIndex))
        } else if (special.startIndex == 0 && currentIndex == 0) {
            blocks.add(DocBlock.TextBlock("", 0, 0))
        }

        when (special) {
            is TableItem -> blocks.add(DocBlock.TableBlock(special.table))
            is ImageItem -> blocks.add(DocBlock.ImageBlock(special.image))
        }
        currentIndex = special.endIndex
    }

    if (currentIndex < text.length) {
        val segmentText = text.substring(currentIndex)
        blocks.add(DocBlock.TextBlock(segmentText, currentIndex, text.length))
    } else if (currentIndex == text.length && blocks.isNotEmpty() && blocks.last() !is DocBlock.TextBlock) {
        blocks.add(DocBlock.TextBlock("", text.length, text.length))
    } else if (blocks.isEmpty()) {
        blocks.add(DocBlock.TextBlock("", 0, 0))
    }

    return blocks
}

fun parseMarkdownTables(text: String): List<ParsedTable> {
    if (!text.contains("|")) return emptyList()
    val lines = text.split("\n")
    val result = mutableListOf<ParsedTable>()
    var i = 0
    var charOffset = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size) {
            val nextTrimmed = lines[i + 1].trim()
            if (nextTrimmed.startsWith("|") && nextTrimmed.contains("---")) {
                val tableStartOffset = charOffset

                fun parseTableLine(l: String): List<String> {
                    val parts = l.trim().split("|")
                    val inner = if (parts.size >= 2 && parts.first().isBlank() && parts.last().isBlank()) {
                        parts.subList(1, parts.size - 1)
                    } else {
                        parts
                    }
                    return inner.map { it.trim() }
                }

                val headers = parseTableLine(trimmed)
                var j = i + 2
                var currentOffset = charOffset + line.length + 1 + lines[i + 1].length + 1
                val rows = mutableListOf<List<String>>()

                while (j < lines.size) {
                    val rLine = lines[j].trim()
                    if (rLine.startsWith("|") && rLine.endsWith("|")) {
                        val cells = parseTableLine(rLine)
                        rows.add(cells)
                        currentOffset += lines[j].length + 1
                        j++
                    } else {
                        break
                    }
                }

                result.add(
                    ParsedTable(
                        startIndex = tableStartOffset,
                        endIndex = currentOffset.coerceIn(0, text.length),
                        headers = headers,
                        rows = rows
                    )
                )
                i = j
                charOffset = currentOffset
                continue
            }
        }
        charOffset += line.length + 1
        i++
    }
    return result
}

fun generateMarkdownTableString(headers: List<String>, rows: List<List<String>>): String {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val sb = StringBuilder()
    
    sb.append("|")
    for (c in 0 until colCount) {
        val h = headers.getOrElse(c) { "Header ${c + 1}" }
        sb.append(" $h |")
    }
    sb.append("\n|")

    for (c in 0 until colCount) {
        sb.append(" --- |")
    }
    sb.append("\n")

    rows.forEach { row ->
        sb.append("|")
        for (c in 0 until colCount) {
            val cell = row.getOrElse(c) { "" }
            sb.append(" $cell |")
        }
        sb.append("\n")
    }

    return sb.toString()
}

@Composable
fun rememberImageBitmapFromFile(filePath: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(filePath) {
        try {
            val cleanPath = filePath.removePrefix("file://")
            val file = File(cleanPath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else if (cleanPath.startsWith("content://")) {
                val uri = Uri.parse(cleanPath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun InlineImageView(
    parsedImage: ParsedImage,
    isEditMode: Boolean,
    onResize: (ParsedImage) -> Unit,
    onRotate: (ParsedImage) -> Unit,
    onDelete: (ParsedImage) -> Unit
) {
    val imgBitmap = rememberImageBitmapFromFile(parsedImage.filePath.substringBefore('?'))

    Box(
        modifier = Modifier
            .padding(4.dp)
            .width(parsedImage.width.dp)
            .height(parsedImage.height.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isEditMode) 4.dp else 2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = parsedImage.rotation),
                contentAlignment = Alignment.Center
            ) {
                if (imgBitmap != null) {
                    Image(
                        bitmap = imgBitmap,
                        contentDescription = parsedImage.alt,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("📷", fontSize = 24.sp)
                }
            }
        }

        if (isEditMode) {
            // Rotate icon on Top Left
            IconButton(
                onClick = { onRotate(parsedImage) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rotate Image",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Resize icon on Bottom Right
            IconButton(
                onClick = { onResize(parsedImage) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize Image",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Delete icon
            IconButton(
                onClick = { onDelete(parsedImage) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(Color.Red.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Image",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(noteId: Long, folderId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val dao = remember { AppDatabase.get(context).noteDao() }
    val scope = rememberCoroutineScope()
    val saveLock = remember { Mutex() }
    var title by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(EditorState()) }
    var inkJson by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(noteId == -1L) }
    var isStylusMode by remember { mutableStateOf(false) }
    var stylusLanguage by remember { mutableStateOf(RecognitionLanguage.GREEK) }

    var loaded by remember { mutableStateOf(noteId == -1L) }
    var currentId by remember { mutableLongStateOf(noteId) }
    var lastSaved by remember { mutableStateOf("") }
    
    var attachmentMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }

    val undoStack = remember { ArrayList<EditorState>() }
    val redoStack = remember { ArrayList<EditorState>() }
    val lastChange = remember { longArrayOf(0L) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

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

    fun exitEditMode() {
        isEditMode = false
        isStylusMode = false
        keyboardController?.hide()
        focusManager.clearFocus()
        editor = editor.copy(typing = CharStyle())
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            commit(editor.insertImage(tempCameraUri.toString(), "Photo"), true)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            try {
                val destFile = File(context.filesDir, "attachments/img_${System.currentTimeMillis()}.jpg")
                destFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                commit(editor.insertImage(destFile.absolutePath, "Photo"), true)
            } catch (_: Exception) {
                commit(editor.insertImage(sourceUri.toString(), "Photo"), true)
            }
        }
    }

    fun handleResizeImage(img: ParsedImage) {
        val (nextW, nextH) = when {
            img.width < 150 -> 180 to 135
            img.width < 210 -> 240 to 180
            img.width < 280 -> 320 to 240
            img.width < 360 -> 380 to 280 // Full width to right side of note
            else -> 120 to 90 // cycle back to small
        }
        val cleanPath = img.filePath.substringBefore('?')
        val newRawUrl = "$cleanPath?w=$nextW&h=$nextH&r=${img.rotation}"
        val newTag = "![${img.alt}]($newRawUrl)"
        val updatedText = editor.content.text.replaceRange(img.startIndex, img.endIndex, newTag)
        commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
    }

    fun handleRotateImage(img: ParsedImage) {
        val nextR = (img.rotation + 90f) % 360f
        val cleanPath = img.filePath.substringBefore('?')
        val newRawUrl = "$cleanPath?w=${img.width}&h=${img.height}&r=$nextR"
        val newTag = "![${img.alt}]($newRawUrl)"
        val updatedText = editor.content.text.replaceRange(img.startIndex, img.endIndex, newTag)
        commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
    }

    fun handleDeleteImage(img: ParsedImage) {
        val updatedText = editor.content.text.removeRange(img.startIndex, img.endIndex)
        commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
    }

    LaunchedEffect(isStylusMode) {
        if (isStylusMode) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

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

    BackHandler {
        if (isStylusMode) {
            isStylusMode = false
        } else if (isEditMode) {
            exitEditMode()
        } else {
            saveAndExit()
        }
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

    val shownColor = if (loaded) editor.shownColor() else 0
    val listKind = editor.currentList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (title.isNotBlank()) title else if (isEditMode) "New note" else "Note View",
                            maxLines = 1
                        )
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
                    IconButton(
                        onClick = {
                            if (isStylusMode) {
                                isStylusMode = false
                            } else if (isEditMode) {
                                exitEditMode()
                            } else {
                                saveAndExit()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }

                        IconButton(
                            onClick = {
                                isStylusMode = !isStylusMode
                                if (isStylusMode) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isStylusMode) Icons.Default.Keyboard else Icons.Default.Create,
                                contentDescription = if (isStylusMode) "Switch to Text Editor" else "Switch to Stylus Canvas"
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                exitEditMode()
                            } else {
                                isEditMode = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.DriveFileRenameOutline,
                            contentDescription = if (isEditMode) "Switch to Viewing Mode" else "Switch to Edit Mode",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
            if (isEditMode && !isStylusMode) {
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val docBlocks = remember(editor.content.text) { parseDocBlocks(editor.content.text) }

                            docBlocks.forEach { block ->
                                when (block) {
                                    is DocBlock.TextBlock -> {
                                        val segmentText = block.text
                                        val segStyles = remember(editor.content.styles, block.startIndex, block.endIndex) {
                                            val safeStyles = if (editor.content.styles.size == editor.content.text.length) {
                                                editor.content.styles
                                            } else {
                                                List(editor.content.text.length) { CharStyle() }
                                            }
                                            val s = block.startIndex.coerceIn(0, safeStyles.size)
                                            val e = block.endIndex.coerceIn(s, safeStyles.size)
                                            safeStyles.subList(s, e)
                                        }
                                        val segContent = remember(segmentText, segStyles) {
                                            RichContent(segmentText, segStyles)
                                        }

                                        if (isEditMode) {
                                            val fullSel = editor.value.selection
                                            val segSelStart = (fullSel.start - block.startIndex).coerceIn(0, segmentText.length)
                                            val segSelEnd = (fullSel.end - block.startIndex).coerceIn(0, segmentText.length)
                                            val segFieldValue = TextFieldValue(segmentText, TextRange(segSelStart, segSelEnd))

                                            BasicTextField(
                                                value = segFieldValue,
                                                onValueChange = { newSegValue ->
                                                    val fullPrefix = editor.content.text.substring(0, block.startIndex)
                                                    val fullSuffix = editor.content.text.substring(block.endIndex.coerceAtMost(editor.content.text.length))
                                                    val newFullText = fullPrefix + newSegValue.text + fullSuffix
                                                    val newSelStart = (block.startIndex + newSegValue.selection.start).coerceIn(0, newFullText.length)
                                                    val newSelEnd = (block.startIndex + newSegValue.selection.end).coerceIn(0, newFullText.length)
                                                    val newFieldValue = TextFieldValue(newFullText, TextRange(newSelStart, newSelEnd))

                                                    val next = editor.edit(newFieldValue)
                                                    val big = abs(newSegValue.text.length - segmentText.length) > 1

                                                    if (!big && newSegValue.selection == segFieldValue.selection && newSegValue.selection.collapsed) {
                                                        val posInFull = block.startIndex + newSegValue.selection.min
                                                        val toggled = editor.toggleCheckAtPosition(posInFull)
                                                        if (toggled != editor) {
                                                            commit(toggled, true)
                                                            return@BasicTextField
                                                        }
                                                    }
                                                    commit(next, big)
                                                },
                                                textStyle = TextStyle(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 16.sp,
                                                    lineHeight = 24.sp
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                visualTransformation = { TransformedText(segContent.toAnnotated(), OffsetMapping.Identity) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = if (docBlocks.size == 1) minHeight else 32.dp)
                                                    .padding(12.dp),
                                                decorationBox = { inner ->
                                                    Box(modifier = Modifier.fillMaxWidth()) {
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
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = if (docBlocks.size == 1) minHeight else 0.dp)
                                                    .padding(12.dp)
                                            ) {
                                                if (editor.content.text.isBlank()) {
                                                    Text(
                                                        "Empty note. Tap the Edit button top right to start writing.",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else if (block.text.isNotEmpty()) {
                                                    val images = remember(block.text) { parseMarkdownImages(block.text) }
                                                    if (images.isEmpty()) {
                                                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                                        val cleanAnnotated = remember(segContent, block.startIndex) {
                                                            segContent.toCleanAnnotated(block.startIndex)
                                                        }

                                                        Text(
                                                            text = cleanAnnotated,
                                                            style = TextStyle(
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                fontSize = 16.sp,
                                                                lineHeight = 24.sp
                                                            ),
                                                            onTextLayout = { layoutResult = it },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .pointerInput(cleanAnnotated) {
                                                                    detectTapGestures { tapOffset ->
                                                                        layoutResult?.let { textLayoutResult ->
                                                                            val pos = textLayoutResult.getOffsetForPosition(tapOffset)
                                                                            cleanAnnotated
                                                                                .getStringAnnotations(tag = "TODO_TOGGLE", start = pos, end = pos)
                                                                                .firstOrNull()?.let { annotation ->
                                                                                    val origPos = annotation.item.toIntOrNull()
                                                                                    if (origPos != null) {
                                                                                        val toggled = editor.toggleCheckAtPosition(origPos)
                                                                                        commit(toggled, true)
                                                                                    }
                                                                                }
                                                                        }
                                                                    }
                                                                }
                                                        )
                                                    } else {
                                                        FlowRow(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            var lastIdx = 0
                                                            images.forEach { img ->
                                                                if (img.startIndex > lastIdx) {
                                                                    val subAnnotated = segContent.toCleanAnnotated(block.startIndex + lastIdx)
                                                                    Text(
                                                                        text = subAnnotated,
                                                                        style = TextStyle(
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                            fontSize = 16.sp,
                                                                            lineHeight = 24.sp
                                                                        )
                                                                    )
                                                                }
                                                                InlineImageView(
                                                                    parsedImage = img.copy(
                                                                        startIndex = block.startIndex + img.startIndex,
                                                                        endIndex = block.startIndex + img.endIndex
                                                                    ),
                                                                    isEditMode = isEditMode,
                                                                    onResize = ::handleResizeImage,
                                                                    onRotate = ::handleRotateImage,
                                                                    onDelete = ::handleDeleteImage
                                                                )
                                                                lastIdx = img.endIndex
                                                            }
                                                            if (lastIdx < block.text.length) {
                                                                val subAnnotated = segContent.toCleanAnnotated(block.startIndex + lastIdx)
                                                                Text(
                                                                    text = subAnnotated,
                                                                    style = TextStyle(
                                                                        color = MaterialTheme.colorScheme.onSurface,
                                                                        fontSize = 16.sp,
                                                                        lineHeight = 24.sp
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is DocBlock.TableBlock -> {
                                        val parsedTable = block.table
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Column(Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "📊 Table Window (${parsedTable.rows.size + 1}x${parsedTable.headers.size})",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (isEditMode) {
                                                        IconButton(
                                                            onClick = {
                                                                val updatedText = editor.content.text.removeRange(parsedTable.startIndex, parsedTable.endIndex)
                                                                commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Remove Table", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(4.dp))

                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            parsedTable.headers.forEachIndexed { colIdx, headerText ->
                                                                OutlinedTextField(
                                                                    value = headerText,
                                                                    onValueChange = { newText ->
                                                                        if (isEditMode) {
                                                                            val newHeaders = parsedTable.headers.toMutableList().apply { set(colIdx, newText) }
                                                                            val newTableStr = generateMarkdownTableString(newHeaders, parsedTable.rows)
                                                                            val updatedText = editor.content.text.substring(0, parsedTable.startIndex) + newTableStr + editor.content.text.substring(parsedTable.endIndex)
                                                                            commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), false)
                                                                        }
                                                                    },
                                                                    readOnly = !isEditMode,
                                                                    modifier = Modifier.width(100.dp),
                                                                    textStyle = TextStyle(
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    ),
                                                                    colors = OutlinedTextFieldDefaults.colors(
                                                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                                                    ),
                                                                    singleLine = true
                                                                )
                                                            }
                                                        }

                                                        Spacer(Modifier.height(4.dp))

                                                        parsedTable.rows.forEachIndexed { rowIdx, rowCells ->
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            ) {
                                                                parsedTable.headers.indices.forEach { colIdx ->
                                                                    val cellValue = rowCells.getOrElse(colIdx) { "" }
                                                                    OutlinedTextField(
                                                                        value = cellValue,
                                                                        onValueChange = { newText ->
                                                                            if (isEditMode) {
                                                                                val newRows = parsedTable.rows.map { it.toMutableList() }
                                                                                while (newRows[rowIdx].size <= colIdx) newRows[rowIdx].add("")
                                                                                newRows[rowIdx][colIdx] = newText
                                                                                val newTableStr = generateMarkdownTableString(parsedTable.headers, newRows)
                                                                                val updatedText = editor.content.text.substring(0, parsedTable.startIndex) + newTableStr + editor.content.text.substring(parsedTable.endIndex)
                                                                                commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), false)
                                                                            }
                                                                        },
                                                                        readOnly = !isEditMode,
                                                                        modifier = Modifier.width(100.dp),
                                                                        textStyle = TextStyle(
                                                                            fontSize = 13.sp,
                                                                            color = MaterialTheme.colorScheme.onSurface
                                                                        ),
                                                                        colors = OutlinedTextFieldDefaults.colors(
                                                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                                                        ),
                                                                        singleLine = true
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (isEditMode) {
                                                            IconButton(
                                                                onClick = {
                                                                    val newRows = parsedTable.rows + listOf(List(parsedTable.headers.size) { "" })
                                                                    val newTableStr = generateMarkdownTableString(parsedTable.headers, newRows)
                                                                    val updatedText = editor.content.text.substring(0, parsedTable.startIndex) + newTableStr + editor.content.text.substring(parsedTable.endIndex)
                                                                    commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
                                                                },
                                                                modifier = Modifier
                                                                    .padding(top = 4.dp)
                                                                    .size(28.dp)
                                                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                                            ) {
                                                                Icon(Icons.Default.Add, contentDescription = "Add Row (+)", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                            }
                                                        }
                                                    }

                                                                                    if (isEditMode) {
                                                        Spacer(Modifier.width(6.dp))

                                                        IconButton(
                                                            onClick = {
                                                                val newHeaders = parsedTable.headers + "Header ${parsedTable.headers.size + 1}"
                                                                val newRows = parsedTable.rows.map { it + "" }
                                                                val newTableStr = generateMarkdownTableString(newHeaders, newRows)
                                                                val updatedText = editor.content.text.substring(0, parsedTable.startIndex) + newTableStr + editor.content.text.substring(parsedTable.endIndex)
                                                                commit(EditorState(TextFieldValue(updatedText), richContentFromBody(updatedText)), true)
                                                            },
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = "Add Column (+)", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is DocBlock.ImageBlock -> {
                                        InlineImageView(
                                            parsedImage = block.image,
                                            isEditMode = isEditMode,
                                            onResize = ::handleResizeImage,
                                            onRotate = ::handleRotateImage,
                                            onDelete = ::handleDeleteImage
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isStylusMode && isEditMode) {
                    val textBeforeCursor = remember(editor.content.text, editor.value.selection) {
                        val selMin = editor.value.selection.min.coerceIn(0, editor.content.text.length)
                        editor.content.text.substring(0, selMin)
                    }
                    StylusCanvas(
                        selectedLanguage = stylusLanguage,
                        isNewNote = (noteId == -1L),
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

            if (isEditMode && !isStylusMode) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = { attachmentMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Add Attachment",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = attachmentMenu,
                                onDismissRequest = { attachmentMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Take a Picture")
                                        }
                                    },
                                    onClick = {
                                        attachmentMenu = false
                                        try {
                                            val file = File(context.filesDir, "attachments/img_${System.currentTimeMillis()}.jpg")
                                            file.parentFile?.mkdirs()
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            tempCameraUri = uri
                                            cameraLauncher.launch(uri)
                                        } catch (_: Exception) {}
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Choose from Gallery")
                                        }
                                    },
                                    onClick = {
                                        attachmentMenu = false
                                        galleryLauncher.launch("image/*")
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val on = !editor.allSelected { it.bold }
                                commit(editor.applyStyle { it.copy(bold = on) }, true)
                            }
                        ) {
                            Icon(
                                Icons.Default.FormatBold,
                                contentDescription = "Bold",
                                tint = if (editor.allSelected { it.bold }) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(
                            onClick = {
                                val on = !editor.allSelected { it.italic }
                                commit(editor.applyStyle { it.copy(italic = on) }, true)
                            }
                        ) {
                            Icon(
                                Icons.Default.FormatItalic,
                                contentDescription = "Italic",
                                tint = if (editor.allSelected { it.italic }) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(
                            onClick = {
                                val on = !editor.allSelected { it.strikethrough }
                                commit(editor.applyStyle { it.copy(strikethrough = on) }, true)
                            }
                        ) {
                            Icon(
                                Icons.Default.FormatStrikethrough,
                                contentDescription = "Strikethrough",
                                tint = if (editor.allSelected { it.strikethrough }) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        Box {
                            IconButton(onClick = { headerMenu = true }) {
                                Icon(Icons.Default.Title, contentDescription = "Headers")
                            }
                            DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("H1 Big Header", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                                    onClick = {
                                        commit(editor.toggleHeader(1), true)
                                        headerMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("H2 Medium Header", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                    onClick = {
                                        commit(editor.toggleHeader(2), true)
                                        headerMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("H3 Small Header", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                    onClick = {
                                        commit(editor.toggleHeader(3), true)
                                        headerMenu = false
                                    }
                                )
                            }
                        }

                        IconButton(onClick = { commit(editor.toggleList(false), true) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Bullet list",
                                tint = if (listKind == 1) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(onClick = { commit(editor.toggleList(true), true) }) {
                            Icon(
                                Icons.Default.FormatListNumbered,
                                contentDescription = "Numbered list",
                                tint = if (listKind == 2) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(onClick = { commit(editor.toggleTodo(), true) }) {
                            Icon(
                                Icons.Default.CheckBox,
                                contentDescription = "Todo list",
                                tint = if (listKind == 3) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        IconButton(onClick = { commit(editor.toggleCodeBlock(), true) }) {
                            Icon(Icons.Default.Code, contentDescription = "Code block")
                        }

                        IconButton(onClick = { commit(editor.insertTable(2, 2), true) }) {
                            Icon(Icons.Default.GridOn, contentDescription = "Insert 2x2 Table Window")
                        }

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
}

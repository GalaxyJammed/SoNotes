package com.example.sonotes.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sonotes.data.AppSettings
import com.example.sonotes.data.DyslexiaCorrector
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.common.RecognitionCandidate
import com.google.mlkit.vision.digitalink.common.RecognitionResult
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

data class StylusPoint(val x: Float, val y: Float, val pressure: Float, val timestamp: Long)
data class StylusStroke(
    val points: List<StylusPoint>,
    val isEraser: Boolean = false,
    val color: Int = android.graphics.Color.BLACK,
    val strokeWidth: Float = 4f
)

enum class RecognitionLanguage(val code: String, val displayName: String, val flag: String) {
    GREEK("el", "Ελληνικά", "🇬🇷"),
    ENGLISH("en", "English", "🇺🇸"),
    MATH("zxx-X-math", "Math", "🧮")
}

// Basic Math Classifier Heuristic for +, -, *, /, =, x, ÷
fun classifyBasicMathStroke(strokes: List<StylusStroke>): List<RecognitionCandidate> {
    val nonEraser = strokes.filter { !it.isEraser && it.points.size > 1 }
    if (nonEraser.isEmpty()) return emptyList()

    val candidates = mutableListOf<String>()

    if (nonEraser.size == 1) {
        val pts = nonEraser[0].points
        val dx = pts.last().x - pts.first().x
        val dy = pts.last().y - pts.first().y
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (absDx > absDy * 1.8f) {
            candidates.add("-")
            candidates.add("—")
        } else if (dy < 0 && absDy > absDx * 0.7f && dx > 0) {
            candidates.add("/")
            candidates.add("÷")
        } else if (absDy > absDx * 2.0f) {
            candidates.add("|")
            candidates.add("1")
        } else {
            candidates.add("+")
            candidates.add("-")
            candidates.add("/")
            candidates.add("*")
        }
    } else if (nonEraser.size == 2) {
        val s1 = nonEraser[0].points
        val s2 = nonEraser[1].points
        val dx1 = abs(s1.last().x - s1.first().x)
        val dy1 = abs(s1.last().y - s1.first().y)
        val dx2 = abs(s2.last().x - s2.first().x)
        val dy2 = abs(s2.last().y - s2.first().y)

        if (dx1 > dy1 * 1.5f && dx2 > dy2 * 1.5f) {
            candidates.add("=")
            candidates.add("-")
        } else {
            candidates.add("+")
            candidates.add("*")
            candidates.add("x")
            candidates.add("=")
            candidates.add("/")
        }
    } else {
        candidates.add("*")
        candidates.add("+")
        candidates.add("/")
        candidates.add("=")
    }

    return candidates.distinct().map { RecognitionCandidate(it) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StylusCanvas(
    selectedLanguage: RecognitionLanguage = RecognitionLanguage.GREEK,
    preContext: String = "",
    onTextRecognized: (String) -> Unit,
    onSubstituteCandidate: ((oldInsertedText: String, newCandidateText: String) -> Unit)? = null,
    onCloseOverlay: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val strokes = remember { mutableStateOf(listOf<StylusStroke>()) }
    var currentPoints by remember { mutableStateOf(listOf<StylusPoint>()) }
    var isEraserActive by remember { mutableStateOf(false) }

    // Math mode & Dyslexia assist state toggles
    var isMathMode by remember { mutableStateOf(false) }
    var isDyslexiaMode by remember { mutableStateOf(AppSettings.isDyslexiaModeEnabled(context)) }

    // Auto-fading hint text state (disappears after 2.5s or on touch)
    var showHintText by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2500)
        showHintText = false
    }

    // Track last inserted text for real-time candidate substitution
    var lastInsertedText by remember { mutableStateOf("") }

    var isModelDownloaded by remember { mutableStateOf(false) }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var modelDownloadError by remember { mutableStateOf<String?>(null) }

    // Recognition state
    var isRecognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<RecognitionCandidate>>(emptyList()) }
    var autoRecognizeJob by remember { mutableStateOf<Job?>(null) }

    // Active DigitalInkRecognizer instance
    var currentRecognizer by remember { mutableStateOf<DigitalInkRecognizer?>(null) }

    val activeLangCode = if (isMathMode) RecognitionLanguage.MATH.code else selectedLanguage.code

    // Helper to resolve ML Kit Digital Ink Model Identifier
    fun getModelIdentifier(languageTag: String): DigitalInkRecognitionModelIdentifier? {
        return try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
                ?: if (languageTag == "el") DigitalInkRecognitionModelIdentifier.fromLanguageTag("el-GR")
                   else if (languageTag == "zxx-X-math") DigitalInkRecognitionModelIdentifier.fromLanguageTag("zxx-X-symbol")
                   else null
        } catch (_: Exception) {
            null
        }
    }

    // Function to check and load/download ML Kit model
    fun checkAndPrepareModel(langCode: String) {
        val identifier = getModelIdentifier(langCode)
        if (identifier == null) {
            isModelDownloaded = false
            return
        }

        val model = DigitalInkRecognitionModel.builder(identifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { downloaded ->
                isModelDownloaded = downloaded
                if (downloaded) {
                    currentRecognizer?.close()
                    val options = DigitalInkRecognizerOptions.builder(model).build()
                    currentRecognizer = DigitalInkRecognition.getClient(options)
                    modelDownloadError = null
                } else {
                    isDownloadingModel = true
                    modelDownloadError = null
                    val conditions = DownloadConditions.Builder().build()
                    remoteModelManager.download(model, conditions)
                        .addOnSuccessListener {
                            isDownloadingModel = false
                            isModelDownloaded = true
                            currentRecognizer?.close()
                            val options = DigitalInkRecognizerOptions.builder(model).build()
                            currentRecognizer = DigitalInkRecognition.getClient(options)
                        }
                        .addOnFailureListener { e ->
                            isDownloadingModel = false
                            isModelDownloaded = false
                            modelDownloadError = "Download failed: ${e.localizedMessage}"
                        }
                }
            }
            .addOnFailureListener { e ->
                modelDownloadError = "Check failed: ${e.localizedMessage}"
            }
    }

    LaunchedEffect(selectedLanguage, isMathMode) {
        checkAndPrepareModel(activeLangCode)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentRecognizer?.close()
        }
    }

    fun applyInsertText(textToInsert: String) {
        if (textToInsert.isNotBlank()) {
            val formatted = "$textToInsert "
            lastInsertedText = formatted
            onTextRecognized(formatted)
            strokes.value = emptyList()
            currentPoints = emptyList()
        }
    }

    // Core ML Kit Digital Ink Recognition routine
    fun performRecognition(autoInsert: Boolean = false) {
        val nonEraserStrokes = strokes.value.filter { !it.isEraser && it.points.isNotEmpty() }
        if (nonEraserStrokes.isEmpty()) {
            recognizedText = ""
            candidates = emptyList()
            return
        }

        if (isMathMode) {
            val mathCandidates = classifyBasicMathStroke(strokes.value)
            if (mathCandidates.isNotEmpty()) {
                candidates = mathCandidates
                recognizedText = mathCandidates[0].text
                if (autoInsert) {
                    applyInsertText(mathCandidates[0].text)
                }
                return
            }
        }

        val recognizer = currentRecognizer
        if (recognizer == null || !isModelDownloaded) {
            return
        }

        val inkBuilder = Ink.builder()
        nonEraserStrokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { pt ->
                strokeBuilder.addPoint(Ink.Point.create(pt.x, pt.y, pt.timestamp))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val ink = inkBuilder.build()

        isRecognizing = true
        val contextBuilder = RecognitionContext.builder()
        contextBuilder.setPreContext(preContext.takeLast(100))
        val recognitionContext = try {
            contextBuilder.build()
        } catch (_: Exception) {
            null
        }

        val task = if (recognitionContext != null) {
            recognizer.recognize(ink, recognitionContext)
        } else {
            recognizer.recognize(ink)
        }

        task.addOnSuccessListener { result: RecognitionResult ->
                isRecognizing = false
                val cList = result.candidates
                if (cList.isNotEmpty()) {
                    val rawText = cList[0].text
                    if (isDyslexiaMode && !isMathMode) {
                        val dyslexiaResult = DyslexiaCorrector.correctWord(rawText, selectedLanguage.code)
                        if (dyslexiaResult.isCorrected) {
                            val correctedWord = dyslexiaResult.correctedWord
                            recognizedText = correctedWord
                            val newCandidates = mutableListOf<RecognitionCandidate>()
                            dyslexiaResult.alternatives.forEach { alt ->
                                newCandidates.add(RecognitionCandidate(alt))
                            }
                            cList.drop(1).forEach { newCandidates.add(it) }
                            candidates = newCandidates.distinctBy { it.text }
                            if (autoInsert) {
                                applyInsertText(correctedWord)
                            }
                        } else {
                            recognizedText = rawText
                            candidates = cList
                            if (autoInsert) {
                                applyInsertText(rawText)
                            }
                        }
                    } else {
                        recognizedText = rawText
                        candidates = cList
                        if (autoInsert) {
                            applyInsertText(rawText)
                        }
                    }
                }
            }
            .addOnFailureListener {
                isRecognizing = false
            }
    }

    // Schedule debounced auto-recognition when stylus stroke finishes (2.5 seconds pause)
    fun scheduleAutoRecognition() {
        autoRecognizeJob?.cancel()
        autoRecognizeJob = scope.launch {
            delay(2500)
            performRecognition(autoInsert = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Overlay Canvas Area (Transparent overlay so note content underneath is visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x05000000))
                .pointerInteropFilter { event ->
                    val action = event.actionMasked
                    val toolType = event.getToolType(0)
                    val eraser = toolType == MotionEvent.TOOL_TYPE_ERASER || isEraserActive

                    when (action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            showHintText = false // Instantly hide hint text on touch
                            val pts = mutableListOf<StylusPoint>().apply { addAll(currentPoints) }
                            val historySize = event.historySize
                            for (i in 0 until historySize) {
                                pts.add(
                                    StylusPoint(
                                        x = event.getHistoricalX(i),
                                        y = event.getHistoricalY(i),
                                        pressure = event.getHistoricalPressure(i).coerceIn(0.1f, 2.0f),
                                        timestamp = event.getHistoricalEventTime(i)
                                    )
                                )
                            }
                            pts.add(
                                StylusPoint(
                                    x = event.x,
                                    y = event.y,
                                    pressure = event.pressure.coerceIn(0.1f, 2.0f),
                                    timestamp = event.eventTime
                                )
                            )
                            currentPoints = pts
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (currentPoints.isNotEmpty()) {
                                val userColor = AppSettings.getStylusColor(context)
                                val newStroke = StylusStroke(
                                    points = currentPoints,
                                    isEraser = eraser,
                                    color = if (eraser) android.graphics.Color.TRANSPARENT else userColor,
                                    strokeWidth = if (eraser) 32f else 5f
                                )
                                strokes.value = strokes.value + newStroke
                                currentPoints = emptyList()
                                scheduleAutoRecognition()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineSpacing = 36.dp.toPx()
                var y = lineSpacing
                while (y < size.height) {
                    drawLine(
                        color = Color(0x12000000),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineSpacing
                }

                strokes.value.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            for (i in 1 until stroke.points.size) {
                                val prev = stroke.points[i - 1]
                                val curr = stroke.points[i]
                                quadraticTo(prev.x, prev.y, (prev.x + curr.x) / 2, (prev.y + curr.y) / 2)
                            }
                        }
                        drawPath(
                            path = path,
                            color = if (stroke.isEraser) Color(0x00000000) else Color(stroke.color),
                            style = Stroke(
                                width = stroke.strokeWidth * (stroke.points.firstOrNull()?.pressure ?: 1f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (currentPoints.size > 1) {
                    val activePath = Path().apply {
                        moveTo(currentPoints[0].x, currentPoints[0].y)
                        for (i in 1 until currentPoints.size) {
                            val prev = currentPoints[i - 1]
                            val curr = currentPoints[i]
                            quadraticTo(prev.x, prev.y, (prev.x + curr.x) / 2, (prev.y + curr.y) / 2)
                        }
                    }
                    drawPath(
                        path = activePath,
                        color = if (isEraserActive) Color.Gray else Color(AppSettings.getStylusColor(context)),
                        style = Stroke(
                            width = (if (isEraserActive) 32f else 5f) * (currentPoints.firstOrNull()?.pressure ?: 1f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // Auto-fading helper hint (disappears after 2.5s or on touch)
            if (showHintText && strokes.value.isEmpty() && currentPoints.isEmpty() && recognizedText.isBlank() && lastInsertedText.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = if (isMathMode) "🧮 Math Mode: Write math symbols (+, -, /, *, =)" else "✍️ Write anywhere on screen with stylus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Status Indicator Header (if model loading or download error)
        if (isDownloadingModel || modelDownloadError != null || onCloseOverlay != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloadingModel) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Downloading handwriting model…", fontSize = 11.sp)
                        }
                    }
                } else if (modelDownloadError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(modelDownloadError!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                onClick = { checkAndPrepareModel(activeLangCode) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) { Text("Retry", fontSize = 11.sp) }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (onCloseOverlay != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = onCloseOverlay,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Stylus Overlay",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Bottom Toolbar & Alternatives Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Live Recognition & Alternatives Card at Bottom
            if (recognizedText.isNotBlank() || isRecognizing || candidates.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (lastInsertedText.isNotBlank()) "Inserted: \"${lastInsertedText.trim()}\"" else "Recognized: \"$recognizedText\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            if (isRecognizing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Recognizing…", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Alternatives chips for real-time word/symbol substitution
                        if (candidates.size > 1) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Alternatives:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                candidates.take(6).forEach { candidate ->
                                    val candFormatted = "${candidate.text} "
                                    val isSelected = candidate.text == recognizedText || candFormatted == lastInsertedText
                                    AssistChip(
                                        onClick = {
                                            if (lastInsertedText.isNotBlank() && onSubstituteCandidate != null) {
                                                onSubstituteCandidate(lastInsertedText, candFormatted)
                                                lastInsertedText = candFormatted
                                                recognizedText = candidate.text
                                            } else {
                                                recognizedText = candidate.text
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = candidate.text,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Single Row Floating Action Toolbar: [Eraser] [Clear] [Math Σ] [Dyslexia ♿] ... [Recognize] [Insert]
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Eraser Toggle Icon
                    IconButton(
                        onClick = { isEraserActive = !isEraserActive },
                        modifier = Modifier.background(
                            if (isEraserActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            if (isEraserActive) Icons.Default.Edit else Icons.Default.Gesture,
                            contentDescription = "Toggle Eraser",
                            tint = if (isEraserActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Clear (Trash) Icon
                    IconButton(
                        onClick = {
                            strokes.value = emptyList()
                            currentPoints = emptyList()
                            recognizedText = ""
                            candidates = emptyList()
                            lastInsertedText = ""
                        },
                        enabled = strokes.value.isNotEmpty() || recognizedText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear Canvas"
                        )
                    }

                    // Math Symbols Mode Button
                    IconButton(
                        onClick = {
                            isMathMode = !isMathMode
                            if (strokes.value.isNotEmpty()) {
                                performRecognition(autoInsert = false)
                            }
                        },
                        modifier = Modifier.background(
                            if (isMathMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Text(
                            text = "Σ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isMathMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Manual Recognize Icon Button
                    IconButton(
                        onClick = { performRecognition(autoInsert = false) },
                        enabled = strokes.value.isNotEmpty() && !isRecognizing
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Recognize Handwriting",
                            tint = if (strokes.value.isNotEmpty() && !isRecognizing) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    // Insert Text Checkmark Icon Button
                    IconButton(
                        onClick = {
                            applyInsertText(recognizedText)
                        },
                        enabled = recognizedText.isNotBlank(),
                        modifier = Modifier.background(
                            if (recognizedText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Insert Text into Note",
                            tint = if (recognizedText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

package com.example.counseling

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.counseling.llm.ChatMessage
import com.example.counseling.llm.ChatRole
import com.example.counseling.llm.EngineStatus
import com.example.counseling.llm.LiteRtLmCounselingEngine
import com.example.counseling.ui.theme.CounselingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialScreen = if (intent?.action?.contains("health", ignoreCase = true) == true) {
            AppScreen.Health
        } else {
            AppScreen.Chat
        }
        val settingsStore = AppSettingsStore(applicationContext)
        setContent {
            var themeMode by remember { mutableStateOf(settingsStore.loadThemeMode()) }
            CounselingTheme(themeMode = themeMode) {
                CounselingApp(
                    initialScreen = initialScreen,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        settingsStore.saveThemeMode(mode)
                    },
                )
            }
        }
    }
}

private enum class AppScreen(val label: String, val icon: String) {
    Chat("상담", "상"),
    Gallery("갤러리", "사"),
    Health("건강", "건"),
    Settings("설정", "설"),
}

private enum class ThinkingMode(val label: String) {
    Auto("사고 자동"),
    On("사고 켬"),
    Off("사고 끔"),
}

private enum class ChatFontSize(val label: String, val sizeSp: Int) {
    Small("작게", 15),
    Normal("보통", 17),
    Large("크게", 20),
}

private data class GalleryImage(
    val uri: Uri,
    val name: String,
)

private enum class GalleryAccess {
    None,
    Partial,
    Full,
}

private data class HealthSummary(
    val period: HealthPeriod = HealthPeriod.Week,
    val steps: Long = 0,
    val caloriesKcal: Double = 0.0,
    val activeCaloriesKcal: Double = 0.0,
    val distanceKm: Double = 0.0,
    val heartRateBpm: Long? = null,
    val sleepHours: Double = 0.0,
    val daily: List<HealthDaySummary> = emptyList(),
    val message: String = "준비됨",
)

private enum class HealthPeriod(val label: String) {
    Week("주"),
    Month("월"),
}

private data class HealthDaySummary(
    val date: LocalDate,
    val steps: Long = 0,
    val caloriesKcal: Double = 0.0,
    val activeCaloriesKcal: Double = 0.0,
    val distanceKm: Double = 0.0,
    val heartRateBpm: Long? = null,
    val sleepHours: Double = 0.0,
)

private data class ImportedSession(
    val messages: List<ChatMessage>,
    val systemPrompt: String,
    val importantMemories: List<String> = emptyList(),
)

private data class MemoryItem(
    val text: String,
    val isDefault: Boolean,
)

private data class ActiveAudioRecording(
    val recorder: AudioRecord,
    val file: File,
    val job: Job,
)

private val defaultImportantMemories = listOf(
    "상담 응답은 진단이나 치료를 대신하지 않고, 감정 반영과 정리, 작은 다음 행동 선택을 돕는 방식으로 유지한다.",
    "불안, 긴장, 감정 과부하가 있을 때는 느린 호흡, 몸 감각 알아차리기, 5-4-3-2-1 감각 그라운딩, 짧은 산책, 점진적 근육 이완 같은 부담 낮은 안정화 방법을 선택지로 제안한다.",
    "자살, 자해, 타해, 학대, 폭력, 응급 위험 신호가 있으면 일반 조언보다 안전 확인과 긴급 지원 안내를 우선한다.",
)

private val healthPermissions = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounselingApp(
    initialScreen: AppScreen = AppScreen.Chat,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    var screen by remember { mutableStateOf(initialScreen) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var chatChromeVisible by remember { mutableStateOf(true) }
    var settingsOpenRequests by remember { mutableStateOf(0) }

    LaunchedEffect(screen) {
        if (screen != AppScreen.Chat) chatChromeVisible = true
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = screen != AppScreen.Chat || chatChromeVisible) {
            TopAppBar(
                title = {
                    Column {
                        Text("상담", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "온디바이스 Gemma",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    Box {
                        TextButton(
                            onClick = { showThemeMenu = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text(themeMode.label)
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false },
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        showThemeMenu = false
                                        onThemeModeChange(mode)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item && item != AppScreen.Settings,
                        onClick = {
                            if (item == AppScreen.Settings) {
                                screen = AppScreen.Chat
                                chatChromeVisible = true
                                settingsOpenRequests += 1
                            } else {
                                screen = item
                            }
                        },
                        icon = { Text(item.icon) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (screen) {
                AppScreen.Chat -> ChatScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    chromeVisible = chatChromeVisible,
                    onChromeVisibleChange = { chatChromeVisible = it },
                    settingsOpenRequests = settingsOpenRequests,
                )
                AppScreen.Gallery -> GalleryScreen()
                AppScreen.Health -> HealthScreen()
                AppScreen.Settings -> ChatScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    chromeVisible = chatChromeVisible,
                    onChromeVisibleChange = { chatChromeVisible = it },
                    settingsOpenRequests = settingsOpenRequests,
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    chromeVisible: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
    settingsOpenRequests: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liteRtEngine = remember { LiteRtLmCounselingEngine(context.applicationContext) }
    val sessionStore = remember { ChatSessionStore(context.applicationContext) }
    val memoryStore = remember { ChatMemoryStore(context.applicationContext) }
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                role = ChatRole.Assistant,
                content = "모델 로드를 눌러 gemma-4-E4B-it.litertlm 파일을 선택해 주세요. 로드가 끝나면 바로 대화할 수 있습니다.",
            ),
        )
    }
    val importantMemories = remember { mutableStateListOf<String>() }

    var status by remember { mutableStateOf(EngineStatus(false, "모델 파일을 선택해 주세요.")) }
    var input by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf(LiteRtLmCounselingEngine.defaultSystemInstruction) }
    var showSystemPrompt by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var attachedAudioPath by remember { mutableStateOf<String?>(null) }
    var attachmentLabel by remember { mutableStateOf<String?>(null) }
    var thinkingMode by remember { mutableStateOf(ThinkingMode.Auto) }
    var includeHealthContext by remember { mutableStateOf(false) }
    var healthContextPeriod by remember { mutableStateOf(HealthPeriod.Week) }
    var directAttachmentMode by remember { mutableStateOf(true) }
    var chatFontSize by remember { mutableStateOf(ChatFontSize.Normal) }
    var currentSessionId by remember { mutableStateOf(ChatMemoryStore.DEFAULT_SESSION_ID) }
    var showSessionList by remember { mutableStateOf(false) }
    var sessionSummaries by remember { mutableStateOf<List<ChatSessionSummary>>(emptyList()) }
    var isThinking by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var activeAudioRecording by remember { mutableStateOf<ActiveAudioRecording?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun loadSelectedModel(uri: Uri) {
        isLoadingModel = true
        status = EngineStatus(false, "모델을 앱 저장소로 복사하고 있습니다...")
        scope.launch {
            status = liteRtEngine.importAndLoadModel(uri, buildSystemPromptWithMemories(systemPrompt, importantMemories))
            isLoadingModel = false
            messages += ChatMessage(ChatRole.Assistant, status.message)
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) loadSelectedModel(uri) },
    )

    val speechInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    input = if (input.isBlank()) spoken else "$input $spoken"
                }
            }
        },
    )

    fun stopRecordingAndAttach() {
        val recording = activeAudioRecording ?: return
        scope.launch {
            runCatching {
                recording.recorder.stop()
                recording.job.join()
                recording.recorder.release()
                require(recording.file.length() > 44L) { "녹음 파일이 비어 있습니다." }
                attachedAudioPath = recording.file.absolutePath
            }.onSuccess {
                attachmentLabel = "WAV 음성 녹음 첨부"
                status = EngineStatus(status.isModelLoaded, "WAV 녹음을 첨부했습니다.")
            }.onFailure {
                runCatching { recording.recorder.release() }
                recording.file.delete()
                status = EngineStatus(status.isModelLoaded, "녹음 실패: ${it.message ?: it.javaClass.simpleName}")
            }
            activeAudioRecording = null
            isRecordingAudio = false
        }
    }

    fun cancelRecording() {
        val recording = activeAudioRecording ?: return
        scope.launch {
            runCatching {
                recording.recorder.stop()
                recording.job.join()
                recording.recorder.release()
            }
            recording.file.delete()
            activeAudioRecording = null
            isRecordingAudio = false
            status = EngineStatus(status.isModelLoaded, "녹음을 취소했습니다.")
        }
    }

    fun startRecording() {
        runCatching {
            val recording = startAudioRecording(context, scope)
            activeAudioRecording = recording
            isRecordingAudio = true
            status = EngineStatus(status.isModelLoaded, "WAV 녹음 중입니다. 다시 누르면 첨부합니다.")
        }.onFailure {
            status = EngineStatus(status.isModelLoaded, "녹음 시작 실패: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    val recordAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) startRecording()
            else status = EngineStatus(status.isModelLoaded, "마이크 권한이 필요합니다.")
        },
    )

    fun resetToNewSession(sessionId: String = sessionStore.createSessionId()) {
        currentSessionId = sessionId
        messages.clear()
        messages += ChatMessage(ChatRole.Assistant, "새 상담을 시작했습니다. 지금 다루고 싶은 주제를 적어 주세요.")
        attachedAudioPath = null
        attachmentLabel = null
        input = ""
        status = EngineStatus(status.isModelLoaded, "새 상담 세션을 시작했습니다.")
        scope.launch {
            sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
            memoryStore.reindexSession(messages.toList(), currentSessionId)
            sessionSummaries = sessionStore.listSessions()
        }
    }

    fun loadSession(sessionId: String) {
        scope.launch {
            sessionStore.loadSession(sessionId)?.let { saved ->
                currentSessionId = saved.id
                messages.clear()
                messages.addAll(saved.messages.ifEmpty {
                    listOf(ChatMessage(ChatRole.Assistant, "불러온 상담에 메시지가 없습니다."))
                })
                if (saved.systemPrompt.isNotBlank()) {
                    systemPrompt = saved.systemPrompt
                }
                importantMemories.clear()
                importantMemories.addAll(saved.importantMemories)
                sessionStore.saveActiveSessionId(currentSessionId)
                memoryStore.reindexSession(messages.toList(), currentSessionId)
                sessionSummaries = sessionStore.listSessions()
                status = EngineStatus(status.isModelLoaded, "상담 세션을 불러왔습니다.")
            }
        }
    }

    val exportSession = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        exportChatSession(context, uri, messages.toList(), systemPrompt, importantMemories.toList())
                    }.onSuccess {
                        status = EngineStatus(status.isModelLoaded, "대화 파일을 내보냈습니다.")
                    }.onFailure {
                        status = EngineStatus(status.isModelLoaded, "대화 내보내기 실패: ${it.message ?: it.javaClass.simpleName}")
                    }
                }
            }
        },
    )

    val importSession = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        importChatSession(context, uri)
                    }.onSuccess { imported ->
                        messages.clear()
                        messages.addAll(imported.messages.ifEmpty {
                            listOf(ChatMessage(ChatRole.Assistant, "불러온 대화에 메시지가 없습니다."))
                        })
                        systemPrompt = imported.systemPrompt.ifBlank { systemPrompt }
                        importantMemories.clear()
                        importantMemories.addAll(imported.importantMemories)
                        currentSessionId = sessionStore.createSessionId()
                        sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
                        memoryStore.reindexSession(messages.toList(), currentSessionId)
                        sessionSummaries = sessionStore.listSessions()
                        status = EngineStatus(status.isModelLoaded, "대화 파일을 불러왔습니다.")
                    }.onFailure {
                        status = EngineStatus(status.isModelLoaded, "대화 불러오기 실패: ${it.message ?: it.javaClass.simpleName}")
                    }
                }
            }
        },
    )

    if (showSystemPrompt) {
        AlertDialog(
            onDismissRequest = { showSystemPrompt = false },
            title = { Text("시스템 프롬프트") },
            text = {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    minLines = 10,
                    maxLines = 18,
                    label = { Text("상담 지침") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSystemPrompt = false
                        scope.launch {
                            status = liteRtEngine.updateSystemInstruction(
                                buildSystemPromptWithMemories(systemPrompt, importantMemories),
                            )
                            sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
                            memoryStore.reindexSession(messages.toList(), currentSessionId)
                            sessionSummaries = sessionStore.listSessions()
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("적용")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSystemPrompt = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("닫기")
                }
            },
        )
    }

    if (showMemories) {
        MemoryDialog(
            defaultMemories = defaultImportantMemories,
            userMemories = importantMemories,
            onDeleteUserMemory = { memory ->
                importantMemories.remove(memory)
                scope.launch {
                    sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
                    sessionSummaries = sessionStore.listSessions()
                    status = if (status.isModelLoaded) {
                        liteRtEngine.updateSystemInstruction(
                            buildSystemPromptWithMemories(systemPrompt, importantMemories),
                        )
                    } else {
                        EngineStatus(status.isModelLoaded, "중요 기억을 삭제했습니다.")
                    }
                }
            },
            onDismiss = { showMemories = false },
        )
    }
    if (showChatSettings) {
        ChatSettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            status = status,
            includeHealthContext = includeHealthContext,
            onToggleHealthContext = { includeHealthContext = !includeHealthContext },
            healthContextPeriod = healthContextPeriod,
            onCycleHealthPeriod = { healthContextPeriod = healthContextPeriod.next() },
            thinkingMode = thinkingMode,
            onCycleThinkingMode = { thinkingMode = thinkingMode.next() },
            directAttachmentMode = directAttachmentMode,
            onToggleDirectAttachmentMode = { directAttachmentMode = !directAttachmentMode },
            chatFontSize = chatFontSize,
            onChatFontSizeChange = { chatFontSize = it },
            onLoadModel = { modelPicker.launch(arrayOf("*/*")) },
            onShowSystemPrompt = { showSystemPrompt = true },
            onShowMemories = { showMemories = true },
            onNewSession = {
                showChatSettings = false
                resetToNewSession()
            },
            onShowSessions = {
                showChatSettings = false
                scope.launch {
                    sessionSummaries = sessionStore.listSessions()
                    showSessionList = true
                }
            },
            onExportSession = { exportSession.launch("counseling_session.json") },
            onImportSession = { importSession.launch(arrayOf("application/json", "text/*", "*/*")) },
            onDismiss = { showChatSettings = false },
        )
    }
    if (showSessionList) {
        SessionListDialog(
            sessions = sessionSummaries,
            currentSessionId = currentSessionId,
            onSelectSession = { sessionId ->
                showSessionList = false
                loadSession(sessionId)
            },
            onNewSession = {
                showSessionList = false
                resetToNewSession()
            },
            onDismiss = { showSessionList = false },
        )
    }

    LaunchedEffect(settingsOpenRequests) {
        if (settingsOpenRequests > 0) {
            showChatSettings = true
        }
    }

    LaunchedEffect(Unit) {
        sessionStore.loadLastSession()?.let { saved ->
            if (saved.messages.isNotEmpty()) {
                messages.clear()
                messages.addAll(saved.messages)
            }
            if (saved.systemPrompt.isNotBlank()) {
                systemPrompt = saved.systemPrompt
            }
            importantMemories.clear()
            importantMemories.addAll(saved.importantMemories)
            currentSessionId = saved.id
            memoryStore.reindexSession(messages.toList(), currentSessionId)
            sessionSummaries = sessionStore.listSessions()
        }
        status = liteRtEngine.initialize()
        if (status.isModelLoaded) {
            status = liteRtEngine.updateSystemInstruction(
                buildSystemPromptWithMemories(systemPrompt, importantMemories),
            )
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset + 6)
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset - 6)
                when {
                    scrollingDown -> onChromeVisibleChange(false)
                    scrollingUp -> onChromeVisibleChange(true)
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                activeAudioRecording?.recorder?.stop()
                activeAudioRecording?.recorder?.release()
            }
            liteRtEngine.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (false) {
        AnimatedVisibility(visible = chromeVisible) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gemma 4 E4B IT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "LiteRT-LM 로컬 실행",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        OutlinedButton(
                            enabled = !isLoadingModel && !isGenerating,
                            onClick = { showChatMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Text("☰")
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("모델 로드") },
                                onClick = {
                                    showChatMenu = false
                                    modelPicker.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("프롬프트") },
                                onClick = {
                                    showChatMenu = false
                                    showSystemPrompt = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("기억") },
                                onClick = {
                                    showChatMenu = false
                                    showMemories = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (includeHealthContext) "건강 데이터 제외" else "건강 데이터 포함") },
                                onClick = {
                                    includeHealthContext = !includeHealthContext
                                    showChatMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("건강 기간: ${healthContextPeriod.next().label}로 변경") },
                                onClick = {
                                    healthContextPeriod = healthContextPeriod.next()
                                    showChatMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("대화 내보내기") },
                                onClick = {
                                    showChatMenu = false
                                    exportSession.launch("counseling_session.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("대화 불러오기") },
                                onClick = {
                                    showChatMenu = false
                                    importSession.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (status.isModelLoaded) "모델 준비됨" else "모델 필요",
                        active = status.isModelLoaded,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (includeHealthContext) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = if (includeHealthContext) "건강 ${healthContextPeriod.label}" else "건강 제외",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = if (status.isModelLoaded) "모델 준비" else "모델 필요",
                    active = status.isModelLoaded,
                )
                Text(
                    text = if (includeHealthContext) "건강 ${healthContextPeriod.label}" else "건강 제외",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showChatSettings = true }) {
                    Text("설정")
                }
            }
        }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages) { message ->
                MessageBubble(message = message, fontSize = chatFontSize)
            }

            if (isGenerating || isLoadingModel) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                        Text(
                            when {
                                isLoadingModel -> "모델 로드 중..."
                                isThinking -> "사고 중..."
                                else -> "응답 생성 중..."
                            },
                        )
                    }
                }
            }
        }

        MessageInput2(
            value = input,
            enabled = status.isModelLoaded && !isGenerating && !isLoadingModel,
            attachmentLabel = attachmentLabel,
            onValueChange = { input = it },
            thinkingMode = thinkingMode,
            onCycleThinkingMode = { thinkingMode = thinkingMode.next() },
            onRecordAudio = {
                if (isRecordingAudio) {
                    stopRecordingAndAttach()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else {
                    recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onSpeechInput = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
                }
                speechInput.launch(intent)
            },
            onClearAttachment = {
                if (isRecordingAudio) cancelRecording()
                attachedAudioPath = null
                attachmentLabel = null
            },
            onSend = {
                val text = input.trim()
                if ((text.isEmpty() && attachmentLabel == null) || isGenerating || isRecordingAudio || !status.isModelLoaded) return@MessageInput2

                input = ""
                val userMessage = ChatMessage(
                    role = ChatRole.User,
                    content = text.ifBlank { "첨부 파일을 확인해 주세요." },
                    audioPath = attachedAudioPath,
                    attachmentLabel = attachmentLabel,
                )
                attachedAudioPath = null
                attachmentLabel = null
                messages += userMessage
                var savedImportantMemory = false
                extractImportantMemory(userMessage.content)?.let { memory ->
                    if (importantMemories.none { it.equals(memory, ignoreCase = true) }) {
                        importantMemories += memory
                        savedImportantMemory = true
                    }
                }
                if (savedImportantMemory) {
                    status = EngineStatus(status.isModelLoaded, "중요한 내용으로 저장했습니다.")
                }
                messages += ChatMessage(ChatRole.Assistant, "")
                val assistantIndex = messages.lastIndex
                isGenerating = true
                val useThinking = shouldUseThinkingMode(
                    mode = thinkingMode,
                    text = text,
                    hasAttachment = userMessage.imagePath != null || userMessage.audioPath != null,
                    importantMemories = importantMemories,
                )
                isThinking = useThinking

                scope.launch {
                    sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
                    memoryStore.reindexSession(messages.toList(), currentSessionId)
                    sessionSummaries = sessionStore.listSessions()
                    val healthContext = if (includeHealthContext) {
                        status = EngineStatus(status.isModelLoaded, "Health Connect 요약을 읽고 있습니다...")
                        val summary = readHealthSummary(context, healthContextPeriod)
                        summary.toPromptContext().also {
                            if (it == null) {
                                status = EngineStatus(status.isModelLoaded, summary.message)
                            }
                        }
                    } else {
                        null
                    }
                    val relevantMemories = memoryStore
                        .searchRelevant(text, sessionId = currentSessionId)
                        .filterNot { it.role == ChatRole.User && it.content == userMessage.content }
                    val promptMessages = messages
                        .take(assistantIndex)
                        .withMemoryContext(importantMemories, relevantMemories)
                        .withHealthContext(healthContext)
                        .withThinkingInstruction(useThinking)
                        .toList()
                    liteRtEngine.setDirectAttachmentMode(directAttachmentMode)
                    val reply = liteRtEngine.generateStreaming(promptMessages) { partial ->
                        messages[assistantIndex] = ChatMessage(ChatRole.Assistant, stripInternalThinking(partial))
                    }
                    messages[assistantIndex] = ChatMessage(
                        ChatRole.Assistant,
                        stripInternalThinking(reply).ifBlank { "응답이 비어 있습니다." },
                    )
                    sessionStore.saveLastSession(messages.toList(), systemPrompt, importantMemories.toList(), currentSessionId)
                    memoryStore.reindexSession(messages.toList(), currentSessionId)
                    sessionSummaries = sessionStore.listSessions()
                    isGenerating = false
                    isThinking = false
                }
            },
            isRecordingAudio = isRecordingAudio,
            isThinking = isThinking,
            imeBottomPadding = imeBottomPadding,
            fontSize = chatFontSize,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun GalleryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var message by remember { mutableStateOf("사진 권한을 허용하면 갤러리 이미지를 불러올 수 있습니다.") }
    var access by remember { mutableStateOf(galleryAccess(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun loadImages() {
        scope.launch {
            access = galleryAccess(context)
            message = "갤러리 이미지를 불러오는 중..."
            images = queryGalleryImages(context)
            val accessLabel = if (access == GalleryAccess.Partial) "선택한 사진" else "전체 사진"
            message = if (images.isEmpty()) {
                "불러올 수 있는 이미지가 없습니다."
            } else {
                "$accessLabel 기준으로 이미지 ${images.size}장을 불러왔습니다."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            access = galleryAccess(context)
            if (access != GalleryAccess.None) {
                loadImages()
            } else {
                message = "사진 권한이 허용되지 않았습니다. 시스템 권한 창에서 전체 사진 접근을 허용해 주세요."
            }
        },
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("사진 접근 권한") },
            text = {
                Text("전체 사진을 불러오거나, 선택한 사진 목록만 다시 조정할 수 있습니다. 권한을 줄이거나 완전히 회수하려면 권한 설정을 여세요.")
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            permissionLauncher.launch(fullGalleryPermissionsForDevice())
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("전체 권한 요청")
                    }
                    OutlinedButton(
                        onClick = {
                            showPermissionDialog = false
                            permissionLauncher.launch(partialGalleryPermissionsForDevice())
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("일부 액세스 변경")
                    }
                    OutlinedButton(
                        onClick = { showPermissionDialog = false },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("거부하거나 유지")
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        access = galleryAccess(context)
        if (access != GalleryAccess.None) loadImages()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    access = galleryAccess(context)
                    if (access == GalleryAccess.Full) loadImages() else showPermissionDialog = true
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    when (access) {
                        GalleryAccess.None -> "사진 권한"
                        GalleryAccess.Partial -> "이미지 추가"
                        GalleryAccess.Full -> "새로고침"
                    },
                )
            }
            OutlinedButton(
                onClick = { openAppSettings(context) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (access == GalleryAccess.Full) "권한 줄이기" else "권한 설정")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(images, key = { it.uri.toString() }) { image ->
                GalleryTile(image)
            }
        }
    }
}

@Composable
private fun GalleryTile(image: GalleryImage) {
    val context = LocalContext.current
    var bitmap by remember(image.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.uri) {
        bitmap = loadThumbnail(context, image.uri)
    }

    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val loaded = bitmap
            if (loaded == null) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = image.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun HealthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableStateOf(HealthPeriod.Week) }
    var selectedDay by remember { mutableStateOf<HealthDaySummary?>(null) }
    var summary by remember {
        mutableStateOf(
            HealthSummary(
                period = selectedPeriod,
                message = "Health Connect 권한을 허용하면 이번 주 또는 이번 달 건강 데이터를 볼 수 있습니다.",
            ),
        )
    }
    var isLoading by remember { mutableStateOf(false) }

    fun loadHealthData(period: HealthPeriod = selectedPeriod) {
        scope.launch {
            isLoading = true
            summary = readHealthSummary(context, period)
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { granted ->
            if (granted.containsAll(healthPermissions)) loadHealthData()
            else summary = summary.copy(message = "건강 데이터 권한이 모두 허용되지 않았습니다.")
        },
    )

    LaunchedEffect(Unit) {
        summary = readHealthSummary(context, selectedPeriod)
    }

    LaunchedEffect(selectedPeriod) {
        loadHealthData(selectedPeriod)
    }

    selectedDay?.let { day ->
        HealthDayDetailDialog(
            day = day,
            onDismiss = { selectedDay = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        scope.launch {
                            when (HealthConnectClient.getSdkStatus(context)) {
                                HealthConnectClient.SDK_AVAILABLE -> permissionLauncher.launch(healthPermissions)
                                HealthConnectClient.SDK_UNAVAILABLE -> {
                                    summary = summary.copy(message = "이 기기에서는 Health Connect를 사용할 수 없습니다.")
                                }
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                    summary = summary.copy(message = "Health Connect 설치 또는 업데이트가 필요합니다.")
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("권한 연결")
                }
                OutlinedButton(
                    onClick = { loadHealthData() },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("새로고침")
                }
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedPeriod = HealthPeriod.Week },
                    enabled = selectedPeriod != HealthPeriod.Week && !isLoading,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("주")
                }
                Button(
                    onClick = { selectedPeriod = HealthPeriod.Month },
                    enabled = selectedPeriod != HealthPeriod.Month && !isLoading,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("월")
                }
            }
        }
        item { Text("${summary.period.label} 요약", style = MaterialTheme.typography.titleMedium) }
        item { HealthMetric("걸음 수", "%,d".format(summary.steps)) }
        item { HealthMetric("총 소모 칼로리", "%,.1f kcal".format(summary.caloriesKcal)) }
        item { HealthMetric("활동 칼로리", "%,.1f kcal".format(summary.activeCaloriesKcal)) }
        item { HealthMetric("이동 거리", "%,.2f km".format(summary.distanceKm)) }
        item { HealthMetric("평균 심박수", summary.heartRateBpm?.let { "%d bpm".format(it) } ?: "데이터 없음") }
        item { HealthMetric("수면 시간", "%,.1f 시간".format(summary.sleepHours)) }
        item { Text("날짜별 기록", style = MaterialTheme.typography.titleMedium) }
        items(summary.daily) { day ->
            HealthDayCard(day = day, onClick = { selectedDay = day })
        }
        item {
            Text(
                text = summary.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HealthDayCard(day: HealthDaySummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = day.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "걸음 %,d · 거리 %,.2f km".format(day.steps, day.distanceKm),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "총 %,.1f kcal · 활동 %,.1f kcal".format(day.caloriesKcal, day.activeCaloriesKcal),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "심박 ${day.heartRateBpm?.let { "%d bpm".format(it) } ?: "데이터 없음"} · 수면 %,.1f 시간".format(day.sleepHours),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HealthDayDetailDialog(day: HealthDaySummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthDetailRow("걸음 수", "%,d".format(day.steps))
                HealthDetailRow("총 소모 칼로리", "%,.1f kcal".format(day.caloriesKcal))
                HealthDetailRow("활동 칼로리", "%,.1f kcal".format(day.activeCaloriesKcal))
                HealthDetailRow("이동 거리", "%,.2f km".format(day.distanceKm))
                HealthDetailRow("평균 심박수", day.heartRateBpm?.let { "%d bpm".format(it) } ?: "데이터 없음")
                HealthDetailRow("수면 시간", "%,.1f 시간".format(day.sleepHours))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun HealthDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(text)
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(8.dp),
                content = {},
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, fontSize: ChatFontSize = ChatFontSize.Normal) {
    val isUser = message.role == ChatRole.User
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
            tonalElevation = if (isUser) 0.dp else 1.dp,
            shadowElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.9f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isUser) "나" else "상담 챗봇",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = markdownText(message.content),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = fontSize.sizeSp.sp,
                )
                if (message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(message.content)) },
                        ) {
                            Text("복사")
                        }
                    }
                }
                if (message.attachmentLabel != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUser) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = message.attachmentLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryDialog(
    defaultMemories: List<String>,
    userMemories: List<String>,
    onDeleteUserMemory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = defaultMemories.map { MemoryItem(it, isDefault = true) } +
        userMemories.map { MemoryItem(it, isDefault = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("중요 기억") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (item.isDefault) "기본 기억" else "사용자 기억",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!item.isDefault) {
                                    OutlinedButton(
                                        onClick = { onDeleteUserMemory(item.text) },
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("삭제")
                                    }
                                }
                            }
                            Text(text = item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (userMemories.isEmpty()) {
                    item {
                        Text(
                            text = "사용자 기억은 아직 없습니다. 대화 중 '이건 중요해', '기억해줘', '저장해줘'처럼 말하면 여기에 저장됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun SessionListDialog(
    sessions: List<ChatSessionSummary>,
    currentSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("상담 세션") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sessions.isEmpty()) {
                    Text(
                        "저장된 상담 세션이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.take(20).forEach { session ->
                        OutlinedButton(
                            onClick = { onSelectSession(session.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (session.id == currentSessionId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(session.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatSessionTime(session.updatedAt)} · 메시지 ${session.messageCount}개",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNewSession, shape = RoundedCornerShape(8.dp)) {
                Text("새 세션")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun ChatSettingsDialog(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    status: EngineStatus,
    includeHealthContext: Boolean,
    onToggleHealthContext: () -> Unit,
    healthContextPeriod: HealthPeriod,
    onCycleHealthPeriod: () -> Unit,
    thinkingMode: ThinkingMode,
    onCycleThinkingMode: () -> Unit,
    directAttachmentMode: Boolean,
    onToggleDirectAttachmentMode: () -> Unit,
    chatFontSize: ChatFontSize,
    onChatFontSizeChange: (ChatFontSize) -> Unit,
    onLoadModel: () -> Unit,
    onShowSystemPrompt: () -> Unit,
    onShowMemories: () -> Unit,
    onNewSession: () -> Unit,
    onShowSessions: () -> Unit,
    onExportSession: () -> Unit,
    onImportSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("모델", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onLoadModel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("모델 파일 선택")
                }

                Text("대화", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowSystemPrompt, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("말투/프롬프트")
                    }
                    OutlinedButton(onClick = onShowMemories, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("기억")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNewSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("새 세션")
                    }
                    OutlinedButton(onClick = onShowSessions, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("세션 목록")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("내보내기")
                    }
                    OutlinedButton(onClick = onImportSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("불러오기")
                    }
                }

                Text("응답 옵션", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleHealthContext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text(if (includeHealthContext) "건강 포함" else "건강 제외")
                    }
                    OutlinedButton(onClick = onCycleHealthPeriod, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("기간 ${healthContextPeriod.label}")
                    }
                }
                OutlinedButton(onClick = onCycleThinkingMode, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("사고 모드 ${thinkingMode.label}")
                }
                OutlinedButton(onClick = onToggleDirectAttachmentMode, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text(if (directAttachmentMode) "오디오 직접 분석 켜짐" else "오디오 안전 모드")
                }

                Text("글씨 크기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatFontSize.entries.forEach { size ->
                        OutlinedButton(
                            onClick = { onChatFontSizeChange(size) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (size == chatFontSize) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(size.label)
                        }
                    }
                }

                Text("테마", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        OutlinedButton(
                            onClick = { onThemeModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (mode == themeMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun MessageInput2(
    value: String,
    enabled: Boolean,
    attachmentLabel: String?,
    onValueChange: (String) -> Unit,
    thinkingMode: ThinkingMode,
    onCycleThinkingMode: () -> Unit,
    onRecordAudio: () -> Unit,
    onSpeechInput: () -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit,
    isRecordingAudio: Boolean,
    isThinking: Boolean,
    imeBottomPadding: androidx.compose.ui.unit.Dp,
    fontSize: ChatFontSize,
    modifier: Modifier = Modifier,
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(
                start = 14.dp,
                top = 12.dp,
                end = 14.dp,
                bottom = 14.dp + imeBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (attachmentLabel != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            attachmentLabel,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onClearAttachment) {
                            Text("제거")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    OutlinedButton(
                        onClick = { showAttachmentMenu = true },
                        enabled = enabled || isRecordingAudio,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text("+")
                    }
                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = { showAttachmentMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("말하기") },
                            onClick = {
                                showAttachmentMenu = false
                                onSpeechInput()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isRecordingAudio) "녹음 정지" else "녹음 시작") },
                            onClick = {
                                showAttachmentMenu = false
                                onRecordAudio()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("사고 모드: ${thinkingMode.next().label}") },
                            onClick = {
                                showAttachmentMenu = false
                                onCycleThinkingMode()
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    minLines = 1,
                    maxLines = 5,
                    textStyle = TextStyle(fontSize = fontSize.sizeSp.sp),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("메시지를 입력하세요") },
                )
                Button(
                    onClick = onSend,
                    enabled = enabled && !isRecordingAudio && !isThinking && (value.isNotBlank() || attachmentLabel != null),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("전송")
                }
            }
        }
    }
}

@Composable
private fun MessageInput(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 3.dp, modifier = modifier) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                minLines = 1,
                maxLines = 5,
                placeholder = { Text("메시지를 입력하세요") },
            )
            Button(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("전송")
            }
        }
    }
}

private fun markdownText(value: String): AnnotatedString {
    if (!value.contains("**")) return AnnotatedString(value)

    return buildAnnotatedString {
        var index = 0
        var bold = false
        while (index < value.length) {
            val next = value.indexOf("**", startIndex = index)
            if (next < 0) {
                appendStyledMarkdownPart(value.substring(index), bold)
                break
            }
            appendStyledMarkdownPart(value.substring(index, next), bold)
            bold = !bold
            index = next + 2
        }
    }
}

private fun AnnotatedString.Builder.appendStyledMarkdownPart(text: String, bold: Boolean) {
    if (text.isEmpty()) return
    if (bold) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text)
        }
    } else {
        append(text)
    }
}

private suspend fun copyUriToInputFile(context: Context, uri: Uri, prefix: String): File = withContext(Dispatchers.IO) {
    val inputDir = File(context.filesDir, "chat_inputs").apply { mkdirs() }
    val displayName = queryOpenableDisplayName(context, uri)
    val extension = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() && it.length <= 8 }
        ?.let { ".$it" }
        ?: ""
    val target = File(inputDir, "${prefix}_${System.currentTimeMillis()}$extension")
    val partial = File(inputDir, "${target.name}.partial")

    if (partial.exists()) partial.delete()
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "첨부 파일을 열 수 없습니다." }
        partial.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    if (target.exists()) target.delete()
    check(partial.renameTo(target)) { "첨부 파일을 저장할 수 없습니다." }
    target
}

private suspend fun copyUriToImageFile(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val inputDir = File(context.filesDir, "chat_inputs").apply { mkdirs() }
    val target = File(inputDir, "image_${System.currentTimeMillis()}.jpg")
    val maxSide = 768
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height).toFloat())
            if (scale < 1f) {
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            }
        }
    } else {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "이미지 파일을 열 수 없습니다." }
            BitmapFactory.decodeStream(input)
        }?.let { decoded ->
            val width = decoded.width
            val height = decoded.height
            val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height).toFloat())
            if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (width * scale).toInt(), (height * scale).toInt(), true).also {
                    decoded.recycle()
                }
            } else {
                decoded
            }
        }
    }
    requireNotNull(bitmap) { "이미지를 디코딩할 수 없습니다." }
    target.outputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) { "이미지 JPEG 변환에 실패했습니다." }
    }
    require(target.length() in 1L..1_500_000L) { "이미지 파일이 너무 큽니다." }
    bitmap.recycle()
    target
}

@Suppress("MissingPermission")
private fun startAudioRecording(context: Context, scope: kotlinx.coroutines.CoroutineScope): ActiveAudioRecording {
    val sampleRate = 16_000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    require(minBufferSize > 0) { "마이크 버퍼를 초기화할 수 없습니다." }

    val inputDir = File(context.filesDir, "chat_inputs").apply { mkdirs() }
    val target = File(inputDir, "recording_${System.currentTimeMillis()}.wav")
    writeEmptyWavHeader(target, sampleRate, channels = 1, bitsPerSample = 16)

    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        audioFormat,
        minBufferSize * 2,
    )
    require(recorder.state == AudioRecord.STATE_INITIALIZED) { "마이크 녹음기를 초기화할 수 없습니다." }
    recorder.startRecording()

    val job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(minBufferSize)
        RandomAccessFile(target, "rw").use { output ->
            output.seek(44)
            while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) output.write(buffer, 0, read)
            }
        }
        updateWavHeader(target, sampleRate, channels = 1, bitsPerSample = 16)
    }
    return ActiveAudioRecording(recorder, target, job)
}

private fun writeEmptyWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
    file.outputStream().use { output ->
        output.write(ByteArray(44))
    }
    updateWavHeader(file, sampleRate, channels, bitsPerSample)
}

private fun updateWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
    val dataSize = (file.length() - 44L).coerceAtLeast(0L).toInt()
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    RandomAccessFile(file, "rw").use { wav ->
        wav.seek(0)
        wav.write("RIFF".toByteArray(Charsets.US_ASCII))
        wav.writeIntLe(36 + dataSize)
        wav.write("WAVE".toByteArray(Charsets.US_ASCII))
        wav.write("fmt ".toByteArray(Charsets.US_ASCII))
        wav.writeIntLe(16)
        wav.writeShortLe(1)
        wav.writeShortLe(channels)
        wav.writeIntLe(sampleRate)
        wav.writeIntLe(byteRate)
        wav.writeShortLe(blockAlign)
        wav.writeShortLe(bitsPerSample)
        wav.write("data".toByteArray(Charsets.US_ASCII))
        wav.writeIntLe(dataSize)
    }
}

private fun RandomAccessFile.writeIntLe(value: Int) {
    write(byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
        ((value shr 16) and 0xff).toByte(),
        ((value shr 24) and 0xff).toByte(),
    ))
}

private fun RandomAccessFile.writeShortLe(value: Int) {
    write(byteArrayOf(
        (value and 0xff).toByte(),
        ((value shr 8) and 0xff).toByte(),
    ))
}

private suspend fun exportChatSession(
    context: Context,
    uri: Uri,
    messages: List<ChatMessage>,
    systemPrompt: String,
    importantMemories: List<String>,
) = withContext(Dispatchers.IO) {
    val json = JSONObject()
        .put("version", 2)
        .put("systemPrompt", systemPrompt)
        .put(
            "importantMemories",
            JSONArray().apply {
                importantMemories.forEach { put(it) }
            },
        )
        .put(
            "messages",
            JSONArray().apply {
                messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role.name)
                            .put("content", message.content)
                            .put("imagePath", message.imagePath)
                            .put("audioPath", message.audioPath)
                            .put("attachmentLabel", message.attachmentLabel),
                    )
                }
            },
        )

    context.contentResolver.openOutputStream(uri).use { output ->
        requireNotNull(output) { "내보낼 파일을 열 수 없습니다." }
        output.write(json.toString(2).toByteArray(Charsets.UTF_8))
    }
}

private suspend fun importChatSession(context: Context, uri: Uri): ImportedSession = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "불러올 파일을 열 수 없습니다." }
        input.readBytes().toString(Charsets.UTF_8)
    }
    val json = JSONObject(text)
    val messages = buildList {
        val array = json.optJSONArray("messages") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val role = runCatching {
                ChatRole.valueOf(item.optString("role", ChatRole.User.name))
            }.getOrDefault(ChatRole.User)
            add(
                ChatMessage(
                    role = role,
                    content = item.optString("content"),
                    imagePath = item.optNullableString("imagePath"),
                    audioPath = item.optNullableString("audioPath"),
                    attachmentLabel = item.optNullableString("attachmentLabel"),
                ),
            )
        }
    }
    val memories = buildList {
        val array = json.optJSONArray("importantMemories") ?: JSONArray()
        for (index in 0 until array.length()) {
            val memory = array.optString(index).trim()
            if (memory.isNotBlank()) add(memory)
        }
    }
    ImportedSession(
        messages = messages,
        systemPrompt = json.optString("systemPrompt"),
        importantMemories = memories,
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}

private fun queryOpenableDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

private fun buildSystemPromptWithMemories(systemPrompt: String, importantMemories: List<String>): String {
    val allMemories = defaultImportantMemories + importantMemories
    if (allMemories.isEmpty()) return systemPrompt
    return """
        ${systemPrompt.trim()}

        [기본 중요 기억]
        ${defaultImportantMemories.joinToString(separator = "\n") { "- $it" }}

        ${if (importantMemories.isNotEmpty()) "[사용자가 중요하다고 저장한 개인 맥락]\n${importantMemories.takeLast(20).joinToString(separator = "\n") { "- $it" }}" else ""}

        기본 중요 기억은 앱의 안전한 상담 방식입니다. 사용자가 저장한 개인 맥락은 답변에 필요할 때만 조심스럽게 참고하고, 사용자의 현재 말과 다르면 현재 말을 우선하세요.
    """.trimIndent()
}

private fun ThinkingMode.next(): ThinkingMode {
    return when (this) {
        ThinkingMode.Auto -> ThinkingMode.On
        ThinkingMode.On -> ThinkingMode.Off
        ThinkingMode.Off -> ThinkingMode.Auto
    }
}

private fun HealthPeriod.next(): HealthPeriod {
    return when (this) {
        HealthPeriod.Week -> HealthPeriod.Month
        HealthPeriod.Month -> HealthPeriod.Week
    }
}

private fun formatSessionTime(updatedAt: Long): String {
    if (updatedAt <= 0L) return "시간 없음"
    return DateTimeFormatter.ofPattern("MM.dd HH:mm")
        .format(Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()))
}

private fun List<ChatMessage>.withMemoryContext(
    importantMemories: List<String>,
    relevantMemories: List<RelevantMemory>,
): List<ChatMessage> {
    if (importantMemories.isEmpty() && relevantMemories.isEmpty()) return this
    val lastUserIndex = indexOfLast { it.role == ChatRole.User }
    if (lastUserIndex < 0) return this
    val importantContext = importantMemories.takeLast(20).joinToString(separator = "\n") { "- $it" }
    val relevantContext = relevantMemories
        .filter { it.content.isNotBlank() }
        .distinctBy { "${it.role.name}:${it.content}" }
        .take(4)
        .joinToString(separator = "\n") { memory ->
            val role = if (memory.role == ChatRole.User) "사용자" else "상담 보조자"
            val attachment = memory.attachmentLabel?.let { " [$it]" }.orEmpty()
            "- $role$attachment: ${memory.content.take(220)}"
        }
    return mapIndexed { index, message ->
        if (index != lastUserIndex) {
            message
        } else {
            message.copy(
                content = """
                    ${if (importantContext.isNotBlank()) "[사용자가 중요하다고 저장한 맥락]\n$importantContext\n" else ""}
                    ${if (relevantContext.isNotBlank()) "[현재 메시지와 관련 있을 수 있는 과거 대화]\n$relevantContext\n" else ""}

                    [현재 사용자 메시지]
                    ${message.content}
                """.trimIndent(),
            )
        }
    }
}

private fun List<ChatMessage>.withHealthContext(healthContext: String?): List<ChatMessage> {
    if (healthContext.isNullOrBlank()) return this
    val lastUserIndex = indexOfLast { it.role == ChatRole.User }
    if (lastUserIndex < 0) return this
    return mapIndexed { index, message ->
        if (index != lastUserIndex) {
            message
        } else {
            message.copy(
                content = """
                    $healthContext

                    [현재 사용자 메시지]
                    ${message.content}
                """.trimIndent(),
            )
        }
    }
}

private fun List<ChatMessage>.withThinkingInstruction(enabled: Boolean): List<ChatMessage> {
    if (!enabled) return this
    val lastUserIndex = indexOfLast { it.role == ChatRole.User }
    if (lastUserIndex < 0) return this
    return mapIndexed { index, message ->
        if (index != lastUserIndex) {
            message
        } else {
            message.copy(
                content = """
                    [사고 모드]
                    답변하기 전에 내부적으로만 상황을 차분히 검토하세요.
                    내부 사고, 숨은 추론, 단계별 사고 과정, <think>...</think> 형식의 내용은 절대 출력하지 마세요.
                    화면에 보이는 답변은 상담자가 사용자에게 바로 말해도 되는 정제된 최종 답변만 쓰세요.
                    필요한 경우에만 짧은 핵심 근거 1-3개와 실행 가능한 답변을 제시하세요.
                    상담 안전성, 위험 신호, 사용자의 감정, 사실/추정 구분, 이전 맥락과 충돌 여부를 우선 확인하세요.

                    [사용자 메시지]
                    ${message.content}
                """.trimIndent(),
            )
        }
    }
}

private fun stripInternalThinking(text: String): String {
    if (text.isBlank()) return text
    var cleaned = text
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
    cleaned = cleaned.replace(Regex("(?is)<think>.*$"), "")
    cleaned = cleaned.replace(Regex("(?is)<thinking>.*$"), "")
    cleaned = cleaned
        .replace(Regex("(?im)^\\s*(내부\\s*)?사고\\s*과정\\s*[:：].*$"), "")
        .replace(Regex("(?im)^\\s*숨은\\s*추론\\s*[:：].*$"), "")
        .replace(Regex("(?im)^\\s*최종\\s*답변\\s*[:：]\\s*"), "")
    return cleaned.trim()
}

private fun HealthSummary.toPromptContext(): String? {
    if (daily.isEmpty()) return null
    val recentDays = daily.take(7).joinToString(separator = "\n") { day ->
        val heartRate = day.heartRateBpm?.let { "$it bpm" } ?: "데이터 없음"
        "- ${day.date.format(healthPromptDateFormatter)}: 걸음 ${"%,d".format(day.steps)}, 수면 ${"%.1f".format(day.sleepHours)}시간, 평균 심박 $heartRate, 활동 칼로리 ${"%.1f".format(day.activeCaloriesKcal)}kcal, 거리 ${"%.2f".format(day.distanceKm)}km"
    }
    val heartRate = heartRateBpm?.let { "$it bpm" } ?: "데이터 없음"
    return """
        [Health Connect ${period.label} 요약]
        이 자료는 사용자가 상담에 참고하도록 허용한 생활 리듬 요약입니다. 진단이나 단정의 근거로 쓰지 말고, 수면/활동/긴장 패턴을 조심스럽게 확인하는 보조 맥락으로만 사용하세요.
        기간 합계: 걸음 ${"%,d".format(steps)}, 총 소모 칼로리 ${"%.1f".format(caloriesKcal)}kcal, 활동 칼로리 ${"%.1f".format(activeCaloriesKcal)}kcal, 이동 거리 ${"%.2f".format(distanceKm)}km, 수면 ${"%.1f".format(sleepHours)}시간, 평균 심박 $heartRate
        최근 날짜별 기록:
        $recentDays
    """.trimIndent()
}

private val healthPromptDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

private fun shouldUseThinkingMode(
    mode: ThinkingMode,
    text: String,
    hasAttachment: Boolean,
    importantMemories: List<String>,
): Boolean {
    return when (mode) {
        ThinkingMode.On -> true
        ThinkingMode.Off -> false
        ThinkingMode.Auto -> {
            val normalized = text.lowercase()
            val riskSignals = listOf(
                "죽고", "자살", "자해", "해치", "폭력", "학대", "응급", "위험", "무서워", "살기 싫",
            )
            val complexSignals = listOf(
                "어떻게 해야", "왜", "판단", "결정", "고민", "갈등", "분석", "정리", "비교", "계획", "도와줘",
                "불안", "우울", "화가", "관계", "수면", "운동", "건강", "기억", "전에", "패턴",
            )
            hasAttachment ||
                importantMemories.isNotEmpty() ||
                text.length >= 80 ||
                riskSignals.any { normalized.contains(it) } ||
                complexSignals.any { normalized.contains(it) }
        }
    }
}

private fun extractImportantMemory(text: String): String? {
    val normalized = text.trim().replace(Regex("\\s+"), " ")
    if (normalized.length < 6) return null
    val wantsSaved = listOf(
        "기억해",
        "기억해줘",
        "기억해 줘",
        "저장해",
        "저장해줘",
        "저장해 줘",
        "중요해",
        "중요한",
        "잊지마",
        "잊지 마",
    ).any { normalized.contains(it) }
    if (!wantsSaved) return null
    return normalized
        .removePrefix("이거")
        .trim()
        .take(240)
}

private fun fullGalleryPermissionsForDevice(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun partialGalleryPermissionsForDevice(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasGalleryReadAccess(context: Context): Boolean {
    return galleryAccess(context) != GalleryAccess.None
}

private fun galleryAccess(context: Context): GalleryAccess {
    fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    return when {
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.Full
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.Partial
        Build.VERSION.SDK_INT == 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.Full
        Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.Full
        else -> GalleryAccess.None
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private suspend fun queryGalleryImages(context: Context): List<GalleryImage> = withContext(Dispatchers.IO) {
    val collection = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Image $id"
                add(GalleryImage(Uri.withAppendedPath(collection, id.toString()), name))
            }
        }
    }.orEmpty()
}

private suspend fun loadThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null)
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}

private suspend fun readHealthSummary(context: Context, period: HealthPeriod): HealthSummary = withContext(Dispatchers.IO) {
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> {
            return@withContext HealthSummary(period = period, message = "이 기기에서는 Health Connect를 사용할 수 없습니다.")
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            return@withContext HealthSummary(period = period, message = "Health Connect 설치 또는 업데이트가 필요합니다.")
        }
    }

    val client = HealthConnectClient.getOrCreate(context)
    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.containsAll(healthPermissions)) {
        return@withContext HealthSummary(period = period, message = "권한 연결을 눌러 Health Connect 읽기 권한을 허용해 주세요.")
    }

    runCatching {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = when (period) {
            HealthPeriod.Week -> today.minusDays((today.dayOfWeek.value - 1).toLong())
            HealthPeriod.Month -> today.withDayOfMonth(1)
        }
        val daily = buildList {
            var date = today
            while (!date.isBefore(startDate)) {
                add(readHealthDaySummary(client, date, zone))
                date = date.minusDays(1)
            }
        }.sortedByDescending { it.date }
        val heartRates = daily.mapNotNull { it.heartRateBpm }
        HealthSummary(
            period = period,
            steps = daily.sumOf { it.steps },
            distanceKm = daily.sumOf { it.distanceKm },
            caloriesKcal = daily.sumOf { it.caloriesKcal },
            activeCaloriesKcal = daily.sumOf { it.activeCaloriesKcal },
            heartRateBpm = heartRates.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            sleepHours = daily.sumOf { it.sleepHours },
            daily = daily,
            message = "이번 ${period.label} Health Connect 요약과 날짜별 기록을 표시하고 있습니다.",
        )
    }.getOrElse {
        HealthSummary(period = period, message = "건강 데이터 읽기 실패: ${it.message ?: it.javaClass.simpleName}")
    }
}

private suspend fun readHealthDaySummary(
    client: HealthConnectClient,
    date: LocalDate,
    zone: ZoneId,
): HealthDaySummary {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().coerceAtMost(Instant.now())
    val result = client.aggregate(
        AggregateRequest(
            metrics = setOf(
                StepsRecord.COUNT_TOTAL,
                DistanceRecord.DISTANCE_TOTAL,
                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                HeartRateRecord.BPM_AVG,
                SleepSessionRecord.SLEEP_DURATION_TOTAL,
            ),
            timeRangeFilter = TimeRangeFilter.between(start, end),
        ),
    )
    return HealthDaySummary(
        date = date,
        steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
        distanceKm = result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0,
        caloriesKcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
        activeCaloriesKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
        heartRateBpm = result[HeartRateRecord.BPM_AVG]?.toLong(),
        sleepHours = (result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L) / 60.0,
    )
}

private fun Instant.coerceAtMost(maximum: Instant): Instant {
    return if (isAfter(maximum)) maximum else this
}

@Preview(showBackground = true)
@Composable
fun CounselingAppPreview() {
    CounselingTheme {
        MessageBubble(
            ChatMessage(
                role = ChatRole.Assistant,
                content = "모델 로드가 끝났습니다. 메시지를 입력해 테스트하세요.",
            ),
        )
    }
}

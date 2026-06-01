package com.psychocare.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.psychocare.data.Emotion
import com.psychocare.data.Photo
import com.psychocare.data.UserProfile
import com.psychocare.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val loadProgress     by viewModel.loadProgress.collectAsState()
    val photos           by viewModel.photos.collectAsState()
    val userProfile      by viewModel.userProfile.collectAsState()
    val phenotypeData    by viewModel.phenotypeData.collectAsState()
    val phenotypeLoading by viewModel.phenotypeLoading.collectAsState()
    val context          = LocalContext.current
    val lifecycle        = LocalLifecycleOwner.current.lifecycle

    // 권한 상태 — 설정에서 돌아올 때 자동 재확인 (onResume)
    var hasCallLog   by remember { mutableStateOf(viewModel.hasCallLogPermission()) }
    var hasUsageStats by remember { mutableStateOf(viewModel.hasUsageStatsPermission()) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCallLog    = viewModel.hasCallLogPermission()
                hasUsageStats = viewModel.hasUsageStatsPermission()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // 사진 권한 런처
    val photoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) viewModel.loadPhotos()
    }

    // 통화기록 권한 런처
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLog = granted
        if (granted) viewModel.collectPhenotypeData()
    }

    // 폴더 직접 입력 다이얼로그 상태
    var showFolderDialog by remember { mutableStateOf(false) }
    var folderInput      by remember { mutableStateOf("/sdcard/DCIM/Camera") }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("카메라 폴더 경로 입력") },
            text = {
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    label = { Text("폴더 경로") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showFolderDialog = false
                    viewModel.selectFolder(folderInput)
                }) { Text("분석 시작") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("취소") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {   // ← fix: modifier 적용

        // ── 상단 바 ──────────────────────────────────────────────
        TopAppBar(
            title = { Text("PsychoCare", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { showFolderDialog = true }) {
                    Icon(Icons.Default.FolderOpen, "폴더 선택")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        when (val state = loadProgress) {

            is MainViewModel.LoadState.Idle ->
                IdleContent(
                    onRequestPhotoPermission = {
                        photoPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.ACCESS_MEDIA_LOCATION
                            )
                        )
                    },
                    onRequestCallLogPermission = {
                        callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    },
                    onOpenUsageSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    hasCallLogPermission    = hasCallLog,
                    hasUsageStatsPermission = hasUsageStats,
                    phenotypeLoading        = phenotypeLoading,
                    onCollectPhenotype      = { viewModel.collectPhenotypeData() }
                )

            is MainViewModel.LoadState.Loading,
            is MainViewModel.LoadState.AnalyzingML ->
                LoadingContent(state)

            is MainViewModel.LoadState.Done ->
                DoneContent(
                    photos        = photos,
                    userProfile   = userProfile,
                    hasPhenotype  = phenotypeData != null,
                    phenotypeLoading = phenotypeLoading,
                    onCollectPhenotype = { viewModel.collectPhenotypeData() }
                )

            is MainViewModel.LoadState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("오류: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 초기 화면
// ─────────────────────────────────────────────────────────────────

@Composable
fun IdleContent(
    onRequestPhotoPermission: () -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    hasCallLogPermission: Boolean,
    hasUsageStatsPermission: Boolean,
    phenotypeLoading: Boolean,
    onCollectPhenotype: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧠", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("PsychoCare", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "사진으로 마음을 읽어드립니다.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        // ── 갤러리 분석 ────────────────────────────────────────────
        PermissionCard(
            icon = "📷",
            title = "갤러리 분석",
            description = "DCIM/Camera 사진으로 감정·취미 분석",
            buttonText = "사진 권한 허용 & 분석 시작",
            onClick = onRequestPhotoPermission
        )

        Spacer(Modifier.height(12.dp))

        // ── 통화 기록 ───────────────────────────────────────────────
        PermissionCard(
            icon = "📞",
            title = "통화 기록 분석",
            description = "대인 교류 빈도·고립 신호 감지",
            buttonText = if (hasCallLogPermission) "✅ 허용됨 — 데이터 수집" else "통화 기록 권한 허용",
            buttonColor = if (hasCallLogPermission) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary,
            onClick = if (hasCallLogPermission) onCollectPhenotype else onRequestCallLogPermission
        )

        Spacer(Modifier.height(12.dp))

        // ── 앱 사용 통계 ────────────────────────────────────────────
        PermissionCard(
            icon = "📱",
            title = "앱 사용 통계",
            description = "스크린타임·야간 사용·게임 패턴 분석\n※ 일반 권한이 아닌 특수 권한 — 설정 화면에서 직접 허용",
            buttonText = if (hasUsageStatsPermission) "✅ 허용됨 — 데이터 수집" else "설정에서 사용 정보 접근 허용",
            buttonColor = if (hasUsageStatsPermission) Color(0xFF388E3C) else MaterialTheme.colorScheme.secondary,
            onClick = if (hasUsageStatsPermission) onCollectPhenotype else onOpenUsageSettings
        )

        if (phenotypeLoading) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("피노타입 데이터 수집 중...", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PermissionCard(
    icon: String,
    title: String,
    description: String,
    buttonText: String,
    buttonColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text(buttonText, fontSize = 13.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 로딩 화면
// ─────────────────────────────────────────────────────────────────

@Composable
fun LoadingContent(state: MainViewModel.LoadState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))

        when (state) {
            is MainViewModel.LoadState.Loading ->
                Text(state.message, textAlign = TextAlign.Center)

            is MainViewModel.LoadState.AnalyzingML -> {
                Text("ML Kit 감정 분석 중...", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("${state.current} / ${state.total}장")
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.total > 0) state.current.toFloat() / state.total else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {}
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 분석 완료 화면
// ─────────────────────────────────────────────────────────────────

@Composable
fun DoneContent(
    photos: List<Photo>,
    userProfile: UserProfile?,
    hasPhenotype: Boolean = false,
    phenotypeLoading: Boolean = false,
    onCollectPhenotype: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {

        userProfile?.let { ProfileSummaryCard(it) }

        // 피노타입 수집 버튼
        if (!hasPhenotype) {
            OutlinedButton(
                onClick = onCollectPhenotype,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                enabled = !phenotypeLoading
            ) {
                if (phenotypeLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (phenotypeLoading) "수집 중..." else "📞📱 통화·앱 데이터도 추가 수집")
            }
        } else {
            Text(
                "✅ 피노타입 데이터 수집 완료",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = Color(0xFF388E3C)
            )
        }

        Text(
            "분석된 사진 (${photos.size}장)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(photos) { photo ->
                PhotoGridItem(photo)
            }
        }
    }
}

@Composable
fun ProfileSummaryCard(profile: UserProfile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                icon = profile.dominantEmotion.emoji,
                label = profile.dominantEmotion.korean,
                sub = "주요 감정"
            )
            if (profile.topHobbies.isNotEmpty()) {
                SummaryItem(icon = "🎯", label = profile.topHobbies.first(), sub = "주요 취미")
            }
            SummaryItem(icon = "📊", label = "${profile.analyzedPhotoCount}장", sub = "분석 사진")
        }
    }
}

@Composable
fun SummaryItem(icon: String, label: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PhotoGridItem(photo: Photo) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val emotion = Emotion.fromLabel(photo.dominantEmotion)
        if (emotion != Emotion.NEUTRAL) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                Text(emotion.emoji, fontSize = 14.sp)
            }
        }
    }
}

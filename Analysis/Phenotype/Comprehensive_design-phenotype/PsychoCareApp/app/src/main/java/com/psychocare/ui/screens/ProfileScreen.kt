package com.psychocare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychocare.data.Emotion
import com.psychocare.data.UserProfile
import com.psychocare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val loadState   by viewModel.loadProgress.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text("나의 심리 프로필", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        when {
            // 아직 분석 안됨
            loadState is MainViewModel.LoadState.Idle ->
                EmptyProfileContent()

            // 분석 중
            loadState is MainViewModel.LoadState.Loading ||
            loadState is MainViewModel.LoadState.AnalyzingML ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("사진 분석 중...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

            // 완료
            userProfile != null ->
                ProfileContent(profile = userProfile!!)

            else -> EmptyProfileContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 분석 전 안내
// ─────────────────────────────────────────────────────────────────

@Composable
fun EmptyProfileContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("홈 화면에서 사진을 분석하면", fontSize = 16.sp)
            Text("여기에 프로필이 표시됩니다.", fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 프로필 컨텐츠
// ─────────────────────────────────────────────────────────────────

@Composable
fun ProfileContent(profile: UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── 감정 상태 카드 ────────────────────────────────────────
        SectionCard(title = "감정 상태") {
            // 감정 점수 바
            val score = profile.emotionalScore   // -1.0 ~ 1.0
            val scorePercent = (score + 1f) / 2f // 0.0 ~ 1.0 로 변환

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("부정", fontSize = 12.sp, color = Color(0xFFE57373))
                Text(
                    "${profile.dominantEmotion.emoji} ${profile.dominantEmotion.korean}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text("긍정", fontSize = 12.sp, color = Color(0xFF81C784))
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { scorePercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = when {
                    scorePercent > 0.6f -> Color(0xFF81C784)
                    scorePercent > 0.4f -> Color(0xFFFFD54F)
                    else                -> Color(0xFFE57373)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // 감정 기복
            val variabilityLabel = when {
                profile.emotionalVariability > 0.7f -> "높음 (감정 기복이 큼)"
                profile.emotionalVariability > 0.4f -> "보통"
                else                                -> "낮음 (감정이 안정적)"
            }
            InfoRow("감정 기복", variabilityLabel)

            // 감정 분포
            Spacer(Modifier.height(8.dp))
            Text("감정 분포", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            profile.emotionDistribution
                .filter { it.value > 0.03f }
                .entries
                .sortedByDescending { it.value }
                .forEach { (emotion, ratio) ->
                    EmotionBar(emotion, ratio)
                }
        }

        // ── 취미 / 관심사 카드 ────────────────────────────────────
        SectionCard(title = "취미 & 관심사") {
            if (profile.topHobbies.isEmpty()) {
                Text("분석된 취미 데이터가 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                profile.topHobbies.forEachIndexed { index, hobby ->
                    val hobbyCount = profile.hobbies[hobby] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}위",
                            fontWeight = FontWeight.Bold,
                            color = when (index) {
                                0 -> Color(0xFFFFD700)
                                1 -> Color(0xFFC0C0C0)
                                2 -> Color(0xFFCD7F32)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            hobby,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${hobbyCount}장",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < profile.topHobbies.lastIndex)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        // ── 생활 패턴 카드 ────────────────────────────────────────
        SectionCard(title = "생활 패턴") {
            InfoRow("활동 시간대", profile.activeTimeOfDay)
            Spacer(Modifier.height(4.dp))
            InfoRow("사회적 성향", profile.socialLevel)
            Spacer(Modifier.height(4.dp))
            InfoRow("촬영 빈도",   profile.photoFrequency)
        }

        // ── 분석 정보 ─────────────────────────────────────────────
        SectionCard(title = "분석 정보") {
            InfoRow("분석된 사진", "${profile.analyzedPhotoCount}장")
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────
// 재사용 컴포저블
// ─────────────────────────────────────────────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun EmotionBar(emotion: Emotion, ratio: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${emotion.emoji} ${emotion.korean}",
            modifier = Modifier.width(90.dp),
            fontSize = 13.sp
        )
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = when (emotion) {
                Emotion.HAPPY     -> Color(0xFF81C784)
                Emotion.SAD       -> Color(0xFF64B5F6)
                Emotion.ANGRY     -> Color(0xFFE57373)
                Emotion.SURPRISED -> Color(0xFFFFD54F)
                Emotion.FEARFUL   -> Color(0xFFBA68C8)
                Emotion.DISGUSTED -> Color(0xFFA1887F)
                Emotion.NEUTRAL   -> Color(0xFF90A4AE)
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${"%.0f".format(ratio * 100)}%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

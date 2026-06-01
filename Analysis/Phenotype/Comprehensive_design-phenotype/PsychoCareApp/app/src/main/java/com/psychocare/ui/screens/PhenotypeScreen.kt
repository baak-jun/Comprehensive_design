package com.psychocare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychocare.data.AppCategory
import com.psychocare.data.AppUsageSummary
import com.psychocare.data.CallLogSummary
import com.psychocare.data.PhenotypeData
import com.psychocare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhenotypeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val phenotypeData   by viewModel.phenotypeData.collectAsState()
    val phenotypeLoading by viewModel.phenotypeLoading.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("생활 패턴 분석", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        // 조기 return 대신 when 분기 — Compose 그룹 구조 안정성 확보
        val data = phenotypeData
        when {
            phenotypeLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("데이터 수집 중...", fontSize = 14.sp)
                    }
                }
            }

            data == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("📊", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("아직 데이터가 없습니다", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "홈 화면에서 통화기록 및 앱 사용 데이터를 수집하세요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.collectPhenotypeData() }) {
                            Text("지금 수집하기")
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 핵심 지표 한눈에 (정상/주의/위험)
                    IndicatorSummary(data.callLog, data.appUsage)
                    data.callLog?.let { CallLogSection(it) }
                    data.appUsage?.let { AppUsageSection(it) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 통화 기록 섹션
// ─────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────
// 핵심 지표 요약 (정상 / 주의 / 위험 등급)
// ─────────────────────────────────────────────────────────────────

private enum class Level(val label: String, val color: Color) {
    NORMAL("정상", Color(0xFF2E7D32)),
    CAUTION("주의", Color(0xFFF57F17)),
    RISK("위험", Color(0xFFC62828))
}

@Composable
private fun IndicatorSummary(callLog: CallLogSummary?, appUsage: AppUsageSummary?) {
    // 1) 사회적 교류 (통화 빈도 + 고립 경보)
    val social = when {
        callLog == null -> null
        callLog.isolationAlert || callLog.totalCallsThisWeek < 3 -> Level.RISK
        callLog.weeklyChangePct <= -30f -> Level.CAUTION
        else -> Level.NORMAL
    }
    // 2) 고립 위험 (연속 무연락일)
    val isolation = when {
        callLog == null -> null
        callLog.zeroCommunicationStreak >= 4 -> Level.RISK
        callLog.zeroCommunicationStreak >= 2 -> Level.CAUTION
        else -> Level.NORMAL
    }
    // 3) 디지털 과의존 (일평균 스크린타임)
    val digital = when {
        appUsage == null || !appUsage.hasPermission -> null
        appUsage.dailyAvgScreenTimeMin >= 360 -> Level.RISK    // 6시간+
        appUsage.dailyAvgScreenTimeMin >= 240 -> Level.CAUTION // 4시간+
        else -> Level.NORMAL
    }
    // 4) 수면/리듬 위험 (월 단위 야간 사용일 + 리듬 붕괴)
    val sleep = when {
        appUsage == null || !appUsage.hasPermission -> null
        appUsage.rhythmDisrupted || appUsage.sleepDeficientDaysPerMonth >= 7 -> Level.RISK
        appUsage.nightUsageDaysPerMonth >= 5 || appUsage.dailyAvgLateNightMin >= 30 -> Level.CAUTION
        else -> Level.NORMAL
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("핵심 지표 요약", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("통화기록 · 앱 사용 통계 기반 심리 신호",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndicatorTile("사회적 교류", social,
                    callLog?.let { "주 ${it.totalCallsThisWeek}건" } ?: "-", Modifier.weight(1f))
                IndicatorTile("고립 위험", isolation,
                    callLog?.let { "무연락 ${it.zeroCommunicationStreak}일" } ?: "-", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndicatorTile("디지털 과의존", digital,
                    appUsage?.takeIf { it.hasPermission }?.let { "${it.dailyAvgScreenTimeMin}분/일" } ?: "권한없음",
                    Modifier.weight(1f))
                IndicatorTile("수면/리듬", sleep,
                    appUsage?.takeIf { it.hasPermission }?.let { "야간 ${it.nightUsageDaysPerMonth}일/월" } ?: "권한없음",
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BigDayCount(
    icon: String,
    label: String,
    days: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$icon $label", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$days", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.width(2.dp))
            Text("일", fontSize = 14.sp, color = color,
                modifier = Modifier.padding(bottom = 5.dp))
        }
        Text("최근 30일 중", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IndicatorTile(title: String, level: Level?, detail: String, modifier: Modifier = Modifier) {
    val lvl = level ?: Level.NORMAL
    val faded = level == null
    Column(
        modifier = modifier
            .background(
                if (faded) Color(0xFF9E9E9E).copy(alpha = 0.12f) else lvl.color.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(if (faded) Color(0xFF9E9E9E) else lvl.color, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(if (faded) "—" else lvl.label, color = Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CallLogSection(callLog: CallLogSummary) {
    SectionCard(
        title = "📞 통화 기록 분석",
        alertColor = if (callLog.isolationAlert) Color(0xFFFFEBEE) else null,
        alertMessage = if (callLog.isolationAlert) "⚠️ 대인 교류 급감 감지" else null
    ) {
        // 주간 통화 비교 바
        WeeklyComparisonBar(
            label = "주간 통화",
            thisWeek = callLog.totalCallsThisWeek,
            prevWeek = callLog.totalCallsPrevWeek,
            unit = "건",
            changePct = callLog.weeklyChangePct
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // 상세 지표들
        StatRow("고유 연락처", "${callLog.uniqueContactsThisWeek}명")
        StatRow(
            "평균 통화 시간",
            "${(callLog.avgDurationSec / 60).toInt()}분 ${(callLog.avgDurationSec % 60).toInt()}초"
        )
        StatRow("부재중 비율", "${"%.0f".format(callLog.missedCallRate * 100)}%",
            valueColor = if (callLog.missedCallRate > 0.3f) Color(0xFFE53935) else null
        )
        StatRow("저장된 연락처 비율", "${"%.0f".format(callLog.namedContactsRate * 100)}%")

        if (callLog.zeroCommunicationStreak >= 2) {
            Spacer(Modifier.height(8.dp))
            AlertChip("연속 무연락 ${callLog.zeroCommunicationStreak}일")
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 앱 사용 통계 섹션
// ─────────────────────────────────────────────────────────────────

@Composable
private fun AppUsageSection(appUsage: AppUsageSummary) {
    if (!appUsage.hasPermission) {
        SectionCard(title = "📱 앱 사용 통계") {
            Text(
                "권한이 허용되지 않았습니다.\n설정 > 앱 > 특별한 앱 접근 > 사용 정보 접근",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }

    SectionCard(
        title = "📱 앱 사용 통계",
        alertColor = if (appUsage.dailyAvgLateNightMin > 30) Color(0xFFFFF3E0) else null,
        alertMessage = if (appUsage.dailyAvgLateNightMin > 30) "⚠️ 야간 사용 과다" else null
    ) {
        // 스크린타임 원형 표시
        ScreenTimeDisplay(
            dailyMin = appUsage.dailyAvgScreenTimeMin,
            weeklyChangePct = appUsage.weeklyChangePct
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        StatRow(
            "야간(00~06시) 평균",
            "${appUsage.dailyAvgLateNightMin}분/일",
            valueColor = if (appUsage.dailyAvgLateNightMin > 30) Color(0xFFE53935) else null
        )
        StatRow("최장 연속 세션", "${appUsage.longestSingleSessionMin}분",
            valueColor = if (appUsage.longestSingleSessionMin > 90) Color(0xFFE53935) else null
        )

        // ── 수면·생활 리듬 (최근 30일) ────────────────────────────
        run {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌙 수면·생활 리듬", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("최근 30일 기준", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))

            // 핵심 숫자 2개를 크게 강조 — "한 달 동안 며칠"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigDayCount(
                    icon = "🌙", label = "야간 폰 사용",
                    days = appUsage.nightUsageDaysPerMonth,
                    color = when {
                        appUsage.nightUsageDaysPerMonth >= 10 -> Color(0xFFC62828)
                        appUsage.nightUsageDaysPerMonth >= 5  -> Color(0xFFF57F17)
                        else -> Color(0xFF2E7D32)
                    },
                    modifier = Modifier.weight(1f)
                )
                BigDayCount(
                    icon = "😴", label = "수면 부족 추정",
                    days = appUsage.sleepDeficientDaysPerMonth,
                    color = when {
                        appUsage.sleepDeficientDaysPerMonth >= 7 -> Color(0xFFC62828)
                        appUsage.sleepDeficientDaysPerMonth >= 3 -> Color(0xFFF57F17)
                        else -> Color(0xFF2E7D32)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))

            // 리듬 점수 게이지
            val rhythmColor = when {
                appUsage.rhythmScore >= 70 -> Color(0xFF2E7D32)
                appUsage.rhythmScore >= 40 -> Color(0xFFF57F17)
                else                        -> Color(0xFFC62828)
            }
            Column(Modifier.padding(vertical = 2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("생활 리듬 점수", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${appUsage.rhythmScore}/100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rhythmColor)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(appUsage.rhythmScore / 100f)
                        .background(rhythmColor, RoundedCornerShape(4.dp)))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (appUsage.avgBedtimeProxyHour >= 0f) {
                val h = appUsage.avgBedtimeProxyHour.toInt()
                val m = ((appUsage.avgBedtimeProxyHour - h) * 60).toInt()
                StatRow("평균 취침 추정", "%02d:%02d".format(h, m),
                    valueColor = if (h in 1..4) Color(0xFFE53935) else null)
            }
            if (appUsage.rhythmDisrupted) {
                Spacer(Modifier.height(8.dp))
                AlertChip("🚨 생활 리듬 붕괴 — 수면/기상 불규칙")
            }
        }

        // 앱 카테고리별 사용 시간
        if (appUsage.topApps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("카테고리별 상위 앱", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            val grouped = appUsage.topApps.groupBy { it.category }
            val categoryColors = mapOf(
                AppCategory.GAME        to Color(0xFFE91E63),
                AppCategory.VIDEO       to Color(0xFF2196F3),
                AppCategory.SNS         to Color(0xFF9C27B0),
                AppCategory.PRODUCTIVITY to Color(0xFF4CAF50),
                AppCategory.OTHER       to Color(0xFF9E9E9E)
            )

            grouped.entries
                .sortedByDescending { entry -> entry.value.sumOf { it.totalTimeMin } }
                .forEach { (category, apps) ->
                    val totalMin = apps.sumOf { it.totalTimeMin }
                    AppCategoryBar(
                        category = "${apps.first().category.korean} (${apps.take(2).joinToString { it.appName }})",
                        minutes = totalMin,
                        maxMinutes = appUsage.topApps.sumOf { it.totalTimeMin },
                        color = categoryColors[category] ?: Color(0xFF9E9E9E)
                    )
                    Spacer(Modifier.height(6.dp))
                }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 재사용 컴포넌트들
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    alertColor: Color? = null,
    alertMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = alertColor ?: MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            alertMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, color = Color(0xFFB71C1C))
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun WeeklyComparisonBar(
    label: String,
    thisWeek: Int,
    prevWeek: Int,
    unit: String,
    changePct: Float
) {
    val max = maxOf(thisWeek, prevWeek, 1)
    val isIncrease = changePct >= 0
    val changeText = if (isIncrease) "+${"%.0f".format(changePct)}%" else "${"%.0f".format(changePct)}%"
    val changeColor = if (isIncrease) Color(0xFF388E3C) else Color(0xFFE53935)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(changeText, fontSize = 12.sp, color = changeColor, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))

        // 이번 주
        BarWithLabel("이번 주", thisWeek, max, unit, Color(0xFF1976D2))
        Spacer(Modifier.height(4.dp))
        // 지난 주
        BarWithLabel("지난 주", prevWeek, max, unit, Color(0xFFBDBDBD))
    }
}

@Composable
private fun BarWithLabel(label: String, value: Int, max: Int, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, modifier = Modifier.width(42.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
        ) {
            val fraction = if (max > 0) value.toFloat() / max else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("$value$unit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun AppCategoryBar(
    category: String,
    minutes: Long,
    maxMinutes: Long,
    color: Color
) {
    val h = minutes / 60
    val m = minutes % 60
    val timeText = if (h > 0) "${h}시간 ${m}분" else "${m}분"
    val fraction = if (maxMinutes > 0) minutes.toFloat() / maxMinutes else 0f

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(category, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text(timeText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun ScreenTimeDisplay(dailyMin: Long, weeklyChangePct: Float) {
    val h = dailyMin / 60
    val m = dailyMin % 60
    val isUp = weeklyChangePct >= 0
    val changeText = if (isUp) "+${"%.0f".format(weeklyChangePct)}%" else "${"%.0f".format(weeklyChangePct)}%"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (h > 0) "${h}시간 ${m}분" else "${m}분",
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = if (dailyMin > 360) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
            Text("일 평균 스크린타임", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(changeText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = if (isUp) Color(0xFFE53935) else Color(0xFF388E3C))
            Text("주간 변화", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AlertChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFCDD2)
    ) {
        Text(
            text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp, color = Color(0xFFB71C1C)
        )
    }
}

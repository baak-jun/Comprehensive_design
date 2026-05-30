package com.example.counseling

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.psychocare.data.AppUsageSummary
import com.psychocare.data.CallLogSummary
import com.psychocare.phenotype.AppUsageAnalyzer
import com.psychocare.phenotype.CallLogAnalyzer
import kotlinx.coroutines.launch

@Composable
fun PhenotypeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callAnalyzer = remember { CallLogAnalyzer(context.applicationContext) }
    val appUsageAnalyzer = remember { AppUsageAnalyzer(context.applicationContext) }
    var callSummary by remember { mutableStateOf<CallLogSummary?>(null) }
    var appUsageSummary by remember { mutableStateOf<AppUsageSummary?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("통화 기록과 앱 사용 통계를 사용자가 허용한 경우에만 분석합니다.") }

    fun refresh() {
        scope.launch {
            isLoading = true
            callSummary = callAnalyzer.analyze()
            appUsageSummary = appUsageAnalyzer.analyze()
            message = when {
                callSummary == null && appUsageSummary?.hasPermission != true -> "통화 기록 권한과 앱 사용 정보 접근 권한이 필요합니다."
                callSummary == null -> "통화 기록 권한이 없어 앱 사용 통계만 표시합니다."
                appUsageSummary?.hasPermission != true -> "앱 사용 정보 접근 권한이 없어 통화 패턴만 표시합니다."
                else -> "최근 14일 기준 피노타입 패턴을 표시합니다."
            }
            isLoading = false
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            message = if (granted) "통화 기록 권한이 허용되었습니다." else "통화 기록 권한이 허용되지 않았습니다."
            refresh()
        },
    )

    LaunchedEffect(Unit) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Text("생활 패턴", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                            refresh()
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("통화 권한")
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("사용 정보 설정")
                }
                OutlinedButton(
                    onClick = { refresh() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("새로고침")
                }
            }
        }
        item {
            CallPatternCard(callSummary)
        }
        item {
            AppUsagePatternCard(appUsageSummary)
        }
        appUsageSummary?.topApps.orEmpty().takeIf { it.isNotEmpty() }?.let { apps ->
            item {
                Text("앱별 사용 시간 - 최근 7일 상위 ${apps.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(apps) { app ->
                PatternMetricCard(
                    title = app.appName,
                    lines = listOf(
                        "사용 시간: ${app.totalTimeMin}분",
                        "분류: ${app.category.korean}",
                        "패키지: ${app.packageName}",
                    ),
                )
            }
        }
    }
}

@Composable
private fun CallPatternCard(summary: CallLogSummary?) {
    if (summary == null) {
        PatternMetricCard(
            title = "통화 패턴",
            lines = listOf("통화 기록 권한이 없거나 분석할 기록이 없습니다."),
        )
        return
    }
    PatternMetricCard(
        title = "통화 패턴",
        lines = listOf(
            "이번 주 통화: ${summary.totalCallsThisWeek}회",
            "지난 주 통화: ${summary.totalCallsPrevWeek}회",
            "고유 연락처: ${summary.uniqueContactsThisWeek}명",
            "평균 통화 시간: ${summary.avgDurationSec.toInt()}초",
            "부재중 비율: ${"%.0f".format(summary.missedCallRate * 100)}%",
            "연속 무연락: ${summary.zeroCommunicationStreak}일",
            "주간 변화: ${"%.1f".format(summary.weeklyChangePct)}%",
            "대인교류 급감 경보: ${if (summary.isolationAlert) "있음" else "없음"}",
        ),
    )
}

@Composable
private fun AppUsagePatternCard(summary: AppUsageSummary?) {
    if (summary == null || !summary.hasPermission) {
        PatternMetricCard(
            title = "앱 사용 패턴",
            lines = listOf("앱 사용 정보 접근 권한이 필요합니다."),
        )
        return
    }
    PatternMetricCard(
        title = "앱 사용 패턴",
        lines = listOf(
            "일평균 스크린타임: ${summary.dailyAvgScreenTimeMin}분",
            "일평균 야간 사용: ${summary.dailyAvgLateNightMin}분",
            "최장 연속 세션: ${summary.longestSingleSessionMin}분",
            "주간 변화: ${"%.1f".format(summary.weeklyChangePct)}%",
        ),
    )
}

@Composable
private fun PatternMetricCard(title: String, lines: List<String>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

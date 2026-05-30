package com.example.counseling

import com.example.counseling.llm.ChatMessage
import com.example.counseling.llm.ChatRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun buildSystemPromptWithMemories(systemPrompt: String, importantMemories: List<String>): String {
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

fun ThinkingMode.next(): ThinkingMode {
    return when (this) {
        ThinkingMode.Auto -> ThinkingMode.On
        ThinkingMode.On -> ThinkingMode.Off
        ThinkingMode.Off -> ThinkingMode.Auto
    }
}

fun HealthPeriod.next(): HealthPeriod {
    return when (this) {
        HealthPeriod.Week -> HealthPeriod.Month
        HealthPeriod.Month -> HealthPeriod.Week
    }
}

fun formatSessionTime(updatedAt: Long): String {
    if (updatedAt <= 0L) return "시간 없음"
    return DateTimeFormatter.ofPattern("MM.dd HH:mm")
        .format(Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()))
}

fun List<ChatMessage>.withMemoryContext(
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

fun List<ChatMessage>.withHealthContext(healthContext: String?): List<ChatMessage> {
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

fun List<ChatMessage>.withThinkingInstruction(enabled: Boolean): List<ChatMessage> {
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

fun stripInternalThinking(text: String): String {
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

fun HealthSummary.toPromptContext(): String? {
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

val healthPromptDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

fun shouldUseThinkingMode(
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

fun extractImportantMemory(text: String): String? {
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


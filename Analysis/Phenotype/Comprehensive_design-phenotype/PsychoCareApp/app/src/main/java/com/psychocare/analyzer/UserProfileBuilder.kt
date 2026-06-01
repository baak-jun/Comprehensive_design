package com.psychocare.analyzer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psychocare.data.AppCategory
import com.psychocare.data.Emotion
import com.psychocare.data.PhenotypeData
import com.psychocare.data.Photo
import com.psychocare.data.UserProfile
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ──────────────────────────────────────────────────────────────────
 * UserProfileBuilder
 *
 * ML Kit 분석이 완료된 Photo 목록을 받아
 * 사용자의 심리/성격 프로필을 생성합니다.
 * ──────────────────────────────────────────────────────────────────
 */
object UserProfileBuilder {

    private val gson = Gson()

    fun build(analyzedPhotos: List<Photo>): UserProfile {
        if (analyzedPhotos.isEmpty()) return UserProfile()

        val validPhotos = analyzedPhotos.filter { it.isAnalyzed }

        return UserProfile(
            emotionDistribution  = calcEmotionDistribution(validPhotos),
            dominantEmotion      = calcDominantEmotion(validPhotos),
            emotionalScore       = calcEmotionalScore(validPhotos),
            emotionalVariability = calcEmotionalVariability(validPhotos),
            hobbies              = calcHobbies(validPhotos),
            topHobbies           = calcTopHobbies(validPhotos),
            activeTimeOfDay      = calcActiveTime(validPhotos),
            socialLevel          = calcSocialLevel(validPhotos),
            photoFrequency       = calcPhotoFrequency(validPhotos),
            analyzedPhotoCount   = validPhotos.size,
            dateRangeStart       = validPhotos.mapNotNull { it.correctedTimeUtc }.minOrNull(),
            dateRangeEnd         = validPhotos.mapNotNull { it.correctedTimeUtc }.maxOrNull()
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 감정 분포 계산
    // ─────────────────────────────────────────────────────────────────

    private fun calcEmotionDistribution(photos: List<Photo>): Map<Emotion, Float> {
        val total = photos.size.toFloat()
        val counts = photos
            .groupBy { Emotion.fromLabel(it.dominantEmotion) }
            .mapValues { (_, list) -> list.size / total }

        return Emotion.values().associateWith { counts[it] ?: 0f }
    }

    private fun calcDominantEmotion(photos: List<Photo>): Emotion {
        return photos
            .groupBy { Emotion.fromLabel(it.dominantEmotion) }
            .maxByOrNull { it.value.size }
            ?.key ?: Emotion.NEUTRAL
    }

    /**
     * 감정 점수: -1.0 (매우 부정) ~ +1.0 (매우 긍정)
     */
    private fun calcEmotionalScore(photos: List<Photo>): Float {
        val scoreMap = mapOf(
            Emotion.HAPPY     to  1.0f,
            Emotion.SURPRISED to  0.3f,
            Emotion.NEUTRAL   to  0.0f,
            Emotion.FEARFUL   to -0.5f,
            Emotion.DISGUSTED to -0.6f,
            Emotion.SAD       to -0.8f,
            Emotion.ANGRY     to -1.0f
        )
        val scores = photos.map { scoreMap[Emotion.fromLabel(it.dominantEmotion)] ?: 0f }
        return scores.average().toFloat()
    }

    /**
     * 감정 변동성: 표준편차 기반 (높을수록 감정 기복이 큼)
     */
    private fun calcEmotionalVariability(photos: List<Photo>): Float {
        val scoreMap = mapOf(
            Emotion.HAPPY to 1.0f, Emotion.SURPRISED to 0.3f,
            Emotion.NEUTRAL to 0.0f, Emotion.FEARFUL to -0.5f,
            Emotion.DISGUSTED to -0.6f, Emotion.SAD to -0.8f, Emotion.ANGRY to -1.0f
        )
        val scores = photos.map { scoreMap[Emotion.fromLabel(it.dominantEmotion)] ?: 0f }
        if (scores.size < 2) return 0f
        val mean = scores.average()
        val variance = scores.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }

    // ─────────────────────────────────────────────────────────────────
    // 취미 분석
    // ─────────────────────────────────────────────────────────────────

    private fun calcHobbies(photos: List<Photo>): Map<String, Int> {
        val hobbyCounts = mutableMapOf<String, Int>()
        val labelType = object : TypeToken<List<PhotoAnalyzer.LabelResult>>() {}.type

        photos.forEach { photo ->
            val labels = runCatching {
                gson.fromJson<List<PhotoAnalyzer.LabelResult>>(photo.detectedLabels, labelType)
            }.getOrNull() ?: return@forEach

            labels.forEach { label ->
                val hobby = PhotoAnalyzer.HOBBY_MAPPING[label.label.lowercase()]
                if (hobby != null) {
                    hobbyCounts[hobby] = (hobbyCounts[hobby] ?: 0) + 1
                }
            }
        }
        return hobbyCounts
    }

    private fun calcTopHobbies(photos: List<Photo>): List<String> {
        return calcHobbies(photos)
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    // ─────────────────────────────────────────────────────────────────
    // 활동 패턴 분석
    // ─────────────────────────────────────────────────────────────────

    /**
     * 주로 활동하는 시간대
     */
    private fun calcActiveTime(photos: List<Photo>): String {
        val hours = photos.mapNotNull { photo ->
            photo.correctedTimeUtc?.let { utc ->
                ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(utc),
                    ZoneId.of(photo.timezoneId ?: "Asia/Seoul")
                ).hour
            }
        }
        if (hours.isEmpty()) return "알 수 없음"

        val avgHour = hours.average()
        return when {
            avgHour in 5.0..11.0  -> "오전형 (아침 활동)"
            avgHour in 11.0..17.0 -> "낮형 (오후 활동)"
            avgHour in 17.0..22.0 -> "저녁형"
            else                  -> "야행성"
        }
    }

    /**
     * 사회성 수준 (얼굴 수 기반)
     */
    private fun calcSocialLevel(photos: List<Photo>): String {
        val avgFaces = photos.map { it.faceCount }.average()
        return when {
            avgFaces < 1.2 -> "혼자 활동을 즐기는 편"
            avgFaces < 2.5 -> "소규모 모임 선호"
            else           -> "사교적이고 그룹 활동 선호"
        }
    }

    /**
     * 사진 촬영 빈도
     */
    private fun calcPhotoFrequency(photos: List<Photo>): String {
        val times = photos.mapNotNull { it.correctedTimeUtc }.sorted()
        if (times.size < 2) return "알 수 없음"

        val spanDays = (times.last() - times.first()) / (1000 * 60 * 60 * 24.0)
        if (spanDays < 1) return "알 수 없음"

        val perDay = photos.size / spanDays
        return when {
            perDay >= 5  -> "매일 (하루 ${perDay.toInt()}장 이상)"
            perDay >= 2  -> "주 3-5회"
            perDay >= 0.5 -> "주 1-2회"
            else          -> "가끔 (한 달에 몇 번)"
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 프로필 → 심리상담 컨텍스트 생성 (Gemma 프롬프트용)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Gemma 프롬프트용 컨텍스트 생성
     * @param profile  갤러리 분석 결과
     * @param phenotype 피노타입(통화·앱 사용) 분석 결과 (선택)
     */
    fun buildCounselingContext(
        profile: UserProfile,
        phenotype: PhenotypeData? = null
    ): String {
        return buildString {
            appendLine("=== 사용자 분석 결과 ===")
            appendLine()

            // ── 갤러리 분석 ────────────────────────────────────────
            appendLine("[감정 상태 — 갤러리 기반]")
            appendLine("• 주요 감정: ${profile.dominantEmotion.korean} ${profile.dominantEmotion.emoji}")
            appendLine("• 감정 점수: ${"%.2f".format(profile.emotionalScore)} (-1=매우부정, +1=매우긍정)")
            appendLine("• 감정 기복: ${
                when {
                    profile.emotionalVariability > 0.7f -> "높음 (감정 기복이 큼)"
                    profile.emotionalVariability > 0.4f -> "보통"
                    else                                -> "낮음 (감정이 안정적)"
                }
            }")
            appendLine()
            appendLine("[감정 분포]")
            profile.emotionDistribution
                .filter { it.value > 0.05f }
                .entries.sortedByDescending { it.value }
                .forEach { (emotion, ratio) ->
                    appendLine("• ${emotion.korean}: ${"%.0f".format(ratio * 100)}%")
                }
            appendLine()
            appendLine("[관심사 / 취미]")
            if (profile.topHobbies.isEmpty()) {
                appendLine("• 분석 데이터 부족")
            } else {
                profile.topHobbies.forEachIndexed { i, hobby ->
                    appendLine("• ${i + 1}. $hobby")
                }
            }
            appendLine()
            appendLine("[생활 패턴 — 사진 기반]")
            appendLine("• 활동 시간대: ${profile.activeTimeOfDay}")
            appendLine("• 사회적 성향: ${profile.socialLevel}")
            appendLine("• 사진 촬영 빈도: ${profile.photoFrequency}")
            appendLine()
            appendLine("[분석 기반] 사진 ${profile.analyzedPhotoCount}장")

            // ── 피노타입 분석 (있을 때만) ──────────────────────────
            phenotype?.let { ph ->
                appendLine()
                appendLine("=== 피노타입 신호 ===")

                // 통화 기록
                ph.callLog?.let { call ->
                    appendLine()
                    appendLine("[대인 교류 — 통화 기록]")
                    appendLine("• 이번 주 통화: ${call.totalCallsThisWeek}건" +
                            " (지난 주: ${call.totalCallsPrevWeek}건, " +
                            "${formatChangePct(call.weeklyChangePct)})")
                    appendLine("• 고유 연락처: ${call.uniqueContactsThisWeek}명")
                    appendLine("• 평균 통화 시간: ${(call.avgDurationSec / 60).toInt()}분 " +
                            "${(call.avgDurationSec % 60).toInt()}초")
                    if (call.missedCallRate > 0.3f) {
                        appendLine("• ⚠️ 부재중 비율 높음: ${"%.0f".format(call.missedCallRate * 100)}%")
                    }
                    if (call.zeroCommunicationStreak >= 3) {
                        appendLine("• ⚠️ 연속 무연락: ${call.zeroCommunicationStreak}일째")
                    }
                    if (call.isolationAlert) {
                        appendLine("• 🚨 대인 교류 급감 감지 (전주 대비 ${formatChangePct(call.weeklyChangePct)})")
                    }
                }

                // 앱 사용 통계
                ph.appUsage?.let { app ->
                    if (!app.hasPermission) return@let
                    appendLine()
                    appendLine("[디지털 행동 — 앱 사용]")
                    appendLine("• 일평균 스크린 타임: ${app.dailyAvgScreenTimeMin}분")
                    if (app.dailyAvgLateNightMin > 10) {
                        appendLine("• ⚠️ 야간(00~06시) 평균 사용: ${app.dailyAvgLateNightMin}분/일")
                    }
                    if (app.longestSingleSessionMin > 60) {
                        appendLine("• ⚠️ 최장 연속 사용: ${app.longestSingleSessionMin}분")
                    }
                    if (abs(app.weeklyChangePct) > 20f) {
                        appendLine("• 스크린타임 주간 변화: ${formatChangePct(app.weeklyChangePct)}")
                    }

                    val gameApps = app.topApps.filter { it.category == AppCategory.GAME }
                    val videoApps = app.topApps.filter { it.category == AppCategory.VIDEO }
                    if (gameApps.isNotEmpty()) {
                        appendLine("• 주요 게임: ${gameApps.take(2).joinToString { it.appName }}")
                    }
                    if (videoApps.isNotEmpty()) {
                        appendLine("• 주요 동영상: ${videoApps.take(2).joinToString { it.appName }}")
                    }

                    // 수면·생활 리듬 (최근 30일)
                    if (app.analyzedDays > 0) {
                        appendLine()
                        appendLine("[수면·생활 리듬 — 최근 ${app.analyzedDays}일]")
                        appendLine("• 야간(00~06시) 폰 사용일: ${app.nightUsageDaysPerMonth}일")
                        appendLine("• 수면 부족 추정일(새벽 활동): ${app.sleepDeficientDaysPerMonth}일")
                        if (app.avgBedtimeProxyHour >= 0f) {
                            val h = app.avgBedtimeProxyHour.toInt()
                            val m = ((app.avgBedtimeProxyHour - h) * 60).toInt()
                            appendLine("• 평균 취침 추정 시각: ${"%02d:%02d".format(h, m)}")
                        }
                        appendLine("• 생활 리듬 점수: ${app.rhythmScore}/100 (높을수록 규칙적)")
                        if (app.rhythmDisrupted) {
                            appendLine("• 🚨 생활 리듬 붕괴 신호 — 수면/기상 패턴 불규칙")
                        }
                    }
                }

                appendLine()
                appendLine("[상담 지침] 위 수면·리듬 신호가 있으면 단정하지 말고 확인형으로 물어보세요. " +
                        "예: \"요즘 잠은 잘 자고 있어? 늦게까지 깨어 있는 날이 좀 있었던 것 같아서.\"")
                appendLine("========================")
            }
        }
    }

    private fun formatChangePct(pct: Float): String {
        val sign = if (pct >= 0) "+" else ""
        return "${sign}${"%.0f".format(pct)}%"
    }
}

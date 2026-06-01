package com.psychocare.chatbot

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.psychocare.data.ChatMessage
import com.psychocare.data.PhenotypeData
import com.psychocare.data.UserProfile
import java.io.File

/**
 * 개발자 대시보드용 세션 로거
 *
 * 매 메시지 처리 시점에 LLM에 실제로 들어가는 프롬프트 전문 +
 * 분석 데이터(감정/취미/피노타입)를 JSON으로 외부 저장소에 기록한다.
 *
 * 컴퓨터의 PowerShell 웹서버가 ADB로 이 파일을 읽어 대시보드에 표시한다.
 *   경로: /sdcard/Android/data/com.psychocare/files/dev_session.json
 */
object DevLogger {

    private const val TAG = "DevLogger"
    private const val FILE_NAME = "dev_session.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 현재 세션 상태를 JSON으로 기록
     */
    fun writeSession(
        context: Context,
        fullPrompt: String,
        systemPrompt: String,
        counselingContext: String,
        currentUserMessage: String,
        userProfile: UserProfile?,
        phenotype: PhenotypeData?,
        messages: List<ChatMessage>,
        modelPath: String,
        lastResponse: String? = null
    ) {
        runCatching {
            // 분석 지표 → 0~100 게이지로 매핑 (실제 데이터 기반 파생값)
            val insights = userProfile?.let { p ->
                // 위험도: 감정 점수가 낮을수록(부정) 높음
                val risk = ((1f - (p.emotionalScore + 1f) / 2f) * 100f).coerceIn(0f, 100f)
                // 진정성: 감정 기복이 클수록 낮음 (안정적일수록 높음)
                val authenticity = ((1f - p.emotionalVariability.coerceIn(0f, 1f)) * 100f)
                // 개방성: 사회성/취미 다양성 기반
                val openness = (p.topHobbies.size * 18f).coerceIn(0f, 100f)
                Insights(
                    riskLevel    = risk.toInt(),
                    authenticity = authenticity.toInt(),
                    openness     = openness.toInt(),
                    emotionalScore = p.emotionalScore,
                    emotionalVariability = p.emotionalVariability,
                    dominantEmotion = p.dominantEmotion.korean,
                    emotionDistribution = p.emotionDistribution.mapKeys { it.key.korean }
                )
            }

            val phenotypeSummary = phenotype?.let { ph ->
                PhenotypeSummary(
                    callsThisWeek      = ph.callLog?.totalCallsThisWeek,
                    callsPrevWeek      = ph.callLog?.totalCallsPrevWeek,
                    callWeeklyChangePct = ph.callLog?.weeklyChangePct,
                    isolationAlert     = ph.callLog?.isolationAlert ?: false,
                    zeroCommStreak     = ph.callLog?.zeroCommunicationStreak,
                    dailyScreenTimeMin = ph.appUsage?.dailyAvgScreenTimeMin,
                    lateNightMin       = ph.appUsage?.dailyAvgLateNightMin,
                    longestSessionMin  = ph.appUsage?.longestSingleSessionMin
                )
            }

            val session = DevSession(
                timestamp         = System.currentTimeMillis(),
                modelPath         = modelPath,
                currentUserMessage = currentUserMessage,
                lastResponse      = lastResponse,
                systemPrompt      = systemPrompt,
                counselingContext = counselingContext,
                fullPrompt        = fullPrompt,
                promptCharCount   = fullPrompt.length,
                profile           = userProfile?.let {
                    ProfileBrief(
                        dominantEmotion = it.dominantEmotion.korean,
                        topHobbies      = it.topHobbies,
                        activeTimeOfDay = it.activeTimeOfDay,
                        socialLevel     = it.socialLevel,
                        photoFrequency  = it.photoFrequency,
                        analyzedPhotoCount = it.analyzedPhotoCount
                    )
                },
                insights          = insights,
                phenotype         = phenotypeSummary,
                messages          = messages.map {
                    MsgBrief(it.role.name, it.content, it.timestamp)
                }
            )

            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            file.writeText(gson.toJson(session))
            Log.d(TAG, "세션 기록: ${file.absolutePath} (프롬프트 ${fullPrompt.length}자)")
        }.onFailure {
            Log.e(TAG, "세션 기록 실패: ${it.message}")
        }
    }

    // ── JSON 직렬화용 데이터 클래스 ──────────────────────────────

    private data class DevSession(
        val timestamp: Long,
        val modelPath: String,
        val currentUserMessage: String,
        val lastResponse: String?,
        val systemPrompt: String,
        val counselingContext: String,
        val fullPrompt: String,
        val promptCharCount: Int,
        val profile: ProfileBrief?,
        val insights: Insights?,
        val phenotype: PhenotypeSummary?,
        val messages: List<MsgBrief>
    )

    private data class ProfileBrief(
        val dominantEmotion: String,
        val topHobbies: List<String>,
        val activeTimeOfDay: String,
        val socialLevel: String,
        val photoFrequency: String,
        val analyzedPhotoCount: Int
    )

    private data class Insights(
        val riskLevel: Int,
        val authenticity: Int,
        val openness: Int,
        val emotionalScore: Float,
        val emotionalVariability: Float,
        val dominantEmotion: String,
        val emotionDistribution: Map<String, Float>
    )

    private data class PhenotypeSummary(
        val callsThisWeek: Int?,
        val callsPrevWeek: Int?,
        val callWeeklyChangePct: Float?,
        val isolationAlert: Boolean,
        val zeroCommStreak: Int?,
        val dailyScreenTimeMin: Long?,
        val lateNightMin: Long?,
        val longestSessionMin: Long?
    )

    private data class MsgBrief(
        val role: String,
        val content: String,
        val timestamp: Long
    )
}

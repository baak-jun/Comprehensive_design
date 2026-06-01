package com.psychocare.chatbot

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.psychocare.analyzer.UserProfileBuilder
import com.psychocare.data.PhenotypeData
import com.psychocare.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaPipe LLM Inference API 기반 Gemma on-device 심리상담 챗봇
 *
 * 모델 파일 배치 방법 (APK는 소형 유지, 모델은 별도 push):
 *   adb push gemma-2b-it-gpu-int4.bin \
 *     /sdcard/Android/data/com.psychocare/files/models/
 *
 * 대안: assets/models/ 에 파일이 있으면 자동 fallback (APK가 1.4GB가 됨)
 */
class GemmaChatbot(private val context: Context) {

    private val TAG = "GemmaChatbot"

    private var llmInference: LlmInference? = null
    private var isReady = false
    private var loadedFromPath: String = ""

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    // ─────────────────────────────────────────────────────────────────
    // 모델 경로 탐색 (외부 저장소 우선 → assets fallback)
    // ─────────────────────────────────────────────────────────────────

    companion object {
        // GPU 모델과 CPU 모델 모두 지원 (GPU → CPU 순서로 탐색)
        private val MODEL_CANDIDATES = listOf(
            "gemma-2b-it-cpu-int4.bin",   // CPU 버전 (모든 기기 호환)
            "gemma-2b-it-gpu-int4.bin",   // GPU 버전 (OpenCL 지원 기기)
        )
        private const val ASSETS_MODEL_PATH = "models/gemma-2b-it-gpu-int4.bin"

        fun externalModelDir(context: Context): String {
            return context.getExternalFilesDir("models")?.absolutePath ?: ""
        }

        fun externalModelPath(context: Context): String {
            return File(externalModelDir(context), MODEL_CANDIDATES.first()).absolutePath
        }
    }

    /**
     * 외부 저장소에서 사용 가능한 모델 파일을 탐색
     * CPU 모델 우선, GPU 모델 fallback, 마지막으로 assets
     */
    private fun resolveModelPath(): String? {
        val modelDir = context.getExternalFilesDir("models") ?: return null

        // CPU → GPU 순서로 외부 저장소 탐색
        for (filename in MODEL_CANDIDATES) {
            val file = File(modelDir, filename)
            if (file.exists() && file.length() > 1_000_000L) {
                Log.d(TAG, "모델 발견: ${file.absolutePath} (${file.length() / 1_000_000}MB)")
                return file.absolutePath
            }
        }

        // assets fallback
        return try {
            context.assets.open(ASSETS_MODEL_PATH).close()
            Log.d(TAG, "assets 모델 사용: $ASSETS_MODEL_PATH")
            null
        } catch (e: Exception) {
            Log.w(TAG, "모델 없음. ${externalModelDir(context)} 에 파일을 복사하세요.")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 초기화
    // ─────────────────────────────────────────────────────────────────

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val absolutePath = resolveModelPath()

            val modelPath = absolutePath ?: ASSETS_MODEL_PATH
            loadedFromPath = absolutePath ?: "assets/$ASSETS_MODEL_PATH"

            // 0.10.20+: setTopK/setTemperature/setRandomSeed 제거됨 → setModelPath + setMaxTokens만 사용
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isReady = true
            Log.d(TAG, "Gemma 모델 로드 완료: $loadedFromPath")
            Unit   // Result<Unit> 명시
        }.onFailure {
            Log.e(TAG, "Gemma 초기화 실패: ${it.message}")
        }
    }

    fun isModelReady() = isReady

    // ─────────────────────────────────────────────────────────────────
    // 메시지 전송 → 스트리밍 응답 (Flow)
    // ─────────────────────────────────────────────────────────────────

    fun sendMessage(
        userMessage: String,
        userProfile: UserProfile?,
        phenotypeData: PhenotypeData? = null
    ): Flow<StreamingToken> = flow {

        if (!isReady || llmInference == null) {
            val extDir = externalModelDir(context)
            emit(StreamingToken.Error(
                "⚠️ Gemma 모델 로드 실패\n\n" +
                "이 기기는 GPU 모델(OpenCL)이 호환되지 않습니다.\n" +
                "CPU 모델을 다운로드 후 복사하세요:\n\n" +
                "① Kaggle에서 gemma-2b-it-cpu-int4 다운로드\n" +
                "   kaggle.com/models/google/gemma/tfLite/\n\n" +
                "② ADB로 복사:\n" +
                "   adb push gemma-2b-it-cpu-int4.bin\n" +
                "   \"$extDir/\""
            ))
            return@flow
        }

        if (detectCrisisSignal(userMessage)) {
            emit(StreamingToken.Text(CRISIS_RESPONSE))
            emit(StreamingToken.Done)
            return@flow
        }

        val prompt = buildPrompt(userMessage, userProfile, phenotypeData)
        Log.d(TAG, "프롬프트 전송 (${prompt.length}자)")

        // 개발자 대시보드용: 프롬프트 전송 시점 기록 (응답 전)
        val counselingCtx = userProfile?.let {
            UserProfileBuilder.buildCounselingContext(it, phenotypeData)
        } ?: ""
        devSnapshot = DevSnapshot(prompt, SYSTEM_PROMPT, counselingCtx, userMessage,
            userProfile, phenotypeData)
        writeDevLog(userMessage, lastResponse = null)

        runCatching {
            val response = withContext(Dispatchers.IO) {
                llmInference!!.generateResponse(prompt)
            }
            conversationHistory.add(userMessage to response)
            if (conversationHistory.size > 10) conversationHistory.removeAt(0)
            // 응답 완료 후 다시 기록 (lastResponse 포함)
            writeDevLog(userMessage, lastResponse = response)
            emit(StreamingToken.Text(response))
            emit(StreamingToken.Done)
        }.onFailure { e ->
            Log.e(TAG, "추론 오류: ${e.message}")
            emit(StreamingToken.Error(e.message ?: "알 수 없는 오류"))
        }
    }

    // ── 개발자 대시보드 로깅 ────────────────────────────────────────
    private data class DevSnapshot(
        val fullPrompt: String,
        val systemPrompt: String,
        val counselingContext: String,
        val userMessage: String,
        val profile: UserProfile?,
        val phenotype: PhenotypeData?
    )
    private var devSnapshot: DevSnapshot? = null
    var devMessages: List<com.psychocare.data.ChatMessage> = emptyList()

    private fun writeDevLog(userMessage: String, lastResponse: String?) {
        val s = devSnapshot ?: return
        DevLogger.writeSession(
            context = context,
            fullPrompt = s.fullPrompt,
            systemPrompt = s.systemPrompt,
            counselingContext = s.counselingContext,
            currentUserMessage = userMessage,
            userProfile = s.profile,
            phenotype = s.phenotype,
            messages = devMessages,
            modelPath = loadedFromPath,
            lastResponse = lastResponse
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Gemma IT 형식 프롬프트
    // ─────────────────────────────────────────────────────────────────

    private fun buildPrompt(
        userMessage: String,
        userProfile: UserProfile?,
        phenotypeData: PhenotypeData? = null
    ): String {
        // 시스템 지침 + 분석 컨텍스트 (Gemma는 system 역할이 없어 첫 user 턴 앞에 붙임)
        val preamble = buildString {
            append(SYSTEM_PROMPT)
            append("\n\n")
            if (userProfile != null) {
                append(UserProfileBuilder.buildCounselingContext(userProfile, phenotypeData))
                append("\n위 분석 결과를 참고해 공감적으로 대화하세요.\n")
            }
            append("\n이제 사용자의 말에 자연스러운 한국어로, 같은 문장을 반복하지 말고 답하세요.\n\n")
        }

        return buildString {
            if (conversationHistory.isEmpty()) {
                // 첫 턴: preamble + 현재 메시지
                append("<start_of_turn>user\n")
                append(preamble)
                append(userMessage)
                append("<end_of_turn>\n<start_of_turn>model\n")
            } else {
                // 첫 user 턴 = preamble + 첫 사용자 발화
                val first = conversationHistory.first()
                append("<start_of_turn>user\n").append(preamble).append(first.first).append("<end_of_turn>\n")
                append("<start_of_turn>model\n").append(first.second).append("<end_of_turn>\n")

                // 나머지 대화 기록 (각각 올바른 턴으로 분리)
                conversationHistory.drop(1).forEach { (u, a) ->
                    append("<start_of_turn>user\n").append(u).append("<end_of_turn>\n")
                    append("<start_of_turn>model\n").append(a).append("<end_of_turn>\n")
                }

                // 현재 메시지 → 모델 턴 열기
                append("<start_of_turn>user\n").append(userMessage).append("<end_of_turn>\n")
                append("<start_of_turn>model\n")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 위기 신호 감지
    // ─────────────────────────────────────────────────────────────────

    private val CRISIS_KEYWORDS = listOf(
        "죽고싶", "자살", "자해", "살기싫", "사라지고싶",
        "끝내고싶", "없어지고싶", "더이상못살겠"
    )

    private fun detectCrisisSignal(message: String): Boolean {
        val cleaned = message.replace(" ", "")
        return CRISIS_KEYWORDS.any { cleaned.contains(it) }
    }

    private val CRISIS_RESPONSE = """
        지금 많이 힘드시군요. 당신의 마음이 정말 걱정됩니다.

        혼자 감당하지 않으셔도 됩니다. 지금 바로 전문가와 이야기해 주세요.

        📞 자살예방상담전화: 1393 (24시간)
        📞 정신건강위기상담전화: 1577-0199 (24시간)
        📱 카카오톡: '마음이음' 채널 (무료)

        당신은 소중한 사람입니다. 도움을 받을 자격이 있어요.
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────
    // 시스템 프롬프트
    // ─────────────────────────────────────────────────────────────────

    private val SYSTEM_PROMPT = """
        당신은 따뜻하고 공감적인 심리상담 AI입니다. 다음 원칙을 따르세요:

        1. 비판단적 태도: 사용자의 감정을 있는 그대로 수용하세요
        2. 공감 우선: 조언보다 먼저 감정을 인정하고 공감을 표현하세요
        3. 개방형 질문: 열린 질문으로 대화를 이끌어주세요
        4. 전문가 연계: 심각한 위기 시 전문가 상담을 권유하세요
        5. 한국어로 대화하며 친근하되 전문적인 어조를 유지하세요
        6. 응답은 2~4문장으로 간결하게 유지하세요
        7. AI임을 밝히고 진단·처방은 하지 마세요
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────
    // 유틸리티
    // ─────────────────────────────────────────────────────────────────

    fun resetConversation() {
        conversationHistory.clear()
        Log.d(TAG, "대화 기록 초기화")
    }

    fun close() {
        llmInference?.close()
        isReady = false
        Log.d(TAG, "Gemma 리소스 해제")
    }

    sealed class StreamingToken {
        data class Text(val value: String) : StreamingToken()
        object Done : StreamingToken()
        data class Error(val message: String) : StreamingToken()
    }
}

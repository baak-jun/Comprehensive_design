package com.psychocare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZonedDateTime

// ─────────────────────────────────────────────
// 사진 데이터 모델
// ─────────────────────────────────────────────
@Entity(tableName = "photos")
data class Photo(
    @PrimaryKey val filePath: String,
    val fileName: String,

    // 파일명 알고리즘으로 파싱한 시간
    val parsedTimeFromName: Long?,          // epoch ms
    // EXIF에서 읽은 원본 시간
    val exifTime: Long?,                    // epoch ms
    // 시간대 알고리즘으로 보정된 최종 시간 (UTC)
    val correctedTimeUtc: Long?,            // epoch ms
    // 촬영 시간대
    val timezoneId: String?,               // e.g. "Asia/Seoul"

    // GPS
    val latitude: Double?,
    val longitude: Double?,

    // ML Kit 분석 결과
    val dominantEmotion: String?,          // HAPPY, SAD, ANGRY, SURPRISED, NEUTRAL
    val emotionConfidence: Float?,
    val detectedLabels: String?,           // JSON array: ["hiking","mountain","sport"]
    val faceCount: Int = 0,

    // 분석 완료 여부
    val isAnalyzed: Boolean = false,
    val analyzedAt: Long? = null
)

// ─────────────────────────────────────────────
// 감정 열거형
// ─────────────────────────────────────────────
enum class Emotion(val label: String, val emoji: String, val korean: String) {
    HAPPY("HAPPY", "😊", "행복"),
    SAD("SAD", "😢", "슬픔"),
    ANGRY("ANGRY", "😡", "분노"),
    SURPRISED("SURPRISED", "😲", "놀람"),
    FEARFUL("FEARFUL", "😨", "두려움"),
    DISGUSTED("DISGUSTED", "😒", "불쾌"),
    NEUTRAL("NEUTRAL", "😐", "평온");

    companion object {
        fun fromLabel(label: String?) =
            values().find { it.label == label } ?: NEUTRAL
    }
}

// ─────────────────────────────────────────────
// 사용자 프로필 모델
// ─────────────────────────────────────────────
data class UserProfile(
    // 감정 분포 (0.0 ~ 1.0)
    val emotionDistribution: Map<Emotion, Float> = emptyMap(),
    // 주요 감정
    val dominantEmotion: Emotion = Emotion.NEUTRAL,
    // 감정 점수 (-1.0: 매우 부정 ~ +1.0: 매우 긍정)
    val emotionalScore: Float = 0f,
    // 감정 변동성 (표준편차 기반)
    val emotionalVariability: Float = 0f,

    // 취미/활동 (label → 빈도)
    val hobbies: Map<String, Int> = emptyMap(),
    // 상위 3개 취미
    val topHobbies: List<String> = emptyList(),

    // 활동 패턴
    val activeTimeOfDay: String = "",       // "morning" / "afternoon" / "evening" / "night"
    val socialLevel: String = "",           // "혼자" / "소규모" / "대규모"
    val photoFrequency: String = "",        // "매일" / "주 2-3회" / "가끔"

    // 분석 기반 사진 수
    val analyzedPhotoCount: Int = 0,
    // 분석 날짜 범위
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null
)

// ─────────────────────────────────────────────
// 채팅 메시지
// ─────────────────────────────────────────────
@Entity(tableName = "chat_messages")
data class ChatMessage(
    // 생성 시점에 고유 ID 부여 (LazyColumn key 충돌 방지)
    // Room에 저장하지 않고 메모리 리스트로만 쓰므로 autoGenerate 대신 직접 발급
    @PrimaryKey val id: Long = nextId(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
) {
    companion object {
        private val counter = java.util.concurrent.atomic.AtomicLong(0)
        private fun nextId(): Long = counter.incrementAndGet()
    }
}

enum class MessageRole { USER, ASSISTANT, SYSTEM }

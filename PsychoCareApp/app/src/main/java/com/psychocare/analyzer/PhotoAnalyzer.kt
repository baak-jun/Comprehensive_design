package com.psychocare.analyzer

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.psychocare.data.Emotion
import com.psychocare.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ──────────────────────────────────────────────────────────────────
 * PhotoAnalyzer - ML Kit 기반 사진 분석기
 *
 * 분석 내용:
 *  1. 얼굴 감정 분석 (FaceDetection + 표정 분류)
 *  2. 이미지 라벨링 (취미/활동/장소 감지)
 *  3. 결과 → UserProfile 구성용 데이터 생성
 * ──────────────────────────────────────────────────────────────────
 */
class PhotoAnalyzer(private val context: Context) {

    private val TAG = "PhotoAnalyzer"

    // ── ML Kit 초기화 ────────────────────────────────────────────────
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // 눈 뜨기, 웃음 확률
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.1f)
            .build()
    )

    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.65f)  // 65% 이상 신뢰도만 사용
            .build()
    )

    // ─────────────────────────────────────────────────────────────────
    // 단일 사진 분석
    // ─────────────────────────────────────────────────────────────────

    suspend fun analyze(photo: Photo): Photo = withContext(Dispatchers.IO) {
        // 원본 크기 그대로 로드하면 고해상도 사진(12MP~48MP)이 수백MB를 차지해 OOM 발생
        // → ML Kit은 512px 이하에서도 충분히 동작하므로 다운샘플링
        val bitmap = runCatching {
            decodeSampledBitmap(photo.filePath, maxSize = 512)
        }.getOrNull() ?: return@withContext photo.copy(isAnalyzed = true)

        val image = InputImage.fromBitmap(bitmap, 0)

        val emotionResult = analyzeEmotion(image)
        val labelResult   = analyzeLabels(image)

        bitmap.recycle()

        photo.copy(
            dominantEmotion   = emotionResult.emotion.label,
            emotionConfidence = emotionResult.confidence,
            faceCount         = emotionResult.faceCount,
            detectedLabels    = Gson().toJson(labelResult),
            isAnalyzed        = true,
            analyzedAt        = System.currentTimeMillis()
        )
    }

    /**
     * 메모리 효율적인 비트맵 디코딩
     * inSampleSize 를 사용해 maxSize 이하로 축소 후 로드
     */
    private fun decodeSampledBitmap(filePath: String, maxSize: Int): android.graphics.Bitmap? {
        // 1단계: 실제 로드 없이 크기만 읽기
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2단계: 적절한 inSampleSize 계산 (2의 배수)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize || bounds.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }

        // 3단계: 축소된 크기로 실제 로드
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565  // ARGB_8888 대비 절반 메모리
        }
        return BitmapFactory.decodeFile(filePath, opts)
    }

    // ─────────────────────────────────────────────────────────────────
    // 감정 분석 (얼굴 감지 + 표정 분류)
    // ─────────────────────────────────────────────────────────────────

    private suspend fun analyzeEmotion(image: InputImage): EmotionResult =
        suspendCancellableCoroutine { cont ->
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        // 얼굴 없음 → 장면 분위기로 감정 추정 (NEUTRAL 기본)
                        cont.resume(EmotionResult(Emotion.NEUTRAL, 0.5f, 0))
                        return@addOnSuccessListener
                    }

                    // 가장 큰 얼굴(주인공) 기준으로 감정 판단
                    val primaryFace = faces.maxByOrNull { it.boundingBox.width() }!!

                    val smilingProb = primaryFace.smilingProbability ?: 0f
                    val leftEyeOpen = primaryFace.leftEyeOpenProbability ?: 1f
                    val rightEyeOpen = primaryFace.rightEyeOpenProbability ?: 1f

                    // 규칙 기반 감정 분류
                    val (emotion, confidence) = classifyEmotion(
                        smiling = smilingProb,
                        leftEyeOpen = leftEyeOpen,
                        rightEyeOpen = rightEyeOpen
                    )

                    cont.resume(EmotionResult(emotion, confidence, faces.size))
                }
                .addOnFailureListener {
                    Log.e(TAG, "얼굴 감지 실패: ${it.message}")
                    cont.resume(EmotionResult(Emotion.NEUTRAL, 0f, 0))
                }
        }

    /**
     * 얼굴 표정 파라미터 → 감정 분류
     *
     * ML Kit은 smiling/eyeOpen 확률만 제공하므로
     * 규칙 기반으로 감정을 추정합니다.
     */
    private fun classifyEmotion(
        smiling: Float,
        leftEyeOpen: Float,
        rightEyeOpen: Float
    ): Pair<Emotion, Float> {
        val eyeOpenAvg = (leftEyeOpen + rightEyeOpen) / 2f

        return when {
            smiling > 0.8f -> Emotion.HAPPY to smiling
            smiling > 0.5f && eyeOpenAvg > 0.7f -> Emotion.HAPPY to (smiling * 0.9f)
            eyeOpenAvg < 0.3f && smiling < 0.3f -> Emotion.SAD to (1f - eyeOpenAvg)
            eyeOpenAvg > 0.9f && smiling < 0.2f -> Emotion.SURPRISED to eyeOpenAvg
            smiling < 0.2f && eyeOpenAvg in 0.4f..0.7f -> Emotion.NEUTRAL to 0.7f
            else -> Emotion.NEUTRAL to 0.6f
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 이미지 라벨링 (취미/활동/장소 감지)
    // ─────────────────────────────────────────────────────────────────

    private suspend fun analyzeLabels(image: InputImage): List<LabelResult> =
        suspendCancellableCoroutine { cont ->
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val result = labels
                        .filter { it.confidence >= 0.65f }
                        .map { LabelResult(it.text, it.confidence) }
                        .take(10)  // 상위 10개만
                    cont.resume(result)
                }
                .addOnFailureListener {
                    Log.e(TAG, "이미지 라벨링 실패: ${it.message}")
                    cont.resume(emptyList())
                }
        }

    // ─────────────────────────────────────────────────────────────────
    // 취미/활동 매핑 테이블
    // ─────────────────────────────────────────────────────────────────

    companion object {
        // ML Kit 라벨 → 취미 카테고리 매핑
        val HOBBY_MAPPING = mapOf(
            // 운동/스포츠
            "hiking" to "등산", "mountain" to "등산", "trail" to "등산",
            "gym" to "운동", "fitness" to "운동", "sport" to "운동",
            "football" to "축구", "basketball" to "농구", "tennis" to "테니스",
            "swimming" to "수영", "cycling" to "자전거", "yoga" to "요가",
            "running" to "달리기",
            // 음식/요리
            "food" to "요리", "cooking" to "요리", "meal" to "맛집탐방",
            "restaurant" to "맛집탐방", "coffee" to "카페", "cafe" to "카페",
            "baking" to "베이킹", "pizza" to "맛집탐방",
            // 여행
            "travel" to "여행", "beach" to "여행", "sea" to "여행",
            "landscape" to "여행", "city" to "여행", "architecture" to "여행",
            "landmark" to "여행", "tourism" to "여행",
            // 음악/공연
            "concert" to "음악", "music" to "음악", "guitar" to "음악",
            "piano" to "음악", "festival" to "공연관람",
            // 독서/공부
            "book" to "독서", "library" to "독서", "reading" to "독서",
            "study" to "공부",
            // 반려동물
            "dog" to "반려동물", "cat" to "반려동물", "pet" to "반려동물",
            // 게임
            "game" to "게임", "gaming" to "게임",
            // 쇼핑
            "shopping" to "쇼핑", "mall" to "쇼핑",
            // 자연/식물
            "flower" to "식물", "garden" to "가드닝", "plant" to "식물",
            // 예술
            "art" to "예술", "painting" to "예술", "museum" to "전시관람",
            "photography" to "사진",
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 결과 데이터 클래스
    // ─────────────────────────────────────────────────────────────────

    data class EmotionResult(
        val emotion: Emotion,
        val confidence: Float,
        val faceCount: Int
    )

    data class LabelResult(
        val label: String,
        val confidence: Float
    )
}

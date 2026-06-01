package com.psychocare.algorithm

import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ──────────────────────────────────────────────────────────────────
 * 파일명 알고리즘 (Filename Algorithm)
 *
 * 목적: 파일명에서 촬영 날짜/시간을 파싱하고,
 *       다운로드된 사진인지 직접 촬영한 사진인지 판별
 *
 * 지원 형식:
 *  - 삼성:   20240315_143022.jpg
 *  - 기본:   IMG_20240315_143022.jpg
 *  - LG/기타: IMG_20240315_143022_001.jpg
 *  - 아이폰: IMG_1234.HEIC (EXIF 의존)
 *  - 스크린샷/다운로드 → 제외
 * ──────────────────────────────────────────────────────────────────
 */
object FilenameParser {

    private val TAG = "FilenameParser"

    // ── 촬영 사진으로 인정하는 파일명 패턴 ──────────────────────────
    private val CAMERA_PATTERNS = listOf(
        // 삼성 기본 카메라: 20240315_143022.jpg
        Regex("""^(\d{8})_(\d{6})(\d+)?"""),
        // Android 표준: IMG_20240315_143022.jpg
        Regex("""^IMG_(\d{8})_(\d{6})"""),
        // 기타 제조사: PIC_20240315_143022.jpg
        Regex("""^(?:PIC|DSC|CAM|PHOTO)_(\d{8})_(\d{6})"""),
        // 동영상: VID_20240315_143022.mp4
        Regex("""^VID_(\d{8})_(\d{6})"""),
        // 아이폰 HEIC: IMG_1234.HEIC
        Regex("""^IMG_\d{4}\.(HEIC|heic|JPG|jpg)$"""),
    )

    // ── 다운로드/공유 이미지로 판단 → 제외할 패턴 ──────────────────
    private val EXCLUDED_PATTERNS = listOf(
        Regex("""^Screenshot""", RegexOption.IGNORE_CASE),    // 스크린샷
        Regex("""^screen""", RegexOption.IGNORE_CASE),
        Regex("""^KakaoTalk""", RegexOption.IGNORE_CASE),     // 카카오톡 수신
        Regex("""^KakaoTalk_\d{8}_\d{6}_\d{3}"""),           // 카카오톡 저장
        Regex("""^Telegram""", RegexOption.IGNORE_CASE),      // 텔레그램
        Regex("""^LINE""", RegexOption.IGNORE_CASE),          // 라인
        Regex("""^twitter""", RegexOption.IGNORE_CASE),       // 트위터
        Regex("""^instagram""", RegexOption.IGNORE_CASE),     // 인스타그램
        Regex("""^NaverBlog""", RegexOption.IGNORE_CASE),     // 네이버 블로그
        Regex("""^received_""", RegexOption.IGNORE_CASE),     // 공유 수신
        Regex("""^share""", RegexOption.IGNORE_CASE),         // 공유
        Regex(""".*_edited\.""", RegexOption.IGNORE_CASE),    // 편집된 사진 (선택적)
        Regex("""^wallpaper""", RegexOption.IGNORE_CASE),     // 배경화면
    )

    // ── 날짜 파서 ──────────────────────────────────────────────────
    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"),
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"),
    )

    // ─────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 파일이 '직접 촬영한 사진'인지 판별
     */
    fun isCameraPhoto(fileName: String): Boolean {
        val name = fileName.substringBeforeLast('.')

        // 1. 명백히 다운로드/공유 파일이면 즉시 제외
        if (EXCLUDED_PATTERNS.any { it.containsMatchIn(name) }) {
            Log.d(TAG, "제외됨 (다운로드/공유): $fileName")
            return false
        }

        // 2. 카메라 패턴 매칭
        val matched = CAMERA_PATTERNS.any { it.containsMatchIn(name) }

        // 3. 파일 확장자 확인
        val ext = fileName.substringAfterLast('.').lowercase()
        val validExt = ext in listOf("jpg", "jpeg", "heic", "heif", "dng", "raw", "mp4", "mov")

        return matched && validExt
    }

    /**
     * 파일명에서 LocalDateTime 파싱
     * 파싱 실패 시 null 반환 → EXIF로 fallback
     */
    fun parseDateTime(fileName: String): LocalDateTime? {
        val name = fileName.substringBeforeLast('.')

        // 패턴 1: YYYYMMDD_HHMMSS (삼성, IMG_ 계열 모두 포함)
        val pattern8_6 = Regex("""(\d{8})_(\d{6})""")
        val match = pattern8_6.find(name)

        if (match != null) {
            val dateStr = match.groupValues[1]  // 20240315
            val timeStr = match.groupValues[2]  // 143022
            val combined = "${dateStr}_${timeStr}"

            return runCatching {
                LocalDateTime.parse(combined, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    .also { Log.d(TAG, "파일명 파싱 성공: $fileName → $it") }
            }.getOrElse {
                Log.w(TAG, "날짜 파싱 실패: $combined")
                null
            }
        }

        Log.d(TAG, "날짜 패턴 없음: $fileName (EXIF 사용 예정)")
        return null
    }

    /**
     * 파일명에서 추출 가능한 모든 메타데이터 반환
     */
    data class ParseResult(
        val isCameraPhoto: Boolean,
        val parsedDateTime: LocalDateTime?,
        val detectedModel: CameraModel
    )

    enum class CameraModel { SAMSUNG, IPHONE, GENERIC_ANDROID, UNKNOWN }

    fun parse(fileName: String): ParseResult {
        val name = fileName.substringBeforeLast('.')
        val isCam = isCameraPhoto(fileName)
        val dt = if (isCam) parseDateTime(fileName) else null

        // 제조사 추정
        val model = when {
            name.matches(Regex("""^\d{8}_\d{6}.*""")) -> CameraModel.SAMSUNG
            fileName.contains("HEIC", ignoreCase = true) ||
                    fileName.contains("HEIF", ignoreCase = true) -> CameraModel.IPHONE
            name.startsWith("IMG_") || name.startsWith("VID_") -> CameraModel.GENERIC_ANDROID
            else -> CameraModel.UNKNOWN
        }

        return ParseResult(isCam, dt, model)
    }
}

package com.psychocare.algorithm

import android.util.Log
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * ──────────────────────────────────────────────────────────────────
 * 시간대 알고리즘 (Timezone Algorithm)
 *
 * 문제:
 *   EXIF에 저장된 시간은 로컬 시간이지만, 시간대 정보가 없는 경우가 많음.
 *   해외 여행 중 촬영된 사진의 경우 실제 UTC 시간 계산이 틀릴 수 있음.
 *
 * 해결 방법 (우선순위):
 *   1순위: EXIF OffsetTime 태그 (최신 Android/iOS에서 제공)
 *   2순위: GPS 좌표 → 시간대 추정 (timeshape 라이브러리)
 *   3순위: 기기 현재 시간대 (기본값, 국내 촬영 가정)
 *
 * 결과: UTC ZonedDateTime 반환
 * ──────────────────────────────────────────────────────────────────
 */
object TimezoneAlgorithm {

    private const val TAG = "TimezoneAlgorithm"

    // 주요 도시 GPS → 시간대 하드코딩 테이블 (timeshape 없을 경우 fallback)
    private val REGION_TIMEZONE_TABLE = listOf(
        // 한국/일본
        Region(35.0..38.0, 125.0..132.0, "Asia/Seoul"),
        Region(30.0..45.0, 129.0..146.0, "Asia/Tokyo"),
        // 중국
        Region(18.0..53.0, 73.0..135.0, "Asia/Shanghai"),
        // 동남아
        Region(1.0..20.0, 95.0..110.0, "Asia/Bangkok"),
        Region(1.0..5.0, 103.0..105.0, "Asia/Singapore"),
        // 유럽
        Region(36.0..72.0, -10.0..15.0, "Europe/London"),
        Region(36.0..72.0, 14.0..25.0, "Europe/Berlin"),
        // 미국
        Region(25.0..50.0, -130.0..-60.0, "America/New_York"),
        // 호주
        Region(-45.0..-10.0, 110.0..155.0, "Australia/Sydney"),
    )

    data class Region(
        val latRange: ClosedRange<Double>,
        val lonRange: ClosedRange<Double>,
        val timezoneId: String
    )

    // ─────────────────────────────────────────────────────────────────
    // MAIN API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 보정된 UTC 시간 반환
     *
     * @param localDateTimeStr  EXIF에서 읽은 로컬 시간 문자열 ("2024:03:15 14:30:22")
     * @param offsetTimeStr     EXIF OffsetTime 태그 ("+09:00" 또는 null)
     * @param latitude          GPS 위도 (null 가능)
     * @param longitude         GPS 경도 (null 가능)
     * @return (UTC ZonedDateTime, 사용된 시간대 ID, 신뢰도)
     */
    fun resolve(
        localDateTimeStr: String,
        offsetTimeStr: String?,
        latitude: Double?,
        longitude: Double?
    ): TimezoneResult {

        val localDt = parseExifDateTime(localDateTimeStr)
            ?: return TimezoneResult.unknown()

        // ── 1순위: EXIF OffsetTime ────────────────────────────────
        if (!offsetTimeStr.isNullOrBlank()) {
            runCatching {
                val offset = ZoneOffset.of(offsetTimeStr)
                val zdt = ZonedDateTime.of(localDt, offset)
                val utc = zdt.withZoneSameInstant(ZoneOffset.UTC)
                return TimezoneResult(
                    utcDateTime = utc,
                    timezoneId = offset.id,
                    confidence = Confidence.HIGH,
                    method = "EXIF_OFFSET"
                )
            }.onFailure {
                Log.w(TAG, "EXIF offset 파싱 실패: $offsetTimeStr")
            }
        }

        // ── 2순위: GPS → 시간대 추정 ─────────────────────────────
        if (latitude != null && longitude != null) {
            val tzId = findTimezoneByGps(latitude, longitude)
            if (tzId != null) {
                runCatching {
                    val tz = ZoneId.of(tzId)
                    // localDt는 해당 지역 시간이라고 가정
                    val zdt = ZonedDateTime.of(localDt, tz)
                    val utc = zdt.withZoneSameInstant(ZoneOffset.UTC)
                    return TimezoneResult(
                        utcDateTime = utc,
                        timezoneId = tzId,
                        confidence = Confidence.MEDIUM,
                        method = "GPS_TIMEZONE"
                    )
                }.onFailure {
                    Log.w(TAG, "GPS 시간대 적용 실패: $tzId")
                }
            }
        }

        // ── 3순위: 기기 시간대 (기본값) ──────────────────────────
        val systemTz = ZoneId.systemDefault()
        val zdt = ZonedDateTime.of(localDt, systemTz)
        val utc = zdt.withZoneSameInstant(ZoneOffset.UTC)
        Log.d(TAG, "기기 시간대 사용: ${systemTz.id}")

        return TimezoneResult(
            utcDateTime = utc,
            timezoneId = systemTz.id,
            confidence = Confidence.LOW,
            method = "DEVICE_TIMEZONE"
        )
    }

    /**
     * EXIF 시간 문자열 파싱
     * 형식: "2024:03:15 14:30:22"
     */
    fun parseExifDateTime(exifStr: String): LocalDateTime? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        )
        for (fmt in formatters) {
            try {
                return LocalDateTime.parse(exifStr.trim(), fmt)
            } catch (e: DateTimeParseException) {
                continue
            }
        }
        Log.w(TAG, "EXIF 날짜 파싱 실패: $exifStr")
        return null
    }

    /**
     * GPS 좌표로 시간대 ID 추정
     */
    private fun findTimezoneByGps(lat: Double, lon: Double): String? {
        // 1. 하드코딩 테이블 검색 (인터넷 불필요, 빠름)
        val found = REGION_TIMEZONE_TABLE.find {
            lat in it.latRange && lon in it.lonRange
        }
        if (found != null) {
            Log.d(TAG, "GPS 시간대 매칭: ($lat, $lon) → ${found.timezoneId}")
            return found.timezoneId
        }

        // 2. Java ZoneId available rules 기반 추가 추정
        // (timeshape 라이브러리 연동 시 더 정확)
        val offsetHours = (lon / 15).toInt()
        val offsetId = if (offsetHours >= 0) "+%02d:00".format(offsetHours)
                       else "-%02d:00".format(-offsetHours)
        Log.d(TAG, "경도 기반 오프셋 추정: $offsetId")
        return "UTC$offsetId"
    }

    // ─────────────────────────────────────────────────────────────────
    // 결과 데이터 클래스
    // ─────────────────────────────────────────────────────────────────

    data class TimezoneResult(
        val utcDateTime: ZonedDateTime?,
        val timezoneId: String?,
        val confidence: Confidence,
        val method: String
    ) {
        val epochMs: Long? get() = utcDateTime?.toInstant()?.toEpochMilli()

        companion object {
            fun unknown() = TimezoneResult(null, null, Confidence.NONE, "UNKNOWN")
        }
    }

    enum class Confidence { HIGH, MEDIUM, LOW, NONE }
}

package com.psychocare.repository

import android.content.Context
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.psychocare.algorithm.FilenameParser
import com.psychocare.algorithm.TimezoneAlgorithm
import com.psychocare.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ──────────────────────────────────────────────────────────────────
 * PhotoRepository
 *
 * 역할:
 *  1. 사용자가 지정한 폴더(기본: DCIM/Camera)에서 사진 파일 목록 읽기
 *  2. 파일명 알고리즘으로 다운로드 사진 필터링
 *  3. EXIF + 시간대 알고리즘으로 촬영 시간 보정
 *  4. Photo 객체 리스트 반환
 * ──────────────────────────────────────────────────────────────────
 */
class PhotoRepository(private val context: Context) {

    private val TAG = "PhotoRepository"

    // 기본 카메라 폴더 경로들 (제조사별)
    private val DEFAULT_CAMERA_FOLDERS = listOf(
        "/sdcard/DCIM/Camera",
        "/storage/emulated/0/DCIM/Camera",
        "/storage/emulated/0/DCIM/100ANDRO",  // 일부 기기
    )

    // 지원 확장자
    private val SUPPORTED_EXTENSIONS = setOf(
        "jpg", "jpeg", "heic", "heif", "dng", "raw", "png"
    )

    // ─────────────────────────────────────────────────────────────────
    // 폴더에서 사진 로드 (Flow: 진행상황 실시간 방출)
    // ─────────────────────────────────────────────────────────────────

    /**
     * @param folderPath  사용자가 선택한 폴더 경로 (null이면 기본 폴더 자동 탐색)
     * @return Flow<LoadProgress>  로딩 진행 상황
     */
    fun loadPhotosFromFolder(folderPath: String? = null): Flow<LoadProgress> = flow {
        emit(LoadProgress.Started)

        val targetFolder = folderPath ?: findDefaultCameraFolder()
        if (targetFolder == null) {
            emit(LoadProgress.Error("카메라 폴더를 찾을 수 없습니다"))
            return@flow
        }

        val folder = File(targetFolder)
        if (!folder.exists() || !folder.isDirectory) {
            emit(LoadProgress.Error("폴더가 존재하지 않습니다: $targetFolder"))
            return@flow
        }

        Log.d(TAG, "폴더 스캔 시작: $targetFolder")

        // ── 1. 파일 목록 수집 ────────────────────────────────────
        val allFiles = folder.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .toList()

        emit(LoadProgress.Scanning(allFiles.size))
        Log.d(TAG, "전체 파일 수: ${allFiles.size}")

        // ── 2. 파일명 알고리즘으로 촬영 사진 필터링 ──────────────
        val cameraPhotos = allFiles.filter { file ->
            FilenameParser.isCameraPhoto(file.name)
        }

        Log.d(TAG, "촬영 사진 수: ${cameraPhotos.size} / ${allFiles.size}")
        emit(LoadProgress.Filtered(total = allFiles.size, camera = cameraPhotos.size))

        // ── 3. 각 사진 메타데이터 파싱 ────────────────────────────
        val photos = mutableListOf<Photo>()

        cameraPhotos.forEachIndexed { index, file ->
            val photo = withContext(Dispatchers.IO) {
                parsePhotoMetadata(file)
            }
            photos.add(photo)

            // 10장마다 진행률 업데이트
            if (index % 10 == 0) {
                emit(LoadProgress.Processing(current = index + 1, total = cameraPhotos.size))
            }
        }

        // ── 4. 시간순 정렬 ────────────────────────────────────────
        val sorted = photos.sortedByDescending {
            it.correctedTimeUtc ?: it.exifTime ?: it.parsedTimeFromName ?: 0L
        }

        emit(LoadProgress.Done(sorted))
        Log.d(TAG, "로드 완료: ${sorted.size}장")
    }

    // ─────────────────────────────────────────────────────────────────
    // 개별 사진 메타데이터 파싱
    // ─────────────────────────────────────────────────────────────────

    private fun parsePhotoMetadata(file: File): Photo {
        // 1. 파일명 알고리즘
        val parseResult = FilenameParser.parse(file.name)
        val parsedTimeMs = parseResult.parsedDateTime
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()?.toEpochMilli()

        // 2. EXIF 데이터 읽기
        var exifTimeStr: String? = null
        var offsetTimeStr: String? = null
        var latitude: Double? = null
        var longitude: Double? = null

        runCatching {
            val exif = ExifInterface(file.absolutePath)

            // 촬영 시간
            exifTimeStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

            // 시간대 오프셋 (최신 Android/iOS)
            offsetTimeStr = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)

            // GPS 좌표
            val latLon = FloatArray(2)
            if (exif.getLatLong(latLon)) {
                latitude = latLon[0].toDouble()
                longitude = latLon[1].toDouble()
            }
        }.onFailure {
            Log.w(TAG, "EXIF 읽기 실패: ${file.name} - ${it.message}")
        }

        // EXIF 시간 → epoch ms
        val exifTimeMs = exifTimeStr?.let {
            TimezoneAlgorithm.parseExifDateTime(it)
                ?.atZone(java.time.ZoneId.systemDefault())
                ?.toInstant()?.toEpochMilli()
        }

        // 3. 시간대 알고리즘으로 UTC 보정
        val tzResult = if (exifTimeStr != null) {
            TimezoneAlgorithm.resolve(
                localDateTimeStr = exifTimeStr!!,
                offsetTimeStr = offsetTimeStr,
                latitude = latitude,
                longitude = longitude
            )
        } else null

        return Photo(
            filePath = file.absolutePath,
            fileName = file.name,
            parsedTimeFromName = parsedTimeMs,
            exifTime = exifTimeMs,
            correctedTimeUtc = tzResult?.epochMs,
            timezoneId = tzResult?.timezoneId,
            latitude = latitude,
            longitude = longitude,
            dominantEmotion = null,     // ML Kit 분석 전
            emotionConfidence = null,
            detectedLabels = null,
            isAnalyzed = false
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // 기본 카메라 폴더 자동 탐색
    // ─────────────────────────────────────────────────────────────────

    private fun findDefaultCameraFolder(): String? {
        return DEFAULT_CAMERA_FOLDERS.firstOrNull { File(it).exists() }
            .also { Log.d(TAG, "카메라 폴더: $it") }
    }

    // ─────────────────────────────────────────────────────────────────
    // 진행 상태 sealed class
    // ─────────────────────────────────────────────────────────────────

    sealed class LoadProgress {
        object Started : LoadProgress()
        data class Scanning(val total: Int) : LoadProgress()
        data class Filtered(val total: Int, val camera: Int) : LoadProgress()
        data class Processing(val current: Int, val total: Int) : LoadProgress()
        data class Done(val photos: List<Photo>) : LoadProgress()
        data class Error(val message: String) : LoadProgress()
    }
}

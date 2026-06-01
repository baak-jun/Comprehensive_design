package com.example.counseling.galleryanalysis

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class GalleryAnalysisEngine(
    private val context: Context,
    private val config: AnalysisConfig = AnalysisConfig(),
) {
    suspend fun analyze(
        progress: (percent: Int, message: String) -> Unit = { _, _ -> },
    ): GalleryAnalysisResult = withContext(Dispatchers.Default) {
        val started = System.currentTimeMillis()
        if (!GalleryPermissionHelper.hasGalleryImageAccess(context)) {
            progress(100, "Gallery permission is not granted.")
            return@withContext emptyResult("NO_GALLERY_PERMISSION")
        }

        progress(5, "Scanning gallery images")
        val scanned = scanRecentImages(config.longTermMonths)
        val analyzable = scanned.filter { isAnalyzable(it) }.take(config.maxImages)
        progress(15, "Found ${scanned.size} images, analyzing ${analyzable.size}")

        val classified = mutableListOf<ClassifiedGalleryImage>()
        analyzable.forEachIndexed { index, image ->
            coroutineContext.ensureActive()
            classify(image)?.let { classified += it }
            if (index == analyzable.lastIndex || index % 12 == 0) {
                val percent = 15 + (((index + 1).toDouble() / analyzable.size.coerceAtLeast(1)) * 70).roundToInt()
                progress(percent, "Analyzed ${index + 1}/${analyzable.size}")
            }
        }

        val elapsed = System.currentTimeMillis() - started
        val summary = AnalysisSummary(
            totalImages = scanned.size,
            analyzableImages = analyzable.size,
            excludedImages = scanned.size - analyzable.size,
            analyzedImages = classified.size,
            eventCount = estimateEventCount(classified),
            elapsedMs = elapsed,
        )
        val quality = buildQuality(scanned, classified)
        val report = buildCounselingReport(classified, quality)
        progress(100, "Gallery analysis complete")
        GalleryAnalysisResult(summary, quality, report)
    }

    private suspend fun scanRecentImages(months: Int): List<ScannedGalleryImage> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val cutoffMillis = Instant.now().minus((months * 31L), ChronoUnit.DAYS).toEpochMilli()
        val cutoffSeconds = cutoffMillis / 1000L
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()
        val selection = "(${MediaStore.Images.Media.DATE_TAKEN} >= ? OR ${MediaStore.Images.Media.DATE_ADDED} >= ?)"
        val args = arrayOf(cutoffMillis.toString(), cutoffSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathCol = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH) else -1
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateTaken = cursor.getLongOrNull(takenCol)?.takeIf { it > 0L }
                    val dateAdded = cursor.getLongOrNull(addedCol)?.takeIf { it > 0L }?.let { it * 1000L }
                    add(
                        ScannedGalleryImage(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            fileName = cursor.getStringOrNull(nameCol),
                            relativePath = cursor.getStringOrNull(pathCol),
                            dateMillis = dateTaken ?: dateAdded,
                            width = cursor.getIntOrNull(widthCol),
                            height = cursor.getIntOrNull(heightCol),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun isAnalyzable(image: ScannedGalleryImage): Boolean {
        val joined = "${image.relativePath.orEmpty()}/${image.fileName.orEmpty()}".lowercase()
        if (joined.contains("screenshot") || joined.contains("download") || joined.contains("kakao")) return false
        val width = image.width ?: return true
        val height = image.height ?: return true
        return width >= 96 && height >= 96
    }

    private suspend fun classify(image: ScannedGalleryImage): ClassifiedGalleryImage? {
        val bitmap = loadThumbnail(image) ?: return null
        return try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(input).await()
                .map { it.text to it.confidence }
                .sortedByDescending { it.second }
                .take(10)
            val objects = objectDetector.process(input).await()
                .flatMap { detected -> detected.labels.map { it.text to it.confidence } }
                .sortedByDescending { it.second }
                .take(6)
            val faceCount = faceDetector.process(input).await().size
            val allText = (labels.map { it.first } + objects.map { it.first }).joinToString(" ").lowercase()
            val category = classifyText(allText, faceCount)
            val confidence = (labels.firstOrNull()?.second ?: objects.firstOrNull()?.second ?: 0.25f).coerceIn(0f, 1f)
            ClassifiedGalleryImage(image, labels, objects, faceCount, category, confidence)
        } catch (_: Throwable) {
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun loadThumbnail(image: ScannedGalleryImage): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(image.uri, Size(config.thumbnailLongSide, config.thumbnailLongSide), null)
            } else {
                context.contentResolver.openInputStream(image.uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    private fun classifyText(text: String, faceCount: Int): GalleryActivityCategory {
        if (faceCount >= 2 || text.hasAny("person", "people", "group", "crowd", "restaurant", "party")) return GalleryActivityCategory.SOCIAL
        if (text.hasAny("book", "laptop", "computer", "desk", "paper", "classroom", "text")) return GalleryActivityCategory.STUDY
        if (text.hasAny("sport", "ball", "bicycle", "running", "gym", "mountain", "outdoor", "park")) return GalleryActivityCategory.PHYSICAL
        if (text.hasAny("food", "meal", "coffee", "drink", "tableware", "restaurant")) return GalleryActivityCategory.FOOD
        if (text.hasAny("pet", "dog", "cat", "flower", "art", "music", "instrument", "plant", "nature")) return GalleryActivityCategory.RECOVERY
        if (text.hasAny("room", "bed", "furniture", "television", "screen")) return GalleryActivityCategory.INDOOR
        return GalleryActivityCategory.UNKNOWN
    }

    private fun buildQuality(
        scanned: List<ScannedGalleryImage>,
        classified: List<ClassifiedGalleryImage>,
    ): AnalysisQualityReport {
        val known = classified.count { it.category != GalleryActivityCategory.UNKNOWN }
        val avg = classified.map { it.confidence.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
        return AnalysisQualityReport(
            mlKitSuccessRate = classified.size.toDouble() / scanned.size.coerceAtLeast(1),
            classificationCoverage = known.toDouble() / classified.size.coerceAtLeast(1),
            averageConfidence = avg,
            warnings = buildList {
                if (classified.size < 20) add("분석 가능한 이미지가 적어 결과를 질문 후보로만 사용해야 합니다.")
                if (avg < 0.55) add("이미지 라벨 확신도가 낮아 단정하면 안 됩니다.")
            },
        )
    }

    private fun buildCounselingReport(
        classified: List<ClassifiedGalleryImage>,
        quality: AnalysisQualityReport,
    ): CounselingAnalysisReport {
        val categoryCounts = classified.groupingBy { it.category }.eachCount()
        val recentCutoff = System.currentTimeMillis() - config.recentDays * 24L * 60L * 60L * 1000L
        val recentCounts = classified
            .filter { (it.image.dateMillis ?: 0L) >= recentCutoff }
            .groupingBy { it.category }
            .eachCount()
        val top = categoryCounts.entries
            .filter { it.key != GalleryActivityCategory.UNKNOWN }
            .sortedByDescending { it.value }
            .take(4)
        val unknown = categoryCounts[GalleryActivityCategory.UNKNOWN] ?: 0
        val riskCues = detectRiskCues(classified)
        val safety = SafetyAssessment(
            level = if (riskCues.isEmpty()) SafetyLevel.NONE else SafetyLevel.LOW_OBSERVE,
            mustUseRuleBasedResponse = false,
            reasons = riskCues.map { "${it.name}: ${it.imageCount}" },
        )
        val context = buildString {
            appendLine("COUNSELING BRIEF - READ FIRST")
            appendLine("- This is auxiliary gallery-derived context. Do not mention photos or gallery access to the user.")
            appendLine("- Use it only to choose one gentle check-in question. User text overrides this context.")
            appendLine()
            appendLine("USE NOW")
            if (top.isEmpty()) {
                appendLine("- GENERAL_CHECK_IN: strong image-based pattern was not found.")
            } else {
                top.forEach { (category, count) ->
                    val recent = recentCounts[category] ?: 0
                    appendLine("- ${category.labelKo}: totalEventsApprox=$count, recentApprox=$recent")
                }
            }
            appendLine()
            appendLine("DATA QUALITY")
            appendLine("- analyzedImages=${classified.size}, coverage=${"%.2f".format(quality.classificationCoverage)}, avgConfidence=${"%.2f".format(quality.averageConfidence)}, unknown=$unknown")
            quality.warnings.forEach { appendLine("- warning=$it") }
            if (riskCues.isNotEmpty()) {
                appendLine()
                appendLine("SAFETY OVERLAY")
                riskCues.forEach { appendLine("- ${it.name}: count=${it.imageCount}, confidence=${"%.2f".format(it.confidence)}") }
                appendLine("- Image-only safety cues must not be treated as direct risk. Ask softly only if relevant.")
            }
        }
        return CounselingAnalysisReport(context, safety, riskCues)
    }

    private fun detectRiskCues(classified: List<ClassifiedGalleryImage>): List<VisualRiskCue> {
        val medical = classified.count { image ->
            val text = (image.labels.map { it.first } + image.objects.map { it.first }).joinToString(" ").lowercase()
            text.hasAny("medicine", "pill", "hospital", "medical", "injury", "knife", "weapon")
        }
        return if (medical > 0) listOf(VisualRiskCue("possible medical or safety visual cue", medical, 0.35)) else emptyList()
    }

    private fun estimateEventCount(classified: List<ClassifiedGalleryImage>): Int {
        return classified.mapNotNull { it.image.dateMillis }
            .map { it / (60L * 60L * 1000L) }
            .distinct()
            .size
            .coerceAtMost(classified.size)
    }

    private fun emptyResult(reason: String): GalleryAnalysisResult {
        return GalleryAnalysisResult(
            summary = AnalysisSummary(0, 0, 0, 0, 0, 0),
            qualityReport = AnalysisQualityReport(0.0, 0.0, 0.0, listOf(reason)),
            counselingReport = CounselingAnalysisReport(
                llmContextSummary = "Gallery permission unavailable. Continue normal counseling without image context.",
                safetyAssessment = SafetyAssessment(SafetyLevel.NONE, false, listOf(reason)),
            ),
        )
    }

    companion object {
        private val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build(),
        )
        private val objectDetector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableClassification()
                .build(),
        )
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }
}

private fun String.hasAny(vararg needles: String): Boolean {
    return needles.any { contains(it) }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? {
    return if (index < 0 || isNull(index)) null else getLong(index)
}

private fun android.database.Cursor.getIntOrNull(index: Int): Int? {
    return if (index < 0 || isNull(index)) null else getInt(index)
}

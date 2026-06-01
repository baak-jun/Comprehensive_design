package com.example.counseling.galleryanalysis

data class AnalysisConfig(
    val longTermMonths: Int = 6,
    val recentDays: Long = 14,
    val baselineDays: Long = 30,
    val thumbnailLongSide: Int = 384,
    val maxImages: Int = 600,
    val enableAuditExport: Boolean = false,
)

data class GalleryAnalysisResult(
    val summary: AnalysisSummary,
    val qualityReport: AnalysisQualityReport,
    val counselingReport: CounselingAnalysisReport,
)

data class AnalysisSummary(
    val totalImages: Int,
    val analyzableImages: Int,
    val excludedImages: Int,
    val analyzedImages: Int,
    val eventCount: Int,
    val elapsedMs: Long,
)

data class AnalysisQualityReport(
    val mlKitSuccessRate: Double,
    val classificationCoverage: Double,
    val averageConfidence: Double,
    val warnings: List<String> = emptyList(),
)

data class CounselingAnalysisReport(
    val llmContextSummary: String,
    val safetyAssessment: SafetyAssessment,
    val visualRiskCues: List<VisualRiskCue> = emptyList(),
)

data class SafetyAssessment(
    val level: SafetyLevel,
    val mustUseRuleBasedResponse: Boolean,
    val reasons: List<String>,
)

enum class SafetyLevel {
    NONE,
    LOW_OBSERVE,
    MEDIUM_CHECK_IN,
    HIGH_SUPPORT,
    IMMEDIATE_DANGER,
}

data class VisualRiskCue(
    val name: String,
    val imageCount: Int,
    val confidence: Double,
)

internal data class ScannedGalleryImage(
    val id: Long,
    val uri: android.net.Uri,
    val fileName: String?,
    val relativePath: String?,
    val dateMillis: Long?,
    val width: Int?,
    val height: Int?,
)

internal data class ClassifiedGalleryImage(
    val image: ScannedGalleryImage,
    val labels: List<Pair<String, Float>>,
    val objects: List<Pair<String, Float>>,
    val faceCount: Int,
    val category: GalleryActivityCategory,
    val confidence: Float,
)

internal enum class GalleryActivityCategory(val labelKo: String) {
    STUDY("학업/과제"),
    PHYSICAL("신체활동/외출"),
    SOCIAL("사회적 연결"),
    FOOD("식사/카페"),
    RECOVERY("취미/회복 자원"),
    INDOOR("실내/정적 활동"),
    UNKNOWN("분류 불확실"),
}

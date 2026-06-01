package com.example.counseling

import android.content.Context
import com.example.counseling.galleryanalysis.AnalysisConfig
import com.example.counseling.galleryanalysis.GalleryAnalysisEngine
import com.example.counseling.galleryanalysis.GalleryAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class GalleryAnalysisCacheSnapshot(
    val fingerprint: String,
    val imageCount: Int,
    val updatedAt: Long,
    val contextText: String,
)

suspend fun refreshGalleryAnalysisCacheIfNeeded(
    context: Context,
    images: List<GalleryImage>,
    force: Boolean = false,
    onProgress: (String) -> Unit = {},
): GalleryAnalysisCacheSnapshot? = withContext(Dispatchers.Default) {
    if (images.isEmpty()) return@withContext null
    val fingerprint = galleryFingerprint(images)
    val cached = readGalleryAnalysisCache(context)
    if (!force && cached?.fingerprint == fingerprint) {
        onProgress("저장된 Gallery 분석 문서를 사용합니다.")
        return@withContext cached
    }

    onProgress("Gallery 이미지 분석을 시작합니다...")
    val result = GalleryAnalysisEngine(
        context = context.applicationContext,
        config = AnalysisConfig(
            maxImages = 600,
            enableAuditExport = false,
        ),
    ).analyze { percent, _ ->
        if (percent == 5 || percent == 15 || percent % 20 == 0 || percent == 100) {
            onProgress("Gallery 이미지 분석 중... $percent%")
        }
    }
    val snapshot = GalleryAnalysisCacheSnapshot(
        fingerprint = fingerprint,
        imageCount = images.size,
        updatedAt = System.currentTimeMillis(),
        contextText = buildGalleryPromptContext(result),
    )
    writeGalleryAnalysisCache(context, snapshot)
    onProgress("Gallery 분석 문서를 갱신했습니다.")
    snapshot
}

suspend fun readGalleryAnalysisCache(context: Context): GalleryAnalysisCacheSnapshot? = withContext(Dispatchers.IO) {
    runCatching {
        val file = galleryAnalysisCacheFile(context)
        if (!file.exists()) return@withContext null
        val json = JSONObject(file.readText())
        GalleryAnalysisCacheSnapshot(
            fingerprint = json.getString("fingerprint"),
            imageCount = json.getInt("imageCount"),
            updatedAt = json.getLong("updatedAt"),
            contextText = json.getString("contextText"),
        )
    }.getOrNull()
}

private suspend fun writeGalleryAnalysisCache(
    context: Context,
    snapshot: GalleryAnalysisCacheSnapshot,
) = withContext(Dispatchers.IO) {
    val json = JSONObject()
        .put("fingerprint", snapshot.fingerprint)
        .put("imageCount", snapshot.imageCount)
        .put("updatedAt", snapshot.updatedAt)
        .put("contextText", snapshot.contextText)
    galleryAnalysisCacheFile(context).writeText(json.toString(2))
}

private fun buildGalleryPromptContext(result: GalleryAnalysisResult): String {
    val summary = result.summary
    return """
        [갤러리 이미지 분석 보조 맥락]
        이 자료는 사용자가 허용한 갤러리 이미지에서 나온 생활 패턴 보조 신호입니다. 진단, 감시, 단정의 근거로 쓰지 말고 사용자의 직접 발화와 현재 메시지를 우선하세요. 사용자에게 사진이나 갤러리를 직접 언급하지 마세요.
        분석 요약: 전체 ${summary.totalImages}장, 분석 대상 ${summary.analyzableImages}장, 실제 분석 ${summary.analyzedImages}장, 이벤트 ${summary.eventCount}개.

        ${result.counselingReport.llmContextSummary}
    """.trimIndent()
}

private fun galleryFingerprint(images: List<GalleryImage>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    images
        .sortedBy { it.uri.toString() }
        .forEach { image ->
            digest.update(image.uri.toString().toByteArray())
            digest.update(0)
            digest.update(image.name.toByteArray())
            digest.update(0)
        }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun galleryAnalysisCacheFile(context: Context): File {
    return File(context.filesDir, "gallery_analysis_context.json")
}

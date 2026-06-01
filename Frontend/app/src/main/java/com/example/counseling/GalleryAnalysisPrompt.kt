package com.example.counseling

import android.content.Context
import com.example.counseling.galleryanalysis.GalleryPermissionHelper
import com.example.counseling.llm.ChatMessage
import com.example.counseling.llm.ChatRole

data class GalleryAnalysisPromptResult(
    val contextText: String?,
    val message: String,
)

suspend fun readGalleryAnalysisPromptContext(context: Context): GalleryAnalysisPromptResult {
    if (!GalleryPermissionHelper.hasGalleryImageAccess(context)) {
        return GalleryAnalysisPromptResult(
            contextText = null,
            message = "갤러리 권한이 없어 이미지 분석 맥락을 포함하지 못했습니다.",
        )
    }

    val cached = readGalleryAnalysisCache(context)
    return GalleryAnalysisPromptResult(
        contextText = cached?.contextText,
        message = if (cached == null) {
            "저장된 Gallery 분석 문서가 없어 상담 맥락에 포함하지 않았습니다. Gallery 화면을 한 번 열어 분석을 갱신하세요."
        } else {
            "저장된 Gallery 분석 문서를 상담 입력에 포함합니다."
        },
    )
}

fun List<ChatMessage>.withGalleryAnalysisContext(galleryContext: String?): List<ChatMessage> {
    if (galleryContext.isNullOrBlank()) return this
    val lastUserIndex = indexOfLast { it.role == ChatRole.User }
    if (lastUserIndex < 0) return this
    return mapIndexed { index, message ->
        if (index != lastUserIndex) {
            message
        } else {
            message.copy(
                content = """
                    $galleryContext

                    [현재 사용자 메시지]
                    ${message.content}
                """.trimIndent(),
            )
        }
    }
}

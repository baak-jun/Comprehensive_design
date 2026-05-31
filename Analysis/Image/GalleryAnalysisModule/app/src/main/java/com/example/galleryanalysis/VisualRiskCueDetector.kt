package com.example.galleryanalysis

import kotlin.math.min

/**
 * ML Kit 라벨/객체에서 위기 보조 단서를 찾는다.
 * 이 클래스는 위험을 확정하지 않는다. 이미지 단서만으로 HIGH_SUPPORT 이상을 만들지 않는다.
 * v3.1: 정상 시나리오 오탐을 줄이기 위해 파일명/폴더명/하위활동명은 위험 단서 계산에서 제외한다.
 */
class VisualRiskCueDetector {
    private data class CueRule(
        val type: VisualRiskCueType,
        val keywords: List<String>,
        val weight: Double,
        val minImageCount: Int = 2
    )

    private val rules = listOf(
        // v3.2: pillow/tablet 같은 비위험 라벨이 pill/tablet으로 오탐되는 문제를 피하기 위해
        // 직접 의료/복약 단어만 사용하고, 단어 경계를 엄격히 본다.
        CueRule(VisualRiskCueType.POSSIBLE_MEDICATION, listOf("pill", "pills", "medicine", "medication", "prescription", "pharmacy", "drugstore"), 0.72, 2),
        CueRule(VisualRiskCueType.POSSIBLE_MEDICAL_CONTEXT, listOf("hospital", "clinic", "doctor", "medical", "ambulance", "emergency room", "nurse", "stretcher", "first aid kit"), 0.62, 2),
        CueRule(VisualRiskCueType.POSSIBLE_INJURY_CONTEXT, listOf("bandage", "wound", "injury", "blood", "bruise", "cast", "first aid"), 0.68, 2),
        CueRule(VisualRiskCueType.POSSIBLE_SHARP_OBJECT, listOf("knife", "blade", "razor", "cutter"), 0.62, 2)
        // 야간/고립은 nightChange에서 이미 다룬다. 일반 풍경/다리/강가 사진 오탐을 피하기 위해 이미지 위험 단서에서는 제외한다.
        // 위협 메시지 캡처는 기본적으로 screenshot/kakaotalk 폴더에서 제외되므로, 이미지 자동 위험 단서로 사용하지 않는다.
    )

    fun detect(classified: List<ClassifiedImage>): List<VisualRiskCue> {
        if (classified.isEmpty()) return emptyList()
        val evidenceByType = mutableMapOf<VisualRiskCueType, MutableList<String>>()
        val imageIdsByType = mutableMapOf<VisualRiskCueType, MutableSet<Long>>()
        val weightByType = mutableMapOf<VisualRiskCueType, Double>()

        classified.forEach { image ->
            val labelText = image.labels
                .filter { it.confidence >= 0.55f }
                .joinToString(" ") { it.text.lowercase() }
            val objectText = image.objects
                .filter { it.confidence >= 0.55f }
                .joinToString(" ") { it.category.lowercase() }
            val lowered = "$labelText $objectText"

            rules.forEach { rule ->
                val hits = rule.keywords.filter { matchesKeyword(lowered, it) }
                if (hits.isNotEmpty()) {
                    imageIdsByType.getOrPut(rule.type) { mutableSetOf() }.add(image.image.imageId)
                    weightByType[rule.type] = (weightByType[rule.type] ?: 0.0) + rule.weight * hits.size.coerceAtMost(2)
                    evidenceByType.getOrPut(rule.type) { mutableListOf() }
                        .add("imageId=${image.image.imageId}, keywords=${hits.take(3).joinToString()}")
                }
            }
        }

        return rules.mapNotNull { rule ->
            val ids = imageIdsByType[rule.type].orEmpty()
            val count = ids.size
            if (count < rule.minImageCount) return@mapNotNull null
            val rawWeight = weightByType[rule.type] ?: 0.0
            val confidence = min(0.82, 0.12 + rawWeight / (classified.size.coerceAtLeast(1) * 0.32 + 6.0))
            if (confidence < 0.28) return@mapNotNull null
            VisualRiskCue(
                cueType = rule.type,
                cueNameKo = rule.type.koName,
                imageCount = count,
                confidence = confidence.coerceIn(0.0, 0.82),
                representativeEvidence = evidenceByType[rule.type].orEmpty().take(5)
            )
        }.sortedWith(compareByDescending<VisualRiskCue> { it.confidence }.thenByDescending { it.imageCount })
    }

    private fun matchesKeyword(text: String, keyword: String): Boolean {
        val escaped = Regex.escape(keyword.lowercase())
        return Regex("""(?<![a-z])$escaped(?![a-z])""").containsMatchIn(text.lowercase())
    }
}


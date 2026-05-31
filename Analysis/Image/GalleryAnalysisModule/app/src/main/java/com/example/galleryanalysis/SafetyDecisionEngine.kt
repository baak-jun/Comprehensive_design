package com.example.galleryanalysis

class SafetyDecisionEngine(
    private val keywordDetector: CrisisKeywordDetector = CrisisKeywordDetector(),
    private val responsePolicy: CrisisResponsePolicy = CrisisResponsePolicy()
) {
    fun assess(
        behaviorScores: BehaviorScores,
        visualRiskCues: List<VisualRiskCue>,
        userText: String? = null
    ): SafetyAssessment {
        val textRisk = keywordDetector.detect(userText)
        if (textRisk.level == SafetyLevel.IMMEDIATE_DANGER || textRisk.level == SafetyLevel.HIGH_SUPPORT) {
            val action = if (textRisk.level == SafetyLevel.IMMEDIATE_DANGER) SafetyAction.EMERGENCY_GUIDE else SafetyAction.RULE_BASED_CRISIS_RESPONSE
            return SafetyAssessment(
                level = textRisk.level,
                action = action,
                mustUseRuleBasedResponse = true,
                primaryTrigger = textRisk.trigger,
                reasons = textRisk.reasons + textRisk.matchedKeywords.map { "matched=$it" },
                responseGuideKo = responsePolicy.responseGuide(textRisk.level),
                emergencyResourcesKo = responsePolicy.emergencyResourcesKo(textRisk.level)
            )
        }

        val cueScore = behaviorScores.visualRiskCueScore
        val strongCueCount = visualRiskCues.count { it.confidence >= 0.55 && it.imageCount >= 2 }
        val combinedPattern = behaviorScores.lifeRhythmInstabilityScore >= 0.55 ||
            behaviorScores.socialWithdrawalSignalScore >= 0.55 ||
            behaviorScores.interestContinuityDropScore >= 0.60

        val level = when {
            cueScore >= 0.65 && strongCueCount >= 2 && combinedPattern -> SafetyLevel.MEDIUM_CHECK_IN
            cueScore >= 0.35 || strongCueCount >= 1 -> SafetyLevel.LOW_OBSERVE
            else -> SafetyLevel.NONE
        }
        val action = when (level) {
            SafetyLevel.MEDIUM_CHECK_IN -> SafetyAction.DIRECT_CHECK_IN
            SafetyLevel.LOW_OBSERVE -> SafetyAction.SOFT_CHECK_IN
            else -> SafetyAction.NORMAL_COUNSELING
        }
        val reasons = buildList {
            if (cueScore >= 0.35) add("이미지 기반 위험 보조 단서 점수=${String.format("%.2f", cueScore)}")
            if (combinedPattern) add("생활 리듬/사회성/관심사 변화 신호가 함께 나타남")
            visualRiskCues.take(3).forEach { add("${it.cueNameKo}: ${it.imageCount}장, conf=${String.format("%.2f", it.confidence)}") }
            if (isEmpty()) add("고위험 직접 발화나 강한 위험 단서가 감지되지 않음")
        }
        return SafetyAssessment(
            level = level,
            action = action,
            mustUseRuleBasedResponse = false,
            primaryTrigger = if (level == SafetyLevel.NONE) "NONE" else "VISUAL_AUXILIARY_AND_BEHAVIOR_PATTERN",
            reasons = reasons,
            responseGuideKo = responsePolicy.responseGuide(level),
            emergencyResourcesKo = responsePolicy.emergencyResourcesKo(level)
        )
    }
}

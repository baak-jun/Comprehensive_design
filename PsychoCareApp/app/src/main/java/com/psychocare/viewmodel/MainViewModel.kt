package com.psychocare.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psychocare.analyzer.PhotoAnalyzer
import com.psychocare.analyzer.UserProfileBuilder
import com.psychocare.chatbot.GemmaChatbot
import com.psychocare.data.ChatMessage
import com.psychocare.data.MessageRole
import com.psychocare.data.PhenotypeData
import com.psychocare.data.Photo
import com.psychocare.data.UserProfile
import com.psychocare.phenotype.AppUsageAnalyzer
import com.psychocare.phenotype.CallLogAnalyzer
import com.psychocare.repository.PhotoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val photoRepository  = PhotoRepository(application)
    private val photoAnalyzer    = PhotoAnalyzer(application)
    private val callLogAnalyzer  = CallLogAnalyzer(application)
    private val appUsageAnalyzer = AppUsageAnalyzer(application)
    val gemmaChatbot = GemmaChatbot(application)

    // ── 사진 로드 상태 ─────────────────────────────────────────────
    private val _loadProgress = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadProgress: StateFlow<LoadState> = _loadProgress.asStateFlow()

    // ── 분석된 사진 목록 ───────────────────────────────────────────
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    // ── 사용자 프로필 ──────────────────────────────────────────────
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    // ── 채팅 메시지 ────────────────────────────────────────────────
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // ── 챗봇 상태 ─────────────────────────────────────────────────
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _gemmaReady = MutableStateFlow(false)
    val gemmaReady: StateFlow<Boolean> = _gemmaReady.asStateFlow()

    // ── 선택된 폴더 ────────────────────────────────────────────────
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // ── 피노타입 데이터 ────────────────────────────────────────────
    private val _phenotypeData = MutableStateFlow<PhenotypeData?>(null)
    val phenotypeData: StateFlow<PhenotypeData?> = _phenotypeData.asStateFlow()

    private val _phenotypeLoading = MutableStateFlow(false)
    val phenotypeLoading: StateFlow<Boolean> = _phenotypeLoading.asStateFlow()

    // 권한 상태 노출 (UI에서 버튼 활성화 여부 판단)
    fun hasCallLogPermission() = callLogAnalyzer.hasPermission()
    fun hasUsageStatsPermission() = appUsageAnalyzer.hasPermission()

    init {
        // 저장된 분석 결과 복원 (앱을 나갔다 와도 유지)
        restoreSavedSession()

        // Gemma 상담 기능 임시 비활성화 — 다른 기능 검증에 집중
        // (1.3GB 모델 로딩 생략으로 앱 시작이 빨라짐. 복원하려면 아래 주석 해제)
        // viewModelScope.launch {
        //     gemmaChatbot.initialize().onSuccess { _gemmaReady.value = true }
        // }
    }

    /**
     * 이전에 저장된 분석 결과를 복원한다.
     * 프로필이 있으면 사진 분석을 다시 하지 않아도 결과가 그대로 표시된다.
     */
    private fun restoreSavedSession() {
        val ctx = getApplication<Application>()
        val savedProfile = com.psychocare.storage.SessionStore.loadProfile(ctx)
        if (savedProfile != null) {
            _userProfile.value   = savedProfile
            _photos.value        = com.psychocare.storage.SessionStore.loadPhotos(ctx)
            _phenotypeData.value = com.psychocare.storage.SessionStore.loadPhenotype(ctx)
            // 홈 화면을 분석 완료 상태로 복원
            _loadProgress.value  = LoadState.Done(savedProfile.analyzedPhotoCount)
            addSystemMessage(buildAnalysisSummary(savedProfile))
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 폴더 선택 & 사진 로드
    // ─────────────────────────────────────────────────────────────────

    fun selectFolder(path: String) {
        _selectedFolder.value = path
        loadPhotos(path)
    }

    fun loadPhotos(folderPath: String? = null) {
        viewModelScope.launch {
            photoRepository.loadPhotosFromFolder(folderPath)
                .collect { progress ->
                    when (progress) {
                        is PhotoRepository.LoadProgress.Started ->
                            _loadProgress.value = LoadState.Loading("폴더 스캔 중...")

                        is PhotoRepository.LoadProgress.Scanning ->
                            _loadProgress.value = LoadState.Loading("전체 ${progress.total}개 파일 발견")

                        is PhotoRepository.LoadProgress.Filtered ->
                            _loadProgress.value = LoadState.Loading(
                                "촬영 사진 ${progress.camera}장 / 전체 ${progress.total}장"
                            )

                        is PhotoRepository.LoadProgress.Processing ->
                            _loadProgress.value = LoadState.Loading(
                                "메타데이터 분석 중... ${progress.current}/${progress.total}"
                            )

                        is PhotoRepository.LoadProgress.Done -> {
                            _photos.value = progress.photos
                            _loadProgress.value = LoadState.AnalyzingML(0, progress.photos.size)
                            analyzePhotosWithML(progress.photos)
                        }

                        is PhotoRepository.LoadProgress.Error ->
                            _loadProgress.value = LoadState.Error(progress.message)
                    }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ML Kit 사진 분석
    // ─────────────────────────────────────────────────────────────────

    private fun analyzePhotosWithML(photos: List<Photo>) {
        viewModelScope.launch {
            val analyzed = mutableListOf<Photo>()

            photos.forEachIndexed { index, photo ->
                val result = photoAnalyzer.analyze(photo)
                analyzed.add(result)
                _loadProgress.value = LoadState.AnalyzingML(index + 1, photos.size)
            }

            _photos.value = analyzed

            // 프로필 생성
            val profile = UserProfileBuilder.build(analyzed)
            _userProfile.value = profile

            _loadProgress.value = LoadState.Done(analyzed.size)

            // 분석 결과 영속화 (앱 재실행 시 복원)
            val ctx = getApplication<Application>()
            com.psychocare.storage.SessionStore.saveProfile(ctx, profile)
            com.psychocare.storage.SessionStore.savePhotos(ctx, analyzed)

            // 분석 완료 메시지
            val summary = buildAnalysisSummary(profile)
            addSystemMessage(summary)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 채팅
    // ─────────────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(role = MessageRole.USER, content = text)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            _isTyping.value = true

            // 개발자 대시보드용: 현재 대화 내역 전달
            gemmaChatbot.devMessages = _messages.value

            // 스트리밍 응답
            val streamingMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )
            _messages.value = _messages.value + streamingMsg

            val responseBuilder = StringBuilder()

            gemmaChatbot.sendMessage(text, _userProfile.value, _phenotypeData.value)
                .collect { token ->
                    when (token) {
                        is GemmaChatbot.StreamingToken.Text -> {
                            responseBuilder.append(token.value)
                            // 스트리밍 메시지 업데이트
                            _messages.value = _messages.value.dropLast(1) + streamingMsg.copy(
                                content = responseBuilder.toString()
                            )
                        }
                        is GemmaChatbot.StreamingToken.Done -> {
                            _messages.value = _messages.value.dropLast(1) + ChatMessage(
                                role = MessageRole.ASSISTANT,
                                content = responseBuilder.toString(),
                                isStreaming = false
                            )
                            _isTyping.value = false
                        }
                        is GemmaChatbot.StreamingToken.Error -> {
                            _messages.value = _messages.value.dropLast(1) + ChatMessage(
                                role = MessageRole.ASSISTANT,
                                content = "오류: ${token.message}"
                            )
                            _isTyping.value = false
                        }
                    }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 피노타입 데이터 수집
    // ─────────────────────────────────────────────────────────────────

    /**
     * 통화 기록 + 앱 사용 통계를 동시에 수집한다.
     * 권한이 없는 항목은 null로 채워진다.
     */
    fun collectPhenotypeData() {
        viewModelScope.launch {
            _phenotypeLoading.value = true

            val callLog  = callLogAnalyzer.analyze()
            val appUsage = appUsageAnalyzer.analyze()

            val pheno = PhenotypeData(callLog, appUsage)
            _phenotypeData.value = pheno
            _phenotypeLoading.value = false

            // 피노타입 영속화 (앱 재실행 시 복원)
            com.psychocare.storage.SessionStore.savePhenotype(getApplication(), pheno)

            // 수집 결과 시스템 메시지
            val sb = StringBuilder("📊 **피노타입 데이터 수집 완료**\n")
            if (callLog != null) {
                sb.append("📞 통화기록: 이번 주 ${callLog.totalCallsThisWeek}건")
                if (callLog.isolationAlert) sb.append(" ⚠️ 대인교류 급감")
                sb.append("\n")
            } else {
                sb.append("📞 통화기록: 권한 없음\n")
            }
            if (appUsage?.hasPermission == true) {
                sb.append("📱 앱 사용: 일평균 ${appUsage.dailyAvgScreenTimeMin}분")
                if (appUsage.dailyAvgLateNightMin > 10) sb.append(" ⚠️ 야간 사용 ${appUsage.dailyAvgLateNightMin}분")
            } else {
                sb.append("📱 앱 사용: 권한 없음 (설정에서 허용 필요)")
            }
            addSystemMessage(sb.toString())
        }
    }

    fun resetChat() {
        gemmaChatbot.resetConversation()
        _messages.value = emptyList()
        _userProfile.value?.let {
            addSystemMessage(buildAnalysisSummary(it))
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 유틸리티
    // ─────────────────────────────────────────────────────────────────

    private fun addSystemMessage(content: String) {
        _messages.value = _messages.value + ChatMessage(
            role = MessageRole.SYSTEM,
            content = content
        )
    }

    private fun buildAnalysisSummary(profile: UserProfile): String {
        return buildString {
            appendLine("📊 **분석 완료** (${profile.analyzedPhotoCount}장)")
            appendLine()
            appendLine("${profile.dominantEmotion.emoji} 주요 감정: **${profile.dominantEmotion.korean}**")
            if (profile.topHobbies.isNotEmpty()) {
                appendLine("🎯 주요 취미: ${profile.topHobbies.take(3).joinToString(", ")}")
            }
            appendLine("🕐 활동 패턴: ${profile.activeTimeOfDay}")
            appendLine("👥 사회성: ${profile.socialLevel}")
            appendLine()
            appendLine("분석 결과를 바탕으로 상담을 시작할게요. 어떤 이야기를 나눠볼까요?")
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaChatbot.close()
    }

    // ─────────────────────────────────────────────────────────────────
    // 상태 sealed class
    // ─────────────────────────────────────────────────────────────────

    sealed class LoadState {
        object Idle : LoadState()
        data class Loading(val message: String) : LoadState()
        data class AnalyzingML(val current: Int, val total: Int) : LoadState()
        data class Done(val count: Int) : LoadState()
        data class Error(val message: String) : LoadState()
    }
}

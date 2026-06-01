package com.psychocare

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 앱 Application 클래스
 *
 * Compose UI 프레임워크의 알려진 호버 버그(ACTION_HOVER_EXIT) 우회:
 *  - 갤럭시 S펜 호버 또는 PC 미러링 마우스 호버 시
 *    androidx.compose.ui.platform.AndroidComposeView 에서 무해한
 *    IllegalStateException("The ACTION_HOVER_EXIT event was not cleared") 가 발생해 앱이 강제 종료됨.
 *  - 이 버그는 Compose 1.7.0에서 수정되었으나, 1.7 마이그레이션은 Kotlin 2.0이 필요.
 *  - 따라서 이 특정 무해한 예외만 메인 루퍼에서 삼켜 앱이 죽지 않도록 한다.
 *    (그 외 모든 예외는 정상적으로 크래시 처리)
 */
class PsychoCareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installHoverCrashGuard()
    }

    private fun installHoverCrashGuard() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (e: Throwable) {
                    if (isHarmlessHoverBug(e)) {
                        Log.w("HoverGuard", "Compose 호버 버그 무시 (앱 유지)")
                        // 무시하고 메시지 루프 재개
                    } else {
                        // 다른 예외는 정상 크래시 처리: 기본 핸들러로 던짐
                        throw e
                    }
                }
            }
        }
    }

    private fun isHarmlessHoverBug(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is IllegalStateException) {
                val msg = t.message ?: ""
                val trace = t.stackTrace.joinToString { it.toString() }
                if (msg.contains("ACTION_HOVER_EXIT") ||
                    trace.contains("sendHoverExitEvent") ||
                    trace.contains("AndroidComposeView")) {
                    return true
                }
            }
            t = t.cause
        }
        return false
    }
}

package com.rta.dignify.core.analytics

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * 앱이 어떤 경로로 앞에 나왔는지를 `app_opened_*` 이벤트 하나로 남긴다(iOS `AppOpenTracker`와 같은 이름).
 *
 * - `app_opened_push` — 알림 탭(`type` 동봉)
 * - `app_opened_now_playing` — 미디어 알림 탭(iOS Live Activity 자리)
 * - `app_opened_link` — 링크(ACTION_VIEW) 실행. 매니페스트에 링크 intent-filter가 생기기 전엔 0
 * - `app_opened_direct` — 위 신호가 없는 실행. 아이콘 탭·앱 전환기 복귀를 iOS와 맞춰 한데 센다.
 *
 * 앞으로 나온 신호(프로세스 onStart)와 경로 신호(인텐트)는 도착 순서가 보장되지 않는다 —
 * 백그라운드 복귀에선 onNewIntent가 onStart 앞뒤 어디든 올 수 있다. 그래서 잠깐 기다렸다가
 * 그 앞뒤로 받은 경로로 판정한다. SDK 자동 `Application Opened`는 그대로 두고, 이건 경로만 가른다.
 */
object AppOpen : DefaultLifecycleObserver {

    /** 미디어 알림이 앱을 띄울 때 인텐트에 싣는 표식. */
    const val EXTRA_FROM_NOW_PLAYING = "from_now_playing"

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var deciding = false
    private var via: Triple<String, Map<String, Any>, Long>? = null

    /** 메인 스레드에서. 여러 번 불러도 한 번만 붙는다. */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun openedBy(event: String, properties: Map<String, Any> = emptyMap()) {
        via = Triple(event, properties, SystemClock.elapsedRealtime())
    }

    override fun onStart(owner: LifecycleOwner) {
        if (deciding) return
        deciding = true
        val start = SystemClock.elapsedRealtime()
        main.postDelayed({
            // 3초 넘게 앞선 신호는 이미 앱을 보던 중에 누른 트레이 알림이다 — 이번 실행과 무관.
            val v = via?.takeIf { it.third > start - 3_000 }
            if (v != null) Analytics.capture(v.first, v.second) else Analytics.capture("app_opened_direct")
            via = null
            deciding = false
        }, 2_000)
    }
}

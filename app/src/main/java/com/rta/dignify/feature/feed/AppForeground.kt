package com.rta.dignify.feature.feed

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * 앱 프로세스가 지금 화면에 있는지. 백그라운드 재생 판단과 `background` 계측이 여기 하나만 본다.
 *
 * **화면 단위 라이프사이클로는 판단할 수 없다.** 기준은 "어느 컴포저블이 resumed인가"가 아니라
 * "지금 어떤 재생 세션이 살아있는가"이고, 화면 이벤트는 탭 전환·시트 열림에도 똑같이 튄다.
 * iOS가 `scenePhase` 핸들러를 뷰에서 걷어내 컨트롤러로 옮긴 것과 같은 이유다.
 *
 * 프로세스 스코프라 옵저버 등록은 한 번뿐이고 해제하지 않는다.
 */
object AppForeground : DefaultLifecycleObserver {

    /** 앱이 백그라운드에 있는가. */
    @Volatile
    var isBackground = false
        private set

    private val listeners = mutableSetOf<(Boolean) -> Unit>()
    private var started = false

    /**
     * 전환 알림을 받는다(`true`면 방금 백그라운드로 나갔다는 뜻).
     * 같은 람다 인스턴스를 두 번 넣어도 하나로 유지된다 — 컨트롤러가 재생을 시작할 때마다 부른다.
     * 메인 스레드에서만 호출할 것.
     */
    fun addListener(listener: (Boolean) -> Unit) {
        if (!started) {
            started = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
        listeners += listener
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners -= listener
    }

    override fun onStart(owner: LifecycleOwner) = notify(background = false)

    override fun onStop(owner: LifecycleOwner) = notify(background = true)

    private fun notify(background: Boolean) {
        if (isBackground == background) return
        isBackground = background
        listeners.toList().forEach { it(background) }
    }
}

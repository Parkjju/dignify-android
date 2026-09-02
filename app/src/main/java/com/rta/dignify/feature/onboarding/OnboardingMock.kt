package com.rta.dignify.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rta.dignify.BuildConfig

/**
 * 온보딩 목업. **디버그 빌드에서 [ENABLED]가 true일 때만** 이미 온보딩을 마친 계정으로도
 * 온보딩 플로우가 뜬다. `PickMock`·`HypeMock`과 같은 패턴.
 *
 * 존재 이유: 온보딩은 `isOnboardingComplete == false`인 계정에만 보인다. 한 번 마치면
 * 서버가 true로 굳혀서, 문구 한 줄을 고쳐도 다시 보려면 새 계정을 만들어야 한다.
 * 튜토리얼 7장 → 시드 고르기(재생·검색·선택)까지가 확인 대상이라 그 비용이 크다.
 *
 * **끝까지 걸어가면 스스로 꺼진다**([stop]) — 안 그러면 완료를 눌러도 온보딩으로 되돌아와
 * 앱 본편에 못 들어간다. 그래서 앱을 다시 켤 때마다 한 번씩 볼 수 있다.
 *
 * 주의: 서버 호출을 건너뛰지 않는다. 완료를 누르면 **고른 곡이 실제로 하입된다** —
 * 그게 온보딩의 실제 동작이라(그 하입이 곧 시드다), 가짜로 만들면 확인하려던 것과 다른 걸
 * 보게 된다. 계정의 시드가 바뀌어도 곤란하지 않은 빌드에서만 켤 것.
 *
 * 확인이 끝나면 [ENABLED]를 false로. 릴리즈는 `BuildConfig.DEBUG` 가드에 걸려 항상 실동작이다.
 */
object OnboardingMock {

    /** 온보딩을 강제로 띄울지. 평소엔 false. */
    const val ENABLED = false

    /**
     * 지금 강제 중인가. 완료하면 꺼지므로 `const`가 아니라 상태여야 하고,
     * 화면이 이 값으로 갈리므로 Compose 상태여야 한다.
     */
    var forcing by mutableStateOf(BuildConfig.DEBUG && ENABLED)
        private set

    /** 온보딩을 끝까지 마쳤을 때. `Session.onOnboardingComplete()`가 부른다. */
    fun stop() {
        forcing = false
    }
}

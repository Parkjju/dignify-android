package com.rta.dignify.core.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.network.ApiClient
import com.rta.dignify.core.network.PrefsTokenStore
import com.rta.dignify.feature.onboarding.OnboardingMock
import com.rta.dignify.feature.push.Push

enum class AuthState {
    UNKNOWN,
    SIGNED_OUT,
    /** 로그인 없이 피드만 둘러보는 상태. 계정 기능은 로그인 화면으로 유도한다. */
    GUEST,
    ONBOARDING_REQUIRED,
    SIGNED_IN,
}

/**
 * 앱 전역 세션. iOS `AppSession`에 대응한다.
 *
 * ponytail: DI 프레임워크 없이 object 하나다. 주입할 구현이 하나뿐이라 Hilt를 깔 이유가 없고,
 * **ApiClient는 반드시 앱에 하나여야 한다** — 서버가 refresh 토큰을 rotation하므로 클라이언트가
 * 둘이면 각자 갱신하다 한쪽이 폐기된 토큰을 들고 가 세션이 끊긴다.
 */
object Session {

    var state by mutableStateOf(AuthState.UNKNOWN)
        private set

    /**
     * 피드가 관찰하는 재조회 신호. 로그인·장르 변경처럼 "피드 내용이 통째로 달라지는" 일이
     * 생기면 올린다. iOS의 `genreVersion`과 같은 자리.
     */
    var feedReloadTick by mutableStateOf(0)
        private set

    lateinit var api: ApiClient
        private set

    /** 푸시 서비스처럼 앱 밖에서 깨어난 진입점이 `api`를 만져도 되는지 묻는 자리. */
    val isInitialized: Boolean get() = ::api.isInitialized

    /**
     * 내 닉네임. 픽 프리뷰 카드가 "@내닉네임"으로 나와야 실제로 올라갈 결과와 같아진다.
     * `/users/me`를 부를 때마다 갱신되고, 로그아웃하면 비운다.
     */
    var nickname by mutableStateOf("")
        private set

    fun init(context: Context) {
        if (::api.isInitialized) return
        api = ApiClient(
            store = PrefsTokenStore(context.applicationContext),
            // refresh까지 실패 = 재로그인 필요. 단 게스트는 잃을 세션이 없으므로 끌어내리지 않는다.
            onAuthFailure = { if (state != AuthState.GUEST) state = AuthState.SIGNED_OUT },
        )
    }

    /** 앱 시작 시 저장된 토큰으로 진입 상태를 결정한다. */
    suspend fun resolveInitialState() {
        if (!api.isAuthenticated) {
            state = AuthState.SIGNED_OUT
            return
        }
        runCatching { refreshAuthState() }.onFailure { state = AuthState.SIGNED_OUT }
    }

    fun enterGuest() {
        state = AuthState.GUEST
    }

    /**
     * 게스트 게이트. 로그인이 필요한 동작을 건드린 순간 **바로 로그인 화면을 띄운다.**
     *
     * 앱 상태를 SIGNED_OUT으로 되돌리지 않는 게 중요하다 — 그러면 보던 피드가 통째로
     * 사라지고, 로그인을 취소해도 돌아갈 자리가 없다. 시트로 덮었다 걷으면 하던 자리가 남는다.
     */
    var pendingSignIn by mutableStateOf(false)
        private set

    /** @return 계정이 있으면 true. 없으면 로그인 시트를 띄우고 false. */
    fun requireAccount(): Boolean {
        if (state == AuthState.GUEST) {
            pendingSignIn = true
            return false
        }
        return true
    }

    fun dismissSignInGate() {
        pendingSignIn = false
    }

    /** Credential Manager가 받아온 ID 토큰으로 로그인. 실패 시 throw — 화면이 표시한다. */
    suspend fun signInWithGoogle(idToken: String) {
        api.signInWithGoogle(idToken)
        refreshAuthState()
        // 게스트→로그인 전환. distinct_id는 익명 그대로 이어져 전환 퍼널이 한 사람으로 연결된다.
        // ponytail: 백엔드가 안정적 userId를 안 내려줘 identify는 생략 — iOS와 같은 판단.
        Analytics.capture("signed_in", mapOf("is_new" to (state == AuthState.ONBOARDING_REQUIRED)))
        pendingSignIn = false   // 게이트 시트에서 로그인했으면 시트를 닫는다.
        feedReloadTick++   // 게스트로 보던 피드는 하입 제외가 안 된 상태라 새로 받는다.
    }

    suspend fun logout() {
        api.logout()
        nickname = ""
        state = AuthState.SIGNED_OUT
        feedReloadTick++
    }

    /** 회원 탈퇴. 서버가 픽·하입·장르까지 지운다. 실패하면 throw — 화면이 표시한다. */
    suspend fun withdraw() {
        api.withdraw()
        state = AuthState.SIGNED_OUT
        feedReloadTick++
    }

    /** 온보딩(장르 선택) 완료 후 호출. 피드를 새 장르로 다시 받게 한다. */
    fun onOnboardingComplete() {
        // 강제로 띄운 온보딩이었으면 여기서 푼다 — 안 풀면 완료를 눌러도 되돌아온다.
        OnboardingMock.stop()
        state = AuthState.SIGNED_IN
        feedReloadTick++
    }

    /** 설정에서 장르만 바꿨을 때. 상태는 그대로고 피드만 다시 받으면 된다. */
    fun onGenresChanged() {
        feedReloadTick++
    }

    private suspend fun refreshAuthState() {
        val profile = api.myProfile()
        nickname = profile.nickname
        state = if (profile.isOnboardingComplete) AuthState.SIGNED_IN else AuthState.ONBOARDING_REQUIRED
        // 로그인이 확정된 뒤에 등록한다 — 토큰에 붙일 유저를 서버가 알아야 한다.
        // 앱 시작·로그인 양쪽이 여기를 지나므로 이 한 줄이면 두 경우가 다 걸린다.
        Push.register()
    }
}

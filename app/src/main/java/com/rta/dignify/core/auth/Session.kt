package com.rta.dignify.core.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rta.dignify.core.network.ApiClient
import com.rta.dignify.core.network.PrefsTokenStore

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

    /** 게스트가 계정 기능을 시도했을 때. 로그인 화면으로 되돌린다(게스트로 다시 들어올 수 있다). */
    fun requestSignIn() {
        if (state == AuthState.GUEST) state = AuthState.SIGNED_OUT
    }

    /** Credential Manager가 받아온 ID 토큰으로 로그인. 실패 시 throw — 화면이 표시한다. */
    suspend fun signInWithGoogle(idToken: String) {
        api.signInWithGoogle(idToken)
        refreshAuthState()
        feedReloadTick++   // 게스트로 보던 피드는 하입 제외가 안 된 상태라 새로 받는다.
    }

    suspend fun logout() {
        api.logout()
        state = AuthState.SIGNED_OUT
        feedReloadTick++
    }

    /** 온보딩(장르 선택) 완료 후 호출. 피드를 새 장르로 다시 받게 한다. */
    fun onOnboardingComplete() {
        state = AuthState.SIGNED_IN
        feedReloadTick++
    }

    private suspend fun refreshAuthState() {
        val profile = api.myProfile()
        state = if (profile.isOnboardingComplete) AuthState.SIGNED_IN else AuthState.ONBOARDING_REQUIRED
    }
}

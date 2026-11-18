package com.rta.dignify.core.auth

import android.content.Context
import android.content.SharedPreferences
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.ApiClient
import com.rta.dignify.core.network.PrefsTokenStore
import com.rta.dignify.feature.onboarding.OnboardingMock
import com.rta.dignify.feature.push.Push
import kotlinx.coroutines.MainScope

/** 푸시가 지정한 목적지. 피드 앞 구간을 바꾸는 알림(큐레이션·신곡·주간 추천)은 전부 FEED다. */
enum class PushTarget { FEED, MY_PICKS }

/**
 * 피드 앞 구간을 바꾸라는 알림 탭 하나. `trackIds`가 비면 큐레이션(세트는 서버가 정한다)이다.
 * 대기 칸이 하나뿐이라 **나중에 누른 알림이 앞 것을 덮는다**(신곡·주간이 같은 날 둘 다 올 수 있다).
 */
data class PushOpen(val type: String, val trackIds: List<Int>)

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

    /**
     * 화면이 사라져도 끝나야 하는 요청용. 라운드에서 고른 곡의 하입이 여기로 간다 —
     * 화면 스코프로 쏘면 마지막 라운드의 하입이 "디깅 시작"과 함께 취소돼 시드가 하나 사라진다.
     */
    val appScope = MainScope()

    /** 푸시 서비스처럼 앱 밖에서 깨어난 진입점이 `api`를 만져도 되는지 묻는 자리. */
    val isInitialized: Boolean get() = ::api.isInitialized

    /**
     * 내 닉네임. 픽 프리뷰 카드가 "@내닉네임"으로 나와야 실제로 올라갈 결과와 같아진다.
     * `/users/me`를 부를 때마다 갱신되고, 로그아웃하면 비운다.
     */
    var nickname by mutableStateOf("")
        private set

    /**
     * 하입 따라가기. 마이페이지 스위치와 피드 상단 버튼이 **같은 값을 본다** — 두 곳에 각자
     * 상태를 두면 한쪽에서 끄고 다른 쪽에 가면 켜진 것으로 보인다. `/users/me`가 채운다.
     */
    var diggingMode by mutableStateOf(true)
        private set

    /**
     * 하입이 **서버에 반영된 뒤에** 오른다. 하입 수에서 파생된 화면(디깅 프로필 통계)이
     * 이걸 보고 숫자를 다시 받는다. 낙관적 갱신 시점에 올리면 삭제가 도착하기 전에 통계를
     * 다시 받아 옛 숫자가 그대로 돌아온다.
     */
    var hypeChangeTick by mutableStateOf(0)
        private set

    fun onHypeChanged() {
        hypeChangeTick++
    }

    /**
     * 푸시가 지정한 목적지. 탭을 옮기는 쪽(`MainTabs`)이 읽고 [consumePushTarget]으로 비운다.
     * 액티비티가 인텐트에서 채우므로 컴포즈 상태여야 한다 — 일반 var면 탭이 안 움직인다.
     */
    var pushTarget by mutableStateOf<PushTarget?>(null)
        private set

    /**
     * 알림 탭으로 받은 피드 앞 구간. 피드가 읽고 즉시 비운다 — **이미 완주한 큐레이션 세트라도 이때는
     * 다시 앞세운다.** 곡을 보여주겠다는 알림을 눌렀는데 평소 피드 첫 장이 나오면 유저는 자기가 뭘
     * 눌렀는지 모른 채 앱만 켜게 된다.
     *
     * 컴포즈 상태가 아니어도 된다. 화면이 이 값을 그리지 않고, 적용은 [pushOpenTick]이 건다.
     */
    private var pendingPushOpen: PushOpen? = null

    /**
     * 알림이 피드 앞 구간을 바꾸라고 할 때 오른다. [feedReloadTick]과 따로 두는 이유: 그쪽은 커서를
     * 버리고 처음부터 받는다. 알림은 **평소 피드의 진행 위치를 바꾸면 안 된다**(주간 추천 2-5).
     */
    var pushOpenTick by mutableStateOf(0)
        private set

    /**
     * 알림 탭으로 들어왔을 때 종류별로 갈라 보낸다. iOS `AppDelegate.userNotificationCenter`와
     * 같은 자리이고 같은 판정이다. 앱이 앞에 있을 때 **도착만 한** 알림은 여기를 안 지난다 —
     * 듣던 곡과 스와이프 위치가 알림 때문에 바뀌면 안 된다.
     *
     * `notice`는 분기가 없는 게 맞다 — 갈 데를 지정하지 않은 알림이다.
     * `pick_reaction`이 픽 탭이 아니라 디깅 프로필로 가는 이유: 어느 픽인지가 페이로드에
     * 없어서, 남의 픽이 섞인 목록을 열면 반응이 달린 내 픽을 찾을 수가 없다.
     */
    fun onPushOpened(type: String, trackIds: List<Int> = emptyList()) {
        when (type) {
            "curation", "new_release", "weekly_pick" -> {
                pendingPushOpen = PushOpen(type, trackIds)
                pushTarget = PushTarget.FEED
                pushOpenTick++
            }

            "pick_reaction" -> pushTarget = PushTarget.MY_PICKS
        }
    }

    fun consumePushTarget() {
        pushTarget = null
    }

    /** 알림이 정한 앞 구간. 읽는 순간 비워진다(1회성). */
    fun consumePushOpen(): PushOpen? = pendingPushOpen.also { pendingPushOpen = null }

    /**
     * 이번 실행에서 권한·동의 팝업을 이미 띄웠나. 피드가 아니라 여기 두는 이유는 픽 게시 경로도
     * 같은 값을 봐야 해서다(한 실행에 팝업 두 번 금지).
     *
     * **프로세스가 아니라 실행([countLaunch]) 단위로 끈다.** 최근 앱에서 지워도 프로세스가 캐시로 남으면
     * 이 object가 그대로 살아 있어서, 메모리에만 두면 다음 실행의 좋아요 팝업이 막혔다.
     */
    var promptedThisSession = false

    /**
     * 이번 실행에서 게스트로 쓰다가 로그인했나(iOS `signedInFromGuest`). 첫 실행 동의 팝업은 이때 다음 실행으로 미룬다 —
     * 로그인하자마자 또 팝업이 뜨면 방금 막힌 동작을 하려던 흐름이 두 번 끊긴다. [promptedThisSession]과 같이 실행마다 끈다.
     */
    var signedInFromGuest = false

    /** 업데이트 소식 시트가 떠 있나. 그 위로 팝업이 겹치지 않게 피드가 본다. */
    var whatsNewOpen by mutableStateOf(false)

    /**
     * 앱 실행 횟수. 좋아요 팝업은 두 번째 실행부터다 — 첫 실행엔 코치마크 뒤 동의 팝업이 이미 나간다.
     * 액티비티가 새로 만들어질 때만 센다(회전·복원은 실행이 아니다).
     */
    val launchCount: Int get() = prefs.getInt(KEY_LAUNCH_COUNT, 0)

    fun countLaunch() {
        promptedThisSession = false
        signedInFromGuest = false
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount + 1).apply()
    }

    /**
     * 광고성 알림 수신 동의(서버 값). **OS 알림 권한과 별개다** — 권한은 광고 수신 동의가 아니다
     * (정보통신망법 제50조, KISA 안내서 Q&A 02-03). `/users/me`가 채우고, 그 필드가 없는 서버에선
     * 이 기기에서 마지막으로 처리한 결과를 쓴다.
     */
    var adPushConsent by mutableStateOf(false)
        private set

    /**
     * 동의·거부를 서버에 남긴다. 실패하면 null — 부르는 쪽이 결과 통지 대신 실패를 알린다.
     * 계측은 부르는 쪽 몫이다(맥락마다 `source`가 다르다).
     */
    suspend fun setAdPushConsent(consent: Boolean): Api.AdPushConsent? =
        runCatching { api.setAdPushConsent(consent) }
            .onFailure { android.util.Log.w("Session", "ad consent failed", it) }
            .getOrNull()
            ?.also { onAdConsentProcessed(it.adPush) }

    /** 앱 밖(알림의 「수신거부」)에서 처리된 결과도 여기로 맞춘다. 광고성을 끄면 서버가 야간도 끈다(켜져 있을 때만). */
    fun onAdConsentProcessed(consent: Boolean) {
        adPushConsent = consent
        adPushAnswered = true
        if (!consent && nightPushConsent == true) nightPushConsent = false
        prefs.edit().putBoolean(KEY_AD_CONSENT, consent).apply()
    }

    /** 광고성 동의에 답한 적이 있나(서버 `adPushConsentAt`). 첫 실행 동의 팝업은 미응답에만 뜬다. */
    var adPushAnswered by mutableStateOf(false)
        private set

    /** 야간 광고성 동의(서버 값). null = 미응답 — 야간 동의 팝업 조건. */
    var nightPushConsent by mutableStateOf<Boolean?>(null)
        private set

    /** 실패하면 null. 광고성 동의가 없으면 서버가 409로 막는다 — 부르는 쪽이 먼저 거른다. */
    suspend fun setNightPushConsent(consent: Boolean): Api.NightPushConsent? =
        runCatching { api.setNightPushConsent(consent) }
            .onFailure { android.util.Log.w("Session", "night consent failed", it) }
            .getOrNull()
            ?.also { nightPushConsent = it.nightPush }

    private lateinit var prefs: SharedPreferences

    /**
     * 게스트가 코치마크에서 한 첫 하입. 로그인 시트 없이 받아 두고 기기에 맡겼다가, 로그인하면
     * 서버 하입으로 올리고 지운다(feed_coachmark 1.0 #8). **하입 기록으로만** 남는다 —
     * 곡 고르기에 미리 골라 두지도, 추천 기준 3곡에 넣지도 않는다.
     */
    fun holdGuestHype(trackId: Int) {
        val held = prefs.getStringSet(KEY_GUEST_HYPES, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_GUEST_HYPES, held + trackId.toString()).apply()
    }

    /**
     * 맡아 둔 게스트 하입을 올린다. 이미 하입된 곡이면 서버가 409(`HYPE_ALREADY_REGISTERED`)를
     * 주는데 원하던 상태와 같으니 성공으로 친다. 네트워크 실패는 남겨 뒀다가 다음 실행 때 다시 올린다.
     */
    private suspend fun flushGuestHypes() {
        val held = prefs.getStringSet(KEY_GUEST_HYPES, emptySet()).orEmpty()
        if (held.isEmpty()) return
        val sent = held.filter { id ->
            runCatching { api.hype(id.toInt()) }
                .map { it.status.isSuccess() || it.status == HttpStatusCode.Conflict }
                .getOrDefault(false)
        }
        if (sent.isNotEmpty()) onHypeChanged()
        prefs.edit().putStringSet(KEY_GUEST_HYPES, held - sent.toSet()).apply()
    }

    private const val KEY_GUEST_HYPES = "pendingGuestHypes"
    private const val KEY_LAUNCH_COUNT = "launchCount"
    private const val KEY_AD_CONSENT = "adPushConsent"

    fun init(context: Context) {
        if (::api.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("dignify", Context.MODE_PRIVATE)
        adPushConsent = prefs.getBoolean(KEY_AD_CONSENT, false)
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
        val wasGuest = state == AuthState.GUEST
        api.signInWithGoogle(idToken)
        if (wasGuest) signedInFromGuest = true
        refreshAuthState()
        // 게스트→로그인 전환. distinct_id는 익명 그대로 이어져 전환 퍼널이 한 사람으로 연결된다.
        // ponytail: 백엔드가 안정적 userId를 안 내려줘 identify는 생략 — iOS와 같은 판단.
        Analytics.capture("signed_in", mapOf("is_new" to (state == AuthState.ONBOARDING_REQUIRED)))
        pendingSignIn = false   // 게이트 시트에서 로그인했으면 시트를 닫는다.
        feedReloadTick++   // 게스트로 보던 피드는 하입 제외가 안 된 상태라 새로 받는다.
    }

    suspend fun logout() {
        api.logout(com.rta.dignify.feature.push.Push.token)
        nickname = ""
        onAdConsentProcessed(false)   // 동의는 계정 것이다. 다음 로그인의 /users/me가 다시 채운다.
        adPushAnswered = false
        nightPushConsent = null
        diggingMode = true
        state = AuthState.SIGNED_OUT
        feedReloadTick++
    }

    /** 회원 탈퇴. 서버가 픽·하입·장르까지 지운다. 실패하면 throw — 화면이 표시한다. */
    suspend fun withdraw() {
        api.withdraw()
        state = AuthState.SIGNED_OUT
        feedReloadTick++
    }

    /**
     * 하입 따라가기를 바꾸고 피드를 처음부터 다시 받게 한다. 정렬 기준 자체가 바뀌어서
     * 들고 있던 커서를 이어 쓸 수 없다 — 그 커서로 이어 보면 옛 기준으로 뽑힌 페이지가
     * 계속 나와서 스위치가 아무 일도 안 한 것처럼 보인다.
     *
     * **낙관적으로 먼저 바꾸고 실패하면 되돌린다.** 스위치가 손가락을 안 따라오면 고장으로 읽힌다.
     *
     * @return 저장에 성공했으면 true. 부르는 쪽이 실패 문구를 띄운다.
     */
    suspend fun setDiggingMode(enabled: Boolean, source: String): Boolean {
        val previous = diggingMode
        diggingMode = enabled
        return runCatching { api.setDiggingMode(enabled) }
            .onSuccess {
                feedReloadTick++
                // 서버에 반영된 뒤에만 센다. 낙관적 갱신 시점에 쏘면 실패해 되돌아간 토글까지
                // 켠 것으로 잡혀 "끈 사람 수"가 부풀려진다.
                Analytics.capture(
                    "digging_mode_changed",
                    mapOf("enabled" to enabled, "source" to source),
                )
            }
            .onFailure { diggingMode = previous }
            .isSuccess
    }

    /** 기준 곡이 바뀌었을 때. 성향 토글과 같은 이유로 커서를 버리고 처음부터 받는다. */
    fun onSeedsChanged() {
        feedReloadTick++
    }

    /**
     * 곡 고르기 직후 첫 피드를 받는 동안 "피드 구성 중"을 띄울 커버들. null이면 안 띄운다.
     * 최소 2초·응답까지 보여 준 뒤 [onFeedBuilt]로 내린다. 온보딩(앱 상태 전환)과
     * 피드(로딩 상태)가 서로 다른 화면이라 여기서 건넨다.
     */
    var buildingFeedFrom by mutableStateOf<List<String>?>(null)
        private set

    fun onFeedBuilt() {
        buildingFeedFrom = null
    }

    /** 온보딩 완료 후 호출. 하입한 곡을 시드로 피드를 다시 받게 한다. */
    fun onOnboardingComplete(buildingFrom: List<String>) {
        // 강제로 띄운 온보딩이었으면 여기서 푼다 — 안 풀면 완료를 눌러도 되돌아온다.
        OnboardingMock.stop()
        buildingFeedFrom = buildingFrom
        state = AuthState.SIGNED_IN
        feedReloadTick++
    }

    /**
     * 닉네임·성향을 서버 값으로 맞춘다. **로그인 단계는 안 건드린다** — 마이페이지가 다시
     * 들어올 때마다 부르는 자리라, 여기서 상태까지 옮기면 화면이 통째로 갈릴 수 있다.
     */
    suspend fun refreshProfile(): Api.UserProfile {
        val profile = api.myProfile()
        nickname = profile.nickname
        // 옛 서버는 이 필드를 안 내려준다. 그때는 지금 동작(켜짐)을 유지한다.
        diggingMode = profile.diggingMode ?: true
        profile.adPushConsent?.let { onAdConsentProcessed(it) }
        adPushAnswered = profile.adPushConsentAt != null
        nightPushConsent = profile.adPushNightConsent
        return profile
    }

    private suspend fun refreshAuthState() {
        val profile = refreshProfile()
        state = if (profile.isOnboardingComplete) AuthState.SIGNED_IN else AuthState.ONBOARDING_REQUIRED
        // 로그인이 확정된 뒤에 등록한다 — 토큰에 붙일 유저를 서버가 알아야 한다.
        // 앱 시작·로그인 양쪽이 여기를 지나므로 이 한 줄이면 두 경우가 다 걸린다.
        Push.register()
        // 로그인 직후의 피드 재조회보다 먼저 끝나야 그 곡이 하입된 채로 보인다(signInWithGoogle이 기다린다).
        flushGuestHypes()
    }
}

package com.rta.dignify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clip
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.IconButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.network.Api
import com.rta.dignify.feature.feed.FeedViewModel
import com.rta.dignify.feature.artist.ArtistRequestScreen
import com.rta.dignify.feature.picks.BlockedUsersScreen
import com.rta.dignify.feature.picks.LocalModeration
import com.rta.dignify.feature.picks.MyPicksScreen
import com.rta.dignify.feature.picks.PickListScreen
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.auth.PushTarget
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSBrandMark
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.feature.auth.SignInScreen
import com.rta.dignify.feature.digging.DiggingProfileScreen
import com.rta.dignify.feature.feed.FeedScreen
import com.rta.dignify.feature.mypage.HypeHistoryScreen
import com.rta.dignify.feature.mypage.MyPageScreen
import com.rta.dignify.feature.mypage.SeedPickerScreen
import com.rta.dignify.feature.onboarding.OnboardingMock
import com.rta.dignify.feature.onboarding.PredictedType
import com.rta.dignify.feature.onboarding.SeedPoolPickerScreen
import com.rta.dignify.feature.onboarding.TutorialScreen
import com.rta.dignify.feature.onboarding.fetchSeedPool
import com.rta.dignify.feature.push.Push
import com.rta.dignify.feature.whatsnew.Changelog
import com.rta.dignify.feature.whatsnew.WhatsNewSheet
import androidx.compose.ui.platform.LocalContext

/**
 * ponytail: 탭 바도 내비게이션 라이브러리도 없다. 화면 전환이 "인증 상태에 따라 하나를 고른다"뿐이라
 * when 하나로 끝난다 — 픽·마이페이지가 생겨서 탭이 필요해지면 그때 iOS `MainTabView`처럼 늘린다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 세션보다 먼저 — 로그인 복원이 곧바로 signed_in을 쏜다.
        Analytics.init(applicationContext)
        Session.init(applicationContext)
        // 차단·숨김 목록은 서버가 모르는 로컬 상태라 앱 시작 때 읽어둔다.
        LocalModeration.init(applicationContext)
        PredictedType.init(applicationContext)
        // 채널이 없으면 도착한 알림이 조용히 버려진다. 만들어두는 건 권한과 무관하다.
        Push.ensureChannel(applicationContext)
        capturePushOpen(intent)
        // 아트워크가 화면을 꽉 채우는 지면이라 시스템 바 뒤까지 그린다.
        enableEdgeToEdge()
        setContent { DignifyApp() }
    }

    // 액티비티가 이미 살아 있으면 onCreate가 아니라 이쪽으로 들어온다.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePushOpen(intent)
    }

    /**
     * 알림을 눌러 들어온 경우에만 찍는다. FCM은 알림 탭으로 연 인텐트에만
     * `google.message_id`를 붙이므로 그게 런처 실행과 가르는 유일한 표식이다.
     *
     * `type`은 서버가 2026-09-02부터 데이터 페이로드로 싣는다(`curation`·`pick_reaction`·`notice`).
     * 목적지 분기는 [Session.onPushOpened]가 한다 — 여기서 갈라 두면 액티비티가 탭 상태를
     * 알아야 하고, 온보딩 중에 도착한 푸시가 갈 곳을 잃는다.
     */
    private fun capturePushOpen(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (!extras.containsKey("google.message_id")) return
        val type = extras.getString("type") ?: "unknown"
        Analytics.capture("push_opened", mapOf("type" to type))
        Session.onPushOpened(type)
    }
}

@Composable
private fun DignifyApp() {
    when (Session.state) {
        AuthState.UNKNOWN -> {
            LaunchTitle()
            LaunchedEffect(Unit) { Session.resolveInitialState() }
        }

        AuthState.SIGNED_OUT -> SignInScreen()
        AuthState.ONBOARDING_REQUIRED -> OnboardingFlow()
        // 세션이 풀린 **뒤에** 가른다 — 라운드 후보가 인증 엔드포인트라 인증 전에 강제로
        // 띄우면 빈 화면만 보인다.
        AuthState.GUEST, AuthState.SIGNED_IN ->
            if (OnboardingMock.forcing) OnboardingFlow() else MainTabs()
    }
}

/**
 * 신규 유저 온보딩. iOS `NewUserOnboardingView` 이식.
 *
 *     튜토리얼 → 시드 고르기(인기곡 풀) → 피드
 *
 * **장르를 한 번도 묻지 않는다.** 장르 이름으로 자기 취향을 말할 수 있는 사람은 많지 않고,
 * 물어봐야 나오는 건 `user_genres` 몇 행뿐인데 서버는 그걸 더 이상 읽지 않는다.
 * 여기서 고른 곡은 그대로 하입되어 시드가 되므로 **첫 피드부터** 무드 정렬이 걸린다.
 *
 * 풀을 못 받으면(네트워크 실패·서버 미시딩) 화면은 그대로 뜨고 버튼만 0곡으로 열린다 —
 * 여기서 조용히 완료 처리를 해버리면 그 요청이 실패했을 때 다시 시도할 자리가 없다.
 */
@Composable
private fun OnboardingFlow() {
    var showSeedPicker by rememberSaveable { mutableStateOf(false) }
    var pool by remember { mutableStateOf<List<Api.FeedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // 튜토리얼이 끝났는데 풀이 아직 안 온 상태. 이때는 튜토리얼 마지막 장에 두고 기다린다 —
    // 빈 화면을 잠깐 보여주는 것보다 낫다.
    var waitingForPool by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 피드 조작을 먼저 익히게 한다. 풀은 그동안 백그라운드로 받는다.
    LaunchedEffect(Unit) {
        pool = fetchSeedPool()
        isLoading = false
        if (waitingForPool) { waitingForPool = false; showSeedPicker = true }
    }

    if (showSeedPicker) {
        SeedPoolPickerScreen(pool = pool) {
            Session.api.completeOnboarding()
            // 업데이트 유저용 1회 트리거가 이 유저에게 또 걸리지 않게 같이 내린다.
            // 방금 가입한 유저에게 What's New를 안 띄우는 판정도 여기서 남긴다.
            context.getSharedPreferences("dignify", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DID_ROUNDS, true)
                .putBoolean(KEY_DID_JUST_ONBOARD, true)
                .apply()
            Session.onOnboardingComplete()
        }
    } else {
        TutorialScreen(
            busy = waitingForPool,
            onDone = { if (isLoading) waitingForPool = true else showSeedPicker = true },
        )
    }
}

/**
 * 시드 고르기를 한 번이라도 태웠는지. 신규 가입·업데이트 유저가 같은 키를 쓴다.
 *
 * **이름을 바꾸면 안 된다.** 값이 `didSoundRounds`인 건 2지선다 시절 이름이라서지 라운드를
 * 뜻해서가 아니다 — 바꾸는 순간 이미 온보딩을 끝낸 유저 전원이 이 화면을 다시 본다.
 */
private const val KEY_DID_ROUNDS = "didSoundRounds"

/** 방금 가입한 유저 표식. What's New 오발동만 막는 용도라 소비 후 지운다. */
private const val KEY_DID_JUST_ONBOARD = "didJustOnboard"

/** iOS `AppTab` 대응. */
private enum class AppTab { FEED, PICKS, MY }

/**
 * iOS `MainTabView` 대응. 라이브러리 없이 상태 하나로 가르는 건 화면 전환이
 * "탭 하나 고르기"뿐이기 때문이다 — 스택이 필요해지는 화면(디깅 프로필·장르 설정)은
 * 그 탭 안에서 자기 상태로 처리한다.
 *
 * 탭바를 Scaffold의 bottomBar가 아니라 오버레이로 얹는 이유: 피드가 아트워크로 화면을
 * 꽉 채우는 지면이라 bottomBar가 레이아웃을 먹으면 배경이 탭바 위에서 잘린다.
 * 대신 피드에 탭바 높이를 여백으로 넘겨 액션 행이 가리지 않게 한다.
 */
@Composable
private fun MainTabs() {
    var tab by rememberSaveable { mutableStateOf(AppTab.FEED) }
    // 탭 안의 화면 위치도 탭 밖에 둔다 — 탭을 옮기면 안쪽 컴포저블이 통째로 사라져서
    // 안에 두면 마이페이지로 갔다 오는 것만으로 디깅 프로필에서 루트로 튕긴다.
    // (피드가 보던 곡을 기억하는 건 FeedViewModel.lastPage가 맡는다.)
    var myRoute by rememberSaveable { mutableStateOf(MyRoute.ROOT) }
    // 재생 중인 픽. 탭 밖에 둬야 다른 탭 갔다 와도 보던 픽이 유지된다.
    var playingPick by remember { mutableStateOf<Api.Pick?>(null) }
    // 시드 고르기를 닫은 뒤에 이어서 띄울 What's New 버전.
    var pendingWhatsNew by remember { mutableStateOf<String?>(null) }

    // 탭바가 실제로 가리는 높이 = 내용 높이 + 시스템 내비게이션 바.
    // 이걸 안 더하면 화면들이 제스처 바 높이만큼 탭바 아래로 파고든다
    // (새 픽 버튼이 탭바에 걸치고 마지막 카드가 탭바에 붙던 원인).
    val barContentHeight = 64.dp
    val barHeight = barContentHeight +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 업데이트로 들어온 유저에게만 새 소식을 띄운다. 신규 설치는 튜토리얼 대상이라 제외.
    val context = LocalContext.current
    var autoWhatsNew by remember { mutableStateOf<String?>(null) }
    // 업데이트 유저에게 한 번 태우는 시드 고르기. 풀을 실제로 받은 뒤에 세팅한다 —
    // **플래그로 열면 안 된다.** 풀이 채워지기 전 값이 화면에 잡혀 곡을 다 받고도
    // 빈 화면이 뜬다(iOS가 실제로 밟은 함정).
    var updateSeedPool by remember { mutableStateOf<List<Api.FeedItem>?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("dignify", android.content.Context.MODE_PRIVATE)
        val lastSeen = prefs.getString("lastSeenVersion", "").orEmpty()
        val current = BuildConfig.VERSION_NAME
        // 기존 로그인 유저가 업데이트로 들어온 경우 = 온보딩을 안 거쳤고 로그인 상태다.
        val didJustOnboard = prefs.getBoolean(KEY_DID_JUST_ONBOARD, false)
        val isReturning = !didJustOnboard && Session.state == AuthState.SIGNED_IN
        val wantsWhatsNew = Changelog.shouldShow(lastSeen, current, isReturningUser = isReturning)

        // 업데이트로 들어온 기존 유저는 한 번은 시드 고르기를 탄다. **하입 수로 가르지 않는다** —
        // 시드가 밀렸으면 마이페이지 → 추천 기준 곡에서 되돌리면 되고, 건너뛰면
        // 하입이 많은 유저일수록 이번 릴리즈에서 무엇이 바뀌었는지 겪어볼 자리가 없어진다.
        val pool = if (isReturning && !prefs.getBoolean(KEY_DID_ROUNDS, false)) {
            // 풀이 없으면(서버 미시딩) 아예 안 띄우고 **플래그도 안 태운다** — 시딩된 뒤에
            // 이 유저가 영영 이 화면을 못 보면 안 된다.
            fetchSeedPool().takeIf { it.isNotEmpty() }
        } else null

        if (pool != null) {
            // 시드 고르기가 What's New를 삼키면 안 된다. 닫힐 때 이어 붙인다 — 이번 릴리즈는
            // 온보딩이 바뀐 게 핵심이라, 화면만 보고 넘어가면 무엇이 달라졌는지 모른 채 피드로 간다.
            pendingWhatsNew = if (wantsWhatsNew) current else null
            updateSeedPool = pool
        } else if (wantsWhatsNew) {
            autoWhatsNew = current
        }
        prefs.edit().putString("lastSeenVersion", current).remove(KEY_DID_JUST_ONBOARD).apply()
    }

    // 푸시가 지정한 목적지로 옮긴다. 탭과 마이 탭 스택을 아는 건 여기뿐이라 이 자리다.
    // `pick_reaction`이 픽 탭이 아니라 디깅 프로필로 가는 이유는 `Session.onPushOpened` 참고.
    LaunchedEffect(Session.pushTarget) {
        when (Session.pushTarget) {
            PushTarget.CURATION -> tab = AppTab.FEED
            PushTarget.MY_PICKS -> { myRoute = MyRoute.DIGGING; tab = AppTab.MY }
            null -> return@LaunchedEffect
        }
        Session.consumePushTarget()
    }

    Box(Modifier.fillMaxSize()) {
        // 시드 고르기 커버가 떠 있는 동안엔 탭 지면을 **아예 안 그린다.** 배경만 깐 Column은 터치를
        // 안 막아서 위에 얹으면 탭바가 그대로 눌리고, 컴포지션에 남은 피드는 계속 소리를 낸다.
        if (updateSeedPool == null) {
            when (tab) {
                AppTab.FEED -> FeedScreen(bottomInset = barHeight)
                // 픽 재생은 별도 화면을 만들지 않는다 — 서버가 픽 상세를 피드와 같은 형태로 주므로
                // 피드 화면에 목록만 갈아끼워 태운다(iOS `FeedMode.pick`과 같은 판단).
                AppTab.PICKS ->
                    if (playingPick != null) {
                        PickPlayback(pick = playingPick!!, bottomInset = barHeight) { playingPick = null }
                    } else {
                        PickListScreen(bottomInset = barHeight, onPlay = { playingPick = it })
                    }

                AppTab.MY -> MyTab(
                    bottomInset = barHeight,
                    route = myRoute,
                    onRoute = { myRoute = it },
                    // 내 픽에서 재생하면 픽 탭으로 넘겨 같은 재생 지면을 쓴다.
                    onPlayPick = { playingPick = it; tab = AppTab.PICKS },
                )
            }

            TabBar(
                current = tab,
                onSelect = { tab = it },
                height = barContentHeight,
                // 피드 위에선 지면이 검으므로 반투명 검정, 마이페이지는 흰 지면이라 불투명.
                onFeed = tab == AppTab.FEED,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 시트가 아니라 화면 전체다 — 쓸어 닫을 수 있으면 고르다 만 채로 끊긴다.
        updateSeedPool?.let { pool ->
            SeedPoolPickerScreen(pool = pool, isUpdate = true) { picked ->
                context.getSharedPreferences("dignify", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DID_ROUNDS, true).apply()
                updateSeedPool = null
                // 방금 고른 곡이 시드다. 피드는 이미 불러온 뒤라 다시 받지 않으면
                // 유저는 다 고르고도 예전 순서를 본다.
                if (picked > 0) Session.onSeedsChanged()
                autoWhatsNew = pendingWhatsNew
                pendingWhatsNew = null
            }
        }

        autoWhatsNew?.let { version ->
            WhatsNewSheet(highlight = version, onDismiss = { autoWhatsNew = null })
        }

        // 게스트 게이트. 보던 화면 위를 덮었다 걷히므로 취소해도 하던 자리가 남는다.
        // `if`로 붙였다 떼면 나갈 때 애니메이션이 안 돈다(컴포저블이 즉시 사라져서) —
        // AnimatedVisibility로 감싸야 퇴장까지 살아 있는다.
        AnimatedVisibility(
            visible = Session.pendingSignIn,
            // 시트가 올라오는 느낌. 들어올 땐 스프링으로 살짝 붙잡고, 나갈 땐 미련 없이 내려간다 —
            // 닫기는 유저가 이미 결정한 동작이라 끌면 굼떠 보인다.
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { it },
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(tween(220), targetOffsetY = { it }) + fadeOut(tween(160)),
        ) {
            SignInGate()
        }
    }
}

/**
 * 마이 탭 안의 화면 스택. 게스트는 계정 화면 대신 로그인 유도를 본다 —
 * 마이페이지의 모든 행이 계정을 전제하므로 빈 화면을 보여줄 수가 없다.
 */
@Composable
private fun MyTab(
    bottomInset: Dp,
    route: MyRoute,
    onRoute: (MyRoute) -> Unit,
    onPlayPick: (Api.Pick) -> Unit,
) {
    // 요약 행이 이미 받아둔 첫 페이지를 목록 화면에 그대로 물려준다(재조회 0).
    var myPicks by remember { mutableStateOf<List<Api.Pick>>(emptyList()) }
    var myPicksCursor by remember { mutableStateOf<String?>(null) }
    val onMyPicks: (List<Api.Pick>, String?) -> Unit = { l, c -> myPicks = l; myPicksCursor = c }

    // 시스템 뒤로가기로도 한 칸 나온다. 화면마다 달지 않고 여기 하나로 두는 이유:
    // 스택을 아는 건 이 컴포저블뿐이고, 안 달면 하위 화면에서 뒤로가기가 앱을 종료시킨다.
    BackHandler(enabled = route != MyRoute.ROOT) { onRoute(route.parent) }

    Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
        if (Session.state == AuthState.GUEST) {
            GuestSignInPrompt()
            return@Box
        }
        // iOS의 푸시/팝처럼 옆으로 민다. 라이브러리 없이 AnimatedContent 하나로 되고,
        // 깊이(뎁스)를 비교해 들어갈 땐 오른쪽에서, 나올 땐 왼쪽에서 들어오게 방향을 뒤집는다.
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState.depth > initialState.depth
                val dir = if (forward) 1 else -1
                // 감속 곡선(EaseOutCubic)이라 끝에서 부드럽게 멎는다. 선형 tween은 딱 멈춰서
                // 화면이 "붙는" 느낌이 난다.
                val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                // 들어오는 화면은 전체 폭, 나가는 화면은 1/4만 — iOS 푸시처럼 뒤 화면이
                // 살짝 밀리며 깊이가 생긴다. 페이드는 짧게 겹쳐 두 화면이 동시에 보이는 구간을 줄인다.
                (slideInHorizontally(tween(300, easing = easing)) { w -> dir * w } +
                    fadeIn(tween(150)))
                    .togetherWith(
                        slideOutHorizontally(tween(300, easing = easing)) { w -> -dir * w / 4 } +
                            fadeOut(tween(150))
                    )
            },
            label = "myTab",
        ) { current ->
        when (current) {
            MyRoute.ROOT -> MyPageScreen(
                onOpenDiggingProfile = { onRoute(MyRoute.DIGGING) },
                onOpenSeedPicker = { onRoute(MyRoute.SEEDS) },
                onOpenArtistRequests = { onRoute(MyRoute.ARTIST_REQUESTS) },
                onOpenBlockedUsers = { onRoute(MyRoute.BLOCKED) },
                onOpenTutorial = { onRoute(MyRoute.TUTORIAL) },
                onOpenWhatsNew = { onRoute(MyRoute.WHATS_NEW) },
            )

            MyRoute.DIGGING -> DiggingProfileScreen(
                onBack = { onRoute(MyRoute.ROOT) },
                onSeeAllHypes = { onRoute(MyRoute.HYPES) },
                onSeeAllPicks = { list, c -> onMyPicks(list, c); onRoute(MyRoute.MY_PICKS) },
            )

            MyRoute.MY_PICKS -> MyPicksScreen(
                initial = myPicks,
                initialCursor = myPicksCursor,
                onBack = { onRoute(MyRoute.DIGGING) },
                onPlay = onPlayPick,
            )

            MyRoute.HYPES -> HypeHistoryScreen(onBack = { onRoute(MyRoute.DIGGING) })
            MyRoute.SEEDS -> SeedPickerScreen(onBack = { onRoute(MyRoute.ROOT) })
            MyRoute.ARTIST_REQUESTS -> ArtistRequestScreen(onBack = { onRoute(MyRoute.ROOT) })
            MyRoute.BLOCKED -> BlockedUsersScreen(onBack = { onRoute(MyRoute.ROOT) })
            MyRoute.TUTORIAL -> TutorialScreen(onDone = { onRoute(MyRoute.ROOT) })
            // 마이페이지에서 직접 열면 전체 로그(highlight = null).
            MyRoute.WHATS_NEW -> WhatsNewSheet(onDismiss = { onRoute(MyRoute.ROOT) })
        }
        }
    }
}

/** `depth`는 전환 방향 판정용 — 값이 커지는 쪽이 "더 들어가는" 것이다. */
enum class MyRoute(val depth: Int) {
    ROOT(0),
    DIGGING(1), SEEDS(1), ARTIST_REQUESTS(1), BLOCKED(1), TUTORIAL(1), WHATS_NEW(1),
    HYPES(2), MY_PICKS(2),
}

/**
 * 뒤로가기가 갈 곳. 화면의 `onBack`이 가리키는 곳과 같아야 한다 —
 * 버튼과 시스템 뒤로가기가 다른 데로 가면 그게 더 헷갈린다.
 */
private val MyRoute.parent: MyRoute
    get() = when (this) {
        MyRoute.HYPES, MyRoute.MY_PICKS -> MyRoute.DIGGING
        else -> MyRoute.ROOT
    }

/**
 * 픽 재생. 피드 화면을 그대로 쓰되 목록만 픽 상세로 갈아끼운다.
 * 뷰모델을 피드 탭과 공유하면 안 되므로 `key`로 별도 인스턴스를 잡는다 —
 * 같은 걸 쓰면 픽을 열었다 닫는 순간 피드 탭이 픽 목록을 들고 있게 된다.
 */
@Composable
private fun PickPlayback(pick: Api.Pick, bottomInset: Dp, onBack: () -> Unit) {
    val vm: FeedViewModel = viewModel(key = "pick-${pick.pickId}")
    LaunchedEffect(pick.pickId) { vm.loadPick(pick.pickId, pick.nickname) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        FeedScreen(bottomInset = bottomInset, onSwipeOutOfRange = onBack, vm = vm)
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

/**
 * 게스트가 계정 기능을 건드렸을 때 덮는 로그인 화면. iOS는 시트로 띄우는데, 여기선
 * 전체를 덮는다 — 로그인 화면이 세로로 꽉 차는 구성이라 부분 시트에 넣으면 잘린다.
 * 뒤로가기로 닫히고, 닫아도 하던 자리가 그대로 남는다.
 */
@Composable
private fun SignInGate() {
    BackHandler { Session.dismissSignInGate() }
    Box(
        Modifier
            .fillMaxSize()
            // 위쪽 모서리를 깎아 "덮인 화면이 뒤에 있다"를 드러낸다 — 전체를 각지게 덮으면
            // 화면이 교체된 것처럼 보여서 닫을 수 있다는 게 안 읽힌다.
            .clip(RoundedCornerShape(topStart = DSRadius.large, topEnd = DSRadius.large))
            .background(DSColor.background),
    ) {
        SignInScreen(isGate = true)
        IconButton(
            onClick = { Session.dismissSignInGate() },
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = DSColor.textSecondary)
        }
    }
}

/** iOS `GuestSignInPromptView` 이식. */
@Composable
private fun GuestSignInPrompt() {
    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DSBrandMark(size = 56.dp)
        Text(
            stringResource(R.string.guest_prompt_title),
            style = DSTypography.title2,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.guest_prompt_message),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = { Session.requireAccount() },
            shape = RoundedCornerShape(DSRadius.medium),
            colors = ButtonDefaults.buttonColors(containerColor = DSColor.brand),
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.signin_action), style = DSTypography.headline, color = Color.White)
        }
    }
}

@Composable
private fun TabBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    height: Dp,
    onFeed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (onFeed) Color.Black.copy(alpha = 0.55f) else DSColor.background)
            .navigationBarsPadding()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(Icons.Filled.Home, R.string.tab_feed, current == AppTab.FEED, onFeed, Modifier.weight(1f)) {
            onSelect(AppTab.FEED)
        }
        TabItem(Icons.Filled.LibraryMusic, R.string.tab_picks, current == AppTab.PICKS, onFeed, Modifier.weight(1f)) {
            onSelect(AppTab.PICKS)
        }
        TabItem(Icons.Filled.Person, R.string.tab_my, current == AppTab.MY, onFeed, Modifier.weight(1f)) {
            onSelect(AppTab.MY)
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    labelRes: Int,
    selected: Boolean,
    onFeed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = when {
        selected -> DSColor.brand
        onFeed -> Color.White.copy(alpha = 0.6f)
        else -> DSColor.textTertiary
    }
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(stringResource(labelRes), color = tint, style = DSTypography.micro)
    }
}

/** 저장된 토큰으로 /users/me를 확인하는 동안의 화면. iOS `LaunchLoadingView`와 같은 자리. */
@Composable
private fun LaunchTitle() {
    Box(
        Modifier
            .fillMaxSize()
            .background(DSColor.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DSBrandMark(size = 56.dp)
            Text(
                "Dignify",
                style = DSTypography.title,
                color = DSColor.textPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

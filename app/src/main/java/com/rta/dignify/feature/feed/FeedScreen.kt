package com.rta.dignify.feature.feed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSFitOrScroll
import com.rta.dignify.core.designsystem.DSSearchBar
import com.rta.dignify.core.designsystem.DSShimmer
import com.rta.dignify.core.model.Feed
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.feature.artist.ArtistRequestSheet
import com.rta.dignify.core.share.ShareCardRenderer
import com.rta.dignify.core.share.TrackShareCard
import com.rta.dignify.core.share.loadArtwork
import com.rta.dignify.core.share.shareBitmap
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.feature.push.Push
import com.rta.dignify.feature.onboarding.COACH_PEEK
import com.rta.dignify.feature.onboarding.COACH_SWIPE_PERIOD_MS
import com.rta.dignify.feature.onboarding.COACH_TAP_PERIOD_MS
import com.rta.dignify.feature.onboarding.CoachGesture
import com.rta.dignify.feature.onboarding.CoachGestureStep
import com.rta.dignify.feature.onboarding.CoachSeen
import com.rta.dignify.feature.onboarding.coachPeekFraction
import com.rta.dignify.feature.onboarding.rememberReduceMotion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import com.rta.dignify.feature.push.PushOptInPopup
import kotlin.math.round
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 푸시 옵트인을 어느 맥락에서 물었는지. iOS와 같은 값이어야 소스별 수락률이 한 표에 모인다. */
private const val PUSH_SOURCE_CURATION = "curation_done"

/**
 * 스와이프 피드. iOS `FeedView`의 이식이되 페이징은 손으로 안 짠다 — iOS가 offset 애니메이션을
 * 직접 굴린 건 SwiftUI에 세로 페이저가 없어서였고, 여기선 `VerticalPager`가 그 역할을 한다.
 *
 * 픽 재생도 이 화면이 맡는다(iOS `FeedMode.pick`과 같은 판단) — 목록만 픽 상세로 갈아끼우고
 * `onSwipeOutOfRange`로 끝에서 나간다. 두 번째 플레이어는 만들지 않는다.
 */
@Composable
fun FeedScreen(
    /** 탭바가 위에 떠 있는 만큼의 여백. 배경은 탭바 뒤까지 그리되 카드·액션 행만 밀어 올린다. */
    bottomInset: Dp = 0.dp,
    /**
     * 픽 재생처럼 **끝에서 더 넘기면 나가는** 지면이 넘긴다. 일반 피드는 null —
     * 무한히 이어지는 목록이라 나갈 끝이 없다.
     */
    onSwipeOutOfRange: (() -> Unit)? = null,
    vm: FeedViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { FeedAudioController(context, scope) }

    var isSearching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // 상단 오버레이(검색 버튼 행 + 성향 칩 + 컨텍스트 배지)가 실제로 먹는 높이.
    // 카드는 이 아래에서 시작한다 — 고정값(64dp)으로 두면 칩이 두 줄이 되거나 글꼴을 키운
    // 기기에서 배지가 아트워크를 덮는다. 처음 한 프레임은 측정 전이라 옛 고정값을 바닥으로 쓴다.
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var topOverlay by remember { mutableStateOf(0.dp) }
    val cardTop = maxOf(topOverlay + 8.dp, statusTop + 64.dp)

    // 상세 시트는 trackId만 들고 있는다 — Feed를 그대로 붙잡으면 하입을 눌러도
    // 시트 안 아이콘이 옛 값에 머문다.
    var detailTrackId by remember { mutableStateOf<Int?>(null) }

    // 공유 카드 렌더 대상. 아트워크를 먼저 받아둬야 카드에 빈 자리가 안 찍힌다.
    var shareTarget by remember { mutableStateOf<Pair<Feed, ImageBitmap?>?>(null) }

    // 하입 버스트(어느 곡, 몇 번째). 회차를 같이 들고 다니는 건 같은 곡을 연달아 하입해도
    // 애니메이션이 처음부터 다시 돌게 하려는 것이다.
    var burst by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var burstSeq by remember { mutableIntStateOf(0) }

    // initialPage는 첫 구성에서만 쓰인다 = 탭에 다시 들어왔을 때 보던 자리로 복귀.
    val pagerState = rememberPagerState(initialPage = vm.lastPage) { vm.feeds.size }

    // 검색은 화면을 갈아끼우진 않지만 "나가야 하는 상태"다 — 뒤로가기가 앱을 닫으면 안 된다.
    // 검색창을 먼저 닫고, 그다음 눌러야 결과에서 전체 피드로 나간다(들어온 순서의 역순).
    BackHandler(enabled = isSearching || vm.activeQuery.isNotEmpty()) {
        if (isSearching) isSearching = false
        else scope.launch { pagerState.scrollToPage(vm.clearSearch()) }
    }

    // 카드와 상세 시트가 같은 하입 동작을 써야 해서 한 곳에 둔다.
    // 게스트는 **누르는 즉시** 로그인 화면이 뜬다 — 스낵바로 알리고 끝내면 유저가
    // 한 번 더 눌러야 하고, 그 사이 스낵바가 사라지면 아무 일도 안 일어난 게 된다.
    val hype: (Int) -> Unit = { trackId ->
        val turningOn = vm.feeds.any { it.trackId == trackId && !it.isHyped }
        if (Session.requireAccount() && vm.toggleHype(trackId) && turningOn) {
            burst = trackId to burstSeq++
        }
    }

    LaunchedEffect(Unit) {
        audio.onListen = { trackId -> vm.recordListen(trackId) }
        // 청취 임계값(5초) 튜닝용 원시 분포. track_listened는 임계값 통과 여부만 알려줘서
        // 임계선을 어디로 옮길지 못 본다. 소수점 한 자리까지만 — 그 아래는 노이즈다.
        //
        // `background`는 **발사 시점이 아니라 백그라운드 진입 시점**에 찍힌 값이다. 이유는
        // DwellTracker.markBackground 참고 — 주머니 체류 하나가 분포를 통째로 망가뜨린다.
        audio.onDwell = { trackId, seconds, hadBackground ->
            Analytics.capture(
                "track_dwell",
                mapOf(
                    "track_id" to trackId,
                    "seconds" to round(seconds * 10) / 10,
                    "background" to hadBackground,
                    "source" to vm.source(trackId),
                ),
            )
        }
        // track_viewed는 있는데 이게 없는 트랙 = 소리를 한 번도 못 들은 노출.
        // 백그라운드 건은 볼 화면이 없어 판독에서 거른다.
        audio.onPlaybackStart = { trackId, latencyMs ->
            Analytics.capture(
                "track_playback_started",
                mapOf(
                    "track_id" to trackId,
                    "latency_ms" to latencyMs,
                    "source" to vm.source(trackId),
                    "background" to AppForeground.isBackground,
                ),
            )
        }
        // 알림·잠금화면·이어폰·차량의 다음/이전과 백그라운드 자동 넘김이 전부 여기로 온다.
        // **화면 효과에 기대면 안 된다** — 백그라운드에선 재구성이 없어 `LaunchedEffect`가
        // 한 줄도 안 돈다. 그래서 윈도우 이동·노출 계측·페이징을 여기서 직접 부른다.
        //
        // 현재 위치를 pagerState가 아니라 vm.lastPage에서 읽는 것도 같은 이유다. 잠금화면에서
        // 연달아 넘기면 페이저는 프레임이 없어 아직 안 움직였고, settledPage로 재면 제자리를 맴돈다.
        // 발사 시점에 읽어야 한다 — 끝에서 붙은 페이지를 스냅샷은 못 본다.
        audio.hasNextTrack = { vm.lastPage + 1 in vm.feeds.indices }
        audio.onRemoteSeek = { delta ->
            val next = vm.lastPage + delta
            if (next in vm.feeds.indices) {
                vm.lastPage = next
                audio.updateWindow(vm.feeds, next)
                vm.onTrackViewed(next)
                vm.loadMoreIfNeeded(next)
                scope.launch { pagerState.scrollToPage(next) }
            }
        }
        // 화면 하입 버튼과 같은 함수로 간다. 갈라두면 낙관적 갱신·롤백·`track_hyped`가 두 벌이 된다.
        // 게스트면 아무 일도 안 일어난다 — 잠금화면에서 로그인을 띄울 방법이 없다.
        audio.onHype = { trackId ->
            if (vm.toggleHype(trackId)) {
                audio.syncHype(vm.feeds.any { it.trackId == trackId && it.isHyped })
            }
        }
        vm.loadInitial()
    }

    // 로그인·성향 변경처럼 피드 내용이 통째로 달라지는 일이 생기면 새로 받는다.
    // 게스트로 본 피드는 하입 표시가 안 된 상태라 로그인 후엔 반드시 다시 받아야 한다.
    LaunchedEffect(Session.feedReloadTick) { vm.onReloadTick(Session.feedReloadTick) }

    // 스와이프가 멎은 뒤에만 오디오 윈도우를 옮긴다. 드래그 중에 옮기면 지나가는 곡이
    // 전부 한 번씩 재생된다.
    LaunchedEffect(pagerState.settledPage, vm.feeds) {
        val page = pagerState.settledPage
        if (vm.feeds.isEmpty()) return@LaunchedEffect
        vm.lastPage = page      // 탭을 옮겼다 돌아왔을 때 복귀할 자리.
        audio.updateWindow(vm.feeds, page)
        vm.onTrackViewed(page)
        vm.loadMoreIfNeeded(page)
    }

    // 첫 장에서 위로(=이전으로), 마지막 장에서 아래로 더 밀면 화면을 나간다.
    // 페이저는 범위를 벗어나 못 가므로 **소비되지 않고 남는 세로 델타**로 판정한다.
    val edgeExit = remember(onSwipeOutOfRange, pagerState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (onSwipeOutOfRange == null) return Offset.Zero
                val atFirst = pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction <= 0f
                val atLast = pagerState.currentPage == (vm.feeds.size - 1).coerceAtLeast(0) &&
                    pagerState.currentPageOffsetFraction >= 0f
                // 아래로 당김(양수) = 이전, 위로 당김(음수) = 다음.
                if ((atFirst && available.y > EDGE_EXIT_PX) || (atLast && available.y < -EDGE_EXIT_PX)) {
                    onSwipeOutOfRange()
                }
                return Offset.Zero
            }
        }
    }

    // 세트를 완주한 순간 알림 권한을 묻는다. 허용이든 거부든 피드는 그대로라 흐름은 안 바뀌지만,
    // 시스템 창의 결과는 남긴다 — 우리 팝업 수락률과 실제 허용률의 차이가 문구 튜닝 근거다.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Analytics.capture(
            "push_permission_result",
            mapOf("granted" to granted, "source" to PUSH_SOURCE_CURATION),
        )
    }

    // 시스템 창을 바로 띄우지 않고 우리 팝업으로 먼저 맥락을 준다 — 자세한 이유는 PushOptInPopup 참고.
    var showPushOptIn by remember { mutableStateOf(false) }

    LaunchedEffect(vm.justFinishedSet) {
        if (!vm.justFinishedSet) return@LaunchedEffect
        vm.onSetCompletionHandled()
        if (Push.canAskNotificationPermission(context)) {
            Analytics.capture("push_optin_shown", mapOf("source" to PUSH_SOURCE_CURATION))
            showPushOptIn = true
        }
    }

    // 백그라운드에서도 재생이 이어진다 — 여기 있던 ON_PAUSE/ON_RESUME 핸들러를 걷어냈다.
    // 멈출지 말지는 [FeedAudioController]가 `ProcessLifecycleOwner`로 직접 본다. 화면 단위
    // 라이프사이클로는 판단할 수 없어서다: 기준은 어느 컴포저블이 resumed인지가 아니라
    // 어떤 재생 세션이 살아있는지고, 마이페이지 프리뷰는 여전히 멈춰야 한다.
    DisposableEffect(Unit) {
        onDispose { audio.stop() }
    }

    // 제스처 코치마크. 더블탭 → 하트 → 위로 쓸기를 **따라 해야** 넘어간다(CoachGesture 참고).
    // 픽 재생 지면과 검색 결과에선 안 띄운다 — 처음 보는 피드에서 익히는 동작이다.
    var seenCoach by remember { mutableStateOf(CoachSeen.get(context, CoachSeen.FEED)) }
    val showsCoach = !seenCoach && vm.pickNickname == null && vm.activeQuery.isEmpty() &&
        !vm.isLoading && vm.feeds.isNotEmpty() && Session.buildingFeedFrom == null
    var coachStep by rememberSaveable { mutableStateOf(CoachGestureStep.DOUBLE_TAP) }
    // 코치마크가 붙은 곡. 스와이프 단계 전엔 유저가 넘기면 따라간다 — 안 그러면 안내가 화면 밖 곡에 남는다.
    var coachPage by remember { mutableIntStateOf(0) }
    // 손과 스와이프 화면 힌트가 같이 보는 시계(한 주기 안의 ms).
    var coachClock by remember { mutableLongStateOf(0L) }
    // 화면 힌트로 곡이 올라가 있는 중인가. 이때는 끌린 거리로 코치마크를 흐리지 않는다.
    var coachPeeking by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val finishCoach = {
        Analytics.capture("coach_finished", mapOf("screen" to "feed", "last_step" to coachStep.ordinal))
        CoachSeen.mark(context, CoachSeen.FEED)
        seenCoach = true
    }

    LaunchedEffect(showsCoach) {
        if (!showsCoach) return@LaunchedEffect
        coachPage = pagerState.settledPage
        // 완주율의 분모. 기존 스포트라이트 코치마크와 같은 이벤트·같은 screen 값이다.
        Analytics.capture("coach_shown", mapOf("screen" to "feed"))
    }

    LaunchedEffect(pagerState.settledPage, showsCoach) {
        if (!showsCoach) return@LaunchedEffect
        if (coachStep != CoachGestureStep.SWIPE_UP) coachPage = pagerState.settledPage
        else if (pagerState.settledPage != coachPage) finishCoach()
    }

    // 손 모션 시계. 스와이프 단계에선 같은 프레임에 곡 화면도 COACH_PEEK만큼 올렸다 내린다 —
    // 따로 돌리면 박자가 어긋난다. 화면 힌트를 **실제 페이저 스크롤**로 내는 이유: 유저가 중간에
    // 잡으면 사용자 입력이 이 스크롤을 끊고 그 위치에서 그대로 끌기로 이어진다. 잡는 동안 시계가
    // 서므로 손도 멈추고, 놓아서 제자리로 돌아오면 둘 다 처음부터 다시 돈다.
    LaunchedEffect(showsCoach, coachStep, reduceMotion) {
        coachClock = 0L
        if (!showsCoach || reduceMotion || coachStep == CoachGestureStep.HYPED) return@LaunchedEffect
        if (coachStep == CoachGestureStep.DOUBLE_TAP) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { coachClock = (it - start) % COACH_TAP_PERIOD_MS }
        }
        val peekPx = with(density) { COACH_PEEK.toPx() }
        while (true) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
            // 놓은 자리가 다음 곡이면 끝이다(위 효과가 마무리한다). 새 곡에서 힌트를 또 돌리지 않는다.
            if (pagerState.settledPage != coachPage) return@LaunchedEffect
            try {
                pagerState.scroll {
                    coachPeeking = true
                    var applied = 0f
                    val start = withFrameMillis { it }
                    while (true) withFrameMillis {
                        coachClock = (it - start) % COACH_SWIPE_PERIOD_MS
                        applied += scrollBy(coachPeekFraction(coachClock) * peekPx - applied)
                    }
                }
            } catch (e: CancellationException) {
                coroutineContext.ensureActive()   // 이 효과 자체가 끝난 거면 그대로 나간다.
            } finally {
                coachPeeking = false
            }
        }
    }
    val coachAlpha = if (coachPeeking) 1f
    else (1f - abs(pagerState.currentPageOffsetFraction) * 4f).coerceIn(0f, 1f)

    // Scaffold를 안 쓴다 — 앱 바가 없어서 content padding이 늘 0이고, 그걸 무시하면
    // lint(UnusedMaterial3ScaffoldPaddingParameter)가 빌드를 세운다.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.isLoading && vm.feeds.isEmpty() -> LoadingSkeleton()
            vm.feeds.isEmpty() -> EmptyState(
                loadFailed = vm.loadFailed,
                query = vm.activeQuery,
                onRetry = { vm.retry(pagerState.currentPage) },
                onBack = { scope.launch { pagerState.scrollToPage(vm.clearSearch()) } },
            )

            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().nestedScroll(edgeExit),
            ) { page ->
                FeedPage(
                    feed = vm.feeds[page],
                    coach = if (showsCoach && page == coachPage) {
                        {
                            Box(Modifier.matchParentSize().alpha(coachAlpha)) {
                                CoachGesture(coachStep, coachClock, reduceMotion)
                            }
                        }
                    } else null,
                    onTap = { audio.toggleCurrentPlayback() },
                    onHype = { hype(vm.feeds[page].trackId) },
                    burstSeq = burst?.takeIf { it.first == vm.feeds[page].trackId }?.second,
                    onHypeAt = {
                        val trackId = vm.feeds[page].trackId
                        // 코치마크 첫 단계는 "실제로 하입해 보기"다. 게스트도 여기서만은 로그인 없이
                        // 하입된다 — 따라 해 보라고 해 놓고 시트로 막으면 안내가 끊긴다.
                        val coachTap = showsCoach && coachStep == CoachGestureStep.DOUBLE_TAP
                        val hyped = if (coachTap && Session.state == AuthState.GUEST) {
                            vm.hypeAsGuest(trackId)
                        } else {
                            // 그 밖의 게스트는 로그인 화면부터. 버스트를 먼저 터뜨리면 "됐다"는 착각을 준다.
                            Session.requireAccount() && vm.hypeOn(trackId)
                        }
                        if (hyped) {
                            burst = trackId to burstSeq++
                            // 이미 하입된 곡이어도 해 본 건 맞다.
                            if (coachTap) coachStep = CoachGestureStep.HYPED
                        }
                    },
                    bottomInset = bottomInset,
                    topInset = cardTop,
                    onShare = {
                        val feed = vm.feeds[page]
                        scope.launch {
                            shareTarget = feed to loadArtwork(context, feed.artworkUrl(600))
                        }
                    },
                    onDetail = {
                        // 상세는 인증 엔드포인트라 게스트가 열면 401만 본다.
                        if (Session.requireAccount()) detailTrackId = vm.feeds[page].trackId
                    },
                )
            }
        }

        // 하트 단계: 화면 아무 데나 닿으면 스와이프 단계로. 닿은 손가락은 먹는다 —
        // 흘려보내면 그 탭이 재생을 멈추거나 그대로 곡을 넘겨 다음 안내를 건너뛴다.
        if (showsCoach && coachStep == CoachGestureStep.HYPED) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            coachStep = CoachGestureStep.SWIPE_UP
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
        }

        // 일시정지 표시는 카드 위, 검색 UI 아래.
        AnimatedVisibility(
            visible = audio.isPaused && !isSearching && vm.feeds.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(72.dp),
            )
        }

        SearchControls(
            isSearching = isSearching,
            text = searchText,
            activeQuery = vm.activeQuery,
            onTextChange = { searchText = it },
            onOpen = { isSearching = true; searchText = vm.activeQuery; audio.pauseCurrent() },
            onSubmit = {
                isSearching = false
                // 목록이 통째로 검색 결과로 갈리므로 첫 장부터. 인덱스를 그대로 두면
                // 이전 피드 위치에 남아 엉뚱한 곡이 current가 된다.
                //
                // 이걸 `LaunchedEffect(vm.activeQuery)`로 하면 안 된다 — LaunchedEffect는
                // 키가 안 바뀌어도 **컴포지션에 처음 들어올 때 무조건 한 번 돈다.** 탭을
                // 옮기면 이 화면이 컴포지션에서 빠졌다 다시 들어오므로, 돌아올 때마다
                // 0번으로 되감겨 vm.lastPage 복원이 통째로 무효가 된다.
                if (searchText.isNotBlank()) {
                    vm.runSearch(searchText, pagerState.currentPage)
                    scope.launch { pagerState.scrollToPage(0) }
                }
            },
            onClearQuery = {
                searchText = ""
                scope.launch { pagerState.scrollToPage(vm.clearSearch()) }
            },
            // 픽 재생 지면엔 성향이란 게 없다 — 목록이 남이 고른 곡 묶음으로 고정돼 있다.
            // 재요청은 Session이 feedReloadTick을 올려서 일으키므로 여기선 오디오를 안 건드린다.
            onToggleMode = if (vm.pickNickname == null) {
                { scope.launch { Session.setDiggingMode(!Session.diggingMode, "feed") } }
            } else null,
            // 배지를 오버레이 안에 넣는다 — 오버레이 높이를 재서 카드를 그 아래로 내리므로,
            // 밖에 따로 띄우면 글꼴을 키운 기기에서 배지가 아트워크를 덮는다.
            badge = {
                // 픽 재생 중이면 특집 대신 픽 배지. 자리가 하나라 둘이 겹칠 일은 없다.
                val nickname = vm.pickNickname
                if (nickname != null) {
                    ContextBadge(
                        text = stringResource(
                            R.string.pick_playing_badge,
                            nickname,
                            pagerState.currentPage + 1,
                            vm.feeds.size,
                        ),
                        visible = !isSearching && vm.feeds.isNotEmpty(),
                    )
                } else {
                    CurationBadge(
                        index = pagerState.currentPage,
                        count = vm.curationCount,
                        // 검색 결과를 보는 중엔 숨긴다. 목록이 통째로 검색 결과인데
                        // "이번 주 특집 1/7"이 뜨면 앞 7곡이 특집인 것처럼 읽힌다.
                        visible = !isSearching && vm.activeQuery.isEmpty(),
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned {
                    topOverlay = with(density) { it.size.height.toDp() }
                },
        )


        detailTrackId?.let { id ->
            TrackDetailSheet(trackId = id, onDismiss = { detailTrackId = null })
        }

        // 공유 카드는 화면에 안 보이게 렌더된다(ShareCardRenderer 참고).
        shareTarget?.let { (feed, art) ->
            val chooser = stringResource(R.string.share)
            ShareCardRenderer(
                onRendered = { bmp ->
                    // 안드로이드는 애플뮤직이 아니라 유튜브뮤직 링크를 같이 보낸다.
                    shareBitmap(context, bmp, chooser, text = feed.youtubeMusicUrl())
                    shareTarget = null
                },
            ) {
                TrackShareCard(
                    artwork = art,
                    trackName = feed.trackName,
                    artistName = feed.artistName,
                    genreName = feed.genreName,
                )
            }
        }

        // 팝업은 최상단. 딤이 피드 제스처를 먹어야 뒤로 스와이프가 안 샌다.
        if (showPushOptIn) {
            PushOptInPopup(
                onAccept = {
                    Analytics.capture("push_optin_accepted", mapOf("source" to PUSH_SOURCE_CURATION))
                    showPushOptIn = false
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onDecline = {
                    Analytics.capture("push_optin_declined", mapOf("source" to PUSH_SOURCE_CURATION))
                    showPushOptIn = false
                },
            )
        }
    }
}

/**
 * 트랙 상세 바텀시트. iOS `TrackDetailView` 이식 — `GET /tracks/{id}`로 메타데이터와
 * 먼저 하입한 유저 최대 5명을 받는다. 카드에 없는 정보(앨범명·발매일·하입한 사람)가
 * 이 화면의 존재 이유다.
 *
 * iOS와 다른 점은 하나: 애플뮤직 버튼이 없다. 안드로이드 지면 유저가 쓰는 서비스가 아니다.
 *
 * 전체화면이 아니라 시트인 건 재생을 안 끊기 위해서다. 닫으면 그대로 스와이프로 복귀한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailSheet(trackId: Int, onDismiss: () -> Unit) {
    var detail by remember(trackId) { mutableStateOf<Api.TrackDetail?>(null) }
    var loadFailed by remember(trackId) { mutableStateOf(false) }
    var reloadTick by remember(trackId) { mutableIntStateOf(0) }

    LaunchedEffect(trackId, reloadTick) {
        loadFailed = false
        runCatching { Session.api.trackDetail(trackId) }
            .onSuccess { detail = it }
            .onFailure { loadFailed = true }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DSColor.background) {
        Box(
            Modifier
                .fillMaxWidth()
                // 제스처 바 위로 띄운다 — 마지막 버튼이 시스템 바에 붙으면 누르기 어렵다.
                .navigationBarsPadding()
                // iOS는 시트 높이를 콘텐츠 실측으로 고정한다. 여기선 하입 영역을 5행으로
                // 예약하는 것만 따라가고(아래 149.dp) 높이는 콘텐츠에 맡긴다.
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            when {
                detail != null -> TrackDetailContent(detail!!)
                loadFailed -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.track_detail_failed),
                        color = DSColor.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { reloadTick++ }) {
                        Text(stringResource(R.string.feed_retry), color = DSColor.brand)
                    }
                }

                else -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = DSColor.brand) }
            }
        }
    }
}

@Composable
private fun TrackDetailContent(d: Api.TrackDetail) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(d.artworkUrl.itunesArtworkUrl(200))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    d.trackName,
                    color = DSColor.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    d.artistName,
                    color = DSColor.textSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                d.collectionName?.let {
                    Text(
                        it,
                        color = DSColor.textTertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    d.genreName?.let { DetailBadge(it) }
                    // "2024-03-15T00:00:00Z" → "2024.03.15". 앞 10자만 쓰므로 파싱이 필요 없다.
                    d.releaseDate?.take(10)?.replace('-', '.')?.let { DetailBadge(it) }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = DSColor.borderLight)

        Text(
            stringResource(R.string.hyped_by),
            color = DSColor.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        // 5행 고정. 하입 수에 따라 시트 높이가 들썩이지 않게 iOS가 잡아둔 값을 그대로 쓴다.
        Box(Modifier.fillMaxWidth().height(149.dp)) {
            if (d.firstHypers.isEmpty()) {
                Text(
                    stringResource(R.string.no_hypes_yet),
                    color = DSColor.textTertiary,
                    fontSize = 14.sp,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    d.firstHypers.forEach { user ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("@${user.nickname}", color = DSColor.textPrimary, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            user.hypedAt?.let { at ->
                                Text(
                                    formatHypedAt(at),
                                    color = DSColor.textTertiary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = { openUrl(context, youtubeMusicUrl(d.artistName, d.trackName)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, DSColor.border, RoundedCornerShape(16.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_youtube_music),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.open_youtube_music),
                    color = DSColor.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String) {
    Text(
        text,
        color = DSColor.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .border(1.dp, DSColor.borderLight, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** ISO-8601 date-time → 기기 로케일의 짧은 날짜. 파싱 실패는 그냥 안 보여준다(장식 정보라). */
private fun formatHypedAt(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
}.getOrDefault("")

/** 끝에서 이만큼 더 당기면 화면을 나간다. 실수로 나가지 않을 만큼은 돼야 한다. */
private const val EDGE_EXIT_PX = 120f

/** 한 곡 = 한 페이지. 배경(흐린 아트워크) → 그라디언트 → 카드 순으로 쌓는다. */
@Composable
private fun FeedPage(
    feed: Feed,
    onTap: () -> Unit,
    onHype: () -> Unit,
    onHypeAt: () -> Unit,
    /** 이 곡에 지금 터뜨릴 하입 버스트 회차. 바뀔 때마다 처음부터 다시 돈다. */
    burstSeq: Int?,
    bottomInset: Dp,
    /** 상단 오버레이가 먹는 높이(상태바 포함). 카드는 이 아래에서 시작한다. */
    topInset: Dp,
    onShare: () -> Unit,
    onDetail: () -> Unit,
    /** 제스처 코치마크가 붙은 곡만 넘긴다. 아트워크 위에 얹힌다. */
    coach: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(feed.trackId) {
                detectTapGestures(
                    // 더블탭(하입)에 우선권을 주므로 단일 탭엔 두 번째 탭 대기 지연이 붙는다 — iOS와 같다.
                    // 터진 자리에 버스트를 띄워야 해서 좌표를 그대로 올린다.
                    onDoubleTap = { onHypeAt() },
                    onTap = { onTap() },
                )
            }
    ) {
        BackgroundArtwork(feed)

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.4f),
                        0.75f to Color.Black.copy(alpha = 0.85f),
                    )
                )
        )

        TrackCard(
            feed = feed,
            bottomInset = bottomInset,
            topInset = topInset,
            onHype = onHype,
            burstSeq = burstSeq,
            onShare = onShare,
            onDetail = onDetail,
            coach = coach,
        )
    }
}

/**
 * 흐린 배경. `Modifier.blur`는 API 31+에서만 도는데 minSdk가 26이라 못 쓴다.
 * 대신 아트워크를 32px로 받아 화면 크기로 늘린다 — 업스케일 자체가 블러라 전 버전에서 같이 보이고,
 * 디코딩할 비트맵도 작아진다.
 */
@Composable
private fun BackgroundArtwork(feed: Feed) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(feed.artworkUrl(100))
            .size(32)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }),
        modifier = Modifier.fillMaxSize(),
    )
    // iOS의 brightness(-0.3)에 해당. 검은 막을 덮는 쪽이 색을 안 뭉갠다.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
}

@Composable
private fun TrackCard(
    feed: Feed,
    bottomInset: Dp,
    topInset: Dp,
    onHype: () -> Unit,
    burstSeq: Int?,
    onShare: () -> Unit,
    onDetail: () -> Unit,
    coach: (@Composable BoxScope.() -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = maxWidth - 48.dp
        Column(
            Modifier
                .fillMaxSize()
                // statusBarsPadding을 안 쓴다 — topInset이 이미 상태바를 포함한다.
                // navigationBarsPadding도 안 쓴다 — bottomInset이 이미 탭바+내비바를 포함해서
                // 같이 걸면 제스처 바 높이만큼 두 번 밀린다.
                // 아래 44 = 정보 줄과 탭바 사이(1.1에서 20 올렸다 — iOS safe+72 → +92와 같은 폭).
                .padding(top = topInset, bottom = 44.dp + bottomInset),
        ) {
            // 아트워크가 남는 높이를 가져간다 — **곡명·아티스트 줄보다 나중에 잰다**는 게
            // 핵심이다. 예전엔 정사각형을 고정 크기로 두고 위아래를 Spacer(weight)로 밀었는데,
            // 그러면 높이가 모자랄 때 밀려나는 게 아래 텍스트 줄이었다. 글꼴을 키운 기기에서
            // 곡명과 아티스트가 통째로 사라지던 원인(실측: 480dpi/글꼴 2.0배).
            // zIndex: 코치마크 캡션이 아트워크 밑으로 삐져나와 아래 줄 위에 그려져야 한다.
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().zIndex(1f),
                contentAlignment = Alignment.Center,
            ) {
                // 아트워크 + 바로 아래 액션 줄(12 + 32)이 한 덩어리로 가운데에 선다.
                val art = minOf(cardWidth, maxHeight - ACTIONS_HEIGHT)
                Column(horizontalAlignment = Alignment.End) {
                    Box(Modifier.size(art)) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .shadow(24.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            DSShimmer(Modifier.fillMaxSize())
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(feed.artworkUrl(600))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = feed.trackName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        coach?.invoke(this)
                    }
                    // 공유 · 더보기. 아트워크 오른쪽 아래에 붙는다.
                    // 상세는 버튼으로 연다 — 탭은 재생 토글, 더블탭은 하입이라 남은 제스처가 없고
                    // 롱프레스로 숨기면 있는 줄도 모른다.
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TrackActionButton(Icons.Filled.Share, stringResource(R.string.share), onShare)
                        TrackActionButton(Icons.Filled.MoreHoriz, stringResource(R.string.track_detail), onDetail)
                    }
                }
            }

            // 하입은 정보 줄 오른쪽, 아래끝을 아티스트명 아래선에 맞춘다.
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "이 트랙이 왜 떴는지" 힌트. 무드로 뽑힌 곡이면 그 근거가 된 **내 하입 곡**을,
                    // 아니면(콜드스타트·무작위·검색) 종전대로 장르명을 띄운다.
                    //
                    // **자리를 하나만 쓴다.** 둘을 같이 띄우면 근거가 있는 카드와 없는 카드가 한
                    // 화면에 섞여 "얘만 왜 이유가 있지"가 된다.
                    val similar = feed.similarToTrackName
                    when {
                        similar != null -> ContextPill(stringResource(R.string.feels_like, similar))
                        feed.genreName != null -> ContextPill(feed.genreName)
                    }
                    Text(
                        feed.trackName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        feed.artistName,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // IconButton은 아이콘 아래로 10의 여백(48 − 28)/2가 있어서 그만큼 내려야 아래선이 맞는다.
                Box(Modifier.offset(y = 10.dp)) {
                    HypeButton(hyped = feed.isHyped, burstSeq = burstSeq, onClick = onHype)
                }
            }
        }
    }
}

/** 아트워크 아래 액션 줄이 먹는 높이(위 여백 12 + 탭 영역 32). */
private val ACTIONS_HEIGHT = 44.dp

/** 공유·더보기: 22 아이콘, 흰색 82%, 탭 영역 32(iOS `TrackActionButton`). */
@Composable
private fun TrackActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(22.dp))
    }
}

/**
 * 하입 버튼 + 버스트(디자인 레포 FeedTrack › 하입 버스트). 하입하는 순간 버튼이 1.35배로 팡 튀고
 * 조각 8개가 위로 튀었다가 아래로 우수수 떨어진다. 버튼·더블탭 둘 다 여기서 터진다.
 */
@Composable
private fun HypeButton(hyped: Boolean, burstSeq: Int?, onClick: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(burstSeq) {
        if (burstSeq == null || reduceMotion) return@LaunchedEffect
        // 0.16초에 1.35배, 0.36초에 제자리.
        pop.snapTo(1f)
        pop.animateTo(1.35f, tween(160, easing = CSS_EASE))
        pop.animateTo(1f, tween(200, easing = CSS_EASE))
    }
    // 조각이 버튼 밖으로 튀어야 해서 IconButton(제 모양으로 잘린다) 바깥 형제로 그린다.
    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            // 하트(1.1부터, 이전 삽). 삽만으론 무슨 버튼인지 안 읽혀서 「좋아요」로 읽히는 기본 하트로 바꿨다.
            // 하입한 하트도 빨강이 아니라 브랜드 색이다. 코드·API 이름(hype)은 그대로.
            Icon(
                if (hyped) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (hyped) R.string.unhype else R.string.hype),
                tint = if (hyped) DSColor.brand else DSColor.textTertiary,
                modifier = Modifier.size(28.dp).scale(pop.value),
            )
        }
        if (burstSeq != null && !reduceMotion) key(burstSeq) { BurstBits() }
    }
}

/** CSS 키프레임 기본 곡선(`ease`). */
private val CSS_EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val BIT_RISE = CubicBezierEasing(0.2f, 0.8f, 0.4f, 1f)
private val BIT_FALL = CubicBezierEasing(0.5f, 0f, 0.9f, 0.6f)

/** 조각 하나: 옆으로 dx, 솟는 높이, 회전, 크기. 시안 `BURST` 표 그대로. */
private class BurstBit(val dx: Float, val up: Float, val rotation: Float, val size: Float)

private val burstBits = listOf(
    BurstBit(-58f, 70f, -200f, 14f), BurstBit(-34f, 96f, 160f, 18f), BurstBit(-10f, 84f, -120f, 12f),
    BurstBit(14f, 104f, 220f, 20f), BurstBit(36f, 78f, -160f, 14f), BurstBit(58f, 92f, 140f, 16f),
    BurstBit(-46f, 52f, 260f, 12f), BurstBit(46f, 58f, -240f, 12f),
)

/**
 * 0.4초 동안 솟으며 커지고(0.3→1), 0.8초 동안 150 아래로 떨어지며 사라진다. 세 무리로 0.03초씩 엇갈린다.
 * 총 ≈1.2초. 끝나면 투명하게 남는다 — 다음 하입이 회차 키로 새로 그린다.
 */
@Composable
private fun BurstBits() {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1_300f, tween(1_300, easing = LinearEasing)) }
    burstBits.forEachIndexed { i, bit ->
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = DSColor.brand,
            modifier = Modifier
                .size(bit.size.dp)
                .graphicsLayer {
                    val t = clock.value - (i % 3) * 30f
                    val x: Float; val y: Float; val s: Float; val r: Float; val a: Float
                    if (t < 400f) {
                        val f = BIT_RISE.transform((t / 400f).coerceIn(0f, 1f))
                        x = bit.dx * .7f * f; y = -bit.up * f; s = .3f + .7f * f; r = bit.rotation * .3f * f
                        a = if (t < 100f) BIT_RISE.transform((t / 100f).coerceIn(0f, 1f)) else 1f
                    } else {
                        val f = BIT_FALL.transform(((t - 400f) / 800f).coerceIn(0f, 1f))
                        x = bit.dx * .7f + bit.dx * .3f * f
                        y = -bit.up + (150f + bit.up) * f
                        s = 1f - .1f * f
                        r = bit.rotation * .3f + bit.rotation * .7f * f
                        a = 1f - f
                    }
                    translationX = x.dp.toPx(); translationY = y.dp.toPx()
                    scaleX = s; scaleY = s; rotationZ = r; alpha = a
                },
        )
    }
}

/** 카드 좌하단의 근거 칩. 하입 곡 이름이거나 장르명이거나, **둘 중 하나만** 온다. */
@Composable
private fun ContextPill(label: String) {
    Text(
        label,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * 지금 무엇을 보고 있는지 알려주는 배지. 세트를 통째로 받았다는 감각을 이 배지와 곡 수만으로
 * 낸다 — 리스트 화면을 만들면 "뭘 누를까" 결정 비용이 생겨 스와이프 경험과 충돌한다.
 */
@Composable
private fun CurationBadge(index: Int, count: Int, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible || count <= 0 || index >= count) return
    ContextBadge(stringResource(R.string.curation_badge, index + 1, count), true, modifier)
}

/**
 * 지금 무엇을 보고 있는지 알려주는 배지. 특집 세트와 픽 재생이 **같은 자리**를 쓴다
 * (iOS `contextBadge`) — 두 상태가 동시에 성립하지 않으므로 자리를 나눌 이유가 없다.
 *
 * **자기 위치를 스스로 정하지 않는다.** 부르는 쪽(`SearchControls`의 badge 슬롯)이 버튼 줄
 * 아래 safe+56에 붙여 준다.
 *
 * 탭을 안 먹는다. 배지는 나가는 문이 아니라 표시라, 눌리면 뒤 카드의 재생 토글을 가로챈다.
 */
@Composable
private fun ContextBadge(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * 접힌 상태(돋보기 버튼) → 탭하면 펼쳐지는 검색. 검색 결과를 보는 중엔 활성 쿼리 칩이 남고,
 * 칩을 누르면 원래 피드로 돌아간다.
 * ponytail: 최근 검색 패널은 뺐다 — 검색 자체가 얼마나 쓰이는지 아직 계측이 없다.
 */
@Composable
private fun SearchControls(
    isSearching: Boolean,
    text: String,
    activeQuery: String,
    onTextChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    /** null이면 성향 버튼·칩을 아예 안 그린다(픽 재생 지면). */
    onToggleMode: (() -> Unit)? = null,
    /** 버튼 줄 아래 가운데(safe+56)에 붙는 컨텍스트 배지. 없으면 자리를 안 먹는다. */
    badge: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 한 줄(safe+8, 좌우 16): 왼쪽 성향 버튼 + 바로 옆 모드 알약(검색 결과면 쿼리 알약), 오른쪽 끝 검색.
    // 배지는 그 아래 가운데 safe+56에 고정 — 줄 높이에 안 딸려 다녀야 곡을 넘겨도 안 흔들린다.
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        if (isSearching) {
            DSSearchBar(
                text = text,
                onTextChange = onTextChange,
                placeholder = stringResource(R.string.search_placeholder),
                // 키보드 내리기는 DSSearchBar가 한다(세 검색창이 같은 규칙을 쓴다).
                onSubmit = onSubmit,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            )
        } else {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 성향 버튼을 피드 안에 두는 이유는 **효과가 보이는 자리가 여기뿐**이기 때문이다.
                // 설정 화면에서 끄면 무엇이 달라졌는지 기억으로 비교해야 한다.
                if (onToggleMode != null) {
                    IconButton(onClick = onToggleMode, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (Session.diggingMode) Icons.Filled.AutoAwesome else Icons.Filled.Shuffle,
                            contentDescription = stringResource(
                                if (Session.diggingMode) R.string.feed_mode_following else R.string.feed_mode_random
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                if (activeQuery.isNotEmpty()) {
                    // iOS와 같이 **칩 전체가 버튼**이다. 예전엔 X 아이콘에 clickable이 없어서 탭이
                    // 뒤 피드로 새고 재생만 멈췄다 — 닫기가 안 먹던 원인.
                    // "전체 피드로 돌아가기"는 여기 두지 않는다. iOS는 검색 결과가 비었을 때만
                    // (EmptyState) 내보내고, 결과가 있을 땐 이 칩이 나가는 문이다.
                    Row(
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(50))
                            .background(DSColor.brand)
                            .clickable(onClick = onClearQuery)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            activeQuery,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.feed_back),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else if (onToggleMode != null) {
                    // 지금 피드가 무엇으로 만들어졌는지를 **항상** 띄운다. 껐을 때만 띄우면 켜진 상태가
                    // 무명(無名)이라, 유저가 이 버튼이 무엇을 바꾸는지 눌러 보기 전엔 알 수 없다.
                    // 문구는 설정 이름("켜짐/꺼짐")이 아니라 지금 보고 있는 것을 말한다.
                    // 폭을 나눠 갖지 않는다 — 뒤 Spacer와 weight를 같이 걸면 남는 폭을 반씩 나눠서
                    // 「좋아요한 곡 따라가는 중」이 말줄임으로 잘렸다.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .clickable(onClick = onToggleMode)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(
                                if (Session.diggingMode) R.string.feed_mode_following else R.string.feed_mode_random
                            ),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            content = { badge() },
        )
    }
}

/** 실제 카드 레이아웃(검은 배경 + 중앙 아트워크)과 맞춰 데이터 도착 시 전환이 안 튀게 한다. */
@Composable
private fun LoadingSkeleton() {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        DSShimmer(
            Modifier
                .width(maxWidth - 48.dp)
                .height(maxWidth - 48.dp)
                .clip(RoundedCornerShape(24.dp))
        )
    }
}

@Composable
private fun EmptyState(
    loadFailed: Boolean,
    query: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    // 상단 검색·성향 칩이 이 위에 떠 있다. safeDrawingPadding이 없으면 문구가 상태바
    // 아래로 파고들고, 스크롤이 없으면 글꼴을 키웠을 때 재시도 버튼이 화면 밖으로 나간다.
    DSFitOrScroll(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 96.dp),
    ) {
        Text(
            when {
                loadFailed -> stringResource(R.string.feed_load_failed)
                query.isNotEmpty() -> stringResource(R.string.feed_no_results, query)
                else -> stringResource(R.string.feed_empty)
            },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (loadFailed) {
            TextButton(onRetry) {
                Text(stringResource(R.string.feed_retry), color = DSColor.brand)
            }
        } else if (query.isNotEmpty()) {
            // 찾다가 없다는 걸 안 순간이 아티스트를 요청하기 가장 자연스러운 지점이다.
            // 인라인으로 바로 보내지 않고 **시트를 띄운다** — 요청 히스토리에서 여는 것과
            // 같은 화면이라, 무엇을 보내는지 확인하고 이름을 고칠 수 있다.
            var showRequest by remember(query) { mutableStateOf(false) }
            TextButton(onClick = { showRequest = true }) {
                Text(stringResource(R.string.artist_request_cta, query), color = DSColor.brand)
            }
            TextButton(onBack) {
                Text(stringResource(R.string.feed_back), color = DSColor.textTertiary)
            }
            if (showRequest) {
                ArtistRequestSheet(prefill = query, onDismiss = { showRequest = false })
            }
        }
    }
}

/**
 * 유튜브뮤직 검색 URL. 서버가 트랙별 유튜브뮤직 ID를 안 들고 있어서 곡으로 직행하는 링크를
 * 만들 수 없다 — 아티스트+곡명 검색이 차선이다. 서버가 ID를 주기 시작하면 이 함수만 바뀐다.
 */
private fun youtubeMusicUrl(artistName: String, trackName: String): String =
    "https://music.youtube.com/search?q=${Uri.encode("$artistName $trackName")}"

private fun Feed.youtubeMusicUrl(): String = youtubeMusicUrl(artistName, trackName)

/** 링크를 외부 앱(유튜브뮤직 설치돼 있으면 그쪽)으로 넘긴다. */


private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

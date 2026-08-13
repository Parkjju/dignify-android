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
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Album
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
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
import com.rta.dignify.feature.push.PushOptInPopup
import kotlinx.coroutines.delay
import kotlin.math.round
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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

    // 상세 시트는 trackId만 들고 있는다 — Feed를 그대로 붙잡으면 하입을 눌러도
    // 시트 안 아이콘이 옛 값에 머문다.
    var detailTrackId by remember { mutableStateOf<Int?>(null) }

    // 더블탭 버스트. seq를 같이 들고 다니는 건 같은 자리를 연달아 두드려도 애니메이션이
    // 처음부터 다시 돌게 하려는 것이다(좌표만 키로 쓰면 두 번째 탭이 무시된다).
    // 공유 카드 렌더 대상. 아트워크를 먼저 받아둬야 카드에 빈 자리가 안 찍힌다.
    var shareTarget by remember { mutableStateOf<Pair<Feed, ImageBitmap?>?>(null) }

    var burst by remember { mutableStateOf<BurstState?>(null) }
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
        if (Session.requireAccount()) vm.toggleHype(trackId)
    }

    LaunchedEffect(Unit) {
        audio.onListen = { trackId -> vm.recordListen(trackId) }
        // 청취 임계값(5초) 튜닝용 원시 분포. track_listened는 임계값 통과 여부만 알려줘서
        // 임계선을 어디로 옮길지 못 본다. 소수점 한 자리까지만 — 그 아래는 노이즈다.
        audio.onDwell = { trackId, seconds ->
            Analytics.capture(
                "track_dwell",
                mapOf("track_id" to trackId, "seconds" to round(seconds * 10) / 10),
            )
        }
        vm.loadInitial()
    }

    // 로그인·장르 변경처럼 피드 내용이 통째로 달라지는 일이 생기면 새로 받는다.
    // 게스트로 본 피드는 하입 표시가 안 된 상태라 로그인 후엔 반드시 다시 받아야 한다.
    LaunchedEffect(Session.feedReloadTick) { vm.onReloadTick(Session.feedReloadTick) }

    // 목록이 통째로 바뀌면(검색 확정/해제) 첫 곡부터. 인덱스만 그대로 두면 이전 피드
    // 위치에 남아 엉뚱한 곡이 current가 된다.
    LaunchedEffect(vm.activeQuery) {
        if (vm.feeds.isNotEmpty()) pagerState.scrollToPage(0)
    }

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

    // 백그라운드로 나가면 멈추고 돌아오면 잇는다. 유저가 직접 멈춘 상태는 건드리지 않는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> audio.pauseCurrent()
                Lifecycle.Event.ON_RESUME -> audio.resumeCurrent()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.stop()
        }
    }

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
                    onTap = { audio.toggleCurrentPlayback() },
                    onHype = { hype(vm.feeds[page].trackId) },
                    onHypeAt = { offset ->
                        // 게스트면 로그인 화면부터. 버스트를 먼저 터뜨리면 "됐다"는 착각을 준다.
                        if (Session.requireAccount() && vm.hypeOn(vm.feeds[page].trackId)) {
                            burst = BurstState(offset, makeConfetti(), burstSeq++)
                        }
                    },
                    bottomInset = bottomInset,
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

        // 하입 버스트는 카드 위, 검색 UI 아래. iOS와 같이 0.65초 뒤 스스로 사라진다.
        burst?.let { state ->
            key(state.seq) {
                HypeBurst(
                    pieces = state.pieces,
                    // iOS `.position()`은 중앙을 맞춘다. offset은 좌상단을 맞추므로 반쪽을 뺀다.
                    modifier = Modifier.offset {
                        val half = (burstSize / 2).toPx()
                        IntOffset(
                            (state.at.x - half).roundToInt(),
                            (state.at.y - half).roundToInt(),
                        )
                    },
                )
                LaunchedEffect(state.seq) {
                    delay(650)
                    if (burst?.seq == state.seq) burst = null
                }
            }
        }

        // 픽 재생 중이면 특집 대신 픽 배지. 같은 자리를 쓰므로 둘이 겹칠 일은 없다.
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
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        CurationBadge(
            index = pagerState.currentPage,
            count = vm.curationCount,
            // 검색 결과를 보는 중엔 숨긴다. 목록이 통째로 검색 결과인데 "이번 주 특집 1/7"이
            // 뜨면 앞 7곡이 특집인 것처럼 읽힌다.
            // ⚠️ iOS도 같은 버그가 있다(FeedView.swift:540 — 조건에 activeQuery가 없고
            //    loadSearch가 curationCount를 안 지운다). 그쪽도 같이 고쳐야 한다.
            visible = !isSearching && vm.activeQuery.isEmpty(),
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SearchControls(
            isSearching = isSearching,
            text = searchText,
            activeQuery = vm.activeQuery,
            onTextChange = { searchText = it },
            onOpen = { isSearching = true; searchText = vm.activeQuery; audio.pauseCurrent() },
            onSubmit = {
                isSearching = false
                vm.runSearch(searchText, pagerState.currentPage)
            },
            onClearQuery = {
                searchText = ""
                scope.launch { pagerState.scrollToPage(vm.clearSearch()) }
            },
            modifier = Modifier.align(Alignment.TopEnd),
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

/**
 * iOS `FeedView.HypeBurstView` 이식. 더블탭한 자리에서 색종이가 터지고 삽 아이콘이
 * 튀어올랐다 사라진다. 타이밍·개수·거리를 iOS 값 그대로 쓴다 — 두 앱의 손맛이 갈리면
 * 같은 제품으로 안 느껴지는 부분이 이런 곳이다.
 */
private data class ConfettiPiece(val color: Color, val angle: Float, val distance: Float)

/** 터진 자리·조각·회차. 회차가 바뀌면 애니메이션이 처음부터 다시 돈다. */
private data class BurstState(val at: Offset, val pieces: List<ConfettiPiece>, val seq: Int)

/** iOS의 `[DSColor.brand, .yellow, .pink, .mint, .orange]`. 뒤 넷은 SwiftUI 시스템 색 실측값. */
private val confettiColors = listOf(
    DSColor.brand,
    Color(0xFFFFCC00),
    Color(0xFFFF2D55),
    Color(0xFF00C7BE),
    Color(0xFFFF9500),
)

private fun makeConfetti(count: Int = 14) = List(count) {
    ConfettiPiece(
        color = confettiColors.random(),
        angle = Random.nextFloat() * 360f,
        distance = 40f + Random.nextFloat() * 50f,
    )
}

/** 아이콘 한 변. 버스트를 탭 지점 중앙에 놓으려면 이 값의 절반을 빼야 한다. */
private val burstSize = 96.dp

/** 끝에서 이만큼 더 당기면 화면을 나간다. 실수로 나가지 않을 만큼은 돼야 한다. */
private const val EDGE_EXIT_PX = 120f

@Composable
private fun HypeBurst(pieces: List<ConfettiPiece>, modifier: Modifier = Modifier) {
    val spark = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { spark.animateTo(1f, tween(220, easing = EaseOut)) }
        launch {
            // response 0.28s → ω=2π/0.28 → stiffness≈ω²≈500. dampingFraction은 그대로 0.55.
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
        launch { alpha.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
        delay(300)
        launch { scale.animateTo(0.4f, tween(350, easing = EaseIn)) }
        alpha.animateTo(0f, tween(350, easing = EaseIn))
    }

    Box(modifier.size(burstSize), contentAlignment = Alignment.Center) {
        pieces.forEach { piece ->
            val rad = piece.angle * PI.toFloat() / 180f
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (cos(rad) * piece.distance * spark.value).roundToInt(),
                            (sin(rad) * piece.distance * spark.value).roundToInt(),
                        )
                    }
                    .rotate(piece.angle)
                    .size(width = 3.dp, height = 10.dp)
                    .alpha(1f - spark.value)
                    .clip(CircleShape)
                    .background(piece.color)
            )
        }
        Icon(
            painterResource(R.drawable.ic_hype),
            contentDescription = null,
            tint = DSColor.brand,
            modifier = Modifier
                .size(96.dp)
                .scale(scale.value)
                .alpha(alpha.value),
        )
    }
}

/** 한 곡 = 한 페이지. 배경(흐린 아트워크) → 그라디언트 → 카드 순으로 쌓는다. */
@Composable
private fun FeedPage(
    feed: Feed,
    onTap: () -> Unit,
    onHype: () -> Unit,
    onHypeAt: (Offset) -> Unit,
    bottomInset: Dp,
    onShare: () -> Unit,
    onDetail: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(feed.trackId) {
                detectTapGestures(
                    // 더블탭(하입)에 우선권을 주므로 단일 탭엔 두 번째 탭 대기 지연이 붙는다 — iOS와 같다.
                    // 터진 자리에 버스트를 띄워야 해서 좌표를 그대로 올린다.
                    onDoubleTap = { offset -> onHypeAt(offset) },
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

        TrackCard(feed = feed, bottomInset = bottomInset, onHype = onHype, onShare = onShare, onDetail = onDetail)
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
    onHype: () -> Unit,
    onShare: () -> Unit,
    onDetail: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = maxWidth - 48.dp
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // navigationBarsPadding을 안 쓴다 — bottomInset이 이미 탭바+내비바를 포함해서
                // 같이 걸면 제스처 바 높이만큼 두 번 밀린다.
                .padding(top = 64.dp, bottom = 24.dp + bottomInset),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(cardWidth)
                    .aspectRatio(1f)
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
            Spacer(Modifier.weight(1f))

            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // "이 트랙이 왜 떴는지" 힌트. 유저가 고른 장르로 피드가 구성되므로 장르명이 곧 근거다.
                    feed.genreName?.let { GenrePill(it) }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onHype) {
                        // 삽 = 디깅. iOS `Assets.xcassets/HypeIcon`을 그대로 가져온 것이라
                        // 두 앱의 하입 아이콘이 물리적으로 같은 파일이다.
                        Icon(
                            painterResource(R.drawable.ic_hype),
                            contentDescription = stringResource(if (feed.isHyped) R.string.unhype else R.string.hype),
                            tint = if (feed.isHyped) DSColor.brand else DSColor.textTertiary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 상세는 버튼으로 연다 — 탭은 재생 토글, 더블탭은 하입이라 남은 제스처가 없고
                    // 롱프레스로 숨기면 있는 줄도 모른다.
                    IconButton(onClick = onDetail) {
                        Icon(
                            // iOS `opticaldisc`. 정보 아이콘이 아니라 판이다 — 디깅 은유와 붙는다.
                            Icons.Outlined.Album,
                            contentDescription = stringResource(R.string.track_detail),
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenrePill(genreName: String) {
    Text(
        genreName,
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
 * 탭을 안 먹는다. 배지는 나가는 문이 아니라 표시라, 눌리면 뒤 카드의 재생 토글을 가로챈다.
 */
@Composable
private fun ContextBadge(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(modifier.statusBarsPadding().padding(top = 56.dp)) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
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
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (isSearching) {
            DSSearchBar(
                text = text,
                onTextChange = onTextChange,
                placeholder = stringResource(R.string.search_placeholder),
                onSubmit = {
                    keyboard?.hide()
                    onSubmit()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            IconButton(onClick = onOpen) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = Color.White,
                )
            }
        }

        if (activeQuery.isNotEmpty() && !isSearching) {
            // iOS와 같이 **칩 전체가 버튼**이다. 예전엔 X 아이콘에 clickable이 없어서 탭이
            // 뒤 피드로 새고 재생만 멈췄다 — 닫기가 안 먹던 원인.
            // "전체 피드로 돌아가기"는 여기 두지 않는다. iOS는 검색 결과가 비었을 때만
            // (EmptyState) 내보내고, 결과가 있을 땐 이 칩이 나가는 문이다.
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DSColor.brand)
                    .clickable(onClick = onClearQuery)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(activeQuery, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.feed_back),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
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

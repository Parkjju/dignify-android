package com.rta.dignify.feature.feed

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSShimmer
import com.rta.dignify.core.model.Feed
import kotlinx.coroutines.launch

/**
 * 스와이프 피드. iOS `FeedView`의 이식이되 페이징은 손으로 안 짠다 — iOS가 offset 애니메이션을
 * 직접 굴린 건 SwiftUI에 세로 페이저가 없어서였고, 여기선 `VerticalPager`가 그 역할을 한다.
 *
 * ponytail: 픽 재생 모드(`FeedMode.pick`)가 없다. 픽 화면 자체가 아직 없어서 —
 * 생기면 iOS처럼 이 화면을 모드로 갈라 쓴다(두 번째 플레이어를 만들지 않는다).
 */
@Composable
fun FeedScreen(vm: FeedViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val audio = remember { FeedAudioController(context, scope) }

    var isSearching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val pagerState = rememberPagerState { vm.feeds.size }

    LaunchedEffect(Unit) {
        audio.onListen = { trackId -> vm.recordListen(trackId) }
        vm.loadInitial()
    }

    // 로그인·장르 변경처럼 피드 내용이 통째로 달라지는 일이 생기면 새로 받는다.
    // 게스트로 본 피드는 하입 표시가 안 된 상태라 로그인 후엔 반드시 다시 받아야 한다.
    LaunchedEffect(Session.feedReloadTick) {
        if (Session.feedReloadTick > 0) vm.loadInitial(force = true)
    }

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
        audio.updateWindow(vm.feeds, page)
        vm.onTrackViewed(page)
        vm.loadMoreIfNeeded(page)
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
    // lint(UnusedMaterial3ScaffoldPaddingParameter)가 빌드를 세운다. 스낵바 하나 띄우려고
    // 쓰던 거라 SnackbarHost만 직접 얹는 게 맞다.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.isLoading && vm.feeds.isEmpty() -> LoadingSkeleton()
            vm.feeds.isEmpty() -> EmptyState(
                loadFailed = vm.loadFailed,
                query = vm.activeQuery,
                onRetry = { vm.retry(pagerState.currentPage) },
                onBack = { scope.launch { pagerState.scrollToPage(vm.clearSearch()) } },
            )

            else -> VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                FeedPage(
                    feed = vm.feeds[page],
                    onTap = { audio.toggleCurrentPlayback() },
                    onHype = {
                        // 게스트면 아무것도 안 바뀌고 false가 온다 → 그때만 로그인을 권한다.
                        if (!vm.toggleHype(vm.feeds[page].trackId)) {
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = context.getString(R.string.hype_needs_signin),
                                    actionLabel = context.getString(R.string.signin_action),
                                )
                                if (result == SnackbarResult.ActionPerformed) Session.requestSignIn()
                            }
                        }
                    },
                    onShare = { shareTrack(context, vm.feeds[page]) },
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

        CurationBadge(
            index = pagerState.currentPage,
            count = vm.curationCount,
            visible = !isSearching,
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

        // 스낵바는 시스템 내비게이션 바 위로. edge-to-edge라 안 밀면 제스처 바에 깔린다.
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

/** 한 곡 = 한 페이지. 배경(흐린 아트워크) → 그라디언트 → 카드 순으로 쌓는다. */
@Composable
private fun FeedPage(
    feed: Feed,
    onTap: () -> Unit,
    onHype: () -> Unit,
    onShare: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(feed.trackId) {
                detectTapGestures(
                    // 더블탭(하입)에 우선권을 주므로 단일 탭엔 두 번째 탭 대기 지연이 붙는다 — iOS와 같다.
                    onDoubleTap = { onHype() },
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

        TrackCard(feed = feed, onHype = onHype, onShare = onShare)
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
private fun TrackCard(feed: Feed, onHype: () -> Unit, onShare: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = maxWidth - 48.dp
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp, bottom = 24.dp),
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
                        // ponytail: iOS는 전용 HypeIcon 애셋을 쓴다. 그 asset을 안드로이드로 내보내기
                        // 전까지 불꽃 아이콘으로 대신한다(픽 반응도 🔥라 뜻은 통한다).
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = stringResource(if (feed.isHyped) R.string.unhype else R.string.hype),
                            tint = if (feed.isHyped) DSColor.brand else DSColor.textTertiary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
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
    Box(modifier.statusBarsPadding().padding(top = 56.dp)) {
        Text(
            stringResource(R.string.curation_badge, index + 1, count),
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
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
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
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DSColor.brand)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(activeQuery, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape),
                )
            }
            TextButton(onClick = onClearQuery) {
                Text(stringResource(R.string.feed_back), color = Color.White)
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
            TextButton(onBack) {
                Text(stringResource(R.string.feed_back), color = DSColor.textTertiary)
            }
        }
    }
}

/**
 * ponytail: iOS는 dignify 아이덴티티 카드를 렌더해서 공유한다. 여기선 iTunes 링크만 —
 * 카드 렌더는 인스타 유입이 안드로이드에서도 도는 게 확인된 뒤에 붙인다.
 */
private fun shareTrack(context: android.content.Context, feed: Feed) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${feed.trackName} — ${feed.artistName}\n${feed.trackViewUrl}")
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

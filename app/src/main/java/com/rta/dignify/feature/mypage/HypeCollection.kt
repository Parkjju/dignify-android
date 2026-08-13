package com.rta.dignify.feature.mypage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.core.designsystem.ScreenScaffold
import com.rta.dignify.feature.feed.FeedAudioController
import com.rta.dignify.feature.feed.TrackDetailSheet
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 하입 트랙을 날짜별 가로 스크롤로 그리는 재사용 컴포넌트. iOS `HypeCollection` 이식.
 *
 * 디깅 프로필(최근 3일 미리보기)과 하입 기록 화면(전체)이 **같은 걸 쓴다** — 셀 탭 재생,
 * 롱프레스 액션(상세·제거), 페이지네이션 규칙이 두 지면에서 갈리면 안 된다.
 *
 * @param maxGroups null이면 전체, 값이 있으면 최근 N개 날짜 그룹만(미리보기).
 * @param perDayLimit null이면 날짜당 전체, 값이 있으면 앞 N개만.
 * @param onReachEnd 마지막 셀이 보이면 호출(페이지네이션). maxGroups가 있으면 호출하지 않는다.
 * @param onReloadNeeded 하입 제거가 하드 실패해 목록 재동기화가 필요할 때.
 * @param onSeeAll 미리보기에서 See all 셀을 누르면(전체 화면 이동).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HypeCollection(
    items: List<Api.HypeItem>,
    onItemsChange: (List<Api.HypeItem>) -> Unit,
    maxGroups: Int? = null,
    perDayLimit: Int? = null,
    onReachEnd: (suspend () -> Unit)? = null,
    onReloadNeeded: (suspend () -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { FeedAudioController(context, scope) }

    var actionTarget by remember { mutableStateOf<Api.HypeItem?>(null) }
    var detailTrackId by remember { mutableStateOf<Int?>(null) }

    // 화면을 벗어나면 소리를 끊는다. 목록에서 재생한 곡이 다른 탭까지 따라가면 안 된다.
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    val groups = HypeGrouping.dayGroups(items, maxGroups, perDayLimit)
    val anchor = HypeGrouping.pagingAnchor(items)

    Column(Modifier.fillMaxWidth()) {
        groups.forEach { group ->
            Text(
                group.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DSColor.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            val listState = rememberLazyListState()
            val trackCount = group.tracks.size

            // iOS `fits` 판정의 이식: 트랙들이 뷰포트 안에 다 들어오면 See all도 당김 제스처도 없다.
            //
            // ⚠️ **스크롤 위치에 의존하면 안 된다.** "마지막 트랙이 뷰포트 안에 있는가"로 재면
            // 행을 끝까지 밀었을 때 당연히 참이 되어, 정작 셀을 봐야 할 순간에 사라진다.
            // iOS는 tracksWidth(콘텐츠 고유 폭)를 재므로 위치와 무관하다 — 그걸 그대로 따른다.
            //
            // 재는 대상은 **트랙 셀만**이다. See all까지 넣고 재면 그 셀 때문에 넘치고,
            // 넘쳐서 셀을 그리고, 그리니까 또 넘치는 순환이 생긴다.
            val overflows by remember(trackCount) {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val vis = info.visibleItemsInfo
                    // 아직 배치 전 — iOS도 tracksWidth가 0이면 fits가 false다(= 넘친다고 본다).
                    if (vis.isEmpty()) return@derivedStateOf true
                    val first = vis.firstOrNull { it.index == 0 }
                    val last = vis.firstOrNull { it.index == trackCount - 1 }
                    // 양 끝 중 하나라도 화면 밖 = 트랙이 뷰포트보다 넓다는 뜻.
                    if (first == null || last == null) return@derivedStateOf true
                    val tracksWidth = (last.offset + last.size) - first.offset
                    val available = info.viewportEndOffset - info.viewportStartOffset -
                        info.beforeContentPadding - info.afterContentPadding
                    tracksWidth > available
                }
            }
            val showsSeeAll = onSeeAll != null && overflows

            // 오른쪽 끝을 넘어 당긴 양. 리빌 애니메이션과 놓을 때 판정에 쓴다.
            var reveal by remember { mutableFloatStateOf(0f) }
            val threshold = with(LocalDensity.current) { 64.dp.toPx() }

            // iOS는 스크롤 관측 API로 오버스크롤 양을 잰다. Compose엔 대응 API가 없고
            // nestedScroll로 남는 델타를 받는 방식은 기본 오버스크롤 효과와 섞여 신뢰할 수 없었다.
            // 그래서 **포인터 이벤트를 직접 읽는다** — Final 패스라 LazyRow는 자기 스크롤을
            // 그대로 처리하고, 여기선 관찰만 한다(소비하지 않는다).
            val dragWatcher = Modifier.pointerInput(showsSeeAll) {
                if (!showsSeeAll) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    var acc = 0f
                    var peakLocal = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        // 오른쪽 끝에 닿아 더 못 가는 상태에서의 좌측 이동만 오버스크롤이다.
                        if (!listState.canScrollForward) {
                            val dx = change.position.x - change.previousPosition.x
                            acc = (acc - dx).coerceAtLeast(0f)
                            peakLocal = maxOf(peakLocal, acc)
                            reveal = acc
                        }
                    }
                    // 손을 뗀 순간 최대 당김이 임계를 넘었으면 이동.
                    if (peakLocal > threshold) onSeeAll?.invoke()
                    reveal = 0f
                }
            }

            // 기본 stretch 오버스크롤을 끈다. 끝에서 지면이 늘어나는 효과가 See all 셀의
            // 리빌과 겹쳐 둘 다 어중간해 보인다. iOS도 stretch가 아니라 bounce라,
            // "더 있다"는 신호는 리빌 하나로 몰아준다.
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // iOS HStack 기본 정렬(center)에 맞춘다. 트랙 셀은 서로 높이가 같아 영향이 없고,
                // 셀보다 짧은 See all(72dp)만 카드 높이 기준 가운데로 온다.
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .then(dragWatcher),
            ) {
                items(group.tracks, key = { it.userHypeTrackId }) { track ->
                    HypeCell(
                        track = track,
                        isPlaying = audio.activeTrackId == track.trackId && !audio.isPaused,
                        onTap = { audio.togglePreview(track.trackId, track.previewUrl) },
                        onLongPress = { actionTarget = track },
                    )
                    // 페이지네이션은 날짜 그룹이 아니라 **마지막 셀**에 건다 — 이유는 HypeGrouping 참고.
                    if (maxGroups == null && track.userHypeTrackId == anchor) {
                        LaunchedEffect(anchor) { onReachEnd?.invoke() }
                    }
                }
                if (showsSeeAll) {
                    item {
                        Column(
                            Modifier
                                .width(56.dp)
                                .height(72.dp)
                                // 당길수록 화살표가 따라 나오고 진해진다 — 더 끌면 뭔가 있다는 신호.
                                .offset { IntOffset((reveal.coerceAtMost(threshold) * 0.25f).toInt(), 0) }
                                .alpha((0.4f + reveal / threshold).coerceAtMost(1f))
                                .clickable { onSeeAll?.invoke() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DSColor.brand,
                            )
                            Text(
                                stringResource(R.string.see_all),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = DSColor.brand,
                            )
                        }
                    }
                }
            }
            }
        }
    }

    actionTarget?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = DSColor.background,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Row(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(track.artworkUrl, 44.dp, 12.dp)
                    Column {
                        Text(
                            track.trackName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DSColor.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artistName,
                            fontSize = 13.sp,
                            color = DSColor.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(color = DSColor.borderLight)
                ActionRow(
                    label = stringResource(R.string.track_detail),
                    icon = { Icon(Icons.Outlined.Info, null, tint = DSColor.textPrimary) },
                ) {
                    val id = track.trackId
                    actionTarget = null
                    detailTrackId = id
                }
                HorizontalDivider(Modifier.padding(start = 20.dp), color = DSColor.borderLight)
                ActionRow(
                    label = stringResource(R.string.remove_hype),
                    destructive = true,
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_hype),
                            null,
                            tint = DSColor.destructive,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    actionTarget = null
                    // 낙관적 제거. 실패하면 목록을 다시 받아 되돌린다.
                    if (audio.activeTrackId == track.trackId) audio.stop()
                    onItemsChange(items.filterNot { it.trackId == track.trackId })
                    scope.launch {
                        runCatching { Session.api.unhype(track.trackId) }
                            .onFailure { e ->
                                // 404 = 이미 없음. 목표 상태와 같으니 되돌리지 않는다.
                                val status = (e as? ClientRequestException)?.response?.status?.value
                                if (status != 404) onReloadNeeded?.invoke()
                            }
                    }
                }
            }
        }
    }

    detailTrackId?.let { id ->
        TrackDetailSheet(trackId = id, onDismiss = { detailTrackId = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HypeCell(
    track: Api.HypeItem,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        Modifier
            .width(72.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Artwork(track.artworkUrl, 72.dp, 16.dp)
            if (isPlaying) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            track.trackName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artistName,
            fontSize = 10.sp,
            color = DSColor.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Artwork(url: String, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url.itunesArtworkUrl(200))
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(DSColor.surface),
    )
}

@Composable
private fun ActionRow(
    label: String,
    destructive: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            fontSize = 16.sp,
            color = if (destructive) DSColor.destructive else DSColor.textPrimary,
        )
        Spacer(Modifier.weight(1f))
    }
}

/** 하입 기록 전체 화면. iOS `HypeHistoryView` 이식. */
@Composable
fun HypeHistoryScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<Api.HypeItem>>(emptyList()) }
    var cursor by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPaging by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun load() {
        isLoading = true
        loadFailed = false
        if (HypeMock.active) {
            // 전체 화면이므로 날짜·개수 제한 없이 전부. 4일치가 다 보여야 한다.
            items = HypeMock.items()
            cursor = null
            isLoading = false
            return
        }
        runCatching { Session.api.myHypes() }
            .onSuccess { items = it.items; cursor = it.nextCursor }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    ScreenScaffold(title = stringResource(R.string.hype_history), onBack = onBack) {
        when {
            isLoading && items.isEmpty() -> CenteredNote("")
            items.isEmpty() -> CenteredNote(
                stringResource(if (loadFailed) R.string.feed_load_failed else R.string.no_hyped_tracks)
            )

            else -> HypeCollection(
                items = items,
                onItemsChange = { items = it },
                onReachEnd = {
                    val c = cursor
                    if (c != null && !isPaging) {
                        isPaging = true
                        runCatching { Session.api.myHypes(c) }.onSuccess {
                            items = items + it.items
                            cursor = it.nextCursor
                        }
                        isPaging = false
                    }
                },
                onReloadNeeded = { load() },
            )
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Text(
        text,
        style = DSTypography.body,
        color = DSColor.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
    )
}

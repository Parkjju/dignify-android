package com.rta.dignify.feature.mypage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSPopup
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold
import com.rta.dignify.core.designsystem.SeedTray
import com.rta.dignify.core.designsystem.SeedTraySlot
import com.rta.dignify.core.designsystem.rememberReduceMotion
import com.rta.dignify.core.network.Api
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 서버 `SeedTracksUpdateRequest @Size(max)` 및 `MoodRecommender.SEEDS`와 같아야 한다.
 * 더 고르게 두면 서버가 400으로 튕기거나 조용히 잘라낸다.
 */
private const val SEED_LIMIT = 3

/**
 * 추천 기준 곡 고르기(디자인 seed_picker_opened 1.0). 좋아요한 곡 중 최대 [SEED_LIMIT]개를 고정하면
 * 서버가 최근 좋아요 대신 그 곡들만 시드로 쓴다.
 *
 * **아무것도 고르지 않은 상태가 기본이고 그게 정상이다.** 그때는 종전대로 최근 좋아요 세 곡이
 * 시드라, 이 화면에 한 번도 안 들어온 유저의 피드가 달라지지 않는다.
 *
 * 장르 칩을 바꿔도 고른 곡은 그대로다 — 필터 밖에 있는 고른 곡도 트레이에 남고 저장에 들어간다.
 *
 * @param onFindTracks 좋아요가 하나도 없을 때 「곡 찾으러 가기」 = 피드 탭.
 */
@Composable
fun SeedPickerScreen(onBack: () -> Unit, onFindTracks: () -> Unit) {
    val scope = rememberCoroutineScope()

    /** 지금 고른 장르의 목록. */
    var items by remember { mutableStateOf<List<Api.HypeItem>>(emptyList()) }
    // 장르 칩 + 「좋아요한 곡 N」. 목록이 커서 페이지라 받은 개수로는 못 센다. 못 받으면 칩·줄을 숨긴다.
    var genres by remember { mutableStateOf<Api.HypeGenresResponse?>(null) }
    var genreId by remember { mutableStateOf<Int?>(null) }
    // 장르를 오가며 받은 곡 전부. 고른 곡이 지금 필터 밖에 있어도 트레이에 커버를 그린다.
    var seen by remember { mutableStateOf<Map<Int, Api.HypeItem>>(emptyMap()) }
    var didReportOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 저장 성공 여부의 기준. 서버에서 받은 그대로를 들고 있다가 비교한다.
    var savedSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var cursor by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPaging by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var showLimit by remember { mutableStateOf(false) }

    /**
     * 받은 곡을 [seen]에 쌓고, 처음 보는 고정 곡을 선택에 더한다. 유저가 아직 손대지 않은 것만 —
     * **방금 해제한 곡이 페이지나 장르를 바꾸면서 되살아나면 안 된다.** 화면에 없는 고정 곡도
     * 저장 때 지워지지 않게 합집합으로 들고 간다.
     */
    fun absorb(page: List<Api.HypeItem>) {
        seen = seen + page.associateBy { it.trackId }
        val pinned = page.filter { it.isSeed == true && it.trackId !in savedSelection }.map { it.trackId }
        selected = selected + pinned
        savedSelection = savedSelection + pinned
    }

    /** 첫 진입과 장르 변경. 고른 곡은 건드리지 않는다. */
    suspend fun loadList() {
        isLoading = true
        loadFailed = false
        val res = runCatching { Session.api.myHypes(genreId = genreId) }
        // 칩을 바꿔 취소된 요청은 실패가 아니다 — 다음 요청이 isLoading을 이어받는다.
        currentCoroutineContext().ensureActive()
        res.onSuccess {
            items = it.items
            cursor = it.nextCursor
            absorb(it.items)
            // 저장까지 간 비율을 보려면 분모가 필요하다. 목록을 못 받은 진입은 세지 않는다 —
            // 그때는 고를 수가 없어서 저장 안 한 게 유저의 선택이 아니다. 장르를 바꿀 때마다 세지 않는다.
            if (!didReportOpen) {
                didReportOpen = true
                Analytics.capture("seed_picker_opened", mapOf("pinned" to savedSelection.size))
            }
        }.onFailure { loadFailed = true }
        isLoading = false
    }

    suspend fun loadMore() {
        val c = cursor ?: return
        if (isPaging) return
        isPaging = true
        val genre = genreId
        runCatching { Session.api.myHypes(c, genre) }.onSuccess { res ->
            // 받는 사이 칩이 바뀌었으면 버린다.
            if (genre != genreId) return@onSuccess
            items = items + res.items
            cursor = res.nextCursor
            absorb(res.items)
        }
        isPaging = false
    }

    LaunchedEffect(Unit) { genres = runCatching { Session.api.myHypeGenres() }.getOrNull() }
    // 키가 바뀌면 이전 요청은 취소된다 — 칩을 빠르게 바꿔도 늦게 온 응답이 덮지 않는다.
    LaunchedEffect(genreId) { loadList() }

    // 좋아요가 하나도 없으면 고를 것도, 저장할 것도 없다 — 제목·트레이·저장 없이 다음 할 일만.
    // 장르 필터 중엔 목록이 비어도 빈 화면이 아니다.
    val hasLikes = items.isNotEmpty() || genreId != null
    val full = selected.size >= SEED_LIMIT
    val likeCount = genres?.let { g ->
        if (genreId == null) g.total else g.genres.firstOrNull { it.genreId == genreId }?.count
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            title = stringResource(R.string.seed_picker_title),
            onBack = onBack,
            scrollable = false,
            trailing = {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = DSColor.brand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 16.dp).size(20.dp),
                    )
                } else if (hasLikes) {
                    // 들어올 때와 달라졌으면 활성(0곡 저장 = 최근 좋아요 기준으로 되돌리기도 된다).
                    // 바꾼 게 없으면 누를 이유가 없다 — 눌렀는데 아무 일도 안 일어나면 실패처럼 보인다.
                    val enabled = selected != savedSelection
                    Text(
                        stringResource(R.string.save),
                        style = DSTypography.bodyMedium,
                        color = if (enabled) DSColor.brand else DSColor.textTertiary,
                        modifier = Modifier
                            .clickable(enabled = enabled) {
                                saveFailed = false
                                isSaving = true
                                scope.launch {
                                    runCatching { Session.api.setSeedTracks(selected.toList()) }
                                        .onSuccess {
                                            savedSelection = selected
                                            // 0도 의미 있는 값이다 — 고정을 전부 풀고
                                            // 최근 좋아요로 되돌린 것.
                                            Analytics.capture(
                                                "seed_saved",
                                                mapOf("count" to selected.size),
                                            )
                                            // 기준 곡이 바뀌면 정렬 기준 자체가 바뀐다. 들고 있던
                                            // 커서로 이어 보면 옛 기준으로 뽑힌 페이지가 계속 나와서
                                            // 방금 고른 게 반영이 안 된 것처럼 보인다.
                                            Session.onSeedsChanged()
                                            onBack()
                                        }
                                        .onFailure { saveFailed = true }
                                    isSaving = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        ) {
            when {
                isLoading && items.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = DSColor.brand) }

                // 실패는 빈 화면으로 보이면 안 된다 — 좋아요가 없는 게 아니라 못 받은 것이다.
                loadFailed && items.isEmpty() -> Text(
                    stringResource(R.string.feed_load_failed),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                )

                !hasLikes -> EmptyLikes(onFindTracks)

                else -> Column(Modifier.fillMaxSize()) {
                    Header(saveFailed = saveFailed)
                    genres?.genres?.takeIf { it.isNotEmpty() }?.let { list ->
                        GenreChips(list, genreId, onSelect = { genreId = it })
                    }
                LazyColumn(
                    Modifier.weight(1f),
                    // 떠 있는 트레이에 마지막 줄이 가리지 않게 그 높이만큼 비운다.
                    contentPadding = PaddingValues(top = 8.dp, bottom = TRAY_CLEARANCE),
                ) {
                    likeCount?.let { n ->
                        item {
                            Text(
                                stringResource(R.string.seed_picker_count, n),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DSColor.textTertiary,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
                            )
                        }
                    }
                    items(items, key = { it.userHypeTrackId }) { track ->
                        val isOn = track.trackId in selected
                        SeedRow(
                            track = track,
                            selected = isOn,
                            disabled = full && !isOn,
                            onClick = {
                                when {
                                    isOn -> selected = selected - track.trackId
                                    // 꽉 찼으면 밀어내지 않고 이유를 말한다. 가장 오래된 걸 빼면
                                    // 방금 무엇이 빠졌는지 화면에서 안 보인다.
                                    full -> showLimit = true
                                    else -> selected = selected + track.trackId
                                }
                            },
                        )
                        if (track.userHypeTrackId == items.last().userHypeTrackId) {
                            LaunchedEffect(track.userHypeTrackId) { loadMore() }
                        }
                    }
                }
                }
            }
        }

        // 목록을 못 받았으면 고를 것도 없다. 빈 트레이만 떠 있으면 무엇을 하라는 건지 모른다.
        if (hasLikes) {
            val left = SEED_LIMIT - selected.size
            SeedTray(
                // 최신 좋아요 먼저. 장르를 바꿔도 순서가 안 흔들리게 [seen]에서 뽑는다.
                slots = selected.mapNotNull { seen[it] }
                    .sortedByDescending { it.userHypeTrackId }
                    .map { SeedTraySlot(it.trackId, it.artworkUrl) },
                limit = SEED_LIMIT,
                numbered = false,
                onOpen = null,
                onRemove = { selected = selected - it },
                hint = when (left) {
                    SEED_LIMIT -> stringResource(R.string.seed_tray_room, SEED_LIMIT)
                    0 -> stringResource(R.string.seed_tray_done, SEED_LIMIT)
                    else -> pluralStringResource(R.plurals.seed_tray_more, left, left)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(DSRadius.medium))
                    .background(DSColor.background, RoundedCornerShape(DSRadius.medium))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        DSPopup(visible = showLimit, onDismiss = { showLimit = false }) {
            LimitNotice(onConfirm = { showLimit = false })
        }
    }
}

/** 트레이 카드(칸 52 + 안쪽 24 + 바닥 12)에 숨 쉴 틈을 더한 값. */
private val TRAY_CLEARANCE = 108.dp

@Composable
private fun Header(saveFailed: Boolean) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        InfoTitle()
        // 꺼진 채로 들어올 수 있다. 고른 것이 지금 아무 데도 안 쓰인다는 걸 말해 주지
        // 않으면 저장하고 나서 피드가 그대로인 것을 고장으로 읽는다.
        if (!Session.diggingMode) {
            Text(
                stringResource(R.string.seed_picker_mode_off),
                style = DSTypography.caption,
                color = DSColor.brand,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (saveFailed) {
            Text(
                stringResource(R.string.save_failed),
                style = DSTypography.caption,
                color = DSColor.destructive,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 디자인 FilterChip. 한 줄 가로 스크롤, 「전체」가 맨 앞. 고른 칩은 `textPrimary` —
 * brand는 이 화면에서 고른 곡 표시에 쓰여서 칩까지 brand면 무엇을 골랐는지가 섞인다.
 */
@Composable
private fun GenreChips(list: List<Api.HypeGenre>, current: Int?, onSelect: (Int?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GenreChip(stringResource(R.string.seed_genre_all), current == null) { onSelect(null) }
        list.forEach { g -> GenreChip(g.genreName, current == g.genreId) { onSelect(g.genreId) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenreChip(label: String, isOn: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 화면 밖에 걸친 칩을 고르면 그 칩이 보이게만 민다(스크롤 위치를 앞으로 당기지 않는다).
    val requester = remember { BringIntoViewRequester() }
    Box(
        Modifier
            .bringIntoViewRequester(requester)
            .selectable(selected = isOn, role = Role.Tab) {
                scope.launch { requester.bringIntoView() }
                if (!isOn) onClick()
            }
            .padding(vertical = 5.dp),   // 탭 영역 44
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isOn) Color.White else DSColor.textPrimary,
            modifier = Modifier
                .background(if (isOn) DSColor.textPrimary else DSColor.surface, CircleShape)
                .height(34.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 14.dp),
        )
    }
}

/**
 * 두 줄 제목 + 끝의 ⓘ(디자인 Tooltip). ⓘ는 글자 흐름 안에 둔다 — 옆 Row로 두면 둘째 줄이
 * 짧은 언어에서 ⓘ가 글 끝이 아니라 허공에 뜬다.
 */
@Composable
private fun InfoTitle() {
    val reduceMotion = rememberReduceMotion()
    val tip = remember { MutableTransitionState(false) }
    // 떠 있는 동안 ⓘ를 다시 누르면 2초를 다시 센다 — 키를 바꿔 타이머를 새로 건다.
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        delay(180 + 2000)
        tip.targetState = false
    }
    val info = stringResource(R.string.seed_picker_info, SEED_LIMIT)
    val density = LocalDensity.current
    val tipPosition = remember(density) { TipPosition(density) }
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.seed_picker_header, SEED_LIMIT))
            append(" ")
            appendInlineContent("info")
        },
        style = DSTypography.title2,
        color = DSColor.textPrimary,
        inlineContent = mapOf(
            "info" to InlineTextContent(Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.Center)) {
                Box {
                    Icon(
                        Icons.Outlined.Info,
                        // 말풍선은 2초라 TalkBack이 못 읽는다. 같은 문장을 버튼에 싣는다.
                        contentDescription = info,
                        tint = DSColor.textTertiary,
                        modifier = Modifier.size(18.dp).clickable {
                            tip.targetState = true
                            taps++
                        },
                    )
                    if (tip.currentState || tip.targetState) {
                        Popup(
                            popupPositionProvider = tipPosition,
                            onDismissRequest = { tip.targetState = false },
                            // focusable: 바깥 탭이 뒤 목록에 새지 않고 닫기만 한다.
                            properties = PopupProperties(focusable = true),
                        ) {
                            AnimatedVisibility(
                                tip,
                                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(180)),
                                exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(400)),
                            ) { Tip(info) }
                        }
                    }
                }
            },
        ),
    )
}

/** ⓘ 아래 8, 오른쪽 끝을 화면 여백 16에 맞춘다(디자인 Tooltip). */
private class TipPosition(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = with(density) {
        IntOffset(
            (windowSize.width - popupContentSize.width - 16.dp.roundToPx()).coerceAtLeast(0),
            anchorBounds.bottom + 8.dp.roundToPx(),
        )
    }
}

@Composable
private fun Tip(text: String) {
    Text(
        text,
        style = DSTypography.caption,
        lineHeight = DSTypography.caption.fontSize * 1.5f,
        color = Color.White,
        modifier = Modifier
            // 왼쪽 여백만 — 오른쪽은 TipPosition이 맞춘다. 긴 문장이 화면 끝에 붙지 않게.
            .padding(start = 16.dp)
            .shadow(16.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.18f))
            .background(DSColor.textPrimary, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** 디자인 TrackRow 체크형(`check`·`play: false`·`disabled`). 행 전체가 고르기/풀기 표적이다. */
@Composable
private fun SeedRow(track: Api.HypeItem, selected: Boolean, disabled: Boolean, onClick: () -> Unit) {
    val dim = DSColor.textTertiary.copy(alpha = 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            track.artworkUrl, 52.dp, 10.dp,
            modifier = if (disabled) Modifier.alpha(0.4f) else Modifier,
            gray = disabled,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.trackName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (disabled) dim else DSColor.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.genreName?.let { "${track.artistName} · $it" } ?: track.artistName,
                fontSize = 12.sp,
                color = if (disabled) dim else DSColor.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 비활성이어도 원은 남긴다(60%) — 눌러서 「1개를 해제해 주세요」가 떠야 하는 자리라는 표시.
        Box(
            Modifier
                .size(24.dp)
                .alpha(if (disabled) 0.6f else 1f)
                .then(
                    if (selected) Modifier.background(DSColor.brand, CircleShape)
                    else Modifier.border(1.5.dp, DSColor.textTertiary, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** 디자인 Popup 확인형(`.ds-popup--notice`) — 4번째를 고르려 할 때. */
@Composable
private fun LimitNotice(onConfirm: () -> Unit) {
    Column(
        Modifier
            .width(300.dp)
            .shadow(24.dp, RoundedCornerShape(DSRadius.large))
            .background(DSColor.background, RoundedCornerShape(DSRadius.large))
            // 카드를 눌렀을 때 딤으로 새어 닫히지 않도록 히트테스트를 먹는다.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.seed_limit_title),
            style = DSTypography.headline,
            color = DSColor.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.seed_limit_message, SEED_LIMIT),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = DSTypography.body.fontSize * 1.45f,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(DSRadius.medium))
                .background(DSColor.brand)
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.ok),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/** 디자인 EmptyState. 좋아요 0개 — 왜 비었는지와 다음 할 일. 불러오기 실패엔 쓰지 않는다. */
@Composable
private fun EmptyLikes(onFindTracks: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).background(DSColor.brandLight, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = DSTypography.title1, color = DSColor.brand)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.seed_empty_title),
            style = DSTypography.title2,
            color = DSColor.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.seed_empty_message),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // Button `small`: 높이 40, 좌우 20, radius.medium, 14 semibold.
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(DSRadius.medium))
                .background(DSColor.brand)
                .clickable(onClick = onFindTracks)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.seed_empty_action),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

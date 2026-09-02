package com.rta.dignify.feature.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSSearchBar
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.dismissKeyboardOnTap
import com.rta.dignify.core.model.Feed
import com.rta.dignify.core.model.toFeed
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.feature.feed.FeedAudioController
import com.rta.dignify.feature.mypage.HypeGrouping
import com.rta.dignify.feature.mypage.HypeMock
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 서버 상한과 같다(`PickCreate @Size(min=1, max=30)`). 넘으면 400이라 클라가 먼저 막는다. */
private const val MAX_TRACKS = 30

/**
 * 목록에 올라가는 최소 단위. 하입 목록과 검색 결과가 같은 행을 쓴다.
 *
 * **비교는 `trackId`로만 한다.** 같은 곡이 검색 결과에는 `hypedAt` 없이, 하입 목록에는 있는
 * 채로 온다 — 값 전체를 비교하면 검색에서 고른 곡이 목록으로 돌아왔을 때 미선택으로 보이고
 * 두 번 담긴다(iOS가 실제로 밟은 버그다). data class 동등성을 쓰지 말 것.
 */
private data class Candidate(
    val trackId: Int,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val previewUrl: String = "",
    /** 하입 시각(ISO date-time). 검색 결과엔 없어서 날짜 그룹 밖에 따로 선다. */
    val hypedAt: String? = null,
)

private fun Api.HypeItem.toCandidate() =
    Candidate(trackId, trackName, artistName, artworkUrl, previewUrl, hypedAt)

private fun Feed.toCandidate() =
    Candidate(trackId, trackName, artistName, artworkUrl, previewUrl)

/** 목록 한 덩어리. 날짜 그룹이면 제목이 있고, 검색 결과면 없다. */
private data class Section(val key: String, val title: String?, val tracks: List<Candidate>)

/**
 * 픽 작성. iOS `PickComposeView` 이식.
 *
 * 소스가 **두 개**다 — 내가 하입한 곡과 검색. 하입 목록만으로 제한하지 않는 이유는
 * 담아둔 게 없는 신규 유저가 첫 픽을 못 만들기 때문이고, 검색만 두지 않는 이유는 하입하는
 * 행위가 픽으로 이어지지 않으면 하입이 아무 데도 안 쓰이기 때문이다.
 *
 * **날짜별 리스트 + 행 탭 재생이다.** 원래는 3열 그리드였는데, 그리드는 *이미 아는 곡*을
 * 찾을 때 빠르고 하입 목록은 대부분 기억나지 않는 곡이다. 작성화면을 연 53명 중 45명이
 * 아무것도 못 내고 나갔고 32명은 재시도가 없었다 — 날짜가 "그날 뭘 파고 있었는지"라는
 * 맥락을 주고 재생이 회상을 마무리한다. **그리드로 되돌리지 말 것.**
 * (온보딩 시드 고르기는 반대 이유로 그리드다: 모르는 곡을 훑는 지면이고 날짜 축이 없다.)
 *
 * **두 단계다**(iOS와 동일): 곡 고르기 → 제목. 제목 단계엔 실제로 올라갈 카드의 프리뷰가 있다 —
 * 픽은 카드 한 장이 결과물이라, 올리기 전에 그 카드를 봐야 제목·순서를 판단할 수 있다.
 * 선택 상태는 상위에 있으므로 뒤로 가도 그대로 유지된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickComposeSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 한 번에 한 곡만 무는 단발 미리듣기. `onListen`을 안 붙였으므로 청취 집계에 안 들어간다 —
    // 여긴 곡을 고르는 자리지 듣는 자리가 아니라서 피드 청취율을 오염시키면 안 된다.
    val audio = remember { FeedAudioController(context, scope) }

    var crate by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var crateCursor by remember { mutableStateOf<Long?>(null) }
    var isPagingCrate by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var searchCursor by remember { mutableStateOf<String?>(null) }
    var isPagingSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }

    val selected = remember { mutableListOf<Candidate>().toMutableStateList() }
    var title by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTitleStep by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { audio.stop() } }

    LaunchedEffect(Unit) {
        // 만들기를 **연** 횟수. `pick_created`와 짝지어야 "만들다 말았다"가 보인다.
        Analytics.capture("pick_compose_opened")
        if (HypeMock.active) {
            crate = HypeMock.items().map { it.toCandidate() }
        } else {
            // 첫 페이지만 받는다. 나머지는 마지막 행이 보일 때 이어 붙인다 —
            // 열자마자 열 페이지를 몰아 받으면 그중 아홉 페이지는 아무도 안 본다.
            runCatching { Session.api.myHypes() }.onSuccess { res ->
                crate = res.items.map { it.toCandidate() }
                crateCursor = res.nextCursor
            }
        }
        isLoading = false
    }

    /**
     * 다음 페이지. 목록이 두 소스를 갈아끼우므로 어느 쪽을 이어받을지 여기서 가른다.
     * ponytail: 실패는 조용히 넘긴다 — 다음 스크롤에 다시 불린다.
     */
    fun loadMore() {
        if (activeQuery.isEmpty()) {
            val cursor = crateCursor ?: return
            if (isPagingCrate) return
            isPagingCrate = true
            scope.launch {
                runCatching { Session.api.myHypes(cursor) }.onSuccess { res ->
                    crate = crate + res.items.map { it.toCandidate() }
                    crateCursor = res.nextCursor
                }
                isPagingCrate = false
            }
        } else {
            val cursor = searchCursor ?: return
            if (isPagingSearch) return
            val query = activeQuery
            isPagingSearch = true
            scope.launch {
                runCatching { Session.api.search(query, cursor) }.onSuccess { res ->
                    // 페이지가 도는 사이 검색어가 바뀌었으면 이건 남의 결과다.
                    if (query != activeQuery) return@onSuccess
                    results = results + res.items.map { it.toFeed().toCandidate() }
                    searchCursor = res.nextCursor?.takeIf { res.hasMore == true }
                }
                isPagingSearch = false
            }
        }
    }

    /** 프리뷰는 한 번에 한 곡. 같은 곡을 다시 누르면 일시정지라 **새 곡이 시작될 때만** 찍는다. */
    fun play(track: Candidate) {
        if (track.previewUrl.isBlank()) return
        if (audio.activeTrackId != track.trackId) {
            Analytics.capture(
                "pick_track_previewed",
                mapOf("from" to if (activeQuery.isEmpty()) "crate" else "search"),
            )
        }
        audio.togglePreview(track.trackId, track.previewUrl)
    }

    fun toggle(track: Candidate) {
        val index = selected.indexOfFirst { it.trackId == track.trackId }
        if (index >= 0) {
            selected.removeAt(index)   // 뒤 번호는 자동으로 당겨진다(재정렬 기능 없음).
            return
        }
        if (selected.size >= MAX_TRACKS) {
            error = context.getString(R.string.track_limit, MAX_TRACKS)
            return
        }
        error = null
        selected.add(track)
        // 작성화면 안이 통째로 무계측이라 85% 이탈이 곡 선택인지 제목 단계인지 못 갈랐다.
        // 이 이벤트가 "한 곡이라도 골랐다"의 하한선이다. **해제할 땐 안 찍는다.**
        Analytics.capture(
            "pick_track_selected",
            mapOf(
                "from" to (if (activeQuery.isEmpty()) "crate" else "search"),
                "selected_count" to selected.size,
            ),
        )
    }

    val dayFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val addedLabel = stringResource(R.string.pick_added_from_search)
    // 검색에서 고른 곡은 `hypedAt`이 없어 날짜 그룹에 낄 자리가 없다. 맨 위에 따로 세워두지
    // 않으면 검색어를 지우는 순간 목록에서 사라져 어디로 갔는지 알 수 없다.
    val sections: List<Section> = if (activeQuery.isNotEmpty()) {
        listOf(Section("results", null, results))
    } else {
        val crateIds = crate.map { it.trackId }.toSet()
        val added = selected.filterNot { it.trackId in crateIds }
        // 날짜 묶기는 하입 기록 화면과 **같은 그루퍼**를 쓴다. 두 개 만들면 두 목록의
        // 날짜 기준이 언젠가 갈린다.
        val days = HypeGrouping.byDay(crate) { it.hypedAt }.map { group ->
            Section(group.day.toString(), group.day.format(dayFormat), group.tracks)
        }
        if (added.isEmpty()) days else listOf(Section("added", addedLabel, added)) + days
    }
    val listItems = sections.flatMap { it.tracks }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DSColor.background,
    ) {
        Column(Modifier.fillMaxHeight(0.94f).dismissKeyboardOnTap()) {
            Text(
                stringResource(R.string.pick_new),
                style = DSTypography.title2,
                color = DSColor.textPrimary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )

            DSSearchBar(
                text = searchText,
                onTextChange = {
                    searchText = it
                    // 검색어를 지우면 확정 상태도 풀려 하입 목록으로 돌아온다.
                    if (it.isBlank()) activeQuery = ""
                },
                placeholder = stringResource(R.string.search_placeholder),
                onSubmit = {
                    val q = searchText.trim()
                    if (q.isEmpty()) return@DSSearchBar
                    activeQuery = q
                    requested = false
                    scope.launch {
                        isSearching = true
                        runCatching { Session.api.search(q) }
                            .onSuccess { res ->
                                // 그 사이 다른 검색어가 확정됐으면 이건 지난 결과다.
                                if (q != activeQuery) return@onSuccess
                                results = res.items.map { it.toFeed().toCandidate() }
                                searchCursor = res.nextCursor?.takeIf { res.hasMore == true }
                            }
                            .onFailure { results = emptyList(); searchCursor = null }
                        isSearching = false
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
            )

            // 지금 보고 있는 소스가 무엇인지 + 하입 목록으로 돌아가는 명시적 문.
            // 검색창을 비우는 게 유일한 복귀 경로면 고른 곡들이 어디 갔는지 알 수 없다.
            Row(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (activeQuery.isEmpty()) stringResource(R.string.your_hypes)
                    else stringResource(R.string.pick_results_for, activeQuery),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DSColor.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // weight는 한쪽에만. 텍스트와 Spacer 둘 다 주면 폭 배분이 어긋나
                    // 오른쪽 묶음이 제자리를 못 잡는다.
                    modifier = Modifier.weight(1f),
                )
                if (activeQuery.isNotEmpty()) {
                    Row(
                        Modifier.clickable { searchText = ""; activeQuery = "" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            null,
                            tint = DSColor.brand,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.your_hypes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DSColor.brand,
                        )
                        if (selected.isNotEmpty()) {
                            // Text에 배경을 직접 씌우면 알약 높이가 글꼴 줄 높이를 따라가
                            // 옆 13sp 텍스트와 중심이 어긋난다. Box로 감싸 높이를 고정하고
                            // 안에서 가운데 정렬한다.
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(DSColor.brandLight)
                                    .height(18.dp)
                                    .padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.pick_selected_count, selected.size),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DSColor.brand,
                                )
                            }
                        }
                    }
                }
            }

            when {
                isSearching || (isLoading && activeQuery.isEmpty()) -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = DSColor.brand) }

                listItems.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (activeQuery.isEmpty()) stringResource(R.string.pick_hypes_empty)
                        else stringResource(R.string.feed_no_results, activeQuery),
                        style = DSTypography.body,
                        color = DSColor.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (activeQuery.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                requested = true
                                scope.launch { runCatching { Session.api.requestArtist(activeQuery) } }
                            },
                            enabled = !requested,
                        ) {
                            Text(
                                if (requested) stringResource(R.string.artist_request_done)
                                else stringResource(R.string.artist_request_cta, activeQuery),
                                color = if (requested) DSColor.textTertiary else DSColor.brand,
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sections.forEach { section ->
                        section.title?.let { label ->
                            item(key = "head-${section.key}") {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DSColor.textTertiary,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                        items(section.tracks, key = { "${section.key}-${it.trackId}" }) { track ->
                            TrackRow(
                                track = track,
                                number = selected.indexOfFirst { it.trackId == track.trackId }
                                    .takeIf { it >= 0 }?.plus(1),
                                isPlaying = audio.activeTrackId == track.trackId && !audio.isPaused,
                                onPlay = { play(track) },
                                onToggle = { toggle(track) },
                            )
                            // 다음 페이지 트리거는 **마지막 행**에 문다. 날짜 그룹에 물면
                            // 새 페이지가 전부 같은 날일 때 키가 그대로라 두 번 다시 안 불린다
                            // (`HypeGrouping`에 적어둔 iOS 1.0.9 버그와 같은 함정).
                            if (track.trackId == listItems.lastOrNull()?.trackId) {
                                LaunchedEffect(track.trackId) { loadMore() }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DSColor.borderLight)
            Column(
                Modifier
                    .background(DSColor.background)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 상한을 넘겨 눌렀을 때만 뜬다. 아무 일도 안 일어나면 고장으로 읽힌다.
                if (!showTitleStep) {
                    error?.let {
                        Text(it, style = DSTypography.caption, color = DSColor.destructive)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 0곡일 땐 개수가 아니라 무엇을 하라는 말이 필요하다.
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.pick_select_prompt)
                        else stringResource(R.string.pick_selected_count, selected.size),
                        fontSize = 14.sp,
                        color = if (selected.isEmpty()) DSColor.textTertiary else DSColor.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(DSRadius.medium))
                            .background(if (selected.isEmpty()) DSColor.borderLight else DSColor.brand)
                            .clickable(enabled = selected.isNotEmpty()) {
                                error = null
                                // 다음 화면에서 소리만 남으면 어디서 나는지 알 수 없다.
                                audio.stop()
                                // `pick_track_selected`와 `pick_created` 사이의 마지막 관문이다.
                                Analytics.capture(
                                    "pick_title_step",
                                    mapOf("track_count" to selected.size),
                                )
                                showTitleStep = true
                            }
                            .height(40.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.pick_next),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected.isEmpty()) DSColor.textTertiary else Color.White,
                        )
                    }
                }
            }
        }
    }

    // 한 뎁스 더 들어간다. 시트 위에 시트를 겹치지 않고 같은 시트 안에서 화면을 갈아끼운다 —
    // 중첩 제시는 뒤 시트가 내려갈 때 앞 것도 같이 사라지는 함정이 있다.
    if (showTitleStep) {
        PickTitleStep(
            selected = selected,
            title = title,
            onTitleChange = { title = it },
            error = error,
            isSubmitting = isSubmitting,
            onBack = { showTitleStep = false },
            onPost = {
                isSubmitting = true
                error = null
                scope.launch {
                    // 목업 상태에선 서버에 올리지 않는다 — 목업 trackId(10000번대)는 실제로
                    // 없는 트랙이라 400이 나고, 성공해도 남의 지면에 쓰레기 픽이 남는다.
                    // 대신 로컬 목록 맨 앞에 끼워 결과 화면만 확인한다.
                    if (PickMock.active) {
                        PickMock.addLocal(
                            title = PickTitle.normalized(title),
                            trackCount = selected.size,
                            distinctArtistCount = selected.map { it.artistName }.distinct().size,
                            firstArtistName = selected.firstOrNull()?.artistName,
                            firstTrackName = selected.firstOrNull()?.trackName,
                            thumbnails = selected.take(3).map { it.artworkUrl },
                        )
                        onCreated()
                        return@launch
                    }
                    runCatching {
                        Session.api.createPick(PickTitle.normalized(title), selected.map { it.trackId })
                    }.onSuccess { response ->
                        // **상태 코드를 직접 본다.** 이 클라이언트엔 expectSuccess가 없어서
                        // 400(금칙어·개수 위반)도 예외를 안 던진다 — 안 보면 서버가 거절한
                        // 픽을 올라간 것으로 알리고 목록엔 없는 상태가 된다.
                        if (!response.status.isSuccess()) {
                            error = context.getString(R.string.pick_publish_failed)
                            capturePostFailure("server", selected.size)
                            isSubmitting = false
                            return@onSuccess
                        }
                        // 하입 목록에 없는 곡 = 검색으로 찾아 넣은 곡. 이 값이 0에 수렴하면
                        // 검색 소스는 죽은 코드고, 높으면 검색 품질이 시급해진다.
                        val crateIds = crate.map { it.trackId }.toSet()
                        Analytics.capture(
                            "pick_created",
                            mapOf(
                                "track_count" to selected.size,
                                "has_title" to (PickTitle.normalized(title) != null),
                                "from_search_count" to selected.count { it.trackId !in crateIds },
                            ),
                        )
                        onCreated()
                    }.onFailure {
                        error = context.getString(R.string.pick_publish_failed)
                        // 마지막 단계까지 와서 못 낸 사람은 이탈로만 보이고 이유가 안 남는다.
                        capturePostFailure("network", selected.size)
                        isSubmitting = false
                    }
                }
            },
        )
    }
}

private fun capturePostFailure(reason: String, trackCount: Int) {
    Analytics.capture(
        "pick_submit_failed",
        mapOf("reason" to reason, "track_count" to trackCount),
    )
}

/**
 * 제목 단계. **프리뷰가 이 화면의 핵심이다** — 픽은 카드 한 장이 결과물이라, 올리기 전에
 * 실제로 올라갈 카드를 봐야 제목이 어떻게 읽히는지·표지 순서가 맞는지를 판단할 수 있다.
 *
 * 프리뷰 카드는 목록의 `PickCard`를 그대로 쓴다(입력은 막는다). 별도 미리보기 컴포넌트를
 * 만들면 실제 카드와 서서히 달라져서 프리뷰가 거짓말이 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTitleStep(
    selected: List<Candidate>,
    title: String,
    onTitleChange: (String) -> Unit,
    error: String?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onPost: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        containerColor = DSColor.background,
    ) {
        Column(Modifier.fillMaxHeight(0.94f)) {
            Text(
                stringResource(R.string.pick_title_step),
                style = DSTypography.title2,
                color = DSColor.textPrimary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, bottom = 24.dp),
            ) {
                SectionLabel(stringResource(R.string.pick_preview), Modifier.padding(horizontal = 16.dp))
                // 카드가 좌우 16을 스스로 가지므로 이 화면은 여백을 안 준다.
                Box(Modifier.pointerInput(Unit) { /* 프리뷰라 입력을 먹는다 */ }) {
                    PickCard(
                        pick = previewPick(selected, title),
                        onPlay = {},
                        onReact = null,
                        onMenu = {},
                    )
                }

                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.pick_title_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DSColor.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${title.length} / ${PickTitle.MAX_LENGTH}",
                        fontSize = 12.sp,
                        color = if (title.length >= PickTitle.MAX_LENGTH) DSColor.destructive
                        else DSColor.textTertiary,
                    )
                }

                val context = LocalContext.current
                // 플레이스홀더 = 비웠을 때 실제로 나올 제목. 그래서 별도 안내 문구가 필요 없다.
                val placeholder = remember(selected) {
                    if (selected.isEmpty()) context.getString(R.string.pick_title_placeholder)
                    else PickTitle.fallback(context, previewPick(selected, null))
                }
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(DSRadius.medium))
                        .background(DSColor.surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= PickTitle.MAX_LENGTH) onTitleChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = DSColor.textPrimary),
                        cursorBrush = SolidColor(DSColor.brand),
                        decorationBox = { inner ->
                            if (title.isEmpty()) {
                                Text(
                                    placeholder,
                                    fontSize = 15.sp,
                                    color = DSColor.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        },
                    )
                }

                error?.let {
                    Text(
                        it,
                        style = DSTypography.caption,
                        color = DSColor.destructive,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    )
                }

                SectionLabel(
                    stringResource(R.string.pick_track_count, selected.size),
                    Modifier.padding(horizontal = 16.dp).padding(top = 24.dp),
                )
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    selected.forEachIndexed { index, track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DSColor.border,
                                modifier = Modifier.width(16.dp),
                            )
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(track.artworkUrl.itunesArtworkUrl(200)).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DSColor.surface),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    track.trackName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DSColor.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artistName,
                                    fontSize = 12.sp,
                                    color = DSColor.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = DSColor.borderLight)
            Box(
                Modifier
                    .background(DSColor.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(DSRadius.medium))
                    .background(DSColor.brand)
                    .clickable(enabled = !isSubmitting, onClick = onPost),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (isSubmitting) R.string.pick_posting else R.string.pick_post),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = DSColor.textTertiary,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** 선택한 곡들로 실제 카드와 같은 모양을 만든다. 서버에 올라간 뒤의 픽과 같은 값이어야 한다. */
private fun previewPick(selected: List<Candidate>, title: String?) = Api.Pick(
    pickId = 0,
    title = title?.let { PickTitle.normalized(it) },
    nickname = Session.nickname,
    isMine = true,
    createdAt = java.time.Instant.now().toString(),
    trackCount = selected.size,
    distinctArtistCount = selected.map { it.artistName }.distinct().size,
    firstArtistName = selected.firstOrNull()?.artistName,
    firstTrackName = selected.firstOrNull()?.trackName,
    thumbnails = selected.take(3).map { it.artworkUrl },
)

/**
 * 재생 가능한 선택 행. **행 본문 탭 = 프리뷰 재생이고, 선택은 오른쪽 별도 표적이다.**
 * 소리를 들어보고 고르게 하려는 것이라 두 동작을 한 탭에 겹치면 안 된다 — 겹치면 아는
 * 이름만 보고 고르게 된다.
 *
 * 배지는 체크가 아니라 **번호**다(고른 순서 = 재생 순서). 24dp인데 **표적은 44dp**다 —
 * 배지 크기를 그대로 표적으로 쓰면 자꾸 빗나간다.
 *
 * 재생 표시를 재생 중일 때만 띄우면 여기가 눌러서 들어보는 자리라는 걸 아무도 모른다.
 * 그래서 정지 상태에도 흐린 삼각형을 남기고, 재생 중에만 스크림을 진하게 한다.
 */
@Composable
private fun TrackRow(
    track: Candidate,
    number: Int?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DSColor.surface),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUrl.itunesArtworkUrl(200)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isPlaying) 0.45f else 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (isPlaying) 1f else 0.85f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.trackName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DSColor.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistName,
                fontSize = 12.sp,
                color = DSColor.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clickable(
                    onClickLabel = stringResource(
                        if (number == null) R.string.select else R.string.deselect
                    ),
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (number == null) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, DSColor.textTertiary, CircleShape),
                )
            } else {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(DSColor.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

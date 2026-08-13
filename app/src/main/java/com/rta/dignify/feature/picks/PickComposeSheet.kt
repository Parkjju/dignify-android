package com.rta.dignify.feature.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.rta.dignify.core.model.Feed
import com.rta.dignify.core.model.toFeed
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.feature.mypage.HypeMock
import kotlinx.coroutines.launch

/** 서버 상한과 같다(`PickCreate @Size(min=1, max=30)`). 넘으면 400이라 클라가 먼저 막는다. */
private const val MAX_TRACKS = 30

/** 그리드에 올라가는 최소 단위. 크레이트(하입)와 검색 결과가 같은 셀을 쓴다. */
private data class Candidate(
    val trackId: Int,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
)

private fun Api.HypeItem.toCandidate() = Candidate(trackId, trackName, artistName, artworkUrl)
private fun Feed.toCandidate() = Candidate(trackId, trackName, artistName, artworkUrl)

/**
 * 픽 작성. iOS `PickComposeView` 이식.
 *
 * 소스가 **두 개**다 — 내 크레이트(하입한 곡)와 검색. 크레이트만으로 제한하지 않는 이유는
 * 담아둔 게 없는 신규 유저가 첫 픽을 못 만들기 때문이고, 검색만 두지 않는 이유는 담아두는
 * 행위가 픽으로 이어지지 않으면 하입이 아무 데도 안 쓰이기 때문이다.
 *
 * 3열 그리드인 건 아트워크로 고르는 화면이라서다. 세로 목록은 한 화면에 몇 곡 안 들어와
 * 30곡 중 고르기가 스크롤 노동이 된다.
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

    var crate by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var results by remember { mutableStateOf<List<Candidate>>(emptyList()) }
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

    LaunchedEffect(Unit) {
        Analytics.capture("pick_compose_opened")
        crate = if (HypeMock.active) {
            HypeMock.items().map { it.toCandidate() }
        } else {
            runCatching {
                val all = mutableListOf<Api.HypeItem>()
                var cursor: Long? = null
                var pages = 0
                do {
                    val res = Session.api.myHypes(cursor)
                    all += res.items
                    cursor = res.nextCursor
                    pages++
                } while (cursor != null && pages < 10)
                all.map { it.toCandidate() }
            }.getOrDefault(emptyList())
        }
        isLoading = false
    }

    val gridItems = if (activeQuery.isEmpty()) crate else results

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DSColor.background,
    ) {
        Column(Modifier.fillMaxHeight(0.94f)) {
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
                    // 검색어를 지우면 확정 상태도 풀려 크레이트 그리드로 돌아온다.
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
                            .onSuccess { results = it.items.map { i -> i.toFeed().toCandidate() } }
                            .onFailure { results = emptyList() }
                        isSearching = false
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
            )

            // 지금 보고 있는 소스가 무엇인지 + 크레이트로 돌아가는 명시적 문.
            // 검색창을 비우는 게 유일한 복귀 경로면 고른 곡들이 어디 갔는지 알 수 없다.
            Row(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (activeQuery.isEmpty()) stringResource(R.string.your_crate)
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
                            stringResource(R.string.your_crate),
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

                gridItems.isEmpty() -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (activeQuery.isEmpty()) stringResource(R.string.pick_crate_empty)
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

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(gridItems, key = { it.trackId }) { track ->
                        val number = selected.indexOfFirst { it.trackId == track.trackId }
                            .takeIf { it >= 0 }?.plus(1)
                        GridCell(track, number) {
                            if (number != null) selected.removeAll { it.trackId == track.trackId }
                            else if (selected.size < MAX_TRACKS) selected.add(track)
                        }
                    }
                }
            }

            HorizontalDivider(color = DSColor.borderLight)
            Row(
                Modifier
                    .background(DSColor.background)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    }.onSuccess {
                        // 크레이트에 없는 곡 = 검색으로 찾아 넣은 곡. 이 값이 0에 수렴하면
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
                    }
                        .onFailure {
                            error = context.getString(R.string.pick_publish_failed)
                            isSubmitting = false
                        }
                }
            },
        )
    }
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
 * 선택은 **테두리가 아니라 아트워크를 브랜드로 덮어** 알린다. 3열 그리드에선 얇은 테두리가
 * 잘 안 보이고, 덮으면 고른 것/안 고른 것이 멀리서도 갈린다(iOS와 같은 판단).
 */
@Composable
private fun GridCell(track: Candidate, number: Int?, onToggle: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(DSColor.surface),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUrl.itunesArtworkUrl(300)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (number != null) {
                Box(Modifier.fillMaxSize().background(DSColor.brand.copy(alpha = 0.55f)))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$number", color = DSColor.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            track.trackName,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artistName,
            fontSize = 10.5.sp,
            color = DSColor.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.rta.dignify.feature.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.rta.dignify.core.designsystem.PrimaryButton
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.feature.feed.FeedAudioController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DignifySeedPool"

/**
 * 서버 `SeedTracksUpdateRequest @Size(max)` · `MoodRecommender.SEEDS` · `SeedPickerScreen`의
 * 상한과 같아야 한다. 더 고르게 두면 시드가 잘려서 유저가 고른 곡 일부가 조용히 버려진다.
 */
private const val SEED_LIMIT = 3

/**
 * 온보딩 시드 고르기. iOS `SeedPoolPickerView` 이식 — 소리 2지선다 3라운드를 **대체했다.**
 *
 * 왜 바꿨나: 2지선다는 18곡 고정 풀에서 6곡만 보여줬고, 그 6곡이 취향에 안 맞으면 시드를
 * 고칠 경로가 5단계였다(피드 → 검색 → 하입 → 마이페이지 → 기준 곡 → 저장). 실측으로
 * 라운드를 완주한 33명 중 시드 저장까지 간 사람은 4명이다. 그 길은 죽어 있었다.
 * 여기선 풀 전체가 보이고 검색까지 되므로 그 경로가 화면 안으로 들어온다.
 *
 * **아트워크 탭 = 프리뷰 재생, 선택은 오른쪽 위 배지다.** 한 탭에 겹치면 소리가 아니라
 * 아는 이름으로 고르게 되는데, 그게 애초에 라운드를 만든 이유였다.
 *
 * 신규 가입과 업데이트 유저가 같은 화면을 쓴다. 다른 건 끝난 뒤에 할 일뿐이라 [onFinish]로 넘긴다.
 * **풀이 비었는지는 이 화면이 모른다** — 부르는 쪽이 [fetchSeedPool]로 먼저 받아 보고 비면 안 띄운다.
 */
@Composable
fun SeedPoolPickerScreen(
    pool: List<Api.FeedItem>,
    /**
     * 업데이트로 들어온 기존 유저인가. "왜 지금 이게 떴는지" 한 줄이 더 붙는다 —
     * 신규 가입자는 튜토리얼 끝에 이어서 보므로 그 줄이 필요 없다.
     */
    isUpdate: Boolean = false,
    /** 마지막 버튼. 인자는 실제로 고른 곡 수(건너뛰면 0). 실패를 던지면 화면에 남는다. */
    onFinish: suspend (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { FeedAudioController(context, scope) }

    // 고른 순서를 그대로 배지 번호로 쓴다.
    val selected = remember { mutableListOf<Api.FeedItem>().toMutableStateList() }
    var searchText by remember { mutableStateOf("") }
    // 확정된 검색어. 비어 있으면 인기곡 풀을 본다.
    var activeQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Api.FeedItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // 상한을 넘겨 눌렀을 때만 뜬다. 아무 일도 안 일어나면 고장으로 읽힌다.
    var limitHint by remember { mutableStateOf(false) }

    // 이 화면이 소리를 내는 동안 다른 소리가 나면 안 된다. 아래 피드는 컴포지션에서
    // 빠져 있으므로(이 화면이 탭 전체를 대신한다) 여기 플레이어만 정리하면 된다.
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    LaunchedEffect(Unit) {
        Analytics.capture(
            "onboarding_seed_shown",
            mapOf("pool_size" to pool.size, "is_update" to isUpdate),
        )
    }

    // 검색에서 고른 곡은 풀에 없을 수 있다. 앞에 붙여두지 않으면 검색어를 지우는 순간
    // 화면에서 사라져 어디로 갔는지 알 수 없다(픽 만들기와 같은 규칙).
    val gridItems = if (activeQuery.isNotEmpty()) {
        results
    } else {
        val poolIds = pool.map { it.trackId }.toSet()
        selected.filterNot { it.trackId in poolIds } + pool
    }

    fun play(item: Api.FeedItem) {
        if (item.previewUrl.isBlank()) return
        // 같은 곡을 다시 누르면 일시정지라 이벤트는 **새 곡이 시작될 때만** 찍는다 —
        // 재생/정지를 반복해도 수치가 부풀지 않아야 "재생이 선택을 돕나"를 판정할 수 있다.
        if (audio.activeTrackId != item.trackId) {
            Analytics.capture(
                "onboarding_seed_previewed",
                mapOf(
                    "track_id" to item.trackId,
                    "from" to if (activeQuery.isEmpty()) "pool" else "search",
                ),
            )
        }
        audio.togglePreview(item.trackId, item.previewUrl)
    }

    fun toggle(item: Api.FeedItem) {
        val index = selected.indexOfFirst { it.trackId == item.trackId }
        if (index >= 0) {
            selected.removeAt(index)
            return
        }
        if (selected.size >= SEED_LIMIT) {
            limitHint = true
            return
        }
        limitHint = false
        selected.add(item)
        // **검색에서 고른 비율이 이 개편의 핵심 질문이다** — 높으면 고정 풀이 취향을 못 덮는다는 뜻이다.
        Analytics.capture(
            "onboarding_seed_selected",
            mapOf(
                "track_id" to item.trackId,
                "from" to if (activeQuery.isEmpty()) "pool" else "search",
                "selected_count" to selected.size,
            ),
        )
    }

    /**
     * 고른 곡을 하입하고 끝낸다. **하입을 기다렸다가** 완료를 알린다 — 던지고 넘어가면
     * 첫 피드 요청이 하입보다 먼저 도착해 무드 정렬이 안 걸린 피드를 본다.
     * 실패한 하입은 시드가 하나 주는 것뿐이라 조용히 넘긴다.
     */
    fun finish() {
        if (isSubmitting) return
        failed = false
        isSubmitting = true
        audio.stop()
        val picked = selected.toList()
        scope.launch {
            picked.forEach { runCatching { Session.api.hype(it.trackId) } }
            Analytics.capture("onboarding_seed_done", mapOf("count" to picked.size))
            runCatching { onFinish(picked.size) }.onFailure { failed = true }
            isSubmitting = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                // 아무것도 안 고르고 나갈 길. 없으면 취향에 맞는 곡이 하나도 없는 유저가 갇힌다.
                Text(
                    stringResource(R.string.tutorial_skip),
                    style = DSTypography.bodyMedium,
                    color = DSColor.textTertiary,
                    modifier = Modifier.clickable(enabled = !isSubmitting) { selected.clear(); finish() },
                )
            }
            Text(
                stringResource(R.string.seed_title),
                style = DSTypography.title1,
                color = DSColor.textPrimary,
            )
            // 한 줄만 둔다. 고르는 화면에서 규칙을 길게 설명하면 읽기 전에 넘긴다 —
            // 하입된다는 것도, 피드가 여기서 시작한다는 것도 고르고 나면 겪어서 알게 된다.
            Text(
                stringResource(R.string.seed_hint, SEED_LIMIT),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (isUpdate) {
                Text(
                    stringResource(R.string.seed_update_note),
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        DSSearchBar(
            text = searchText,
            onTextChange = {
                searchText = it
                // 검색어를 지우면 확정 상태도 풀려 인기곡 풀로 돌아온다.
                if (it.isBlank()) activeQuery = ""
            },
            placeholder = stringResource(R.string.search_placeholder),
            onSubmit = {
                val query = searchText.trim()
                if (query.isEmpty()) return@DSSearchBar
                activeQuery = query
                isSearching = true
                Analytics.capture("onboarding_seed_searched", mapOf("query" to query))
                scope.launch {
                    // ponytail: 첫 페이지만 쓴다 — 시드 3곡을 고르는 자리라 더 내려갈 이유가 없다.
                    val res = runCatching { Session.api.search(query) }.getOrNull()
                    // 그 사이 다른 검색어가 확정됐으면 이건 지난 결과다.
                    if (query != activeQuery) return@launch
                    results = res?.items.orEmpty()
                    isSearching = false
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 14.dp),
        )

        when {
            isSearching -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DSColor.brand) }

            gridItems.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 풀 자체가 비어 있는 경우(서버 미시딩·네트워크 실패)도 여기로 온다.
                // 그땐 아래 버튼이 열려 있어 0곡으로 그냥 나갈 수 있다 — 고를 게 없는 화면에
                // 가두면 온보딩이 서버 시딩에 인질로 잡힌다.
                Text(
                    if (activeQuery.isEmpty()) stringResource(R.string.feed_load_failed)
                    else stringResource(R.string.feed_no_results, activeQuery),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            else -> Column(Modifier.weight(1f)) {
                // 검색 중일 때만 뜬다. 풀을 보고 있을 땐 헤더 문구가 이미 무엇을 고르는 자리인지 말한다.
                if (activeQuery.isNotEmpty()) {
                    Row(
                        Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.pick_results_for, activeQuery),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DSColor.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
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
                                stringResource(R.string.seed_popular_now),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DSColor.brand,
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(gridItems, key = { it.trackId }) { item ->
                        SeedCell(
                            item = item,
                            number = selected.indexOfFirst { it.trackId == item.trackId }
                                .takeIf { it >= 0 }?.plus(1),
                            isPlaying = audio.activeTrackId == item.trackId && !audio.isPaused,
                            onPlay = { play(item) },
                            onToggle = { toggle(item) },
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = DSColor.borderLight)
        Column(
            Modifier.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (limitHint) {
                Text(
                    stringResource(R.string.track_limit, SEED_LIMIT),
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.save_failed),
                    style = DSTypography.caption,
                    color = DSColor.destructive,
                    textAlign = TextAlign.Center,
                )
            }
            PrimaryButton(
                stringResource(R.string.seed_start_digging),
                busy = isSubmitting,
                // 풀이 비면 고를 수가 없다. 그때만 0곡으로도 눌린다(= 건너뛰기와 같다).
                enabled = (selected.isNotEmpty() || pool.isEmpty()) && !isSubmitting,
                onClick = ::finish,
            )
        }
    }
}

/**
 * 풀을 미리 받아 둔다. 실패·빈 응답은 빈 리스트로 뭉갠다 — 시드를 못 고르는 건
 * 막을 일이 아니라 건너뛸 일이다(그 유저는 콜드스타트 피드가 받는다).
 *
 * 화면엔 아무 말도 안 남기므로 **여기 로그가 필요하다** — 서버가 안 줬는지 디코딩이
 * 깨졌는지가 로그캣에서만 갈린다.
 */
suspend fun fetchSeedPool(): List<Api.FeedItem> = runCatching {
    // 8초를 넘기면 없는 것으로 친다. HTTP 타임아웃이 안 걸려 있어 느린 망에서는 10초 넘게
    // 매달리는데, 그동안 튜토리얼 마지막 장의 "시작하기"가 아무 반응도 못 한다.
    withTimeoutOrNull(8_000) {
        // 프리뷰가 없는 곡은 들어볼 수가 없다 — 소리로 고르는 화면에서 자리만 차지한다.
        Session.api.onboardingSeedPool().items.filter { it.previewUrl.isNotBlank() }
    }.orEmpty()
}.onFailure { Log.w(TAG, "seed pool failed", it) }.getOrDefault(emptyList())

/**
 * 아트워크 탭 = 재생, 오른쪽 위 배지 = 선택. **배지는 24dp인데 표적은 44dp다** —
 * 그리드에서 배지 크기를 그대로 표적으로 쓰면 자꾸 빗나간다.
 *
 * 재생 표시를 재생 중일 때만 띄우면 여기가 눌러서 들어보는 자리라는 걸 아무도 모른다.
 * 그래서 정지 상태에도 흐린 삼각형을 남기고, 재생 중에만 스크림을 진하게 한다.
 */
@Composable
private fun SeedCell(
    item: Api.FeedItem,
    number: Int?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(DSColor.surface)
                .clickable(onClick = onPlay),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.artworkUrl.itunesArtworkUrl(300)).crossfade(true).build(),
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
                    modifier = Modifier.size(24.dp),
                )
            }
            // 고른 것은 브랜드색으로 덮는다 — 3열에선 얇은 테두리가 멀리서 안 보인다.
            if (number != null) {
                Box(Modifier.fillMaxSize().background(DSColor.brand.copy(alpha = 0.55f)))
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .clickable(
                        onClickLabel = stringResource(
                            if (number == null) R.string.select else R.string.deselect
                        ),
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SelectBadge(number)
            }
        }
        Text(
            item.trackName,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.artistName,
            fontSize = 10.5.sp,
            color = DSColor.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 체크가 아니라 **번호**다 — 고른 순서가 곧 시드 순서라 몇 번째로 고른 건지가 보여야 한다. */
@Composable
private fun SelectBadge(number: Int?) {
    if (number == null) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
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

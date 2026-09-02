package com.rta.dignify.feature.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSFitOrScroll
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.PrimaryButton
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.feature.feed.FeedAudioController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "DignifyRounds"

/**
 * 소리 2지선다 라운드. iOS `SoundRoundsView` 이식 — 시작 화면 → 3라운드 → 마지막 한 줄.
 *
 * 두 곡을 듣고 끌리는 쪽을 고르면 **그 곡을 그대로 하입한다.** 산출물이 무드 시드라
 * 다음 피드부터 그 방향으로 정렬된다. 장르를 한 번도 묻지 않는 이유가 이것이다 —
 * 장르 이름으로 자기 취향을 말할 수 있는 사람은 많지 않고, 물어봐야 나오는 건
 * `user_genres` 몇 행뿐인데 서버는 그걸 더 이상 읽지 않는다.
 *
 * 신규 가입과 업데이트 유저가 같은 화면을 쓴다. 다른 건 끝난 뒤에 할 일뿐이라 [onFinish]로 넘긴다.
 * **후보가 비었는지는 이 화면이 모른다** — 부르는 쪽이 [fetchRounds]로 먼저 받아 보고 비면 안 띄운다.
 */
@Composable
fun SoundRoundsScreen(
    rounds: List<Api.OnboardingCandidates.Round>,
    /**
     * 업데이트로 들어온 기존 유저인가. 시작 화면에 "왜 지금 이게 떴는지" 한 줄이 더 붙는다 —
     * 신규 가입자는 튜토리얼 끝에 이어서 보므로 그 줄이 필요 없다.
     */
    isUpdate: Boolean = false,
    /** 마지막 버튼. 인자는 실제로 고른 곡 수(전부 건너뛰면 0). 실패를 던지면 화면에 남는다. */
    onFinish: suspend (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { FeedAudioController(context, scope) }

    var didStart by remember { mutableStateOf(false) }
    var roundIndex by remember { mutableIntStateOf(0) }
    // 이번 라운드에서 고른 곡. 라운드를 넘길 때 비운다. null이면 하단 버튼이 안 눌린다 —
    // 기본 선택을 두면 아무것도 안 듣고 넘긴 유저의 곡이 시드가 된다.
    var selected by remember { mutableStateOf<Api.FeedItem?>(null) }
    val picks = remember { mutableStateListOf<Pick>() }
    var isSubmitting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // 이 화면이 소리를 내는 동안 다른 소리가 나면 안 된다. 아래 피드는 컴포지션에서
    // 빠져 있으므로(이 화면이 탭 전체를 대신한다) 여기 플레이어만 정리하면 된다.
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    val round = rounds.getOrNull(roundIndex)
    // 남은 라운드가 없으면 마지막 화면이다. **상태로 굳히지 않는다** — 후보가 0개면
    // 처음부터 여기라 온보딩이 막히지 않는다.
    val isDone = roundIndex >= rounds.size

    fun play(item: Api.FeedItem) {
        if (item.previewUrl.isNotBlank()) audio.togglePreview(item.trackId, item.previewUrl)
    }

    // 라운드에 들어오면 A를 자동 재생한다(선택은 안 한다). 첫 소리까지 탭을 요구하면
    // 아무것도 안 듣고 넘기는 유저가 생긴다.
    LaunchedEffect(didStart, roundIndex) {
        if (!didStart) return@LaunchedEffect
        rounds.getOrNull(roundIndex)?.items?.firstOrNull()?.let { play(it) }
    }

    /** 고른 곡을 하입하고 다음 라운드로. 건너뛰기는 selected가 null이라 하입 없이 지나간다. */
    fun advance() {
        val item = selected
        if (item != null && round != null) {
            val isHigh = round.highTrackId?.let { it == item.trackId }
            picks += Pick(round.axis, isHigh, item.trackName, item.artistName)
            Analytics.capture(
                "onboarding_sound_picked",
                mapOf(
                    "round" to roundIndex,
                    "axis" to round.axis,
                    "pole" to (isHigh?.let { if (it) "HIGH" else "LOW" } ?: ""),
                    "track_id" to item.trackId,
                ),
            )
            // 하입은 결과를 기다리지 않는다. 실패해도 시드가 하나 줄 뿐이고, 여기서 막으면
            // 이 화면이 네트워크에 인질로 잡힌다. **화면 스코프로 쏘면 안 된다** —
            // 마지막 라운드의 하입이 "디깅 시작"과 함께 취소돼 시드가 하나 사라진다.
            Session.appScope.launch { runCatching { Session.api.hype(item.trackId) } }
        }
        audio.stop()
        selected = null
        roundIndex++
        if (roundIndex >= rounds.size) {
            Analytics.capture("onboarding_sound_completed", mapOf("picked" to picks.size))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when {
            !didStart -> IntroView(isUpdate) { didStart = true }

            isDone -> DoneView(
                picks = picks,
                isSubmitting = isSubmitting,
                failed = failed,
                onFinish = {
                    failed = false
                    isSubmitting = true
                    audio.stop()
                    scope.launch {
                        runCatching { onFinish(picks.size) }.onFailure { failed = true }
                        isSubmitting = false
                    }
                },
            )

            round != null -> RoundView(
                round = round,
                index = roundIndex,
                total = rounds.size,
                selected = selected,
                activeTrackId = audio.activeTrackId,
                isPaused = audio.isPaused,
                onChoose = { item ->
                    // 같은 카드를 다시 누르면 재생만 토글한다. **하입은 여기서 안 보낸다** —
                    // 마음이 바뀌어 다른 카드를 누르면 안 고른 곡까지 시드가 되기 때문이다.
                    if (selected?.trackId == item.trackId) audio.toggleCurrentPlayback()
                    else {
                        selected = item
                        play(item)
                    }
                },
                onSkip = {
                    Analytics.capture(
                        "onboarding_sound_skipped",
                        mapOf("round" to roundIndex, "axis" to round.axis),
                    )
                    selected = null
                    advance()
                },
                onNext = ::advance,
            )
        }
    }
}

/** 요약 한 줄. [isHigh]는 서버가 `highTrackId`를 안 줬으면 null이라 극단 표시가 빠진다. */
private data class Pick(
    val axis: String,
    val isHigh: Boolean?,
    val trackName: String,
    val artistName: String,
)

/**
 * 후보를 미리 받아 둔다. 실패·빈 응답은 빈 리스트로 뭉갠다 — 라운드를 못 보여주는 건
 * 막을 일이 아니라 건너뛸 일이다(그 유저는 콜드스타트 피드가 받는다).
 *
 * 화면엔 아무 말도 안 남기므로 **여기 로그가 필요하다** — 서버가 안 줬는지 디코딩이
 * 깨졌는지가 로그캣에서만 갈린다.
 */
suspend fun fetchRounds(): List<Api.OnboardingCandidates.Round> = runCatching {
    // 8초를 넘기면 없는 것으로 친다. HTTP 타임아웃이 안 걸려 있어 느린 망에서는 10초 넘게
    // 매달리는데, 그동안 튜토리얼 마지막 장의 "시작하기"가 아무 반응도 못 한다.
    withTimeoutOrNull(8_000) {
        // 곡이 두 개 다 안 온 라운드는 2지선다가 성립하지 않는다.
        Session.api.onboardingCandidates().rounds.filter { it.items.size == 2 }
    }.orEmpty()
}.onFailure { Log.w(TAG, "onboarding candidates failed", it) }.getOrDefault(emptyList())

/**
 * 라운드 세 화면의 뼈대 — 넘치면 스크롤되는 본문 + 자리를 먼저 확보한 하단 버튼.
 *
 * 원래는 세 화면 모두 `Spacer(weight)` 사이에 본문을 쌓고 버튼을 Column의 마지막 자식으로 뒀다.
 * **본문이 화면보다 길어지는 순간 잘리는 건 버튼이다** — Column은 가중치 없는 자식부터 재고,
 * 본문이 높이를 다 먹으면 마지막에 놓인 버튼에 남는 게 없다. 글꼴 크기를 키운 기기에서
 * 실측했다: 1080x1920/480dpi/글꼴 1.8배에서 "시작하기"가 글자가 반쯤 잘린 띠로 남고,
 * 1080x1780/글꼴 2.0배에서는 아예 안 보였다. 유저 눈에는 "버튼이 안 눌리는" 화면이다.
 *
 * `heightIn(min = maxHeight)`는 본문이 짧을 때의 가운데 정렬용 — 스크롤 안에서는 높이 제약이
 * 무한이라 이게 없으면 `Arrangement.Center`가 아무 일도 하지 않는다.
 */
@Composable
private fun RoundsPage(
    /** 본문 위 고정 줄(라운드 헤더). 없으면 자리를 차지하지 않는다. */
    top: @Composable ColumnScope.() -> Unit = {},
    /** 하단 고정 영역. 버튼과 그 위 경고 문구가 여기 온다. */
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        top()
        DSFitOrScroll(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            content = content,
        )
        bottom()
    }
}

/**
 * 라운드가 아무 말 없이 뜨면 유저는 이게 왜 떴는지, 뭘 고르는 건지 모른 채 답을 찍는다.
 * 업데이트 유저에겐 특히 그렇다 — 요청한 적 없는 화면이 앱을 켜자마자 덮는다.
 */
@Composable
private fun IntroView(isUpdate: Boolean, onStart: () -> Unit) {
    RoundsPage(
        bottom = {
            PrimaryButton(
                stringResource(R.string.tutorial_start),
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                onClick = onStart,
            )
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_hype),
                contentDescription = null,
                tint = DSColor.brand,
                modifier = Modifier.size(56.dp),
            )
            Text(
                stringResource(R.string.rounds_intro_title),
                style = DSTypography.title1,
                color = DSColor.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (isUpdate) {
                Text(
                    stringResource(R.string.rounds_intro_update),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.rounds_intro_body),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.rounds_intro_why),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.rounds_intro_length),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundView(
    round: Api.OnboardingCandidates.Round,
    index: Int,
    total: Int,
    selected: Api.FeedItem?,
    activeTrackId: Int?,
    isPaused: Boolean,
    onChoose: (Api.FeedItem) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    RoundsPage(
        top = {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1} / $total",
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.tutorial_skip),
                    style = DSTypography.bodyMedium,
                    color = DSColor.textTertiary,
                    modifier = Modifier.clickable(onClick = onSkip),
                )
            }
        },
        bottom = {
            PrimaryButton(
                stringResource(R.string.tutorial_next),
                enabled = selected != null,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                onClick = onNext,
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(axisQuestion(round.axis)),
                style = DSTypography.title1,
                color = DSColor.textPrimary,
            )
            axisPoles(round.axis)?.let {
                Text(stringResource(it), style = DSTypography.body, color = DSColor.textSecondary)
            }
            Text(
                stringResource(R.string.rounds_tap_hint),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            round.items.forEach { item ->
                CandidateCard(
                    item = item,
                    axis = round.axis,
                    isHigh = round.highTrackId?.let { it == item.trackId },
                    isSelected = selected?.trackId == item.trackId,
                    isPlaying = activeTrackId == item.trackId && !isPaused,
                    onClick = { onChoose(item) },
                )
            }
        }
    }
}

/**
 * 탭 = 이 곡을 고르면서 듣기. 넘기는 건 하단 버튼이 한다 — 카드가 선택이자 페이지 넘김이면
 * 뭘 누른 건지 알 수 없다.
 */
@Composable
private fun CandidateCard(
    item: Api.FeedItem,
    axis: String,
    isHigh: Boolean?,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.medium))
            .background(DSColor.surface)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) DSColor.brand else DSColor.surface,
                shape = RoundedCornerShape(DSRadius.medium),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.artworkUrl.itunesArtworkUrl(200))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DSColor.surface),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.trackName,
                style = DSTypography.bodyMedium,
                color = DSColor.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 고르기 전엔 극단을 숨긴다 — 알려주면 소리가 아니라 라벨을 고른다. 고른 뒤엔 반대다.
            // 뭘 고른 건지 그 자리에서 알아야 마지막 요약이 처음 보는 말이 아니게 된다.
            val poleLabel = if (isSelected && isHigh != null) poleLabel(axis, isHigh) else null
            if (poleLabel != null) {
                Text(
                    stringResource(poleLabel),
                    style = DSTypography.caption,
                    color = DSColor.brand,
                    maxLines = 1,
                )
            } else {
                Text(
                    item.artistName,
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            when {
                isSelected -> Icons.Filled.CheckCircle
                isPlaying -> Icons.Filled.PauseCircle
                else -> Icons.Filled.PlayCircle
            },
            contentDescription = null,
            tint = if (isSelected || isPlaying) DSColor.brand else DSColor.textTertiary,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * 마지막 화면. 고른 것을 그대로 되짚어 준다 — 축, 고른 쪽, 그리고 곡 이름.
 * 주장만 남기면 유저는 이 라운드가 실제로 쓰이는지 알 수 없다.
 */
@Composable
private fun DoneView(
    picks: List<Pick>,
    isSubmitting: Boolean,
    failed: Boolean,
    onFinish: () -> Unit,
) {
    RoundsPage(
        bottom = {
            if (failed) {
                Text(
                    stringResource(R.string.save_failed),
                    style = DSTypography.caption,
                    color = DSColor.destructive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                )
            }
            PrimaryButton(
                stringResource(R.string.rounds_start_digging),
                busy = isSubmitting,
                enabled = !isSubmitting,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                onClick = onFinish,
            )
        },
    ) {
        if (picks.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_hype),
                    contentDescription = null,
                    tint = DSColor.brand,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    stringResource(R.string.rounds_done_empty_title),
                    style = DSTypography.title1,
                    color = DSColor.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.rounds_done_empty_body),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.rounds_summary_title),
                    style = DSTypography.title1,
                    color = DSColor.textPrimary,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DSRadius.medium))
                        .background(DSColor.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    picks.forEach { pick ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            axisName(pick.axis)?.let {
                                Text(
                                    stringResource(it),
                                    style = DSTypography.caption,
                                    color = DSColor.textTertiary,
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val label = pick.isHigh?.let { poleLabel(pick.axis, it) }
                                if (label != null) {
                                    Text(
                                        stringResource(label),
                                        style = DSTypography.bodyMedium,
                                        color = DSColor.brand,
                                    )
                                }
                                Text(
                                    "${pick.trackName} · ${pick.artistName}",
                                    style = DSTypography.caption,
                                    color = DSColor.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.rounds_summary_footer),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 이 라운드가 무엇을 묻는지. 서버는 축 이름만 주고 설명은 앱이 붙인다 — 카피 톤과 번역이
 * 문자열 리소스에 있어야 하기 때문이다. **어느 카드가 어느 극단인지는 말하지 않는다.**
 * 모르는 축이 오면(서버가 축을 늘렸는데 앱이 옛 버전) 일반 문구로 떨어지고 라운드는 그대로 돈다.
 */
private fun axisQuestion(axis: String): Int = when (axis) {
    "arousal" -> R.string.rounds_q_arousal
    "lofi" -> R.string.rounds_q_lofi
    "valence" -> R.string.rounds_q_valence
    else -> R.string.rounds_q_default
}

private fun axisPoles(axis: String): Int? = when (axis) {
    "arousal" -> R.string.rounds_poles_arousal
    "lofi" -> R.string.rounds_poles_lofi
    "valence" -> R.string.rounds_poles_valence
    else -> null
}

private fun axisName(axis: String): Int? = when (axis) {
    "arousal" -> R.string.rounds_axis_arousal
    "lofi" -> R.string.rounds_axis_lofi
    "valence" -> R.string.rounds_axis_valence
    else -> null
}

/** 고른 쪽을 사람 말로. **고른 뒤에만 보여준다.** */
private fun poleLabel(axis: String, isHigh: Boolean): Int? = when (axis to isHigh) {
    "arousal" to true -> R.string.rounds_pole_driving
    "arousal" to false -> R.string.rounds_pole_calm
    "lofi" to true -> R.string.rounds_pole_raw
    "lofi" to false -> R.string.rounds_pole_clean
    "valence" to true -> R.string.rounds_pole_bright
    "valence" to false -> R.string.rounds_pole_dark
    else -> null
}

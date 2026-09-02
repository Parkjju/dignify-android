package com.rta.dignify.feature.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.designsystem.DSColor

/**
 * 코치마크 — 실제 UI 요소를 뚫어 보여주는 1회성 안내. iOS `CoachMarks.swift` 이식.
 * 튜토리얼 카드와 달리 **가리키는 대상이 화면에 실재한다**는 게 값이다.
 *
 * 네 곳이 한 벌을 나눠 쓴다: 피드(하입·모드 칩), 픽 탭, 마이페이지(피드를 바꾸는 설정 둘),
 * 추천 기준 곡(직접 골라 보게 하는 안내). 화면은 `CoachSeen` 플래그와 스텝 배열만 갖는다.
 *
 * **좌표를 한 줄도 안 적는다.** 대상이 `coachAnchor()`로 실측 bounds를 올리고 오버레이가
 * 그걸 받아 구멍을 뚫는다 — 유저마다 담은 곡 수도 날짜 묶음 수도 다르므로 고정 좌표는
 * 누구에게도 안 맞는다. 대상이 스크롤 밖으로 밀려 있으면 구멍 없이 카드만 띄운다.
 */
enum class CoachAnchor {
    /** 픽 탭 */
    PLAY, REACT, SHARE, COMPOSE,

    /** 피드 */
    HYPE, FEED_MODE,

    /** 마이페이지 */
    FOLLOW_SWITCH, SEED_ROW,

    /** 추천 기준 곡 */
    SEED_CELL, SEED_SAVE,
}

data class CoachStep(
    val anchor: CoachAnchor,
    val title: Int,
    val body: Int,
    /** 원형으로 뚫을지. 버튼은 원, 행·카드처럼 큰 요소는 둥근 사각형이 자연스럽다. */
    val circular: Boolean = false,
)

/** 순서 = 픽을 쓰는 순서다. 보는 것(재생) → 반응 → 공유 → 만들기. */
object PicksCoach {
    val steps = listOf(
        CoachStep(CoachAnchor.PLAY, R.string.coach_play_title, R.string.coach_play_body),
        CoachStep(CoachAnchor.REACT, R.string.coach_react_title, R.string.coach_react_body, circular = true),
        CoachStep(CoachAnchor.SHARE, R.string.coach_share_title, R.string.coach_share_body, circular = true),
        CoachStep(CoachAnchor.COMPOSE, R.string.coach_compose_title, R.string.coach_compose_body, circular = true),
    )
}

/** 피드. **하입 버튼이 먼저다** — 그게 무슨 버튼인지 모르면 나머지 설명이 성립하지 않는다. */
object FeedCoach {
    val steps = listOf(
        CoachStep(CoachAnchor.HYPE, R.string.coach_hype_title, R.string.coach_hype_body, circular = true),
        CoachStep(CoachAnchor.FEED_MODE, R.string.coach_feedmode_title, R.string.coach_feedmode_body),
    )
}

/** 마이페이지. 피드를 실제로 바꾸는 설정 둘만. 나머지 행은 읽기용이라 안내가 필요 없다. */
object MyPageCoach {
    val steps = listOf(
        CoachStep(CoachAnchor.FOLLOW_SWITCH, R.string.coach_follow_title, R.string.coach_follow_body),
        CoachStep(CoachAnchor.SEED_ROW, R.string.coach_seedrow_title, R.string.coach_seedrow_body),
    )
}

/**
 * 추천 기준 곡. 여기만 **해 보게 하는** 안내다 — 읽고 나가면 아무것도 안 바뀌는 화면이라
 * 곡 하나를 실제로 골라 저장하는 데까지 데려간다.
 */
object SeedCoach {
    val steps = listOf(
        CoachStep(CoachAnchor.SEED_CELL, R.string.coach_seedcell_title, R.string.coach_seedcell_body),
        CoachStep(CoachAnchor.SEED_SAVE, R.string.coach_seedsave_title, R.string.coach_seedsave_body),
    )
}

/**
 * "이 화면 안내를 봤는가" 플래그. iOS `@AppStorage`와 **같은 키를 쓴다** — 기기를 옮긴
 * 유저 얘기가 아니라, 두 앱의 상태 이름이 갈리면 어느 쪽 버그인지 못 가리기 때문이다.
 */
object CoachSeen {
    const val FEED = "seenFeedCoach"
    const val PICKS = "seenPicksCoach"
    const val MY_PAGE = "seenMyPageCoach"
    const val SEED = "seenSeedCoach"

    private fun prefs(context: Context) =
        context.getSharedPreferences("dignify", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)

    fun mark(context: Context, key: String) {
        prefs(context).edit().putBoolean(key, true).apply()
    }
}

/** 앵커 수집판. `CoachOverlay`가 깔고, 그 안의 `coachAnchor()`가 자기 위치를 적어 넣는다. */
private val LocalCoachAnchors =
    staticCompositionLocalOf<MutableMap<CoachAnchor, Rect>?> { null }

/**
 * 이 요소가 어디에 있는지 코치마크에 알린다. 레이아웃은 안 바뀐다.
 * null이면 아무것도 안 올린다 — 목록의 첫 셀만 앵커를 다는 분기를 호출부에서 쓰기 위해서다.
 */
@Composable
fun Modifier.coachAnchor(anchor: CoachAnchor?): Modifier {
    val anchors = LocalCoachAnchors.current
    if (anchor == null || anchors == null) return this
    return onGloballyPositioned { anchors[anchor] = it.boundsInRoot() }
}

/**
 * 화면 위에 코치마크를 얹는다. `content`가 이 화면 전부이고, 그 안의 `coachAnchor()`가
 * 대상 위치를 올린다.
 *
 * @param active 지금 띄울지. 목록이 비었거나 아직 로딩 중이면 false여야 한다 —
 *   가리킬 것이 없는 화면에서 "눌러 보세요"는 가리킬 데가 없다.
 */
@Composable
fun CoachOverlay(
    steps: List<CoachStep>,
    screen: String,
    active: Boolean,
    /**
     * 화면 위에 떠 있는 탭바가 가리는 높이. 안 주면 안내 카드가 탭바 **밑으로** 들어가
     * "다음"·"시작하기"가 반쯤 덮인다 — 덮인 자리를 누르면 탭이 바뀐다.
     * 탭바 밖에서 부르는 화면(마이페이지 하위 화면)은 0이면 된다.
     */
    bottomInset: Dp = 0.dp,
    onFinish: () -> Unit,
    content: @Composable () -> Unit,
) {
    val anchors = remember { mutableStateMapOf<CoachAnchor, Rect>() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCoachAnchors provides anchors) { content() }
        if (active) {
            // 완주율의 분모. 안내를 도중에 나가버린 유저는 finished가 없으므로 여기서만 세진다.
            // 키가 `screen`이라 이 블록이 붙어 있는 동안 한 번만 돈다 — 키 없이 두면
            // 리컴포지션마다 다시 쏴서 노출 수가 부풀려진다.
            LaunchedEffect(screen) {
                Analytics.capture("coach_shown", mapOf("screen" to screen))
            }
            CoachMarks(steps, screen, anchors, bottomInset, onFinish)
        }
    }
}

@Composable
private fun CoachMarks(
    steps: List<CoachStep>,
    /** 어느 화면의 안내인지. 네 화면이 이벤트 이름을 같이 쓰므로 이 값으로만 구분된다. */
    screen: String,
    anchors: Map<CoachAnchor, Rect>,
    bottomInset: Dp,
    onFinish: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps.getOrNull(index) ?: return
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // 뒤쪽 UI가 눌리면 안 된다. 안내 중에 재생이 시작되면 설명이 무슨 말인지 알 수 없다.
            // **소비까지 해야 막힌다** — 위에 얹기만 하면 아래 clickable이 그대로 받는다.
            // 카드의 버튼은 이 노드의 자식이라 먼저 히트되므로 안 막힌다.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val height = with(density) { maxHeight.toPx() }
        val width = with(density) { maxWidth.toPx() }
        // 스크롤로 화면 밖에 밀린 대상은 **없는 것으로 친다.** 보이지도 않는 자리에 구멍을
        // 뚫고 카드를 화면 끝에 붙이는 것보다, 구멍 없이 설명만 띄우는 편이 낫다.
        val spot = anchors[step.anchor]
            ?.inflate(pad)
            ?.takeIf { it.overlaps(Rect(0f, 0f, width, height)) }

        Dimmed(spot, step.circular)

        // 카드는 구멍 반대쪽, 그중에서도 **남는 자리가 더 넓은 쪽**에 붙는다. 상한을 두면
        // 그 상한이 카드를 구멍 위로 도로 끌어와 가리킬 자리를 덮는다(iOS가 밟은 함정).
        val below = spot == null || (height - spot.bottom) >= spot.top
        val topPad = with(density) { (if (below && spot != null) spot.bottom + pad * 3 else 0f).toDp() }
        val bottomPad =
            with(density) { (if (!below && spot != null) height - spot.top + pad * 3 else 0f).toDp() }
        // 카드가 놓일 자리. 구멍 반대쪽 여백에서 시스템 바와 탭바를 뺀 만큼이 전부다 —
        // 빼지 않으면 카드가 상태바 밑에서 시작하거나 탭바 밑으로 내려가 버튼이 덮인다.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = maxOf(topPad, statusTop), bottom = bottomPad + bottomInset)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = if (below) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            CoachCard(
                step = step,
                index = index,
                count = steps.size,
                onNext = {
                    if (index == steps.size - 1) {
                        Analytics.capture(
                            "coach_finished",
                            mapOf("screen" to screen, "last_step" to index),
                        )
                        onFinish()
                    } else {
                        index++
                    }
                },
            )
        }
    }
}

/** 딤 + 구멍. `BlendMode.Clear`는 별도 레이어 안에서만 먹으므로 `graphicsLayer`가 필수다. */
@Composable
private fun Dimmed(spot: Rect?, circular: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawRect(Color.Black.copy(alpha = 0.72f))
                if (spot == null) return@drawWithContent
                val center = spot.center
                if (circular) {
                    val r = maxOf(spot.width, spot.height) / 2
                    drawCircle(Color.Black, radius = r, center = center, blendMode = BlendMode.Clear)
                    // 테두리. 어두운 지면에서 뚫린 자리가 밝은 얼룩으로 안 읽히게 한다.
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = r, center = center, style = Stroke(2f))
                } else {
                    val radius = CornerRadius(20.dp.toPx())
                    drawRoundRect(
                        Color.Black,
                        topLeft = Offset(spot.left, spot.top),
                        size = Size(spot.width, spot.height),
                        cornerRadius = radius,
                        blendMode = BlendMode.Clear,
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.5f),
                        topLeft = Offset(spot.left, spot.top),
                        size = Size(spot.width, spot.height),
                        cornerRadius = radius,
                        style = Stroke(2f),
                    )
                }
            },
    )
}

@Composable
private fun CoachCard(step: CoachStep, index: Int, count: Int, onNext: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(DSColor.pickElevated),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 설명만 스크롤한다. `fill = false`라 짧으면 카드가 내용만큼만 커지고, 길면 남는
        // 높이까지만 차지한다 — 이걸 안 하면 높이가 모자랄 때 Column이 **마지막 자식인
        // 버튼부터** 눌러서, 글꼴 2.0배에서 "시작하기"가 5dp짜리 띠로 남았다(실측).
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(step.title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(step.body),
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        // 현재 스텝만 길쭉한 알약. 개수는 스텝 수에서 나온다.
        Row(
            Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .width(if (i == index) 20.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == index) Color.White else Color.White.copy(alpha = 0.25f))
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(50))
                .background(DSColor.brand)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (index == count - 1) R.string.tutorial_start else R.string.tutorial_next),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
}

package com.rta.dignify.feature.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography

/**
 * 피드 제스처 코치마크(디자인 레포 Components › CoachGesture). 앱에 남은 유일한 코치마크다 —
 * Picks·마이·추천 기준 곡의 설명 카드 코치마크는 feed_coachmark 1.2에서 지웠다.
 * **손 모션을 보고 실제로 따라 해야** 다음 단계로 간다. "다음" 버튼도 점도 없다.
 *
 *     더블탭(= 실제 하입, 피드 좋아요 버스트의 큰 하트가 뜬다) → 화면 아무 데나 터치 → 위로 쓸어 다음 곡
 *
 * 단계 전환·끝 판정은 피드가 한다(제스처를 받는 게 피드라서). 여기는 그림만 그린다.
 */
enum class CoachGestureStep(val title: Int, val body: Int) {
    DOUBLE_TAP(R.string.coach_gesture_doubletap_title, R.string.coach_gesture_doubletap_body),
    HYPED(R.string.coach_gesture_hyped_title, R.string.coach_gesture_hyped_body),
    SWIPE_UP(R.string.coach_gesture_swipe_title, R.string.coach_gesture_swipe_body),
}

/**
 * "피드 코치마크를 봤는가" 플래그. iOS `@AppStorage`와 **같은 키를 쓴다** — 두 앱의 상태 이름이
 * 갈리면 어느 쪽 버그인지 못 가린다.
 */
object CoachSeen {
    const val FEED = "seenFeedCoach"

    private fun prefs(context: Context) = context.getSharedPreferences("dignify", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)

    fun mark(context: Context, key: String) {
        prefs(context).edit().putBoolean(key, true).apply()
    }
}

/** 더블탭 손 주기: 1초 안에 두 번 누르고 1초 쉰다. */
const val COACH_TAP_PERIOD_MS = 2_000L

/** 스와이프 손·화면 힌트 주기. 둘이 같은 시계를 본다. */
const val COACH_SWIPE_PERIOD_MS = 2_400L

/** 손 중심이 아트워크 가운데에서 위로 얼마나(명세 `handOffset`). */
private val HAND_OFFSET = (-18).dp

/** 캡션 아래끝이 아트워크 밑으로 삐져나오는 길이(명세 `captionBelow`). */
private val CAPTION_BELOW = 25.dp

/** 스와이프 단계에서 곡 화면이 따라 올라가는 높이(명세 `peek`). */
val COACH_PEEK = 32.dp

/** 명세 `prefers-reduced-motion`에 해당 — 개발자 옵션의 "애니메이션 끄기". 켜져 있으면 손이 멈춘 채 보인다. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 스와이프 단계 화면 힌트. 시각 [t](ms, 한 주기 안)에 곡 화면이 올라가 있어야 할 비율(0~1, 1 = [COACH_PEEK]).
 * 0.3초까지 가만 → 1.15초에 꼭대기 → 1.7초까지 살짝 튕기며 제자리.
 */
fun coachPeekFraction(t: Long): Float = when {
    t < 288 -> 0f
    t < 1_152 -> PEEK_UP.transform((t - 288) / 864f)
    t < 1_728 -> 1f - PEEK_BACK.transform((t - 1_152) / 576f)
    else -> 0f
}

private val PEEK_UP = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
private val PEEK_BACK = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)
private val TAP_EASE = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val SWIPE_EASE = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

/** CSS 키프레임 한 줄. [times]는 오름차순 ms, 구간마다 [easing]을 건다. */
private fun keyframes(t: Float, times: FloatArray, values: FloatArray, easing: Easing): Float {
    if (t <= times.first()) return values.first()
    for (i in 1 until times.size) {
        if (t <= times[i]) {
            val f = easing.transform((t - times[i - 1]) / (times[i] - times[i - 1]))
            return values[i - 1] + (values[i] - values[i - 1]) * f
        }
    }
    return values.last()
}

/**
 * 아트워크 위에 얹는다(부르는 쪽이 아트워크와 같은 크기의 Box 안에서 부른다).
 * 좌표를 적지 않는다 — 손은 아트워크 가운데, 캡션은 아트워크 아래끝 기준이라 화면 높이가 달라도 안 어긋난다.
 *
 * @param clock 지금 주기의 경과 ms. 손과 스와이프 화면 힌트가 **같은 시계**를 봐야 박자가 맞아서 피드가 굴린다.
 *   람다로 받아 그리기 단계에서만 읽는다 — 값으로 받으면 프레임마다 이 컴포저블이 다시 구성된다.
 */
@Composable
fun BoxScope.CoachGesture(step: CoachGestureStep, clock: () -> Long, reduceMotion: Boolean) {
    AnimatedContent(
        targetState = step,
        // 단계가 끝나면 캡션·손이 함께 0.2초 페이드. clip = false: 기본값은 전환 중 내용을 아트워크
        // 크기로 잘라서, 아래로 25 삐져나온 캡션이 더블탭 순간 아래쪽부터 잘려 보였다.
        transitionSpec = {
            (fadeIn(tween(200)) togetherWith fadeOut(tween(200))).using(SizeTransform(clip = false))
        },
        modifier = Modifier.matchParentSize(),
        label = "coachGesture",
    ) { current ->
        Box(Modifier.fillMaxWidth()) {
            // 마크 기준선 = 아트워크 가운데 + handOffset. 높이 0인 줄이라 손·하트는 이 선에서 매달린다.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = HAND_OFFSET)
                    .size(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (current) {
                    CoachGestureStep.DOUBLE_TAP -> Hand(tapping = true, clock, reduceMotion)
                    CoachGestureStep.SWIPE_UP -> Hand(tapping = false, clock, reduceMotion)
                    // 큰 하트는 더블탭이 일으킨 피드 좋아요 버스트(FeedScreen `BigHeart`)가 그린다 —
                    // 일반 좋아요와 같은 모션 하나를 쓴다(feed_coachmark 1.2). 여기선 캡션만.
                    CoachGestureStep.HYPED -> Unit
                }
            }
            Caption(
                current,
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = CAPTION_BELOW),
            )
        }
    }
}

/**
 * 손 72, 흰색 + 그림자. 손끝이 기준선에 오도록 상자를 오른쪽 아래로 둔다(시안 `translate: 6px -14px`가
 * 상자 위끝 기준이라, 가운데 기준으로 옮기면 x +6, y +22).
 */
/** 시각 [t]의 손 모양. 그리기 단계에서 매 프레임 부른다. */
private class HandPose(val scale: Float, val alpha: Float, val dy: Float, val ringAlpha: Float, val ringScale: Float)

private fun handPose(tapping: Boolean, t: Float, reduceMotion: Boolean): HandPose = when {
    reduceMotion -> HandPose(1f, 1f, 0f, 0f, 1f)
    // 0.15s·0.48s에 누름(scale .86), 0.3s·0.63s에 뗌. 물결은 누를 때마다 커지며 사라진다.
    tapping -> HandPose(
        scale = keyframes(t, floatArrayOf(0f, 150f, 300f, 480f, 630f), floatArrayOf(1f, .86f, 1f, .86f, 1f), TAP_EASE),
        alpha = 1f,
        dy = 0f,
        ringAlpha = keyframes(
            t, floatArrayOf(0f, 150f, 330f, 360f, 480f, 690f), floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f), TAP_EASE,
        ),
        ringScale = if (t < 345f) {
            keyframes(t, floatArrayOf(0f, 330f), floatArrayOf(.4f, 1.6f), TAP_EASE)
        } else {
            keyframes(t, floatArrayOf(360f, 690f), floatArrayOf(.4f, 1.6f), TAP_EASE)
        },
    )
    // 아래(+70)에서 위(-70)로 1.2초, 0.29초에 다 나타나고 1.44초에 사라진다.
    else -> HandPose(
        scale = 1f,
        alpha = keyframes(t, floatArrayOf(0f, 288f, 1_200f, 1_440f), floatArrayOf(0f, 1f, 1f, 0f), SWIPE_EASE),
        dy = keyframes(t, floatArrayOf(0f, 1_200f), floatArrayOf(70f, -70f), SWIPE_EASE),
        ringAlpha = 0f,
        ringScale = 1f,
    )
}

/**
 * 손 72, 흰색 + 그림자. 손끝이 기준선에 오도록 상자를 오른쪽 아래로 둔다(시안 `translate: 6px -14px`가
 * 상자 위끝 기준이라, 가운데 기준으로 옮기면 x +6, y +22).
 */
@Composable
private fun Hand(tapping: Boolean, clock: () -> Long, reduceMotion: Boolean) {
    val pose = { handPose(tapping, clock().toFloat(), reduceMotion) }
    Box(
        Modifier
            .layout { measurable, constraints ->
                // 크기 0인 기준선 안에서 72짜리 상자를 넘치게 그린다.
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE))
                layout(0, 0) { p.place(-p.width / 2, -p.height / 2) }
            }
            .offset(x = 6.dp, y = 22.dp)
            .graphicsLayer {
                val p = pose()
                translationY = p.dy.dp.toPx(); alpha = p.alpha
            }
            .size(72.dp),
    ) {
        // 물결: 손끝(아이콘 24 그리드의 (10.8, 3.2)) 자리에서 28짜리 고리가 커진다.
        Box(
            Modifier
                .offset(x = 32.dp - 14.dp, y = 10.dp - 14.dp)
                .size(28.dp)
                .graphicsLayer {
                    val p = pose()
                    alpha = p.ringAlpha; scaleX = p.ringScale; scaleY = p.ringScale
                }
                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        )
        // 그림자: 벡터엔 drop-shadow가 없어서 같은 모양을 반투명 검정으로 4 아래에 깐다.
        // ponytail: 흐림 없는 그림자. 흰 아트워크 위에서 손이 묻히면 그때 RenderEffect 블러(31+)로.
        listOf(Color.Black.copy(alpha = 0.35f) to 4.dp, Color.White to 0.dp).forEach { (tint, y) ->
            Icon(
                painterResource(R.drawable.ic_hand_tap),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .offset(y = y)
                    .size(72.dp)
                    .graphicsLayer {
                        val sc = pose().scale
                        scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin(0.3f, 0.1f)
                    },
            )
        }
    }
}

/** 캡션: 아트워크 폭, 안쪽 세로 16·가로 20, radius.medium, pickElevated. */
@Composable
private fun Caption(step: CoachGestureStep, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(DSRadius.medium), ambientColor = Color.Black.copy(alpha = .35f))
            .background(DSColor.pickElevated, RoundedCornerShape(DSRadius.medium))
            .padding(vertical = 16.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(step.title),
            style = DSTypography.headline,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(step.body),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

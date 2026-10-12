package com.rta.dignify.feature.onboarding

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography

/**
 * 피드 구성 중(디자인 레포 Components › FeedBuilding). 곡 고르기를 마치고 첫 피드를 받는 동안 —
 * 고른 곡이 피드를 만든다는 걸 보여 주는 대기 화면이다. 노출 시간(최소 1초, 늦으면 응답까지)은
 * 부르는 쪽(피드)이 정한다.
 *
 * @param covers 고른 곡 아트워크 URL. 최대 3장만 그린다. 건너뛰었으면 비어 있고 문구만 뜬다.
 */
@Composable
fun FeedBuilding(covers: List<String>, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "feedBuilding")
    Box(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (covers.isNotEmpty()) {
                // 96 커버를 −20씩 겹쳐 −8°/0/8°로 펼친다. 가운데 장이 위.
                Row(
                    Modifier.padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy((-20).dp),
                ) {
                    covers.take(3).forEachIndexed { i, url ->
                        // 위로 8 둥실, 1.6초 주기, 0.2초 간격으로 차례로.
                        val lift by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = if (reduceMotion) 0f else -8f,
                            animationSpec = infiniteRepeatable(
                                tween(800, easing = EaseInOut),
                                RepeatMode.Reverse,
                                StartOffset(i * 200),
                            ),
                            label = "cover$i",
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .zIndex(if (i == 1) 1f else 0f)
                                .graphicsLayer { translationY = lift.dp.toPx() }
                                .rotate((i - 1) * 8f)
                                .size(96.dp)
                                .shadow(12.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = .18f))
                                .clip(RoundedCornerShape(12.dp))
                                .background(DSColor.surface),
                        )
                    }
                }
            }
            // 말줄임 점이 0.2초 간격으로 차례로 깜빡인다(1.2초 주기, 앞 20%만 흐림).
            // 같은 Text의 span으로 둔다 — 제목이 두 줄이 되면 따로 둔 점은 엉뚱한 줄 끝에 붙는다.
            val dots = List(3) { i ->
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        keyframes {
                            durationMillis = 1200
                            0.2f at 0
                            0.2f at 240
                            1f at 252
                        },
                        initialStartOffset = StartOffset(i * 200),
                    ),
                    label = "dot$i",
                )
            }
            val title = stringResource(R.string.feed_building_title)
            Text(
                buildAnnotatedString {
                    append(title)
                    dots.forEach { dot ->
                        val a = if (reduceMotion) 1f else dot.value
                        withStyle(SpanStyle(color = DSColor.textPrimary.copy(alpha = a))) { append(".") }
                    }
                },
                style = DSTypography.title2,
                color = DSColor.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.feed_building_body),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

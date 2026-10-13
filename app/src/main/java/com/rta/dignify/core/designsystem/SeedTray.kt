package com.rta.dignify.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.network.itunesArtworkUrl

/** 트레이 한 칸. 고른 순서대로 넘긴다. */
data class SeedTraySlot(val trackId: Int, val artworkUrl: String)

/**
 * 고른 곡 트레이(디자인 SeedTray, feed_coachmark 1.3). 온보딩 곡 고르기와 마이 「추천 기준 곡」이 같이 쓴다.
 *
 * **비어 있어도 숨기지 않는다** — 빈 점선 칸 [limit]개가 "몇 곡까지"를 말해 준다.
 * 위치(버튼 위 / 탭바 위 카드)는 부르는 쪽이 정한다.
 *
 * @param numbered 고른 순서 번호. 온보딩만 — 마이는 체크 표시라 순서가 없다.
 */
@Composable
fun SeedTray(
    slots: List<SeedTraySlot>,
    limit: Int,
    numbered: Boolean,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = slots.size >= limit
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val count = "${slots.size}/$limit"
            val label = stringResource(R.string.seed_tray_count, count)
            Text(
                buildAnnotatedString {
                    val at = label.indexOf(count)
                    append(label)
                    if (at >= 0) addStyle(SpanStyle(color = DSColor.brand), at, at + count.length)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DSColor.textPrimary,
            )
            Text(
                stringResource(if (full) R.string.seed_tray_full else R.string.seed_tray_hint),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(limit) { i ->
                val slot = slots.getOrNull(i)
                if (slot == null) EmptySlot() else FilledSlot(
                    slot = slot,
                    number = if (numbered) i + 1 else null,
                    onOpen = { onOpen(slot.trackId) },
                    onRemove = { onRemove(slot.trackId) },
                )
            }
        }
    }
}

private val SlotShape = RoundedCornerShape(12.dp)

@Composable
private fun EmptySlot() {
    Box(
        Modifier.size(52.dp).drawBehind {
            drawRoundRect(
                color = DSColor.border,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                ),
            )
        },
    )
}

@Composable
private fun FilledSlot(slot: SeedTraySlot, number: Int?, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box(Modifier.size(52.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(slot.artworkUrl.itunesArtworkUrl(200)).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(SlotShape)
                .background(DSColor.surface)
                .clickable(onClickLabel = stringResource(R.string.seed_tray_hint), onClick = onOpen),
        )
        if (number != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset((-6).dp, 6.dp)
                    .ringed(DSColor.brand),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 보이는 원은 20인데 표적은 44 — 52 칸 모서리의 20짜리 원은 손가락에 너무 작다.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(18.dp, (-18).dp)
                .size(44.dp)
                .clickable(onClickLabel = stringResource(R.string.deselect), onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.ringed(DSColor.textPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
}

/** 원 20 + 바깥 바탕색 테두리 2(디자인의 `box-shadow: 0 0 0 2px background`) — 커버와 겹친 자리를 끊어 준다. */
private fun Modifier.ringed(fill: Color) = this
    .size(24.dp)
    .clip(CircleShape)
    .background(DSColor.background)
    .padding(2.dp)
    .clip(CircleShape)
    .background(fill)

package com.rta.dignify.core.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSBrandMark
import com.rta.dignify.core.designsystem.DSColor

/** 픽 카드에 적는 곡 줄 수 상한. 더 적으면 9:16 안에서 글자가 작아져 안 읽힌다. */
private const val MAX_SHARE_ROWS = 5

/**
 * 공유 카드 하단 브랜드 블록. **세 카드가 같이 쓴다** — 카드마다 따로 그리면 폰트나 문구가
 * 갈려서 같은 앱이 만든 것으로 안 읽힌다(iOS `ShareCardFooter`와 같은 이유).
 */
@Composable
private fun ShareCardFooter() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DSBrandMark(size = 22.dp)
            Text("dignify", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(
            "dig deeper",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = Color.White.copy(alpha = 0.65f),
        )
    }
}

/**
 * 트랙 공유 카드. iOS `ShareCardView` 이식.
 *
 * 아트워크는 **미리 받아 비트맵으로 주입한다** — 렌더는 두 프레임 안에 끝나야 하는데
 * 그 사이 원격 이미지가 도착할 보장이 없다. 없으면 브랜드 그라디언트로 폴백한다.
 */
@Composable
fun TrackShareCard(
    artwork: ImageBitmap?,
    trackName: String,
    artistName: String,
    genreName: String?,
) {
    Box(Modifier.fillMaxSize()) {
        ShareBackground(artwork)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("DIGGING", Modifier.padding(top = 44.dp))
            Spacer(Modifier.weight(1f))

            Box(Modifier.padding(bottom = 28.dp)) {
                Box(
                    Modifier
                        .size(220.dp)
                        .shadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(DSColor.brandLight),
                ) {
                    artwork?.let {
                        Image(
                            BitmapPainter(it),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // 시그니처 하입 아이콘 배지 — 모서리에 걸쳐 앱 정체성을 드러낸다.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(DSColor.brand)
                        .border(2.5.dp, Color.White.copy(alpha = 0.95f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_hype),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Text(
                trackName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artistName,
                fontSize = 17.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            genreName?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(bottom = 40.dp)) { ShareCardFooter() }
        }
    }
}

/**
 * 픽 공유 카드. iOS `PickShareCardView` 이식.
 *
 * 텍스트만 보내면 받는 쪽엔 곡이 하나도 안 보이고, **픽을 열어줄 웹 페이지가 없어서
 * 링크로도 못 만든다.** 그래서 이미지 카드가 픽을 밖으로 내보내는 유일한 경로다.
 */
@Composable
fun PickShareCard(
    cover: ImageBitmap?,
    title: String,
    nickname: String,
    trackLines: List<Pair<String, String>>,
    trackCount: Int,
) {
    Box(Modifier.fillMaxSize()) {
        ShareBackground(cover, dim = 0.55f)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("PICK", Modifier.padding(top = 44.dp))
            Spacer(Modifier.weight(1f))

            // 커버 180dp. 트랙 카드(220dp)보다 작다 — 아래에 곡 목록이 들어가야 해서.
            Box(
                Modifier
                    .size(180.dp)
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(DSColor.brandLight),
            ) {
                cover?.let {
                    Image(
                        BitmapPainter(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                "@$nickname",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            // 번호 = 재생 순서. 남는 곡은 마지막 줄에 수로만 접는다 —
            // 다 적으면 9:16 안에서 글자가 작아져 아무것도 안 읽힌다.
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                trackLines.take(MAX_SHARE_ROWS).forEachIndexed { i, (track, artist) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.45f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(12.dp),
                        )
                        Text(
                            track,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            artist,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val shown = minOf(trackLines.size, MAX_SHARE_ROWS)
                // 줄이 하나도 없으면(상세 요청 실패) "+N곡"만 남아 무슨 픽인지 알 수 없다.
                // 그럴 땐 아무것도 안 적고 커버·제목으로만 성립시킨다.
                if (shown > 0 && trackCount > shown) {
                    Text(
                        stringResource(R.string.pick_more_tracks, trackCount - shown),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(bottom = 40.dp)) { ShareCardFooter() }
        }
    }
}

/**
 * 취향 유형 공유 카드. iOS `ProfileShareCardView` 이식.
 *
 * 아트워크가 없어 원격 로드가 필요 없다. 배경은 단색 그라디언트 한 장이 밋밋해서
 * 대각 베이스 + 빛 번짐 두 개를 겹친다(iOS와 같은 구성).
 */
@Composable
fun ProfileShareCard(
    typeName: String,
    flavor: String?,
    headline: String?,
    listenedCount: Int,
    hypeCount: Int,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1340), DSColor.brand, Color(0xFF140F30))
                    )
                )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF8B7BFF).copy(alpha = 0.55f), Color.Transparent),
                    radius = 900f,
                )
            )
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(stringResource(R.string.share_my_type_eyebrow), Modifier.padding(top = 56.dp))
            Spacer(Modifier.weight(1f))

            Text(
                typeName,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            flavor?.let {
                Text(
                    stringResource(R.string.stats_flavor, it),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            headline?.let {
                Text(
                    it,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 8.dp, end = 8.dp),
                )
            }
            Row(
                Modifier.padding(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                CardStat(listenedCount, stringResource(R.string.stats_listened))
                CardStat(hypeCount, stringResource(R.string.stats_hyped))
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(bottom = 44.dp)) { ShareCardFooter() }
        }
    }
}

@Composable
private fun CardStat(value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$value", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 5.sp,
        color = Color.White.copy(alpha = 0.9f),
        modifier = modifier,
    )
}

/** 아트워크를 크게 흐린 배경. 없으면 브랜드 그라디언트. */
@Composable
private fun ShareBackground(artwork: ImageBitmap?, dim: Float = 0.5f) {
    if (artwork != null) {
        Image(
            BitmapPainter(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(DSColor.brand, Color(0xFF2A2350)))
            )
        )
    }
}

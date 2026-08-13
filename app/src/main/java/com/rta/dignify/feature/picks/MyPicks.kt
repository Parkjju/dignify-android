package com.rta.dignify.feature.picks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import kotlinx.coroutines.launch

/**
 * 프로필의 "내가 만든 픽" — iOS `MyPicksSection` 이식. **요약 행 한 줄**이고 목록은
 * 눌러서 들어가는 별도 화면이다.
 *
 * 카드를 프로필에 그대로 쌓지 않는 이유(iOS 주석 그대로): 카드 한 장이 350dp쯤이라 세 장이면
 * 통계 블록 전체보다 길어진다. 크레이트가 이미 미리보기 + See all로 접혀 있어서, 픽만
 * 펼쳐두면 한 화면에 규칙이 두 개가 된다.
 *
 * 픽이 없으면 **섹션을 통째로 숨긴다** — 안 만드는 유저가 다수라 빈 껍데기를 상시 노출할
 * 이유가 없다. 로딩 스켈레톤도 같은 이유로 안 깐다.
 */
@Composable
fun MyPicksSection(onSeeAll: (List<Api.Pick>, String?) -> Unit) {
    var picks by remember { mutableStateOf<List<Api.Pick>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (PickMock.active) {
            picks = PickMock.items(mineOnly = true)
            return@LaunchedEffect
        }
        // ponytail: 실패하면 조용히 접는다 — 섹션이 없는 화면과 같아지고 다시 들어오면 재시도된다.
        // 프로필 본문(통계·크레이트)이 이미 각자 오류를 말하고 있다.
        runCatching { Session.api.picks(mine = true) }.onSuccess {
            picks = it.items
            nextCursor = it.nextCursor?.takeIf { _ -> it.hasMore == true }
        }
    }

    val latest = picks.firstOrNull() ?: return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 구분선은 섹션이 자기 위의 것을 갖는다. 픽이 없어 이 블록이 통째로 빠지면 선도 같이
        // 빠져서, 통계와 크레이트 사이에 선이 두 줄 남지 않는다.
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = DSColor.borderLight)
        Text(
            stringResource(R.string.your_picks),
            style = DSTypography.title2,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSeeAll(picks, nextCursor) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(latest.thumbnails.firstOrNull()?.itunesArtworkUrl(200))
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DSColor.pickBackground),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    latest.displayTitle(),
                    style = DSTypography.bodyMedium,
                    color = DSColor.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 다음 페이지가 남아 있으면 지금 받은 수가 총량이 아니다 — "20+"로 정직하게 쓴다.
                Text(
                    stringResource(
                        R.string.pick_count,
                        if (nextCursor == null) "${picks.size}" else "${picks.size}+",
                    ),
                    style = DSTypography.caption,
                    color = DSColor.textSecondary,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DSColor.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 내가 만든 픽 전체. iOS `MyPicksView` 이식.
 *
 * **할 수 있는 건 조회와 삭제 두 개뿐이다.** 곡 구성 수정은 삭제 후 재게시고(반응이 붙은 픽의
 * 곡이 사후에 바뀌면 그 반응이 무엇에 대한 것이었는지가 무너진다), 제목 수정 진입점은
 * 픽 탭의 `···` 한 곳에만 둔다 — 같은 동작을 두 화면에 깔면 롤백을 두 벌 관리하게 된다.
 *
 * 첫 페이지는 요약 행이 이미 받아뒀으므로 여기서 다시 안 부른다(이어받기만).
 */
@Composable
fun MyPicksScreen(
    initial: List<Api.Pick>,
    initialCursor: String?,
    onBack: () -> Unit,
    onPlay: (Api.Pick) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picks by remember { mutableStateOf(initial) }
    var cursor by remember { mutableStateOf(initialCursor) }
    var isPaging by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Api.Pick?>(null) }

    PickCardList(
        title = stringResource(R.string.your_picks),
        picks = picks,
        // 프로필에서 들어오는 화면이라 앱 기본 흰 지면이다(픽 탭만 다크).
        dark = false,
        onBack = onBack,
        onPlay = { pick ->
            // 픽 탭과 달리 position은 안 붙인다 — 내 픽 목록은 남의 픽을 고른 게 아니라
            // 내가 만든 걸 다시 트는 자리라 순위에 뜻이 없다(iOS와 동일).
            Analytics.capture(
                "pick_opened",
                mapOf("is_official" to (pick.isOfficial == true), "source" to "profile"),
            )
            onPlay(pick)
        },
        // 내 픽에 내가 반응하는 자리가 아니다. 숫자는 보여주되 버튼은 아니다.
        onReact = null,
        // `···`엔 삭제만. 신고·차단은 자기 픽에 뜻이 없다.
        onMenu = { deleteTarget = it },
        onReachEnd = {
            val c = cursor
            if (c != null && !isPaging) {
                isPaging = true
                // mine은 커서 요청에도 실어야 한다 — 커서엔 mine이 안 들어 있어 빠지면 남의 픽이 섞인다.
                runCatching { Session.api.picks(c, mine = true) }.onSuccess {
                    picks = picks + it.items
                    cursor = it.nextCursor?.takeIf { _ -> it.hasMore == true }
                }
                isPaging = false
            }
        },
    )

    // 액션시트가 아니라 알럿이다 — 되돌릴 수 없는 동작은 화면 가운데서 한 번 막아야
    // 손가락이 지나가는 자리에서 끝나지 않는다.
    deleteTarget?.let { pick ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.pick_delete_title)) },
            text = { Text(stringResource(R.string.pick_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    Analytics.capture("pick_deleted", mapOf("source" to "profile"))
                    val index = picks.indexOfFirst { it.pickId == pick.pickId }
                    picks = picks.filterNot { it.pickId == pick.pickId }
                    // 마지막 한 장을 지웠으면 볼 게 없다. 빈 목록은 뒤로 가는 것 말고 할 게 없는 화면이다.
                    if (picks.isEmpty()) onBack()
                    scope.launch {
                        if (PickMock.active) return@launch
                        runCatching { Session.api.deletePick(pick.pickId) }.onFailure {
                            // 실패하면 제자리에 되돌린다 — 안 그러면 서버엔 살아 있는 픽이
                            // 앱을 다시 켜기 전까지 화면에서만 사라진다.
                            picks = picks.toMutableList().also { l ->
                                l.add(index.coerceIn(0, l.size), pick)
                            }
                        }
                    }
                }) { Text(stringResource(R.string.withdraw_confirm), color = DSColor.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel), color = DSColor.textSecondary)
                }
            },
            containerColor = DSColor.background,
        )
    }
}

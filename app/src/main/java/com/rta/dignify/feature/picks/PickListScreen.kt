package com.rta.dignify.feature.picks

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rta.dignify.R
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.feature.push.Push
import com.rta.dignify.feature.push.PushOptInPopup
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl
import com.rta.dignify.core.share.PickShareCard
import com.rta.dignify.core.share.ShareCardRenderer
import com.rta.dignify.core.share.loadArtwork
import com.rta.dignify.core.share.shareBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 픽 지면. iOS `PickListView` 이식. 남이 고른 곡 묶음이 최신순으로 깔리고, 카드를 누르면
 * 그 자리에서 재생된다. **게시판이 아니다** — 글이 없고 곡 묶음 + 이모지 반응이 전부다.
 *
 * 앱 전체가 라이트 지면인데 여기만 다크다(iOS와 동일). 흰 배경 위 surface 카드는 대비가
 * 거의 없어 아트워크가 떠 있기만 했다.
 *
 * ponytail: 코치마크(iOS `PicksCoachMarks`)와 줌 전환은 아직 없다. 목록·재생·반응이
 * 실제로 도는 걸 본 뒤에 붙인다.
 */
/** iOS와 같은 값. 소스별 수락률이 한 표에 모이려면 문자열이 같아야 한다. */
private const val PUSH_SOURCE_PICK = "pick_created"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickListScreen(bottomInset: androidx.compose.ui.unit.Dp, onPlay: (Api.Pick) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var picks by remember { mutableStateOf<List<Api.Pick>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var isPaging by remember { mutableStateOf(false) }

    var menuTarget by remember { mutableStateOf<Api.Pick?>(null) }
    var reportTarget by remember { mutableStateOf<Api.Pick?>(null) }
    var blockTarget by remember { mutableStateOf<Api.Pick?>(null) }
    var deleteTarget by remember { mutableStateOf<Api.Pick?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Api.Pick?>(null) }
    var shareArt by remember { mutableStateOf<ImageBitmap?>(null) }
    var shareTracks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var shareReady by remember { mutableStateOf(false) }

    // 첫 픽을 올린 직후에만 알림 권한을 묻는다. 반응 알림이 알릴 대상이 방금 생겼기 때문이고,
    // 그전엔 알림이 무엇을 알리는지 유저가 겪은 적이 없다. iOS PickListView와 같은 판단.
    var justCreatedPick by remember { mutableStateOf(false) }
    var didOfferPush by rememberSaveable { mutableStateOf(false) }
    var showPushOptIn by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Analytics.capture(
            "push_permission_result",
            mapOf("granted" to granted, "source" to PUSH_SOURCE_PICK),
        )
    }

    suspend fun load() {
        isLoading = true
        loadFailed = false
        if (PickMock.active) {
            picks = PickMock.items()
            cursor = null
            isLoading = false
            return
        }
        runCatching { Session.api.picks() }
            .onSuccess { picks = it.items; cursor = it.nextCursor?.takeIf { _ -> it.hasMore == true } }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        Analytics.capture("pick_list_viewed")
        load()
    }

    // 차단·신고 숨김은 목록을 다시 받지 않고 걸러낸다 — 재요청하면 스크롤 위치와
    // 이미 받아둔 페이지가 통째로 날아간다.
    val visible = picks.filter {
        it.nickname !in LocalModeration.blocked &&
            it.pickId.toString() !in LocalModeration.hiddenPickIds
    }

    Box(Modifier.fillMaxSize().background(DSColor.pickBackground)) {
        when {
            isLoading && picks.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DSColor.pickAccent) }

            visible.isEmpty() -> EmptyPicks(
                failed = loadFailed,
                onRetry = { scope.launch { load() } },
                onCompose = { if (Session.requireAccount()) showCompose = true },
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyColumn(
                // 마지막 카드가 플로팅 버튼과 탭바 뒤로 들어가지 않게 비운다.
                // bottomInset(탭바+내비바) + 버튼 높이 48 + 버튼 위아래 간격 16*2.
                contentPadding = PaddingValues(bottom = bottomInset + 48.dp + 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.tab_picks),
                        style = DSTypography.title1,
                        color = Color.White,
                        modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp),
                    )
                }
                itemsIndexed(visible, key = { _, it -> it.pickId }) { index, pick ->
                    PickCard(
                        pick = pick,
                        onPlay = {
                            Analytics.capture(
                                "pick_opened",
                                mapOf(
                                    "position" to index,
                                    "is_official" to (pick.isOfficial == true),
                                    "source" to "picks",
                                ),
                            )
                            onPlay(pick)
                        },
                        onReact = onReact@{
                            if (!Session.requireAccount()) return@onReact
                            // 낙관적 토글. 서버 실패는 조용히 되돌린다.
                            val was = pick.myReaction == PickReaction.PRIMARY
                            picks = picks.map { if (it.pickId == pick.pickId) it.toggledReaction(!was) else it }
                            // is_replace = 다른 이모지에서 갈아탄 경우. 지금은 🔥 하나뿐이라
                            // 항상 false지만, 이모지를 늘렸을 때 교체와 신규가 안 섞이게 키를 맞춰둔다.
                            Analytics.capture(
                                "pick_reacted",
                                mapOf(
                                    "emoji" to PickReaction.PRIMARY,
                                    "is_replace" to (pick.myReaction != null && was.not() &&
                                        pick.myReaction != PickReaction.PRIMARY),
                                ),
                            )
                            scope.launch {
                                // 목업이면 로컬 토글만 하고 끝낸다(서버에 없는 pickId).
                                if (PickMock.active) return@launch
                                val ok = runCatching {
                                    if (was) Session.api.deleteReaction(pick.pickId)
                                    else Session.api.setReaction(pick.pickId, PickReaction.PRIMARY)
                                }.isSuccess
                                if (!ok) {
                                    picks = picks.map { if (it.pickId == pick.pickId) it.toggledReaction(was) else it }
                                }
                            }
                        },
                        // 메뉴 안이 신고·차단뿐이라 여는 것부터 계정을 요구한다.
                        onMenu = { if (Session.requireAccount()) menuTarget = pick },
                        onShare = {
                            Analytics.capture("pick_shared")
                            shareTarget = pick
                        },
                    )
                    // 끝 2장 이내로 접근하면 다음 페이지.
                    if (pick.pickId == visible.getOrNull(visible.size - 2)?.pickId) {
                        LaunchedEffect(pick.pickId) {
                            val c = cursor
                            if (c != null && !isPaging) {
                                isPaging = true
                                runCatching { Session.api.picks(c) }.onSuccess {
                                    picks = picks + it.items
                                    cursor = it.nextCursor?.takeIf { _ -> it.hasMore == true }
                                }
                                isPaging = false
                            }
                        }
                    }
                }
            }
        }

        if (visible.isNotEmpty()) {
            // 탭바 바로 위에 16dp 띄워 놓는다. bottomInset이 내비바까지 포함하므로
            // 이 값이 곧 "탭바 위"가 된다.
            ComposeButton(
                onClick = { if (Session.requireAccount()) showCompose = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 16.dp),
            )
        }
    }

    menuTarget?.let { pick ->
        PickMenuSheet(
            pick = pick,
            onDismiss = { menuTarget = null },
            onDelete = { menuTarget = null; deleteTarget = pick },
            onReport = { menuTarget = null; reportTarget = pick },
            onBlock = { menuTarget = null; blockTarget = pick },
        )
    }

    reportTarget?.let { pick ->
        ReportSheet(
            pick = pick,
            onDismiss = { reportTarget = null },
            onSubmit = { reason, detail ->
                reportTarget = null
                Analytics.capture(
                    "pick_reported",
                    mapOf("reason" to reason, "has_detail" to (detail != null)),
                )
                // 신고한 픽은 즉시 숨긴다 — 신고해놓고 계속 보이면 신고가 무의미하다.
                // 숨김은 목업이든 아니든 로컬이라 그대로 동작한다.
                LocalModeration.hidePick(pick.pickId)
                scope.launch {
                    PickMock.skipIfMock { Session.api.reportPick(pick.pickId, reason, detail) }
                }
            },
        )
    }

    blockTarget?.let { pick ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text(stringResource(R.string.block_user, pick.nickname)) },
            text = { Text(stringResource(R.string.block_message)) },
            confirmButton = {
                TextButton(onClick = {
                    LocalModeration.block(pick.nickname)
                    blockTarget = null
                }) { Text(stringResource(R.string.block), color = DSColor.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) {
                    Text(stringResource(R.string.cancel), color = DSColor.textSecondary)
                }
            },
            containerColor = DSColor.background,
        )
    }

    deleteTarget?.let { pick ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.pick_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    Analytics.capture("pick_deleted", mapOf("source" to "picks"))
                    picks = picks.filterNot { it.pickId == pick.pickId }
                    scope.launch { PickMock.skipIfMock { Session.api.deletePick(pick.pickId) } }
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

    // 곡 목록은 목록 응답에 없어서 상세를 한 번 더 부른다. 실패하면 곡 줄 없이
    // 커버·제목만으로 렌더한다 — 공유가 통째로 막히는 것보다 낫다.
    LaunchedEffect(shareTarget) {
        val pick = shareTarget ?: return@LaunchedEffect
        shareReady = false
        shareArt = loadArtwork(context, pick.thumbnails.firstOrNull()?.itunesArtworkUrl(600))
        shareTracks =
            runCatching { Session.api.pickDetail(pick.pickId).items.map { it.trackName to it.artistName } }
                .getOrDefault(emptyList())
        shareReady = true
    }

    if (shareReady) {
        shareTarget?.let { pick ->
            val chooser = stringResource(R.string.share)
            val title = pick.displayTitle()
            ShareCardRenderer(
                onRendered = { bmp ->
                    shareBitmap(context, bmp, chooser)
                    shareTarget = null
                    shareReady = false
                },
            ) {
                PickShareCard(
                    cover = shareArt,
                    title = title,
                    nickname = pick.nickname,
                    trackLines = shareTracks,
                    trackCount = pick.trackCount,
                )
            }
        }
    }

    if (showCompose) {
        PickComposeSheet(
            onDismiss = { showCompose = false },
            onCreated = {
                showCompose = false
                justCreatedPick = true
                scope.launch { load() }
            },
        )
    }

    // 게시 직후가 아니라 **시트가 닫힌 뒤**에 묻는다(iOS와 같은 이유 — 시트 위 팝업은
    // 시트가 내려가면서 같이 사라진다). 이미 답한 유저에겐 물어봐야 소용이 없다.
    LaunchedEffect(showCompose, justCreatedPick) {
        if (showCompose || !justCreatedPick) return@LaunchedEffect
        justCreatedPick = false
        if (didOfferPush || Session.state != AuthState.SIGNED_IN) return@LaunchedEffect
        if (!Push.canAskNotificationPermission(context)) return@LaunchedEffect
        Analytics.capture("push_optin_shown", mapOf("source" to PUSH_SOURCE_PICK))
        didOfferPush = true
        showPushOptIn = true
    }

    if (showPushOptIn) {
        PushOptInPopup(
            title = stringResource(R.string.push_optin_pick_title),
            message = stringResource(R.string.push_optin_pick_message),
            onAccept = {
                Analytics.capture("push_optin_accepted", mapOf("source" to PUSH_SOURCE_PICK))
                showPushOptIn = false
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDecline = {
                Analytics.capture("push_optin_declined", mapOf("source" to PUSH_SOURCE_PICK))
                showPushOptIn = false
            },
        )
    }
}

/** 반응 하나뿐이라 토글은 로컬에서 개수까지 같이 맞춘다. */
private fun Api.Pick.toggledReaction(on: Boolean): Api.Pick {
    val count = (reactions[PickReaction.PRIMARY] ?: 0L) + if (on) 1 else -1
    return copy(
        myReaction = if (on) PickReaction.PRIMARY else null,
        reactions = reactions + (PickReaction.PRIMARY to count.coerceAtLeast(0)),
    )
}

@Composable
fun PickCard(
    pick: Api.Pick,
    onPlay: () -> Unit,
    /** null이면 반응 버블이 **표시 전용**이 된다(내 픽 목록 — 할 수 있는 건 조회와 삭제뿐). */
    onReact: (() -> Unit)?,
    onMenu: () -> Unit,
    /** null이면 공유 버튼을 숨긴다(프리뷰 카드). */
    onShare: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(DSColor.pickSurface, RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 작성자가 맨 위 한 줄. 아바타 원은 iOS에서 뺐다 — 대부분이 digger_ 자동 닉네임이라
        // 모든 카드에 같은 글자가 찍혀 구분이 아니라 반복만 만들었다.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // weight를 주지 않는다 — 주면 남는 폭을 전부 먹으려다 짧은 닉네임까지 잘린다.
            // iOS도 닉네임만 lineLimit(1)이고, 시간은 fixedSize라 절대 안 잘린다.
            Text(
                "@${pick.nickname}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pick.isOfficial) {
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = stringResource(R.string.pick_official),
                    tint = DSColor.pickAccent,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text("·", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
            Text(
                relativeTime(pick.createdAt),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White.copy(alpha = 0.6f))
            }
        }

        // 제목이 미디어 위에 온다 — 커버를 보기 전에 무슨 묶음인지 읽힌다.
        Text(
            pick.displayTitle(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        // 배경은 1번 곡 커버를 크게 흐린 것 — 픽마다 색이 달라 그 픽의 것으로 읽히고,
        // 단색 블록처럼 비어 보이지 않는다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DSColor.pickBackground)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            pick.thumbnails.firstOrNull()?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url.itunesArtworkUrl(400)).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
            ThumbnailStack(pick.thumbnails, pick.trackCount)
        }

        // 반응·곡 수는 왼쪽. 버블이 배경을 갖는 덕에 이모지가 지면에 겉돌지 않는다.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val count = pick.reactions[PickReaction.PRIMARY] ?: 0L
            val mine = pick.myReaction == PickReaction.PRIMARY
            // 0은 안 적는다 — "0"이 적힌 카드가 깔리면 아무도 안 쓰는 지면으로 읽힌다.
            Bubble(
                text = if (count > 0) "${PickReaction.PRIMARY} $count" else PickReaction.PRIMARY,
                highlighted = mine,
                onClick = onReact,
            )
            // 곡 수는 표시 전용이라 알약을 안 씌운다 — 같은 버블을 두르면 누르면 뭔가
            // 일어날 것처럼 보인다. 이 행에서 배경 있는 것만 버튼이다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    stringResource(R.string.pick_track_count, pick.trackCount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            Spacer(Modifier.weight(1f))
            // 9:16 이미지 카드로 내보낸다. 텍스트만 보내면 받는 쪽엔 곡이 하나도 안 보이고,
            // 픽을 열어줄 웹 페이지가 없어 링크로도 못 만든다.
            onShare?.let { share ->
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(DSColor.pickElevated)
                        .clickable(onClick = share)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.share),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, highlighted: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (highlighted) Color.White else Color.White.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) DSColor.brand else DSColor.pickElevated)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/**
 * 커버 최대 3장을 겹쳐 쌓는다. iOS `PickThumbnailStack` 이식.
 *
 * 세로 카드에선 아트워크가 주인공이라 크게 잡고(150dp) 대신 겹침을 줄여 옆으로 편다.
 * 각도는 4도만 — 큰 커버가 많이 기울면 촌스럽다. 남은 곡 수는 **맨 앞 장 한가운데**에 얹어
 * 스택 전체가 "몇 곡짜리 묶음"이라는 하나의 오브젝트로 읽히게 한다.
 */
@Composable
private fun ThumbnailStack(urls: List<String>, trackCount: Int) {
    val side = 150.dp
    val shiftX = 40.dp
    val tilt = 4f
    val shown = urls.take(3)
    if (shown.isEmpty()) return
    val extra = trackCount - shown.size

    Box(
        Modifier.size(
            width = side + shiftX * (shown.size - 1).coerceAtLeast(0),
            height = side + 10.dp,
        )
    ) {
        // 뒤 장부터 그려야 1번 곡이 맨 위에 온다.
        shown.indices.reversed().forEach { index ->
            val t = if (shown.size > 1) index.toFloat() / (shown.size - 1) else 0f
            Box(
                Modifier
                    .offset(x = shiftX * t)
                    .size(side)
                    .rotate(t * tilt)
                    // 그림자가 깊이를 만든다 — 앞 장이 더 떠 보인다.
                    .shadow(
                        elevation = if (index == 0) 9.dp else 4.dp,
                        shape = RoundedCornerShape(DSRadius.large),
                    )
                    .clip(RoundedCornerShape(DSRadius.large))
                    // 다크(목록)와 라이트(만들기) 양쪽에 쓰이므로 중립 회색 자리표시.
                    .background(Color.Gray.copy(alpha = 0.25f)),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(shown[index].itunesArtworkUrl(400)).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (index == 0 && extra > 0) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+$extra", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            // 브랜드색 그림자가 목록 위에 떠 있다는 걸 만든다.
            .shadow(12.dp, CircleShape, ambientColor = DSColor.brand, spotColor = DSColor.brand)
            .clip(CircleShape)
            .background(DSColor.brand)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            stringResource(R.string.pick_new),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyPicks(
    failed: Boolean,
    onRetry: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (failed) R.string.feed_load_failed else R.string.pick_empty),
            style = DSTypography.title2,
            color = Color.White,
        )
        if (failed) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.feed_retry), color = DSColor.pickAccent)
            }
        } else {
            Text(
                stringResource(R.string.pick_empty_message),
                style = DSTypography.body,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ComposeButton(onClick = onCompose)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickMenuSheet(
    pick: Api.Pick,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DSColor.background) {
        Column(Modifier.navigationBarsPadding()) {
            // 내 픽엔 신고·차단이 없다(자기 자신을 신고할 일이 없다). 남의 픽엔 삭제가 없다.
            if (pick.isMine) {
                MenuRow(Icons.Filled.Delete, stringResource(R.string.pick_delete), true, onDelete)
            } else {
                MenuRow(Icons.Outlined.Flag, stringResource(R.string.report), false, onReport)
                HorizontalDivider(Modifier.padding(start = 20.dp), color = DSColor.borderLight)
                MenuRow(Icons.Outlined.Block, stringResource(R.string.block), true, onBlock)
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tint = if (destructive) DSColor.destructive else DSColor.textPrimary
        Icon(icon, null, tint = tint, modifier = Modifier.width(24.dp))
        Text(label, fontSize = 16.sp, color = tint)
    }
}

/** 신고 사유는 서버 enum 그대로: NICKNAME / CONTENT / OTHER. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    pick: Api.Pick,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DSColor.background) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            // iOS 사유 시트엔 제목이 없다 — 세 줄이 전부 "신고 사유"라 제목이 같은 말을 반복한다.
            // 기타는 사유를 못 받으면 운영자가 판단할 근거가 없다. 그 자리에서 입력을 연다.
            var detail by remember { mutableStateOf<String?>(null) }
            listOf(
                "NICKNAME" to R.string.report_reason_nickname,
                "CONTENT" to R.string.report_reason_content,
                "OTHER" to R.string.report_reason_other,
            ).forEach { (reason, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (reason == "OTHER") detail = "" else onSubmit(reason, null)
                        }
                        .padding(horizontal = 20.dp)
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), fontSize = 16.sp, color = DSColor.textPrimary)
                }
            }

            detail?.let { current ->
                ReportDetailDialog(
                    text = current,
                    onTextChange = { detail = it },
                    onDismiss = { detail = null },
                    onSubmit = { onSubmit("OTHER", it) },
                )
            }
        }
    }
}

/** "3분 전" 같은 상대 시각. 서버는 ISO-8601로 준다. */
@Composable
private fun relativeTime(raw: String): String {
    val then = runCatching { Instant.parse(raw) }.getOrNull() ?: return ""
    val minutes = ChronoUnit.MINUTES.between(then, Instant.now()).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes, minutes)
        minutes < 60 * 24 -> stringResource(R.string.time_hours, minutes / 60)
        else -> stringResource(R.string.time_days, minutes / (60 * 24))
    }
}


/**
 * 상단바 + 카드 목록. 픽 탭의 목록과 "내가 만든 픽" 화면이 **같은 카드**를 쓴다 —
 * 카드 모양이 두 지면에서 갈리면 같은 것으로 안 읽힌다.
 */
@Composable
fun PickCardList(
    title: String,
    picks: List<Api.Pick>,
    /** 픽 탭은 다크 지면, 프로필의 "내가 만든 픽"은 앱 기본 흰 지면이다(iOS와 동일). */
    dark: Boolean = true,
    onBack: () -> Unit,
    onPlay: (Api.Pick) -> Unit,
    onReact: ((Api.Pick) -> Unit)?,
    onMenu: (Api.Pick) -> Unit,
    onReachEnd: (suspend () -> Unit)? = null,
) {
    val bg = if (dark) DSColor.pickBackground else DSColor.background
    val fg = if (dark) Color.White else DSColor.textPrimary
    Column(Modifier.fillMaxSize().background(bg)) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg)
            }
            Text(title, style = DSTypography.title2, color = fg)
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(picks, key = { it.pickId }) { pick ->
                PickCard(
                    pick = pick,
                    onPlay = { onPlay(pick) },
                    onReact = onReact?.let { r -> { r(pick) } },
                    onMenu = { onMenu(pick) },
                )
                if (pick.pickId == picks.lastOrNull()?.pickId) {
                    LaunchedEffect(pick.pickId) { onReachEnd?.invoke() }
                }
            }
        }
    }
}


/**
 * 기타 사유 입력. 서버 `ReportCreate.detail`이 `@Size(max = 200)`이라 같은 상한을 건다.
 * 비워도 보낼 수 있게 두지 않는다 — 기타를 고르고 아무 말도 안 쓰면 운영자가 볼 게 없다.
 */
@Composable
private fun ReportDetailDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_reason_other)) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DSColor.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 200) onTextChange(it) },
                    textStyle = TextStyle(fontSize = 15.sp, color = DSColor.textPrimary),
                    cursorBrush = SolidColor(DSColor.brand),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.report_detail_hint),
                                fontSize = 15.sp,
                                color = DSColor.textTertiary,
                            )
                        }
                        inner()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text(
                    stringResource(R.string.report),
                    color = if (text.isBlank()) DSColor.textTertiary else DSColor.destructive,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = DSColor.textSecondary)
            }
        },
        containerColor = DSColor.background,
    )
}

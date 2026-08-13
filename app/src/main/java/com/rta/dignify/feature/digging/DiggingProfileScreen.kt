package com.rta.dignify.feature.digging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold
import com.rta.dignify.core.model.DiggingStats
import com.rta.dignify.core.model.DiggingType
import com.rta.dignify.core.network.Api
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import com.rta.dignify.core.share.ProfileShareCard
import com.rta.dignify.core.share.ShareCardRenderer
import com.rta.dignify.core.share.shareBitmap
import com.rta.dignify.feature.onboarding.PredictedType
import com.rta.dignify.feature.mypage.HypeCollection
import com.rta.dignify.feature.picks.MyPicksSection
import com.rta.dignify.feature.mypage.HypeMock
import com.rta.dignify.feature.mypage.HypeGrouping

/** 유형 표시명. 번역 대상이라 enum이 아니라 문자열 리소스에 둔다. */
@Composable
fun DiggingType.displayName(): String = stringResource(
    when (this) {
        DiggingType.RESTLESS_CURATOR -> R.string.type_restless_curator
        DiggingType.PURIST -> R.string.type_purist
        DiggingType.OMNIVORE -> R.string.type_omnivore
        DiggingType.LOYALIST -> R.string.type_loyalist
    }
)

@Composable
fun DiggingType.blurb(): String = stringResource(
    when (this) {
        DiggingType.RESTLESS_CURATOR -> R.string.type_restless_curator_blurb
        DiggingType.PURIST -> R.string.type_purist_blurb
        DiggingType.OMNIVORE -> R.string.type_omnivore_blurb
        DiggingType.LOYALIST -> R.string.type_loyalist_blurb
    }
)

/**
 * 유형의 "맞아 나 저래" 불릿. iOS `DiggingType.traits` — 한 줄 설명(blurb)보다 이쪽이
 * 자기 인식을 만든다.
 */
@Composable
fun DiggingType.traits(): List<String> = when (this) {
    DiggingType.RESTLESS_CURATOR -> listOf(
        stringResource(R.string.trait_curator_1),
        stringResource(R.string.trait_curator_2),
        stringResource(R.string.trait_curator_3),
    )
    DiggingType.PURIST -> listOf(
        stringResource(R.string.trait_purist_1),
        stringResource(R.string.trait_purist_2),
        stringResource(R.string.trait_purist_3),
    )
    DiggingType.OMNIVORE -> listOf(
        stringResource(R.string.trait_omnivore_1),
        stringResource(R.string.trait_omnivore_2),
        stringResource(R.string.trait_omnivore_3),
    )
    DiggingType.LOYALIST -> listOf(
        stringResource(R.string.trait_loyalist_1),
        stringResource(R.string.trait_loyalist_2),
        stringResource(R.string.trait_loyalist_3),
    )
}

/** 프로필엔 최근 3일 미리보기만. 그 이상은 See all → 하입 기록 화면. */
private const val PREVIEW_DAY_LIMIT = 3
private const val PER_DAY_PREVIEW_LIMIT = 10

/**
 * 디깅 프로필. iOS `DiggingProfileView` 이식.
 *
 * 구성 순서도 iOS와 같다 — 히어로(유형) → 헤드라인 → 볼륨 → 렌즈 블록 → 크레이트.
 * 만든 픽(`MyPicksSection`)은 크레이트 바로 위 — 만든 것 다음 모은 것 순이다.
 */
@Composable
fun DiggingProfileScreen(
    onBack: () -> Unit,
    onSeeAllHypes: () -> Unit,
    onSeeAllPicks: (List<Api.Pick>, String?) -> Unit,
) {
    var range by remember { mutableStateOf("all") }
    var stats by remember { mutableStateOf<DiggingStats?>(null) }
    var statsFailed by remember { mutableStateOf(false) }

    var hypes by remember { mutableStateOf<List<Api.HypeItem>>(emptyList()) }
    var hypeCursor by remember { mutableStateOf<Long?>(null) }
    var hypesLoading by remember { mutableStateOf(true) }
    var hypesFailed by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(range) {
        // range를 바꾸면 먼저 비운다 — 이전 숫자가 새 라벨 아래 잠깐이라도 남으면 안 된다.
        stats = null
        statsFailed = false
        runCatching { Session.api.myStats(range) }
            .onSuccess { stats = DiggingStats.from(it) }
            .onFailure { statsFailed = true }
    }

    suspend fun loadHypes() {
        hypesLoading = true
        hypesFailed = false
        if (HypeMock.active) {
            hypes = HypeMock.items()
            hypeCursor = null   // 목업은 전부 로컬이라 다음 페이지가 없다.
            hypesLoading = false
            return
        }
        runCatching {
            // 최근 3일치가 완결될 때까지 페이지를 이어 받는다. 상한(8페이지)을 두는 건 하입이
            // 많은 유저에서 이 화면 진입이 무한정 늘어나지 않게 하려는 것.
            //
            // ⚠️ `repeat`을 쓰면 안 된다 — `return@repeat`은 break가 아니라 continue라
            // 커서가 소진된 뒤에도 남은 횟수만큼 `myHypes(null)`을 다시 불러 첫 페이지를
            // 반복해 받는다. 그러면 같은 항목이 여러 번 들어가 목록 키가 충돌한다.
            val collected = mutableListOf<Api.HypeItem>()
            var cursor: Long? = null
            var pages = 0
            do {
                val res = Session.api.myHypes(cursor)
                collected += res.items
                cursor = res.nextCursor
                pages++
            } while (
                cursor != null &&
                pages < 8 &&
                collected.map { HypeGrouping.dayOf(it) }.distinct().size <= PREVIEW_DAY_LIMIT
            )
            collected.toList() to cursor
        }.onSuccess { (list, c) -> hypes = list; hypeCursor = c }
            .onFailure { hypesFailed = true }
        hypesLoading = false
    }

    LaunchedEffect(Unit) { loadHypes() }

    if (sharing) {
        val s3 = stats
        val context = LocalContext.current
        val chooser = stringResource(R.string.share)
        val typeName = s3?.type?.displayName().orEmpty()
        val same = stringResource(R.string.headline_same, s3?.hypedByGenre?.firstOrNull()?.name ?: "")
        val differ = stringResource(
            R.string.headline_differ,
            s3?.listenedByGenre?.firstOrNull()?.name ?: "",
            s3?.hypedByGenre?.firstOrNull()?.name ?: "",
        )
        if (s3 != null) {
            ShareCardRenderer(
                onRendered = { bmp -> shareBitmap(context, bmp, chooser); sharing = false },
            ) {
                ProfileShareCard(
                    typeName = typeName,
                    flavor = s3.flavorGenre,
                    headline = s3.headline(same = { same }, differ = { _, _ -> differ }),
                    listenedCount = s3.distinctListenedCount,
                    hypeCount = s3.hypeCount,
                )
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.digging_profile), onBack = onBack) {
        Column(
            Modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RangeToggle(range) { range = it }

            val s = stats
            when {
                s != null -> {
                    Hero(s)
                    Headline(s)
                    VolumePair(s)
                    LensBlock(
                        stringResource(R.string.stats_top_genres),
                        dig = s.listenedByGenre,
                        keep = s.hypedByGenre,
                    )
                    LensBlock(
                        stringResource(R.string.stats_top_artists),
                        dig = s.listenedByArtist,
                        keep = s.hypedByArtist,
                    )
                }

                statsFailed -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.feed_load_failed),
                        style = DSTypography.body,
                        color = DSColor.textSecondary,
                    )
                    TextButton(onClick = { statsFailed = false; range = range }) {
                        Text(stringResource(R.string.feed_retry), color = DSColor.brand)
                    }
                }

                else -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = DSColor.brand) }
            }

            // iOS와 같은 순서 — 만든 것 → 모은 것. 픽이 없으면 섹션이 통째로 빠진다.
            MyPicksSection(onSeeAll = onSeeAllPicks)

            CrateSection(
                hypes = hypes,
                onHypesChange = { hypes = it },
                loading = hypesLoading,
                failed = hypesFailed,
                hasMore = HypeGrouping.hasMore(
                    hypes, hypeCursor, PREVIEW_DAY_LIMIT, PER_DAY_PREVIEW_LIMIT,
                ),
                onSeeAll = onSeeAllHypes,
                onReloadNeeded = { loadHypes() },
            )

            // iOS와 같이 **맨 아래**. 통계 → 만든 픽 → 담은 곡을 다 본 뒤가 공유를 권할 자리다.
            // 유형이 확정된 사람만 — 잠금 카드를 내보내면 "아직 없음"을 자랑하는 꼴이 된다.
            val s2 = stats
            if (s2 != null && s2.isUnlocked && s2.type != null) {
                ShareTasteButton(onClick = { sharing = true })
            }
        }
    }
}

/** iOS `shareButton`. 유형이 확정된 경우에만 나온다. */
@Composable
private fun ShareTasteButton(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DSColor.brand)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.share_my_taste),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun RangeToggle(range: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(DSColor.surface)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // "week"은 달력상 이번 주가 아니라 최근 7일이다(서버 정의). 라벨은 iOS 문구를 따른다.
        listOf("week" to R.string.range_week, "all" to R.string.range_all).forEach { (key, label) ->
            val selected = range == key
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) DSColor.background else Color.Transparent)
                    .clickable { onChange(key) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) DSColor.textPrimary else DSColor.textSecondary,
                )
            }
        }
    }
}

/** 유형 카드. 확정 > 잠금 순. 잠금이어도 빈 화면이 아니라 "무엇을 더 하면 되는지"를 보여준다. */
@Composable
private fun Hero(s: DiggingStats) {
    val type = s.type
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(DSColor.brand, Color(0xFF2A2350)))
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (type != null) {
            Text(
                stringResource(R.string.your_type),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                type.displayName(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            s.flavorGenre?.let {
                Text(
                    stringResource(R.string.stats_flavor, it),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                type.blurb(),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (PredictedType.get() != null) {
            // 온보딩 퀴즈가 남긴 예상 유형. 확정 전까지 자리를 채워 두면 잠긴 화면이
            // 빈 상태가 아니라 "확인 대기 중"이 된다.
            val predicted = PredictedType.get()!!
            Text(
                stringResource(R.string.likely_your_type),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                "${predicted.emoji} ${predicted.displayName()}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    R.string.type_locked_remaining,
                    (10 - s.distinctListenedCount).coerceAtLeast(0),
                    (3 - s.hypeCount).coerceAtLeast(0),
                ),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.type_locked_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            // iOS는 "예상 유형"(온보딩 취향 퀴즈 결과)이 있을 때만 남은 개수를 안내하고,
            // 없으면 고정 문구를 쓴다. 안드로이드엔 아직 퀴즈가 없어 항상 고정 문구다 —
            // 퀴즈가 붙으면 여기에 "Dig %d more..." 분기를 되살린다.
            Text(
                stringResource(R.string.type_locked_message),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 파는 장르 vs 담는 장르. 데이터가 부족하면 아무것도 안 그린다. */
@Composable
private fun Headline(s: DiggingStats) {
    val same = stringResource(R.string.headline_same, s.hypedByGenre.firstOrNull()?.name ?: "")
    val differ = stringResource(
        R.string.headline_differ,
        s.listenedByGenre.firstOrNull()?.name ?: "",
        s.hypedByGenre.firstOrNull()?.name ?: "",
    )
    val text = s.headline(same = { same }, differ = { _, _ -> differ }) ?: return
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = DSColor.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun VolumePair(s: DiggingStats) {
    Row(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 세는 기준(5초·세션당 1회·피드 한정)이 숫자만 봐선 안 드러나서 여기만 안내를 붙인다.
        // 담은 곡은 하입 버튼을 누른 결과라 설명이 필요 없다.
        StatBox(s.distinctListenedCount, stringResource(R.string.stats_listened),
            note = stringResource(R.string.stats_listened_note), modifier = Modifier.weight(1f))
        StatBox(s.hypeCount, stringResource(R.string.stats_hyped), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(value: Int, label: String, note: String? = null, modifier: Modifier = Modifier) {
    var showNote by remember { mutableStateOf(false) }
    Column(
        modifier
            .background(DSColor.surface, RoundedCornerShape(16.dp))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$value", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DSColor.textPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = DSTypography.caption, color = DSColor.textSecondary)
            if (note != null) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = DSColor.textTertiary,
                    modifier = Modifier.size(14.dp).clickable { showNote = true },
                )
            }
        }
    }
    if (showNote && note != null) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            text = { Text(note, style = DSTypography.body, color = DSColor.textPrimary) },
            confirmButton = {
                TextButton(onClick = { showNote = false }) {
                    Text(stringResource(R.string.ok), color = DSColor.brand)
                }
            },
            containerColor = DSColor.background,
        )
    }
}

/**
 * 탐색(들은 것) vs 담음(하입) 두 렌즈를 나란히. 첫인상에 구분되도록 컬럼마다 고유
 * 아이콘·색·배경을 준다 — explore=헤드폰/회색, keep=삽/브랜드.
 */
private enum class Lens { EXPLORE, KEEP }

@Composable
private fun LensBlock(title: String, dig: List<DiggingStats.Count>, keep: List<DiggingStats.Count>) {
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = DSTypography.title2, color = DSColor.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LensColumn(Lens.EXPLORE, dig, Modifier.weight(1f))
            LensColumn(Lens.KEEP, keep, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LensColumn(lens: Lens, items: List<DiggingStats.Count>, modifier: Modifier = Modifier) {
    val tint = if (lens == Lens.EXPLORE) DSColor.textSecondary else DSColor.brand
    val bg = if (lens == Lens.EXPLORE) DSColor.surface else DSColor.brandLight
    Column(
        modifier
            .background(bg, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (lens == Lens.EXPLORE) {
                Icon(Icons.Filled.Headphones, null, tint = tint, modifier = Modifier.size(12.dp))
            } else {
                Icon(
                    painterResource(R.drawable.ic_hype), null,
                    tint = tint, modifier = Modifier.size(13.dp),
                )
            }
            Text(
                stringResource(if (lens == Lens.EXPLORE) R.string.lens_explore else R.string.lens_keep),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
        if (items.isEmpty()) {
            Text("—", style = DSTypography.body, color = DSColor.textTertiary)
        } else {
            items.take(5).forEach { item ->
                // 이름은 남는 폭을 다 먹고(길면 말줄임) 숫자는 오른쪽 끝에 고정된다.
                // weight를 이름에 주지 않으면 숫자 위치가 이름 길이를 따라 들쭉날쭉해진다.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = DSTypography.bodyMedium,
                        color = DSColor.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${item.count}", style = DSTypography.caption, color = DSColor.textSecondary)
                }
            }
        }
    }
}

/** 내 크레이트 = 하입한 트랙. 최근 3일 미리보기 + See all. */
@Composable
private fun CrateSection(
    hypes: List<Api.HypeItem>,
    onHypesChange: (List<Api.HypeItem>) -> Unit,
    loading: Boolean,
    failed: Boolean,
    hasMore: Boolean,
    onSeeAll: () -> Unit,
    onReloadNeeded: suspend () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 통계 → 담은 곡이 한 스크롤에 이어 붙어 어디까지가 뭔지 안 읽혔다.
        // 각 섹션이 자기 위의 구분선을 갖는다.
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = DSColor.borderLight)
        Text(
            stringResource(R.string.your_crate),
            style = DSTypography.title2,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        when {
            loading && hypes.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DSColor.brand) }

            hypes.isEmpty() -> Text(
                stringResource(if (failed) R.string.feed_load_failed else R.string.no_hyped_tracks),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )

            else -> {
                HypeCollection(
                    items = hypes,
                    onItemsChange = onHypesChange,
                    maxGroups = PREVIEW_DAY_LIMIT,
                    perDayLimit = PER_DAY_PREVIEW_LIMIT,
                    onReloadNeeded = onReloadNeeded,
                    onSeeAll = if (hasMore) onSeeAll else null,
                )
                if (hasMore) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSeeAll)
                            .padding(horizontal = 20.dp)
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.see_all_hypes),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DSColor.brand,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = DSColor.brand,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

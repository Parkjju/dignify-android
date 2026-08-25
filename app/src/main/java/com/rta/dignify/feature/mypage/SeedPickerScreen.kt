package com.rta.dignify.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold
import com.rta.dignify.core.network.Api
import com.rta.dignify.feature.onboarding.CoachAnchor
import com.rta.dignify.feature.onboarding.CoachOverlay
import com.rta.dignify.feature.onboarding.CoachSeen
import com.rta.dignify.feature.onboarding.SeedCoach
import com.rta.dignify.feature.onboarding.coachAnchor
import kotlinx.coroutines.launch

/**
 * 서버 `SeedTracksUpdateRequest @Size(max)` 및 `MoodRecommender.SEEDS`와 같아야 한다.
 * 더 고르게 두면 서버가 400으로 튕기거나 조용히 잘라낸다.
 */
private const val SEED_LIMIT = 3

/**
 * 추천 기준 곡 고르기. iOS `SeedPickerView` 이식. 하입한 곡 중 최대 [SEED_LIMIT]개를 고정하면
 * 서버가 최근 하입 대신 그 곡들만 시드로 쓴다.
 *
 * **아무것도 고르지 않은 상태가 기본이고 그게 정상이다.** 그때는 종전대로 최근 하입 세 곡이
 * 시드라, 이 화면에 한 번도 안 들어온 유저의 피드가 달라지지 않는다.
 *
 * 목록은 `HypeCollection`을 선택 모드로 재사용한다 — 날짜 묶음·페이지네이션·썸네일 셀이
 * 하입 기록 화면과 같아야 "내가 담은 곡 중에서 고르는 것"이라는 게 한눈에 읽힌다.
 */
@Composable
fun SeedPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<Api.HypeItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 저장 성공 여부의 기준. 서버에서 받은 그대로를 들고 있다가 비교한다.
    var savedSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var cursor by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPaging by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    suspend fun load() {
        isLoading = true
        loadFailed = false
        runCatching { Session.api.myHypes() }
            .onSuccess { res ->
                items = res.items
                cursor = res.nextCursor
                // 첫 페이지 밖에 고정된 곡이 있어도 여기선 안 보인다. 페이지를 더 받으면
                // 합쳐지므로, 저장할 때 화면에 없던 선택을 지우지 않게 합집합으로 들고 간다.
                selected = res.items.filter { it.isSeed == true }.map { it.trackId }.toSet()
                savedSelection = selected
                // 저장까지 간 비율을 보려면 분모가 필요하다. 목록을 못 받은 진입은 세지 않는다 —
                // 그때는 고를 수가 없어서 저장 안 한 게 유저의 선택이 아니다.
                Analytics.capture("seed_picker_opened", mapOf("pinned" to savedSelection.size))
            }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    // 고를 곡이 있을 때만. 빈 목록에서 "눌러 보세요"는 가리킬 데가 없다.
    var seenCoach by remember { mutableStateOf(CoachSeen.get(context, CoachSeen.SEED)) }

    CoachOverlay(
        steps = SeedCoach.steps,
        screen = "seed_picker",
        active = !seenCoach && !isLoading && items.isNotEmpty(),
        onFinish = { CoachSeen.mark(context, CoachSeen.SEED); seenCoach = true },
    ) {
        ScreenScaffold(
            title = stringResource(R.string.seed_picker_title),
            onBack = onBack,
            trailing = {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = DSColor.brand,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(horizontal = 16.dp).size(20.dp),
                    )
                } else {
                    // 바꾼 게 없으면 누를 이유가 없다. 저장을 눌렀는데 아무 일도 안 일어나면
                    // 실패한 것처럼 보인다.
                    val enabled = selected != savedSelection
                    Text(
                        stringResource(R.string.save),
                        style = DSTypography.bodyMedium,
                        color = if (enabled) DSColor.brand else DSColor.textTertiary,
                        modifier = Modifier
                            .coachAnchor(CoachAnchor.SEED_SAVE)
                            .clickable(enabled = enabled) {
                                saveFailed = false
                                isSaving = true
                                scope.launch {
                                    runCatching { Session.api.setSeedTracks(selected.toList()) }
                                        .onSuccess {
                                            savedSelection = selected
                                            // 0도 의미 있는 값이다 — 고정을 전부 풀고
                                            // 최근 하입으로 되돌린 것.
                                            Analytics.capture(
                                                "seed_saved",
                                                mapOf("count" to selected.size),
                                            )
                                            // 기준 곡이 바뀌면 정렬 기준 자체가 바뀐다. 들고 있던
                                            // 커서로 이어 보면 옛 기준으로 뽑힌 페이지가 계속 나와서
                                            // 방금 고른 게 반영이 안 된 것처럼 보인다.
                                            Session.onSeedsChanged()
                                            onBack()
                                        }
                                        .onFailure { saveFailed = true }
                                    isSaving = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.seed_picker_header, SEED_LIMIT),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                )
                Text(
                    stringResource(R.string.seed_picker_default, SEED_LIMIT),
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                )
                // 꺼진 채로 들어올 수 있다. 고른 것이 지금 아무 데도 안 쓰인다는 걸 말해 주지
                // 않으면 저장하고 나서 피드가 그대로인 것을 고장으로 읽는다.
                if (!Session.diggingMode) {
                    Text(
                        stringResource(R.string.seed_picker_mode_off),
                        style = DSTypography.caption,
                        color = DSColor.brand,
                    )
                }
                if (saveFailed) {
                    Text(
                        stringResource(R.string.save_failed),
                        style = DSTypography.caption,
                        color = DSColor.destructive,
                    )
                }
            }

            when {
                isLoading && items.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = DSColor.brand) }

                // 하입이 없으면 고를 것도 없다. 빈 목록에 "고르세요"만 남기지 않는다.
                items.isEmpty() -> Text(
                    stringResource(
                        if (loadFailed) R.string.feed_load_failed else R.string.no_hyped_tracks
                    ),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                )

                else -> HypeCollection(
                    items = items,
                    onItemsChange = { items = it },
                    selection = selected,
                    onSelectionChange = { selected = it },
                    selectionLimit = SEED_LIMIT,
                    coachAnchors = true,
                    onReachEnd = {
                        val c = cursor
                        if (c != null && !isPaging) {
                            isPaging = true
                            runCatching { Session.api.myHypes(c) }.onSuccess { res ->
                                items = items + res.items
                                cursor = res.nextCursor
                                // 뒤 페이지에 있던 고정 곡을 이제야 알게 된다. 유저가 아직 손대지
                                // 않은 것만 더한다 — **방금 해제한 곡이 되살아나면 안 된다.**
                                res.items.filter { it.isSeed == true }
                                    .map { it.trackId }
                                    .filterNot { it in savedSelection }
                                    .forEach {
                                        selected = selected + it
                                        savedSelection = savedSelection + it
                                    }
                            }
                            isPaging = false
                        }
                    },
                    onReloadNeeded = { load() },
                )
            }
        }
    }
}

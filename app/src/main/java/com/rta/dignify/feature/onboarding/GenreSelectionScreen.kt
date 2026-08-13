package com.rta.dignify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSGenreChip
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.network.Api
import kotlinx.coroutines.launch

/** iOS와 같은 값. 이 이상 고르면 "취향"이 아니라 "전체"가 돼서 피드가 개인화되지 않는다. */
private const val MAX_PICKS = 3

/**
 * 장르 선택. 두 자리에서 같이 쓴다 — 화면이 똑같아서 따로 만들 이유가 없다.
 *
 *  - **온보딩**(`onDone == null`): 로그인 직후 `isOnboardingComplete == false`인 유저.
 *    저장하면 온보딩 완료까지 찍고 세션 상태가 바뀐다.
 *  - **설정**(`onDone != null`): 마이페이지에서 진입. 현재 장르를 채워 열고, 저장하면
 *    피드만 다시 받게 한 뒤 `onDone`으로 돌아간다(온보딩 완료는 이미 찍혀 있다).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionScreen(
    onDone: (() -> Unit)? = null,
    /** 취향 테스트가 추천한 장르(genreNameEn). 화면이 뜰 때 미리 골라둔다. */
    recommended: List<String> = emptyList(),
    /** 온보딩에서만 넘어온다. 누르면 취향 테스트로. */
    onTakeQuiz: (() -> Unit)? = null,
) {
    val isSettings = onDone != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var genres by remember { mutableStateOf<List<Api.Genre>>(emptyList()) }
    val selected = remember { mutableListOf<Int>().toMutableStateList() }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        genres = runCatching { Session.api.genres().genres }.getOrDefault(emptyList())
        // 설정으로 들어왔으면 지금 장르를 채워둔다 — 빈 화면에서 다시 고르게 하면
        // 무엇을 고르고 있었는지 유저가 기억해내야 한다.
        if (isSettings) {
            runCatching { Session.api.myProfile().genres.map { it.genreId } }
                .onSuccess { selected.addAll(it.take(MAX_PICKS)) }
        } else if (recommended.isNotEmpty()) {
            // 퀴즈 결과를 미리 골라둔다. 잠그지는 않는다 — 추천이지 결정이 아니다.
            val byName = genres.associateBy { it.genreNameEn }
            selected.addAll(recommended.mapNotNull { byName[it]?.genreId }.take(MAX_PICKS))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            // 이 화면만 빠져 있어서 완료 버튼이 제스처 바에 붙어 있었다.
            // 아래 24dp는 iOS bottomBar의 `.padding(.bottom, 24)`와 같은 값.
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        // 설정에서 들어왔을 때만 뒤로가기. 온보딩은 돌아갈 곳이 없다.
        // `onDone`을 그대로 쓴다 — 저장 여부와 무관하게 "마이페이지로 돌아간다"는 같은 동작이다.
        onDone?.let { done ->
            IconButton(
                onClick = done,
                // 아이콘(24dp)이 IconButton(48dp) 한가운데라 그냥 두면 제목보다 12dp 안쪽에 선다.
                modifier = Modifier.offset(x = (-12).dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = DSColor.textPrimary,
                )
            }
        }
        Text(
            stringResource(R.string.onboarding_title),
            style = DSTypography.title1,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(top = if (isSettings) 8.dp else 32.dp),
        )
        Text(
            stringResource(R.string.onboarding_subtitle, MAX_PICKS),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        // 설정에서만 설명을 펼칠 수 있다. 온보딩은 퀴즈가 골라주므로 칩만으로 충분하고,
        // 여긴 이미 앱을 쓰는 사람이 직접 고르는 자리라 칩만으론 부족할 때가 있다.
        if (isSettings) {
            Text(
                stringResource(if (showGuide) R.string.genre_hide_guide else R.string.genre_show_guide),
                style = DSTypography.bodyMedium,
                color = DSColor.brand,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { showGuide = !showGuide }
                    .padding(bottom = 12.dp),
            )
        }

        if (isSettings && showGuide) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    val isSelected = genre.genreId in selected
                    GenreGuideRow(
                        genre = genre,
                        isSelected = isSelected,
                        isDisabled = !isSelected && selected.size >= MAX_PICKS,
                    ) {
                        if (isSelected) selected.remove(genre.genreId) else selected.add(genre.genreId)
                    }
                }
            }
        } else FlowRow(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { genre ->
                val isSelected = genre.genreId in selected
                DSGenreChip(
                    title = genre.genreName,
                    isSelected = isSelected,
                    // 상한에 걸린 칩은 눌리지 않는 대신 흐려진다 — 눌러본 뒤에야 안 된다고 알려주면
                    // 왜 안 되는지를 유저가 추측해야 한다.
                    isDisabled = !isSelected && selected.size >= MAX_PICKS,
                ) {
                    if (isSelected) selected.remove(genre.genreId) else selected.add(genre.genreId)
                }
            }
        }

        // 고른 장르를 이름으로 되읽어준다. 칩이 스크롤 밖으로 나가면 무엇을 골랐는지
        // 화면에서 사라져서, 3개 제한에 걸렸을 때 무엇을 빼야 할지가 안 보인다.
        if (selected.isNotEmpty()) {
            val names = genres.filter { it.genreId in selected }.map { it.genreName }.sorted()
            Text(
                stringResource(R.string.genres_selected, names.joinToString(", ")),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        error?.let {
            Text(it, color = DSColor.destructive, style = DSTypography.caption)
        }

        // 온보딩에선 "테스트 하기", 설정에선 "다시 하기". iOS도 두 자리에서 같은 퀴즈를 연다 —
        // 취향은 변하는데 재응시 경로가 없으면 첫 결과에 갇힌다.
        onTakeQuiz?.let { quiz ->
            Text(
                stringResource(if (isSettings) R.string.quiz_retake else R.string.quiz_start),
                style = DSTypography.bodyMedium,
                color = DSColor.brand,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        // 웹 랜딩(dignify.web.app)이 같은 PostHog 프로젝트에 quiz_*를 보낸다.
                        // 앱 이벤트는 onboarding_ 접두사로 분리해야 두 퍼널이 안 섞인다.
                        Analytics.capture(
                            "onboarding_quiz_started",
                            mapOf("source" to quizSource(isSettings)),
                        )
                        quiz()
                    }
                    .padding(vertical = 12.dp),
            )
        }

        Button(
            onClick = {
                if (isSubmitting) return@Button
                isSubmitting = true
                error = null
                scope.launch {
                    try {
                        Session.api.updateGenres(selected.toList())
                        if (isSettings) {
                            Session.onGenresChanged()
                            onDone()
                        } else {
                            Session.api.completeOnboarding()
                            Session.onOnboardingComplete()
                        }
                    } catch (e: Exception) {
                        error = context.getString(R.string.genres_save_failed)
                        isSubmitting = false
                    }
                }
            },
            enabled = selected.isNotEmpty() && !isSubmitting,
            shape = RoundedCornerShape(DSRadius.medium),
            colors = ButtonDefaults.buttonColors(containerColor = DSColor.brand),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (isSubmitting) {
                // SignInScreen과 같은 이유로 size() — height()만 주면 원이 찌그러진다.
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

package com.rta.dignify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.network.Api
import com.rta.dignify.core.model.DiggingType
import kotlinx.coroutines.launch
import com.rta.dignify.feature.digging.blurb
import com.rta.dignify.feature.digging.traits
import com.rta.dignify.feature.digging.displayName

/**
 * 온보딩과 재검사가 같은 화면을 쓴다. 태그가 없으면 재검사 중도이탈이 온보딩 퍼널에 섞여
 * 이탈률이 실제보다 나빠 보인다. 값은 iOS와 같아야 한다.
 */
internal fun quizSource(isSettings: Boolean) = if (isSettings) "retake" else "onboarding"

/**
 * 취향 테스트. iOS `TasteQuizView` + `TasteResultView` 이식.
 *
 * 한 화면에 한 문항. 고르면 바로 다음으로 넘어간다 — "다음" 버튼을 두면 문항마다 탭이
 * 두 번이 되고, 11문항이면 그 차이가 이탈로 나타난다.
 *
 * 결과 화면에서 **장르 저장까지 끝낸다** — 추천을 확인한 자리에서 바로 시작하는 게
 * 자연스럽고, 장르 화면으로 다시 보내면 방금 본 추천을 또 고르게 된다.
 * "직접 고를래요"를 누른 사람만 장르 화면으로 간다.
 *
 * @param onFinished 저장까지 끝난 뒤. 온보딩은 세션 상태가 바뀌고, 설정은 화면을 닫는다.
 * @param onEditManually 추천을 안 쓰고 직접 고르겠다고 했을 때. 추천 장르를 들려 보낸다.
 */
@Composable
fun TasteQuizScreen(
    isSettings: Boolean = false,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
    onEditManually: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val answers = remember { mutableListOf<Int>().toMutableStateList() }
    var result by remember { mutableStateOf<TasteQuiz.Result?>(null) }
    var genres by remember { mutableStateOf<List<Api.Genre>>(emptyList()) }
    val selected = remember { mutableListOf<Int>().toMutableStateList() }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        genres = runCatching { Session.api.genres().genres }.getOrDefault(emptyList())
    }

    result?.let { r ->
        // 추천을 미리 체크해 둔다. 화면이 처음 뜰 때 한 번만.
        LaunchedEffect(r, genres) {
            if (selected.isEmpty() && genres.isNotEmpty()) {
                selected.addAll(r.genreNames.mapNotNull { n -> genres.firstOrNull { it.genreNameEn == n }?.genreId })
            }
        }
        TasteResult(
            result = r,
            genres = genres,
            selected = selected,
            isBusy = isBusy,
            error = error,
            primaryLabel = if (isSettings) R.string.genre_apply else R.string.quiz_apply,
            onStart = {
                isBusy = true
                error = null
                scope.launch {
                    try {
                        Session.api.updateGenres(selected.toList())
                        if (isSettings) Session.onGenresChanged()
                        else {
                            Session.api.completeOnboarding()
                            Session.onOnboardingComplete()
                        }
                        onFinished()
                    } catch (e: Exception) {
                        error = context.getString(R.string.genres_save_failed)
                        isBusy = false
                    }
                }
            },
            onEditManually = { onEditManually(r.genreNames) },
        )
        return
    }

    val index = answers.size
    val question = TasteQuiz.questions[index]

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            // 첫 문항엔 돌아갈 곳이 없다. 이전 문항으로 가면 그 답도 같이 지운다 —
            // 남겨두면 다시 고른 답이 뒤에 덧붙어 문항과 답의 짝이 어긋난다.
            if (index > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = DSColor.textSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { answers.removeAt(answers.lastIndex) },
                )
            }
            Text(
                stringResource(R.string.quiz_skip),
                style = DSTypography.caption,
                color = DSColor.textTertiary,
                modifier = Modifier.align(Alignment.CenterEnd).clickable {
                    // 몇 번째 문항에서 나갔는지가 문항 수를 줄일지 판단하는 근거가 된다.
                    Analytics.capture(
                        "onboarding_quiz_abandoned",
                        mapOf("at_question" to index, "source" to quizSource(isSettings)),
                    )
                    onSkip()
                },
            )
        }

        // 진행 막대. 남은 문항 수가 안 보이면 11문항이 끝없이 느껴진다.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(DSColor.borderLight)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((index + 1).toFloat() / TasteQuiz.questions.size)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DSColor.brand)
            )
        }

        Text(
            stringResource(question.prompt),
            style = DSTypography.title1,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(top = 40.dp, bottom = 28.dp),
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            question.options.forEachIndexed { i, option ->
                Text(
                    stringResource(option.label),
                    style = DSTypography.body,
                    color = DSColor.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DSRadius.medium))
                        .border(1.dp, DSColor.border, RoundedCornerShape(DSRadius.medium))
                        .clickable {
                            answers.add(i)
                            // 마지막 문항까지 답했으면 바로 채점하고 예상 유형을 남긴다.
                            if (answers.size == TasteQuiz.questions.size) {
                                val r = TasteQuiz.result(answers.toList())
                                PredictedType.save(r.type)
                                // 답안까지 통째로 보낸다 — 어느 문항이 유형을 가르는지는
                                // 배점을 바꿔보기 전엔 모르고, 원본 답이 없으면 재계산이 안 된다.
                                Analytics.capture(
                                    "onboarding_quiz_completed",
                                    mapOf(
                                        "type" to r.type.key,
                                        "genres" to r.genreNames,
                                        "answers" to answers.toList(),
                                        "source" to quizSource(isSettings),
                                    ),
                                )
                                result = r
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/**
 * 결과 — 유형 + 추천 장르(미리 선택됨). iOS `TasteResultView` 이식.
 *
 * 유형을 **"예상"이라고 못 박는다.** 11문항은 얇은 데이터라 정체성을 확정할 근거가 못 되고,
 * 확정은 실제 청취·하입에서 나온다(`DiggingStats.type`). "예상 → 확정"의 어긋남 자체가
 * 나중에 보여줄 콘텐츠라서, 여기서 단정하면 그 서사를 잃는다.
 *
 * 장르 설명이 **여기** 붙는 이유: 추천을 받아든 순간이 "이게 뭔데?"가 나오는 자리다.
 * 칩만 늘어놓는 온보딩 화면엔 설명을 안 붙인다(퀴즈가 대신 골라주므로).
 */
@Composable
private fun TasteResult(
    result: TasteQuiz.Result,
    genres: List<Api.Genre>,
    selected: MutableList<Int>,
    isBusy: Boolean,
    error: String?,
    primaryLabel: Int,
    onStart: () -> Unit,
    onEditManually: () -> Unit,
) {
    // 추천 장르를 서버 목록과 맞춘다. 못 찾으면 그 줄은 빠진다(카탈로그가 바뀐 경우).
    val recommended = result.genreNames.mapNotNull { name -> genres.firstOrNull { it.genreNameEn == name } }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // ── 유형 카드
            Column(
                Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(result.type.emoji, fontSize = 56.sp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.quiz_likely_type),
                        style = DSTypography.caption,
                        color = DSColor.textTertiary,
                    )
                    Text(
                        result.type.displayName(),
                        style = DSTypography.display,
                        letterSpacing = (-1).sp,
                        color = DSColor.brand,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        result.type.blurb(),
                        style = DSTypography.body,
                        color = DSColor.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                // "맞아 나 저래" 불릿. blurb 한 줄보다 이쪽이 자기 인식을 만든다.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DSRadius.medium))
                        .background(DSColor.brandLight)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.type.traits().forEach { trait ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(DSColor.brand)
                            )
                            Text(trait, style = DSTypography.body, color = DSColor.textPrimary)
                        }
                    }
                }

                Text(
                    stringResource(R.string.quiz_confirm_later),
                    style = DSTypography.caption,
                    color = DSColor.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            // ── 추천 장르
            if (recommended.isEmpty()) {
                Text(
                    stringResource(R.string.quiz_genres_failed),
                    style = DSTypography.body,
                    color = DSColor.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                Column(
                    Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.quiz_genre_title),
                            style = DSTypography.title2,
                            color = DSColor.textPrimary,
                        )
                        Text(
                            stringResource(R.string.quiz_genre_subtitle),
                            style = DSTypography.caption,
                            color = DSColor.textTertiary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recommended.forEach { genre ->
                            GenreGuideRow(
                                genre = genre,
                                isSelected = genre.genreId in selected,
                                onToggle = {
                                    if (genre.genreId in selected) selected.remove(genre.genreId)
                                    else selected.add(genre.genreId)
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 하단 바
        HorizontalDivider(color = DSColor.divider)
        Column(
            Modifier.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let {
                Text(
                    it,
                    style = DSTypography.caption,
                    color = DSColor.destructive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (recommended.isEmpty()) {
                // 추천이 하나도 안 잡힌 상태에서 주 버튼을 비활성으로 두면 화면이 죽는다.
                // 유형은 유효하니(장르와 무관한 문항에서 나옴) 장르만 고르러 보낸다.
                PrimaryButton(stringResource(R.string.quiz_choose_genres), onClick = onEditManually)
            } else {
                PrimaryButton(
                    stringResource(primaryLabel),
                    enabled = selected.isNotEmpty() && !isBusy,
                    busy = isBusy,
                    onClick = onStart,
                )
                Text(
                    stringResource(R.string.quiz_skip),
                    style = DSTypography.bodyMedium,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditManually)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** 장르 한 줄 + 설명 + 체크. iOS `GenreGuideList.row`와 결과 화면 장르 행이 같은 모양이다. */
@Composable
fun GenreGuideRow(
    genre: Api.Genre,
    isSelected: Boolean,
    isDisabled: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.medium))
            .background(if (isSelected) DSColor.brandLight else DSColor.surface)
            .clickable(enabled = !isDisabled, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                genre.genreName,
                style = DSTypography.bodyMedium,
                color = if (isDisabled) DSColor.textTertiary else DSColor.textPrimary,
            )
            // nameEn은 백엔드 배포 전 null — en 로케일에선 name이 곧 원문이라 폴백이 먹힌다.
            GenreGuide.blurb(genre.genreNameEn ?: genre.genreName)?.let {
                Text(stringResource(it), style = DSTypography.caption, color = DSColor.textTertiary)
            }
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isSelected) DSColor.brand else DSColor.border,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.medium))
            .background(if (enabled) DSColor.brand else DSColor.borderLight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                label,
                style = DSTypography.headline,
                color = if (enabled) Color.White else DSColor.textTertiary,
            )
        }
    }
}

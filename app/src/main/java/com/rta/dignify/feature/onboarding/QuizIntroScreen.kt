package com.rta.dignify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography

/**
 * 취향 테스트 유도 화면. iOS `GenreSelectionView.introView` 이식.
 *
 * 온보딩에서 튜토리얼 **다음**, 장르 선택 **앞**에 온다. 칩 목록을 먼저 보여주면
 * 장르 이름 11개를 놓고 "이게 뭔데"가 되는데, 테스트를 먼저 권하면 고를 필요가 없다.
 *
 * 강제하지 않는다 — "건너뛰고 장르 고르기"가 항상 있다. 그래서 문항을 늘려도
 * 이탈 위험이 안 커진다(iOS와 같은 전제).
 */
@Composable
fun QuizIntroScreen(onTakeQuiz: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🎧", fontSize = 56.sp)
            Text(
                stringResource(R.string.quiz_intro_title),
                style = DSTypography.title1,
                color = DSColor.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.quiz_intro_body),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.weight(1f))

        PrimaryButton(stringResource(R.string.quiz_start), onClick = onTakeQuiz)
        Text(
            stringResource(R.string.quiz_skip_to_genres),
            style = DSTypography.bodyMedium,
            color = DSColor.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSkip)
                .padding(vertical = 12.dp),
        )
    }
}

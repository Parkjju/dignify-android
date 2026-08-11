package com.rta.dignify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
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
 * 온보딩 장르 선택. 로그인 직후 `isOnboardingComplete == false`인 유저가 보는 화면이고,
 * 여기서 고른 장르가 피드 구성의 입력이 된다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionScreen() {
    val scope = rememberCoroutineScope()
    var genres by remember { mutableStateOf<List<Api.Genre>>(emptyList()) }
    val selected = remember { mutableListOf<Int>().toMutableStateList() }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        genres = runCatching { Session.api.genres().genres }.getOrDefault(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_title),
            style = DSTypography.title1,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            stringResource(R.string.onboarding_subtitle, MAX_PICKS),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        FlowRow(
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

        error?.let {
            Text(it, color = DSColor.destructive, style = DSTypography.caption)
        }

        Button(
            onClick = {
                if (isSubmitting) return@Button
                isSubmitting = true
                error = null
                scope.launch {
                    try {
                        Session.api.updateGenres(selected.toList())
                        Session.api.completeOnboarding()
                        Session.onOnboardingComplete()
                    } catch (e: Exception) {
                        error = "Couldn't save"
                        isSubmitting = false
                    }
                }
            },
            enabled = selected.isNotEmpty() && !isSubmitting,
            shape = RoundedCornerShape(DSRadius.medium),
            colors = ButtonDefaults.buttonColors(containerColor = DSColor.brand),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 0.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
        Text(
            stringResource(R.string.onboarding_change_later),
            style = DSTypography.caption,
            color = DSColor.textTertiary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp),
        )
    }
}

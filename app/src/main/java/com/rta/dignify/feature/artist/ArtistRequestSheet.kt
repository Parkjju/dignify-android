package com.rta.dignify.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 백엔드 `@Size(max = 100)`과 맞춘 입력 상한. */
private const val MAX_LENGTH = 100

/**
 * 아티스트 요청 시트. iOS `ArtistRequestSheet` 이식.
 *
 * **두 곳에서 같은 시트를 띄운다** — 검색 빈결과와 요청 히스토리. 검색에서 열면 실패한
 * 검색어가 채워져 있고, 히스토리에서 열면 빈 상태다.
 *
 * 서버는 저장만 하고(수동 리뷰) 성공하면 확인 화면으로 바뀐 뒤 스스로 닫힌다 —
 * 유저가 "보내졌나?"를 확인할 시간을 주되 닫는 동작까지 시키지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistRequestSheet(prefill: String = "", onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(prefill.take(MAX_LENGTH)) }
    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val trimmed = text.trim()
    val canSubmit = trimmed.isNotEmpty() && !submitting

    // 성공 화면을 잠깐 보여준 뒤 자동으로 닫는다.
    LaunchedEffect(submitted) {
        if (!submitted) return@LaunchedEffect
        delay(1400)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DSColor.background,
    ) {
        Column(
            // imePadding: 시트 안에 입력칸이 있는데 ModalBottomSheet은 키보드를 안 밀어준다.
            // verticalScroll: 키보드가 올라오면 남는 높이가 확 줄어 전송 버튼이 잘린다.
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (submitted) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DSColor.brand,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        stringResource(R.string.artist_request_done),
                        style = DSTypography.title2,
                        color = DSColor.textPrimary,
                    )
                    Text(
                        stringResource(R.string.artist_request_sent_body),
                        style = DSTypography.body,
                        color = DSColor.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            Text(
                stringResource(R.string.artist_request_title),
                style = DSTypography.title2,
                color = DSColor.textPrimary,
            )
            Text(
                stringResource(R.string.artist_request_body),
                style = DSTypography.body,
                color = DSColor.textSecondary,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DSColor.surface)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_LENGTH) text = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = DSColor.textPrimary),
                    cursorBrush = SolidColor(DSColor.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (canSubmit) {
                            submitting = true
                            scope.launch {
                                runCatching { Session.api.requestArtist(trimmed) }
                                    .onSuccess { submitted = true }
                                    // 실패하면 그대로 두고 재시도 가능하게.
                                    .onFailure { submitting = false }
                            }
                        }
                    }),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.artist_request_hint),
                                style = DSTypography.body,
                                color = DSColor.textTertiary,
                            )
                        }
                        inner()
                    },
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DSColor.brand.copy(alpha = if (canSubmit) 1f else 0.4f))
                    .clickable(enabled = canSubmit) {
                        submitting = true
                        scope.launch {
                            runCatching { Session.api.requestArtist(trimmed) }
                                .onSuccess { submitted = true }
                                .onFailure { submitting = false }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.artist_request_send),
                    style = DSTypography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

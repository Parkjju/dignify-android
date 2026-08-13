package com.rta.dignify.feature.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography

/**
 * 새 소식. iOS `WhatsNewView` 이식.
 *
 * @param highlight 값이 있으면 그 버전만(업데이트 직후 자동 노출), null이면 전체 로그
 *   (마이페이지에서 직접 열었을 때).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(highlight: String? = null, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val releases = releasesFor(highlight)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DSColor.background,
    ) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            Text(
                stringResource(R.string.whatsnew_title),
                style = DSTypography.title1,
                color = DSColor.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                releases.forEach { release ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.whatsnew_version, release.version),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DSColor.brand,
                        )
                        release.notes.forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 불릿을 문자로 안 쓴다 — 폰트에 따라 줄맞춤이 흔들린다.
                                Box(
                                    Modifier
                                        .padding(top = 7.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(DSColor.border)
                                )
                                Text(
                                    stringResource(note),
                                    style = DSTypography.body,
                                    color = DSColor.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DSRadius.medium))
                    .background(DSColor.brand)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.whatsnew_got_it),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** highlight가 있으면 그 버전만, 없으면 전체 로그. */
private fun releasesFor(highlight: String?): List<Release> =
    if (highlight != null) Changelog.releases.filter { it.version == highlight }
    else Changelog.releases

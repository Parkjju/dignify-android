package com.rta.dignify.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import kotlinx.coroutines.launch

/**
 * 사용법. iOS `TutorialView` 이식.
 *
 * 안드로이드에만 있는 제스처는 없고 화면 구성이 같아서 페이지 내용을 그대로 옮겼다.
 * 다만 **아이콘은 SF Symbol이 아니라 Material 아이콘**이라 모양이 iOS와 다르다 —
 * 같은 뜻의 기호를 골랐지 픽셀을 맞추지는 않았다.
 */
private data class TutorialPage(
    val icon: ImageVector?,
    /** 하입 아이콘만 벡터가 아니라 우리 애셋이다. */
    val useHypeIcon: Boolean = false,
    val title: Int,
    val body: Int,
)

private val pages = listOf(
    TutorialPage(Icons.Filled.TouchApp, title = R.string.tutorial_doubletap_title, body = R.string.tutorial_doubletap_body),
    TutorialPage(null, useHypeIcon = true, title = R.string.tutorial_hypebutton_title, body = R.string.tutorial_hypebutton_body),
    // 피드의 상세 트리거가 디스크 아이콘이라(FeedScreen) 튜토리얼 그림도 같은 걸 쓴다.
    TutorialPage(Icons.Outlined.Album, title = R.string.tutorial_detail_title, body = R.string.tutorial_detail_body),
    TutorialPage(Icons.Filled.TouchApp, title = R.string.tutorial_longpress_title, body = R.string.tutorial_longpress_body),
    TutorialPage(Icons.Filled.PlayCircle, title = R.string.tutorial_play_title, body = R.string.tutorial_play_body),
    TutorialPage(Icons.Filled.LibraryMusic, title = R.string.tutorial_pick_title, body = R.string.tutorial_pick_body),
    TutorialPage(Icons.Filled.PersonAdd, title = R.string.tutorial_request_title, body = R.string.tutorial_request_body),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.size - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 마지막 장에선 건너뛰기를 감춘다 — 바로 옆에 "시작하기"가 있어 둘이 같은 일을 한다.
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
            if (!isLast) {
                Text(
                    stringResource(R.string.tutorial_skip),
                    style = DSTypography.body,
                    color = DSColor.textTertiary,
                    modifier = Modifier.clickable(onClick = onDone),
                )
            }
        }

        HorizontalPager(pagerState, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    if (page.useHypeIcon) {
                        Icon(
                            painterResource(R.drawable.ic_hype),
                            contentDescription = null,
                            tint = DSColor.brand,
                            modifier = Modifier.size(60.dp),
                        )
                    } else {
                        Icon(
                            page.icon!!,
                            contentDescription = null,
                            tint = DSColor.brand,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Text(
                    stringResource(page.title),
                    style = DSTypography.title1,
                    color = DSColor.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
                )
                Text(
                    stringResource(page.body),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) DSColor.brand else DSColor.border
                        )
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.medium))
                .background(DSColor.brand)
                .clickable {
                    if (isLast) onDone()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (isLast) R.string.tutorial_start else R.string.tutorial_next),
                fontSize = 16.sp,
                color = Color.White,
                style = DSTypography.headline,
            )
        }
    }
}

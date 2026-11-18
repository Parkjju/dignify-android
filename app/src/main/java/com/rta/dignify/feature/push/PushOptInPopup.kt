package com.rta.dignify.feature.push

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.rta.dignify.core.designsystem.DSPopup
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSBrandMark
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography

/**
 * 푸시 권한 안내 팝업. iOS `PushOptInPopup` 이식.
 *
 * 시스템 권한창을 바로 띄우지 않고 이걸 먼저 보여주는 이유는 **맥락** 때문이다. 시스템 창은
 * "알림을 허용하시겠습니까?"만 묻지 무엇을 알려주는지는 말해주지 않는다. 무엇을 받게 되는지
 * 모르는 채로 받은 질문은 대개 거절당하고, 안드로이드 13+는 두 번 거절하면 앱이 다시 물어볼
 * 방법이 없어진다(설정으로 보내는 수밖에 없다).
 *
 * **문구를 호출부가 넘기는 게 이 컴포넌트의 핵심이다.** 알림이 무엇을 알리는지가 요청 위치마다
 * 다른데 한 문구로 뭉치면 물어보는 이유 자체가 사라진다.
 *
 * 시스템 알럿처럼 보이게 만들면 안 된다 — 바로 뒤에 진짜 시스템 창이 오는 구조라, 회색 크롬을
 * 흉내 내면 가짜 시스템 다이얼로그가 된다. 브랜드 컬러·우리 타이포로 우리 것임을 드러낸다.
 */
@Composable
fun PushOptInPopup(
    visible: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    title: String,
    message: String,
) {
    // 딤 탭 = 거절. 시스템 알럿이 공짜로 주던 동작이라 직접 붙인다.
    DSPopup(visible = visible, onDismiss = onDecline) {
        Column(
            // 카드가 화면을 넘지 않게 안전 영역 안으로 가둔다. 안 가두면 글꼴을 키운 기기에서
            // 긴 문구가 카드를 늘려 아래 버튼 둘이 화면 밖으로 나간다 — 팝업을 닫을 방법이
            // 딤 탭뿐이 되고, 그건 "거절"이라 유저가 수락할 길이 사라진다.
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .shadow(24.dp, RoundedCornerShape(DSRadius.large))
                .background(DSColor.background, RoundedCornerShape(DSRadius.large))
                // 카드를 눌렀을 때 딤의 onClick으로 새어 닫히지 않도록 히트테스트를 먹는다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                // 그래도 넘치면 카드 안에서 스크롤. 문구가 길어도 버튼은 항상 닿는다.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DSBrandMark(size = 44.dp)
            Text(
                title,
                style = DSTypography.title2,
                color = DSColor.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                style = DSTypography.body,
                color = DSColor.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = DSTypography.body.fontSize * 1.45f,
            )
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(DSRadius.medium),
                colors = ButtonDefaults.buttonColors(containerColor = DSColor.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.push_optin_accept), color = Color.White)
            }
            TextButton(onClick = onDecline) {
                Text(
                    stringResource(R.string.push_optin_decline),
                    style = DSTypography.body,
                    color = DSColor.textTertiary,
                )
            }
        }
    }
}

/**
 * 광고성 동의 팝업(디자인 Popup `row` — 첫 실행 P02 · 야간 P14). 마크 없이 「나중에 | 알림 받기」 가로 두 칸.
 * **버튼으로만 닫힌다** — 딤·뒤로 가기 무반응(동의는 명시적 선택만).
 */
@Composable
fun ConsentPopup(
    visible: Boolean,
    title: AnnotatedString,
    message: String,
    declineLabel: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    acceptLabel: String = stringResource(R.string.push_optin_accept),
) {
    DSPopup(visible = visible, onDismiss = null) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(DSRadius.large))
                .background(DSColor.background, RoundedCornerShape(DSRadius.large))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = DSTypography.title2,
                    color = DSColor.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = DSTypography.title2.fontSize * 1.4f,
                )
                Text(message, style = DSTypography.bodySmall, color = DSColor.textSecondary, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(DSRadius.medium)).clickable(onClick = onDecline),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(declineLabel, style = DSTypography.body, color = DSColor.textTertiary)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DSRadius.medium))
                        .background(DSColor.brand)
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(acceptLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

/** [full]에서 [part]만 [style]로. 문구 안 인자 자리를 강조할 때(번역마다 위치가 달라서 찾아서 칠한다). */
fun highlighted(full: String, part: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(full)
    val at = full.indexOf(part)
    if (at >= 0 && part.isNotEmpty()) addStyle(style, at, at + part.length)
}

/**
 * 좋아요 시점 신곡 알림 시트(디자인 ConsentSheet, P05). 버튼으로만 닫힌다 — 딤·뒤로 가기 무반응.
 * 미리보기 그림은 Figma `image_nps` 실측 그대로 그리고, 아이콘 자리만 그 아티스트 자켓이다.
 */
@Composable
fun ConsentSheet(
    visible: Boolean,
    artist: String,
    artworkUrl: String?,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
) {
    BackHandler(enabled = visible) {}
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DSColor.dim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(250)) { it },
            exit = slideOutVertically(tween(200)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(DSColor.background, RoundedCornerShape(topStart = DSRadius.large, topEnd = DSRadius.large))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .navigationBarsPadding()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bold = stringResource(R.string.consent_sheet_message_bold)
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = DSColor.brand)) { append(stringResource(R.string.consent_sheet_title_artist, artist)) }
                            append("\n")
                            append(stringResource(R.string.consent_sheet_title_rest))
                        },
                        style = DSTypography.title1,
                        color = DSColor.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = DSTypography.title1.fontSize * 1.2f,
                    )
                    Text(
                        highlighted(
                            stringResource(R.string.consent_sheet_message, bold),
                            bold,
                            SpanStyle(fontWeight = FontWeight.SemiBold, color = DSColor.textPrimary),
                        ),
                        style = DSTypography.bodySmall,
                        color = DSColor.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = DSTypography.bodySmall.fontSize * 1.5f,
                    )
                    Spacer(Modifier.height(12.dp))
                    NotificationPreview(artworkUrl)
                }
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.ad_consent_legal),
                        style = DSTypography.caption,
                        color = DSColor.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(DSRadius.medium))
                            .background(DSColor.brand)
                            .clickable(onClick = onAccept),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.push_optin_accept), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Box(
                        Modifier
                            .padding(top = 12.dp, bottom = 32.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(DSRadius.medium))
                            .clickable(onClick = onSnooze)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.consent_sheet_snooze), style = DSTypography.bodySmall, color = DSColor.textSecondary)
                    }
                }
            }
        }
    }
}

/** 343×120 알림 미리보기. 위치는 그림이라 절대값(Figma 실측). 아래 21은 흰색으로 묻힌다. */
@Composable
private fun NotificationPreview(artworkUrl: String?) {
    Box(Modifier.size(343.dp, 120.dp).clipToBounds()) {
        Box(
            Modifier
                .offset(16.dp, 0.dp)
                .size(311.dp, 130.dp)
                .border(6.dp, DSColor.borderLight, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(DSColor.surface, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)),
        )
        Box(Modifier.offset(155.dp, 11.dp).size(32.dp, 8.dp).background(DSColor.borderLight, RoundedCornerShape(50)))
        Box(Modifier.offset(39.dp, 40.dp).size(262.dp, 59.dp).background(DSColor.background, RoundedCornerShape(18.dp)))
        Box(Modifier.offset(31.dp, 27.dp).size(280.dp, 63.dp).background(DSColor.background, RoundedCornerShape(18.dp)))
        val iconModifier = Modifier.offset(44.dp, 42.dp).size(34.dp).clip(RoundedCornerShape(8.dp))
        if (artworkUrl != null) {
            AsyncImage(model = artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = iconModifier.background(DSColor.surface))
        } else {
            Box(iconModifier, contentAlignment = Alignment.Center) { DSBrandMark(size = 34.dp) }
        }
        Text(
            stringResource(R.string.consent_sheet_preview_title),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DSColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(85.dp, 42.dp).width(226.dp),
        )
        Text(
            stringResource(R.string.consent_sheet_preview_body),
            fontSize = 10.sp, color = DSColor.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(85.dp, 59.dp).width(226.dp),
        )
        Box(
            Modifier
                .offset(0.dp, 103.dp)
                .size(343.dp, 21.dp)
                .background(Brush.verticalGradient(listOf(DSColor.background.copy(alpha = 0f), DSColor.background))),
        )
    }
}

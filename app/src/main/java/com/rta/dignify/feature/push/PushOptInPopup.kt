package com.rta.dignify.feature.push

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
 * 다른데 한 문구로 뭉치면 물어보는 이유 자체가 사라진다. 기본값은 특집 세트 맥락.
 *
 * 시스템 알럿처럼 보이게 만들면 안 된다 — 바로 뒤에 진짜 시스템 창이 오는 구조라, 회색 크롬을
 * 흉내 내면 가짜 시스템 다이얼로그가 된다. 브랜드 컬러·우리 타이포로 우리 것임을 드러낸다.
 */
@Composable
fun PushOptInPopup(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    title: String = stringResource(R.string.push_optin_set_title),
    message: String = stringResource(R.string.push_optin_set_message),
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // 딤 탭 = 거절. 시스템 알럿이 공짜로 주던 동작이라 직접 붙인다.
            // indication을 끄는 건 딤 전체에 물결이 번지면 카드가 아니라 배경이 눌린 것처럼 보여서.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDecline,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .shadow(24.dp, RoundedCornerShape(DSRadius.large))
                .background(DSColor.background, RoundedCornerShape(DSRadius.large))
                // 카드를 눌렀을 때 딤의 onClick으로 새어 닫히지 않도록 히트테스트를 먹는다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
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

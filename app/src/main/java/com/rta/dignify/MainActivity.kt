package com.rta.dignify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSBrandMark
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.feature.auth.SignInScreen
import com.rta.dignify.feature.feed.FeedScreen
import com.rta.dignify.feature.onboarding.GenreSelectionScreen

/**
 * ponytail: 탭 바도 내비게이션 라이브러리도 없다. 화면 전환이 "인증 상태에 따라 하나를 고른다"뿐이라
 * when 하나로 끝난다 — 픽·마이페이지가 생겨서 탭이 필요해지면 그때 iOS `MainTabView`처럼 늘린다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        // 아트워크가 화면을 꽉 채우는 지면이라 시스템 바 뒤까지 그린다.
        enableEdgeToEdge()
        setContent { DignifyApp() }
    }
}

@Composable
private fun DignifyApp() {
    when (Session.state) {
        AuthState.UNKNOWN -> {
            LaunchTitle()
            LaunchedEffect(Unit) { Session.resolveInitialState() }
        }

        AuthState.SIGNED_OUT -> SignInScreen()
        AuthState.ONBOARDING_REQUIRED -> GenreSelectionScreen()
        AuthState.GUEST, AuthState.SIGNED_IN -> FeedScreen()
    }
}

/** 저장된 토큰으로 /users/me를 확인하는 동안의 화면. iOS `LaunchLoadingView`와 같은 자리. */
@Composable
private fun LaunchTitle() {
    Box(
        Modifier
            .fillMaxSize()
            .background(DSColor.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DSBrandMark(size = 56.dp)
            Text(
                "Dignify",
                style = DSTypography.title,
                color = DSColor.textPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

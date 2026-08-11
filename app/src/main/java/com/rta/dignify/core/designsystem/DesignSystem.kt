package com.rta.dignify.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS `DSColor.swift` 1:1 이식. 값이 갈라지면 두 앱이 서서히 다른 제품이 되므로
 * 새 색을 넣을 땐 iOS 쪽도 같이 고친다.
 */
object DSColor {
    val brand = Color(0xFF4B3FD8)
    val brandLight = Color(0xFFEEF0FF)
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFF3F4F6)
    val textPrimary = Color(0xFF111827)
    val textSecondary = Color(0xFF6B7280)
    val textTertiary = Color(0xFF9CA3AF)
    val border = Color(0xFFD1D5DB)
    val borderLight = Color(0xFFE5E7EB)
    val divider = Color(0xFFF3F4F6)
    val destructive = Color(0xFFEF4444)

    // Picks (다크 지면). 앱은 라이트 고정이지만 Picks만 예외 — 흰 배경 위 surface 카드는
    // 대비가 거의 없어 아트워크가 떠 있기만 했다.
    val pickBackground = Color(0xFF141420)
    val pickSurface = Color(0xFF1E1E2A)
    val pickElevated = Color(0xFF282836)
    val pickAccent = Color(0xFF8F86FF)
}

/** iOS `DSTypography.swift` 1:1. SwiftUI의 `.system`은 Compose 기본 폰트에 대응한다. */
object DSTypography {
    val display = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)
    val title1 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val micro = TextStyle(fontSize = 10.5f.sp, fontWeight = FontWeight.Normal)

    val title = title1
}

object DSRadius {
    val full = 999.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 28.dp
}

/**
 * iOS `DSGenreChip` 이식. 상태 3개(선택·기본·비활성)의 색이 iOS와 같아야 두 앱의 온보딩이
 * 같은 화면으로 보인다. 비활성은 "상한을 다 채웠다"는 뜻이지 "고를 수 없는 장르"가 아니다.
 */
@Composable
fun DSGenreChip(
    title: String,
    isSelected: Boolean,
    isDisabled: Boolean = false,
    onClick: () -> Unit,
) {
    val foreground = when {
        isSelected -> Color.White
        isDisabled -> Color(0xFFD1D5DB)
        else -> Color(0xFF374151)
    }
    val border = when {
        isSelected -> DSColor.brand
        isDisabled -> DSColor.borderLight
        else -> DSColor.border
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (isSelected) DSColor.brand else DSColor.background)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = !isDisabled, onClick = onClick)
            .padding(horizontal = 16.dp)
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** iOS `DSShimmerView` 대응. 아트워크 자리를 잡아둬 도착 시 레이아웃이 안 튀게 한다. */
@Composable
fun DSShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "phase",
    )
    Box(
        modifier
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        // 폭을 모르는 채로 그리므로 화면 밖까지 넉넉히 잡고 phase로 쓸어 넘긴다.
                        start = Offset(phase * 1600f, 0f),
                        end = Offset(phase * 1600f + 600f, 0f),
                    )
                )
        )
    }
}

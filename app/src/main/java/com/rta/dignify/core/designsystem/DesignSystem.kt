package com.rta.dignify.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
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

/**
 * 뒤로가기 + 제목만 있는 상단바와 스크롤 지면. iOS의 `navigationTitle` + `NavigationStack`
 * 조합에 해당하는 자리다 — 내비게이션 라이브러리를 안 쓰므로 이 껍데기만 공유한다.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = DSColor.textPrimary,
                )
            }
            Text(title, style = DSTypography.title2, color = DSColor.textPrimary)
        }
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    }
}

/**
 * iOS `DSSearchBar` 이식.
 *
 * 맨 `OutlinedTextField`를 쓰면 안 되는 이유: 이 검색바는 피드의 **검은 배경 위**에 놓이는데
 * Material3 기본 색은 밝은 지면을 전제해서 글씨가 배경에 묻혀 안 보인다. iOS처럼 surface로
 * 지면을 불투명하게 깔고 그 위에 textPrimary를 얹어야 한다.
 */
@Composable
fun DSSearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    Row(
        modifier
            .clip(RoundedCornerShape(DSRadius.medium))
            .background(DSColor.surface)
            .border(1.dp, DSColor.borderLight, RoundedCornerShape(DSRadius.medium))
            .padding(horizontal = 14.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = DSColor.textTertiary,
            modifier = Modifier.size(15.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = DSColor.textPrimary),
            cursorBrush = SolidColor(DSColor.brand),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(placeholder, color = DSColor.textTertiary, fontSize = 14.sp)
                }
                inner()
            },
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = null,
                tint = DSColor.textTertiary,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onTextChange("") },
            )
        }
    }
}

/**
 * iOS `DSBrandMark` 대응. 애셋은 iOS `Assets.xcassets/BrandMark`를 그대로 가져온 것이라
 * 두 앱의 로고가 물리적으로 같은 파일이다(한쪽만 바뀌는 일이 안 생긴다).
 */
@Composable
fun DSBrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
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

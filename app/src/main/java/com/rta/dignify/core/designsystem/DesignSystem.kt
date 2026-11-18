package com.rta.dignify.core.designsystem

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

    /** 팝업·시트 뒤 딤(푸시알림 기획 1.0: 55% → 35%). */
    val dim = Color.Black.copy(alpha = 0.35f)
    /** 아트워크 위 어둡게 까는 막(피드 배경). */
    val scrim = Color.Black.copy(alpha = 0.45f)
    /** 토스트 지면 — 피드·라이트 지면 공통. */
    val toastSurface = Color(0xD9151719)
    val textOnDarkSecondary = Color.White.copy(alpha = 0.75f)

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
    val bodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
 * 화면 높이를 채우되 내용이 넘치면 스크롤되는 가운데 정렬 지면.
 *
 * 안 쓰면 어떻게 되는지 실측했다 — 스크롤 없는 `Column(fillMaxSize)`에 내용을 쌓고 버튼을
 * 마지막 자식으로 두면, Column이 **가중치 없는 자식부터** 재기 때문에 내용이 화면보다 길어지는
 * 순간 잘리는 건 버튼이다. 1080x1920/480dpi/글꼴 1.8배에서 하단 버튼이 글자 반쯤 잘린 띠로
 * 남았고, 1080x1780/글꼴 2.0배에서는 아예 안 보였다. 유저 눈에는 "버튼이 안 눌리는" 화면이다.
 *
 * `heightIn(min = maxHeight)`가 핵심이다 — 스크롤 안에서는 높이 제약이 무한이라 이게 없으면
 * `Arrangement.Center`가 아무 일도 하지 않아 짧은 내용이 위로 붙는다.
 */
@Composable
fun DSFitOrScroll(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * 화면 하단 확정 버튼. iOS `DSPrimaryButtonStyle` 자리 — 온보딩·라운드·추천 기준 곡이
 * 같은 모양을 쓴다. Material3 `Button`을 안 쓰는 이유는 지면 색이 우리 브랜드 색 하나뿐이라
 * 테마를 통째로 들여올 이유가 없어서다.
 */
@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DSRadius.medium))
            .background(if (enabled) DSColor.brand else DSColor.borderLight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                label,
                style = DSTypography.headline,
                color = if (enabled) Color.White else DSColor.textTertiary,
            )
        }
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
    /** 상단바 오른쪽 끝(편집·저장). 없으면 자리를 차지하지 않는다. */
    trailing: @Composable RowScope.() -> Unit = {},
    /** false면 지면이 남은 높이를 그대로 받는다 — 안에 LazyColumn을 둘 때(무한 높이에선 터진다). */
    scrollable: Boolean = true,
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
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Column(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier.weight(1f)) {
            content()
        }
    }
}

/** 명세 `prefers-reduced-motion`에 해당 — 개발자 옵션의 "애니메이션 끄기". */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 팝업 층(디자인 Popup 「띄우기·닫기 모션」). 딤과 카드를 **따로** 움직여 OS 대화상자처럼 뜨게 한다 —
 * 조건부로 그냥 그리면 깜빡이고, 통째로 페이드하면 카드가 뜨는 순간이 안 읽힌다.
 *
 * 딤 탭·뒤로 가기 = [onDismiss]. null이면 **버튼으로만 닫힌다**(동의 팝업 — 동의는 명시적 선택만). 그때도 딤은
 * 터치를 먹어 뒤 피드로 새지 않는다. 카드는 바깥 여백까지 [card]가 정하므로, 카드 면을 눌렀을 때 딤으로 새지 않게
 * 하는 것도 [card] 쪽 몫이다(히트테스트를 먹는 clickable).
 */
@Composable
fun DSPopup(visible: Boolean, onDismiss: (() -> Unit)?, card: @Composable () -> Unit) {
    BackHandler(enabled = visible) { onDismiss?.invoke() }
    val reduceMotion = rememberReduceMotion()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DSColor.dim)
                    // indication을 끄는 건 딤 전체에 물결이 번지면 배경이 눌린 것처럼 보여서.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onDismiss?.invoke() },
                    ),
            )
        }
        val enterSpec = tween<Float>(200, easing = LinearOutSlowInEasing)
        val exitSpec = tween<Float>(150, easing = FastOutLinearInEasing)
        AnimatedVisibility(
            visible,
            enter = if (reduceMotion) fadeIn(enterSpec) else fadeIn(enterSpec) + scaleIn(enterSpec, 0.9f),
            exit = if (reduceMotion) fadeOut(exitSpec) else fadeOut(exitSpec) + scaleOut(exitSpec, 0.9f),
        ) { card() }
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
    // 검색을 확정하면 **키보드를 내리고 포커스도 푼다.** 결과를 보려고 누른 건데 키보드가
    // 남아 있으면 목록 절반이 가리고, 내릴 방법이 시스템 뒤로가기뿐이라 화면을 닫는 것과
    // 헷갈린다(온보딩 시드 고르기에서 실측으로 걸린 자리다).
    //
    // 부르는 쪽마다 달지 않고 여기 한 곳에 두는 이유: 검색창은 셋(피드·픽 작성·시드 고르기)이
    // 같은 컴포저블을 쓰는데, 호출부에 두면 새로 붙는 네 번째가 조용히 빠진다.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
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
 * 빈 데를 누르면 키보드를 내린다. **검색창이 있는 지면의 루트에 건다.**
 *
 * 엔터(IME의 검색)로 내리는 건 [DSSearchBar]가 하지만, 검색어만 넣어보고 결과를 훑으러
 * 내려갈 때는 엔터를 안 누른다. 그때 키보드를 내릴 방법이 시스템 뒤로가기뿐이면 화면을
 * 닫는 것과 헷갈린다.
 *
 * 자식이 자기 탭을 먹으면(버튼·셀) 여기까지 안 온다 — 그래서 "상호작용 없는 영역"만 걸린다.
 * 드래그는 안 걸리므로 스크롤도 그대로 된다.
 *
 * 피드에는 안 건다. 그 지면은 탭이 재생/일시정지고 더블탭이 하입이라 겹친다.
 */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return pointerInput(Unit) {
        detectTapGestures {
            keyboard?.hide()
            focusManager.clearFocus()
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

/**
 * 처리 결과 토스트(디자인 Components › Toast). 3초 뒤 사라지고 누르는 동작이 없다. 화면이 아니라 앱에 하나 —
 * 마이 › 알림에서 토글을 끄고 바로 뒤로 가도 결과가 남아야 해서 [DSToastHost]가 탭바 위에 그린다.
 */
object DSToast {
    data class Message(val title: String, val body: String?, val seq: Int)

    var current by mutableStateOf<Message?>(null)
        private set
    private var seq = 0

    fun show(title: String, body: String? = null) {
        current = Message(title, body, ++seq)
    }

    internal fun dismiss(message: Message) {
        if (current == message) current = null
    }
}

/** 탭바 위 8, 좌우 18. 등장 0.2s 페이드 + 6 올라옴, 3초 뒤 0.3s 페이드 아웃. */
@Composable
fun DSToastHost(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val rise = with(LocalDensity.current) { 6.dp.roundToPx() }
    val message = DSToast.current
    // 사라지는 동안에도 문구가 남아 있어야 한다.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        delay(3_000)
        DSToast.dismiss(message)
    }
    AnimatedVisibility(
        visible = message != null,
        enter = if (reduceMotion) fadeIn(tween(200)) else fadeIn(tween(200)) + slideInVertically(tween(200)) { rise },
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        val m = shown ?: return@AnimatedVisibility
        // ponytail: 배경 블러 20은 뺐다 — Modifier.blur는 자기 내용을 흐리지 뒤를 흐리지 않는다. 85% 지면이라 차이가 작다.
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(DSRadius.medium))
                .background(DSColor.toastSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(m.title, style = DSTypography.callout, color = Color.White)
            m.body?.let { Text(it, style = DSTypography.caption, color = DSColor.textOnDarkSecondary) }
        }
    }
}

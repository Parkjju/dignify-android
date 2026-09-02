package com.rta.dignify.feature.mypage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.BuildConfig
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.model.DiggingStats
import com.rta.dignify.core.model.DiggingType
import com.rta.dignify.feature.digging.displayName
import com.rta.dignify.feature.onboarding.CoachAnchor
import com.rta.dignify.feature.onboarding.CoachOverlay
import com.rta.dignify.feature.onboarding.CoachSeen
import com.rta.dignify.feature.onboarding.MyPageCoach
import com.rta.dignify.feature.onboarding.coachAnchor
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

/**
 * 마이페이지. iOS `MyPageView` 이식.
 *
 * 행 묶음은 iOS와 같다 — 기능 / 안내 / 약관 / 계정. 되돌리기 어려운 것(로그아웃·탈퇴)만
 * 마지막에 모으고, 묶음 사이에만 구분선을 둔다.
 */
private const val TAG = "DignifyMyPage"

@Composable
fun MyPageScreen(
    onOpenDiggingProfile: () -> Unit,
    onOpenSeedPicker: () -> Unit,
    onOpenArtistRequests: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenWhatsNew: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var confirmedType by remember { mutableStateOf<DiggingType?>(null) }
    var showWithdraw by remember { mutableStateOf(false) }
    var diggingModeFailed by remember { mutableStateOf(false) }
    // 프로필을 받아 스위치가 실제 값으로 그려진 뒤에 코치마크를 띄운다 — 그전엔 기본값(켜짐)
    // 이라 실제와 다른 화면을 설명하게 된다.
    var profileLoaded by remember { mutableStateOf(false) }
    var seenCoach by remember { mutableStateOf(CoachSeen.get(context, CoachSeen.MY_PAGE)) }

    LaunchedEffect(Unit) {
        // 배지는 실패해도 화면이 성립하므로 각각 따로 삼킨다.
        runCatching { Session.refreshProfile() }.onSuccess { nickname = it.nickname }
        profileLoaded = true
        runCatching { Session.api.myStats("all") }
            .onSuccess { confirmedType = DiggingStats.from(it).type }
    }

    // 피드를 실제로 바꾸는 설정 둘을 한 번만 짚어 준다.
    CoachOverlay(
        steps = MyPageCoach.steps,
        screen = "mypage",
        active = !seenCoach && profileLoaded,
        onFinish = { CoachSeen.mark(context, CoachSeen.MY_PAGE); seenCoach = true },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.mypage_title),
            style = DSTypography.title1,
            color = DSColor.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        NicknameHeader(
            nickname = nickname,
            onCommit = { draft, onError ->
                scope.launch { commitNickname(context, draft, nickname, onError) { nickname = it } }
            },
        )

        DiggingProfileEntry(type = confirmedType, onClick = onOpenDiggingProfile)

        GroupDivider()

        // 1. 기능 — 실제로 무언가를 바꾸는 행들.
        // 이 화면에서 유일하게 피드 자체를 바꾸는 설정이라 묶음 맨 위에 둔다.
        DiggingModeRow(
            failed = diggingModeFailed,
            onChange = { enabled ->
                diggingModeFailed = false
                // 되돌리기와 피드 재요청은 Session이 한다 — 피드 안 버튼과 같은 경로여야
                // 어느 쪽으로 껐든 결과가 같다.
                scope.launch { diggingModeFailed = !Session.setDiggingMode(enabled, "mypage") }
            },
        )
        // 껐을 때도 보여준다. 숨기면 기능이 사라진 것처럼 보이는데, 실제로는 다시 켜면
        // 그대로 쓰이는 설정이다. 꺼진 동안 무슨 뜻인지는 그 화면이 설명한다.
        SettingsRow(
            stringResource(R.string.seed_picker_title),
            modifier = Modifier.coachAnchor(CoachAnchor.SEED_ROW),
            onClick = onOpenSeedPicker,
        )
        SettingsRow(stringResource(R.string.artist_requests), onClick = onOpenArtistRequests)
        // 차단은 로컬 저장이라 해제 경로가 여기밖에 없다.
        SettingsRow(stringResource(R.string.blocked_users), onClick = onOpenBlockedUsers)

        GroupDivider()

        // 2. 안내 — 읽기만 하는 행들.
        SettingsRow(stringResource(R.string.mypage_how_to_use), onClick = onOpenTutorial)
        SettingsRow(stringResource(R.string.mypage_whats_new), onClick = onOpenWhatsNew)
        // 인스타도 "우리를 더 보는" 자리라 안내 묶음에 들어간다.
        SettingsRow(stringResource(R.string.mypage_instagram)) { openUrl(context, instagramUrl()) }

        GroupDivider()

        // 3. 약관 — 읽기만 하지만 성격이 달라 따로 묶는다.
        SettingsRow(stringResource(R.string.mypage_terms)) { openUrl(context, legalUrl(terms = true)) }
        SettingsRow(stringResource(R.string.mypage_privacy)) { openUrl(context, legalUrl(terms = false)) }

        GroupDivider()

        // 4. 계정 — 되돌리기 어려운 것들만 마지막에 모은다.

        SettingsRow(stringResource(R.string.mypage_logout)) { scope.launch { Session.logout() } }
        SettingsRow(stringResource(R.string.mypage_withdraw), destructive = true) { showWithdraw = true }

        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = DSTypography.caption,
            color = DSColor.border,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
    }

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text(stringResource(R.string.withdraw_title)) },
            // 픽을 빠뜨리면 안 된다 — FK가 CASCADE라 내가 올린 픽과 거기 붙은 반응까지 사라진다.
            text = { Text(stringResource(R.string.withdraw_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showWithdraw = false
                    // 실패하면 **로그아웃되지 않는다**(Session.withdraw가 throw하면 상태를 안 바꾼다).
                    // 계정 화면에 그대로 남는 것이 "안 됐다"는 표시다 — 지우지도 않았는데
                    // 로그아웃시키면 유저는 탈퇴된 줄 안다. 그게 1.0.1까지의 실제 동작이었다.
                    // 문구는 iOS에 없어서 안 만든다(로컬라이제이션 원본이 iOS다). 로그는 남긴다.
                    scope.launch {
                        runCatching { Session.withdraw() }
                            .onFailure { Log.w(TAG, "withdraw failed", it) }
                    }
                }) { Text(stringResource(R.string.withdraw_confirm), color = DSColor.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { showWithdraw = false }) {
                    Text(stringResource(R.string.cancel), color = DSColor.textSecondary)
                }
            },
            containerColor = DSColor.background,
        )
    }
}

@Composable
private fun NicknameHeader(nickname: String, onCommit: (String, (String) -> Unit) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.padding(top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = DSTypography.headline.copy(
                    color = DSColor.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(DSColor.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    editing = false
                    onCommit(draft.trim()) { msg ->
                        // 조용한 롤백을 막는다 — 실패하면 편집 모드를 다시 열어 이유를 보여준다.
                        error = msg
                        editing = true
                    }
                }),
                modifier = Modifier.width(180.dp),
            )
            Box(
                Modifier
                    .width(180.dp)
                    .height(2.dp)
                    .background(DSColor.brand)
                    .padding(top = 6.dp)
            )
            error?.let {
                Text(
                    it,
                    style = DSTypography.caption,
                    color = DSColor.destructive,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            Row(
                Modifier.clickable {
                    draft = nickname
                    error = null
                    editing = true
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    nickname.ifEmpty { " " },
                    style = DSTypography.headline,
                    color = DSColor.textPrimary,
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = DSColor.textTertiary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/**
 * 셀 부제목 자리에 실제 유형을 넣는다. 유형이 곧 이 화면의 내용이라
 * "당신의 취향" 같은 고정 문구보다 실제 유형이 언제나 더 알려주는 게 많다.
 */
@Composable
private fun DiggingProfileEntry(type: DiggingType?, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .background(DSColor.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(DSColor.brand, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.BarChart,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.digging_profile),
                style = DSTypography.bodyMedium,
                color = DSColor.textPrimary,
            )
            if (type != null) {
                Text(
                    "${type.emoji} ${type.displayName()}",
                    style = DSTypography.caption,
                    color = DSColor.brand,
                )
            } else {
                Text(
                    stringResource(R.string.digging_profile_subtitle),
                    style = DSTypography.caption,
                    color = DSColor.textSecondary,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DSColor.border,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 하입 따라가기 스위치. 토글 하나로는 무엇이 켜지는지 알 수 없어서 한 줄 설명을 붙인다.
 * 낙관적 반영·롤백은 `Session.setDiggingMode`가 한다 — 스위치가 손가락을 안 따라오면 고장으로 읽힌다.
 */
@Composable
private fun DiggingModeRow(failed: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .coachAnchor(CoachAnchor.FOLLOW_SWITCH)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.follow_my_hypes),
                fontSize = 15.sp,
                color = DSColor.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = Session.diggingMode,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = DSColor.brand,
                ),
            )
        }
        Text(
            stringResource(R.string.follow_my_hypes_note),
            style = DSTypography.caption,
            color = DSColor.textTertiary,
        )
        if (failed) {
            Text(
                stringResource(R.string.save_failed),
                style = DSTypography.caption,
                color = DSColor.destructive,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = if (destructive) DSColor.destructive else DSColor.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (!destructive) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DSColor.border,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        color = DSColor.borderLight,
    )
}

/**
 * 닉네임 확정. 낙관적으로 반영하고 실패하면 되돌린다.
 *
 * 서버 오류 문구를 그대로 못 쓰는 이유: 백엔드 메시지가 한국어로 고정돼 있다.
 * 그래서 상태 코드로만 갈라 문구는 여기서 낸다(iOS도 같은 이유로 같은 방식).
 */
private suspend fun commitNickname(
    context: Context,
    draft: String,
    current: String,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit,
) {
    if (draft == current) return
    if (!NICKNAME_PATTERN.matches(draft)) {
        onError(context.getString(R.string.nickname_invalid))
        return
    }
    runCatching { Session.api.updateNickname(draft) }
        .onSuccess { onSuccess(it.nickname) }
        .onFailure { e ->
            val status = (e as? ClientRequestException)?.response?.status?.value
            onError(
                when (status) {
                    409 -> context.getString(R.string.nickname_taken)
                    400 -> context.getString(R.string.nickname_rejected)
                    else -> context.getString(R.string.nickname_update_failed)
                }
            )
        }
}

/**
 * 백엔드 `NicknameUpdateRequest @Pattern`과 **글자 하나까지 같아야 한다** —
 * 어긋나면 클라가 통과시킨 값이 서버에서 400으로 튕기거나 멀쩡한 값이 막힌다.
 */
private val NICKNAME_PATTERN = Regex("^[a-zA-Z0-9_가-힣]{1,20}$")

/** 한국어 기기는 국내 계정, 그 외는 글로벌. 약관 링크와 같은 로케일 분기. */
private fun instagramUrl(): String =
    if (isKorean()) "https://instagram.com/dignify_music.kr"
    else "https://instagram.com/dignify_music"

private fun legalUrl(terms: Boolean): String = when {
    terms && isKorean() -> "https://galvanized-borogovia-cd2.notion.site/39234ce1f84d80b88af6f8ba45a6afc7"
    terms -> "https://galvanized-borogovia-cd2.notion.site/Terms-Conditions-39234ce1f84d805c9ec3edc2fde9ce79"
    isKorean() -> "https://galvanized-borogovia-cd2.notion.site/39234ce1f84d80889cd2fc918abc6d95"
    else -> "https://galvanized-borogovia-cd2.notion.site/Privacy-Policy-39234ce1f84d8079a794c69a4ae74456"
}

private fun isKorean() = Locale.getDefault().language == "ko"

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

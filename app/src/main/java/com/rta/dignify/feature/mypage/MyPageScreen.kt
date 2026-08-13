package com.rta.dignify.feature.mypage

import android.content.Context
import android.content.Intent
import android.net.Uri
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
@Composable
fun MyPageScreen(
    onOpenDiggingProfile: () -> Unit,
    onOpenGenreSettings: () -> Unit,
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

    LaunchedEffect(Unit) {
        // 배지는 실패해도 화면이 성립하므로 각각 따로 삼킨다.
        runCatching { Session.api.myProfile() }.onSuccess { nickname = it.nickname }
        runCatching { Session.api.myStats("all") }
            .onSuccess { confirmedType = DiggingStats.from(it).type }
    }

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
        SettingsRow(stringResource(R.string.mypage_genre_settings), onClick = onOpenGenreSettings)
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

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            title = { Text(stringResource(R.string.withdraw_title)) },
            // 픽을 빠뜨리면 안 된다 — FK가 CASCADE라 내가 올린 픽과 거기 붙은 반응까지 사라진다.
            text = { Text(stringResource(R.string.withdraw_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showWithdraw = false
                    scope.launch { runCatching { Session.withdraw() } }
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

@Composable
private fun SettingsRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
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

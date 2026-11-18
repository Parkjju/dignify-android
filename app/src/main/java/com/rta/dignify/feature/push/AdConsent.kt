package com.rta.dignify.feature.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold
import com.rta.dignify.core.designsystem.DSToast
import com.rta.dignify.core.network.Api
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 기기 알림 권한이 「설정 전」이면 시스템 창을 띄우고 답을 기다린다. 그 밖(허용·거부)은 아무것도 안 한다 —
 * 거부 상태에서 앱 알림 설정을 **자동으로 열지 않는다**(푸시알림 기획 1.0). 켜는 길은 마이 › 알림 「기기 알림 권한」.
 */
@Composable
fun rememberRequestNotificationPermission(): suspend (source: String) -> Boolean {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, CompletableDeferred<Unit>>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val (source, done) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        Analytics.capture("push_permission_result", mapOf("granted" to granted, "source" to source))
        done.complete(Unit)
    }
    return { source ->
        val activity = context.findActivity()
        if (activity != null && Push.permissionUndecided(activity)) {
            Push.markPermissionAsked(context)
            val done = CompletableDeferred<Unit>()
            pending = source to done
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            done.await()
            true
        } else {
            false
        }
    }
}

/**
 * 거부 상태에서 동의했을 때 설정 안내(푸시알림 기획 0-5, iOS `AppSession.acceptAdPush`).
 * 설정은 자동으로 안 연다 — 「설정 열기」를 눌렀을 때만. 동의 진입점(first_run·hype·settings)이 같은 판정을 쓴다.
 */
object SettingsGuide {
    /** 띄울 때의 source. null이면 안 보인다. */
    var source by mutableStateOf<String?>(null)
        private set

    /**
     * 시스템 창을 방금 띄웠으면 그 답이 우선이라 안내하지 않는다. 띄울 수 없는데 알림이 꺼져 있을 때만 —
     * 13 미만에서 앱 알림을 끈 경우, 한 번 이상 거부해 [Push.permissionUndecided]가 false인 경우.
     */
    fun showIfDenied(context: Context, source: String, askedNow: Boolean) {
        if (askedNow || Push.notificationsEnabled(context)) return
        Analytics.capture("push_settings_guide_shown", mapOf("source" to source))
        this.source = source
    }

    fun dismiss() { source = null }
}

/** 탭바까지 덮어야 해서 `MainTabs`에 하나 둔다. 결과 토스트는 이 위에 그대로 뜬다. */
@Composable
fun SettingsGuidePopup() {
    val context = LocalContext.current
    // 닫히는 애니메이션 동안 source가 비어도 같은 값으로 계측하려고 마지막 값을 들고 있다.
    var last by remember { mutableStateOf("") }
    SettingsGuide.source?.let { last = it }
    ConsentPopup(
        visible = SettingsGuide.source != null,
        title = AnnotatedString(stringResource(R.string.settings_guide_title)),
        message = stringResource(R.string.settings_guide_message),
        declineLabel = stringResource(R.string.settings_guide_close),
        acceptLabel = stringResource(R.string.settings_guide_open),
        onAccept = {
            Analytics.capture("push_settings_guide_opened", mapOf("source" to last))
            SettingsGuide.dismiss()
            Push.openNotificationSettings(context)
        },
        onDecline = SettingsGuide::dismiss,
    )
}

/** 광고성 동의·거부 결과 토스트(제50조 7항 처리 결과 통지). 날짜는 서버 처리일. 실패면 실패 토스트. */
fun showAdConsentToast(context: Context, result: Api.AdPushConsent?) {
    if (result == null) return DSToast.show(context.getString(R.string.save_failed))
    DSToast.show(
        context.getString(R.string.consent_toast_title),
        context.getString(
            if (result.adPush) R.string.consent_toast_accept else R.string.consent_toast_decline,
            PushNotifications.processedDate(result.processedAt),
        ),
    )
}

fun showNightConsentToast(context: Context, result: Api.NightPushConsent?) {
    if (result == null) return DSToast.show(context.getString(R.string.save_failed))
    DSToast.show(
        context.getString(R.string.consent_toast_title),
        context.getString(
            if (result.nightPush) R.string.night_toast_accept else R.string.night_toast_decline,
            PushNotifications.processedDate(result.processedAt),
        ),
    )
}

/**
 * 「알림 받기」 한 동작: 동의 저장 → 기기 권한이 설정 전이면 시스템 창 → 결과 토스트.
 * 저장과 시스템 창은 같이 돈다(네트워크를 기다렸다 창을 띄우면 늦게 뜬다). 토스트는 둘 다 끝난 뒤 —
 * 시스템 창 뒤에 깔리면 못 읽는다.
 */
suspend fun acceptAdConsent(context: Context, source: String, requestPermission: suspend (String) -> Boolean) = coroutineScope {
    val saved = async { Session.setAdPushConsent(true) }
    val askedNow = requestPermission(source)
    val result = saved.await()
    showAdConsentToast(context, result)
    if (result != null) SettingsGuide.showIfDenied(context, source, askedNow)
}

/**
 * 마이 › 알림. 광고성·야간 동의(서버 값)와 기기 알림 권한을 따로 보여 준다 —
 * 권한은 동의가 아니고, 셋 다 맞아야 알림이 간다(야간은 21~08시만).
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requestPermission = rememberRequestNotificationPermission()
    var busy by remember { mutableStateOf(false) }
    val activity = context.findActivity()
    // 설정 앱·시스템 창에 다녀오면 상태가 바뀌어 있다.
    var device by remember { mutableStateOf<Int?>(null) }
    val refreshDevice = {
        device = when {
            Push.notificationsEnabled(context) -> R.string.device_notifications_on
            activity != null && Push.permissionUndecided(activity) -> R.string.device_notifications_not_set
            else -> R.string.device_notifications_off
        }
    }
    LifecycleResumeEffect(Unit) {
        refreshDevice()
        onPauseOrDispose {}
    }

    ScreenScaffold(title = stringResource(R.string.notification_settings), onBack = onBack) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ToggleRow(
                title = stringResource(R.string.ad_consent_toggle),
                note = stringResource(R.string.ad_consent_toggle_note),
                checked = Session.adPushConsent,
                enabled = !busy,
            ) { on ->
                Analytics.capture(if (on) "ad_consent_accepted" else "ad_consent_declined", mapOf("source" to "settings"))
                busy = true
                scope.launch {
                    if (on) acceptAdConsent(context, "settings", requestPermission).also { refreshDevice() }
                    else showAdConsentToast(context, Session.setAdPushConsent(false))
                    busy = false
                }
            }
            ToggleRow(
                title = stringResource(R.string.night_consent_toggle),
                note = stringResource(R.string.night_consent_toggle_note),
                // 광고성이 꺼져 있으면 야간도 꺼진 것으로 보인다(서버도 같이 끈다).
                checked = Session.adPushConsent && Session.nightPushConsent == true,
                enabled = !busy,
            ) { on ->
                if (!Session.adPushConsent) {
                    DSToast.show(
                        context.getString(R.string.night_needs_consent_title),
                        context.getString(R.string.night_needs_consent_message),
                    )
                    return@ToggleRow
                }
                Analytics.capture(if (on) "ad_consent_accepted" else "ad_consent_declined", mapOf("source" to "settings_night"))
                busy = true
                scope.launch {
                    showNightConsentToast(context, Session.setNightPushConsent(on))
                    busy = false
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = DSColor.borderLight)
            // 켜짐·꺼짐 = 이 앱 알림 설정. 설정 전이면 무반응 — 시스템 창은 동의 팝업의 「알림 받기」에서만 띄운다.
            val undecided = device == R.string.device_notifications_not_set
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = device != null && !undecided) { Push.openNotificationSettings(context) }
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.device_notifications),
                    fontSize = 15.sp,
                    color = DSColor.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                device?.let { Text(stringResource(it), fontSize = 15.sp, color = DSColor.textTertiary) }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DSColor.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 행 전체가 스위치다 — 글자를 눌러도 바뀐다. 바뀌는 건 서버 응답 뒤(낙관 갱신 없음). */
@Composable
private fun ToggleRow(title: String, note: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, color = DSColor.textPrimary)
            Text(note, style = DSTypography.caption, color = DSColor.textTertiary)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DSColor.brand),
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

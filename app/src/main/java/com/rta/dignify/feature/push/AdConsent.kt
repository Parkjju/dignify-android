package com.rta.dignify.feature.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rta.dignify.R
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold
import kotlinx.coroutines.launch

/**
 * 알림을 켜는 한 동작. 이미 켜져 있으면 아무것도 안 한다 / 시스템 창을 띄울 수 있으면 띄운다 /
 * 그 밖(13 미만에서 꺼 둠, 영구 거부)은 앱 알림 설정 화면으로 보낸다.
 *
 * 영구 거부는 미리 알 수 없어서 결과로 가른다: 창이 안 떴으면 결과가 바로 거절로 돌아온다.
 * ponytail: 판정이 시간(0.5초)이다. 사람이 창을 읽고 0.5초 안에 거절할 일은 없어서 충분하다.
 */
@Composable
fun rememberEnableNotifications(): (source: String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val (source, askedAt) = pending ?: ("unknown" to 0L)
        pending = null
        Analytics.capture("push_permission_result", mapOf("granted" to granted, "source" to source))
        val dialogShown = SystemClock.elapsedRealtime() - askedAt > 500
        val activity = context.findActivity()
        if (!granted && !dialogShown && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            Push.openNotificationSettings(context)
        }
    }
    return { source ->
        when {
            Push.notificationsEnabled(context) -> Unit
            Push.canAskNotificationPermission(context) -> {
                Push.markPermissionAsked(context)
                pending = source to SystemClock.elapsedRealtime()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> Push.openNotificationSettings(context)
        }
    }
}

/**
 * 광고성 수신 동의·거부를 서버에 남기고 결과 통지 문구를 돌려준다(제50조 7항, 14일 이내).
 * 실패하면 null — 처리됐다고 알리면 안 된다. iOS처럼 피드 팝업에선 조용히, 설정 화면은 실패를 띄운다.
 */
suspend fun processAdConsent(context: Context, consent: Boolean): String? {
    val result = Session.setAdPushConsent(consent) ?: return null
    return PushNotifications.resultText(context, result.adPush, result.processedAt)
}

/** 결과 통지. 토스트는 두 줄에서 잘리고 금방 사라져서, 읽고 닫는 창으로 띄운다. */
@Composable
fun AdConsentNotice(text: String?, onDismiss: () -> Unit) {
    if (text == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ad_consent_notice_title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok), color = DSColor.brand) } },
        containerColor = DSColor.background,
    )
}

/**
 * 마이페이지 알림 설정. 광고성 수신 동의(서버 값)와 기기 알림 상태를 따로 보여 준다 —
 * 둘은 별개이고(권한은 동의가 아니다), 둘 다 켜져야 신곡·주간 추천이 온다.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enable = rememberEnableNotifications()
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val activity = context.findActivity()
    // 설정 앱·시스템 창에 다녀오면 상태가 바뀌어 있다.
    var device by remember { mutableStateOf<Int?>(null) }
    LifecycleResumeEffect(Unit) {
        device = when {
            Push.notificationsEnabled(context) -> R.string.device_notifications_on
            activity != null && Push.permissionUndecided(activity) -> R.string.device_notifications_not_set
            else -> R.string.device_notifications_off
        }
        onPauseOrDispose {}
    }

    ScreenScaffold(title = stringResource(R.string.notification_settings), onBack = onBack) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ad_consent_toggle),
                        fontSize = 15.sp,
                        color = DSColor.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = Session.adPushConsent,
                        enabled = !busy,
                        onCheckedChange = { on ->
                            Analytics.capture(
                                if (on) "ad_consent_accepted" else "ad_consent_declined",
                                mapOf("source" to "settings"),
                            )
                            busy = true
                            failed = false
                            scope.launch {
                                notice = processAdConsent(context, on)
                                failed = notice == null
                                busy = false
                            }
                            // 켤 때 기기 알림이 꺼져 있으면 같이 켠다(iOS `acceptAdPush`).
                            if (on) enable("settings")
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DSColor.brand),
                    )
                }
                Text(stringResource(R.string.ad_consent_toggle_note), style = DSTypography.caption, color = DSColor.textTertiary)
                if (failed) {
                    Text(stringResource(R.string.save_failed), style = DSTypography.caption, color = DSColor.destructive)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = DSColor.borderLight)
            // 미결정이면 시스템 창, 정한 뒤엔 시스템 창이 다시 안 뜨니 이 앱 알림 설정으로.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (device == R.string.device_notifications_not_set) enable("settings")
                        else Push.openNotificationSettings(context)
                    }
                    .padding(horizontal = 20.dp)
                    .height(48.dp),
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
    AdConsentNotice(notice) { notice = null }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

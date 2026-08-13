package com.rta.dignify.feature.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessaging
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * FCM 토큰을 서버에 등록한다. iOS `AppSession.registerDeviceToken` 자리.
 *
 * **알림 권한과 무관하게 등록한다.** iOS는 권한을 받아야 APNs 토큰이 나오지만 FCM 토큰은
 * 권한과 별개로 발급되고, 등록해둔 토큰은 유저가 나중에 권한을 켜는 순간 그대로 동작한다.
 * 권한 시점에 맞춰 등록을 미루면 "허용은 눌렀는데 알림이 안 온다"는 구멍이 생긴다.
 *
 * ponytail: 등록 실패를 재시도하지 않는다 — 앱을 다시 켜면 어차피 다시 부른다.
 */
object Push {
    /** 공지·반응 알림이 들어가는 채널. 하나뿐이라 유저가 종류별로 끌 일이 없다. */
    const val CHANNEL_ID = "dignify_default"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 알림 채널을 만든다. 안드로이드 8+는 채널이 없으면 알림이 **조용히 버려진다.**
     * 앱이 한 번은 실행돼야 푸시 대상이 되므로 시작 시 한 번 만들어두면 충분하다
     * (같은 ID로 다시 만들어도 무해하다 — 시스템이 기존 것을 유지한다).
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_channel_default),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * 알림 권한을 물어볼 수 있는 상태인지. 이미 허용했거나 런타임 권한이 없는 버전(13 미만)이면
     * 안내 팝업부터 띄우지 않는다 — 받을 것도 없는데 물어보는 꼴이 된다.
     *
     * 피드(세트 완주)와 픽(첫 게시) 두 자리가 같은 판정을 쓴다. iOS는 이 판정이
     * `AppSession.pushAuthorizationUndecided()` 한 곳에 있어서, 여기도 한 곳에 둔다.
     *
     * ponytail: "영구 거부"는 따로 안 가린다. 안드로이드는 그 상태를 직접 물어볼 API가 없고
     * (shouldShowRequestPermissionRationale로 추정만 가능), 추정이 틀리면 물어볼 기회를 통째로
     * 날린다. 시스템이 조용히 무시하는 쪽이 낫다.
     */
    fun canAskNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    /** 로그인 상태가 확정된 뒤 호출. 토큰은 FCM에서 받아온다. */
    fun register() {
        if (!canRegister()) {
            Log.i(TAG, "등록 스킵 — 로그인 상태가 아니다")
            return
        }
        // **여기서 던지면 안 된다.** 이 함수는 `Session.refreshAuthState()` 안에서 불리고,
        // 그 예외는 `resolveInitialState()`의 runCatching이 인증 실패로 읽어 유저를 로그인
        // 화면으로 튕긴다. FirebaseApp 초기화가 안 된 기기(google-services.json 누락,
        // 플레이 서비스 없음)가 정확히 그 경우다 — 푸시가 세션을 넘어뜨릴 이유는 없다.
        runCatching { FirebaseMessaging.getInstance().token }
            .onFailure { Log.w(TAG, "FCM을 쓸 수 없는 기기", it) }
            .onSuccess { task ->
                task.addOnCompleteListener {
                    if (it.isSuccessful) upload(it.result)
                    else Log.w(TAG, "FCM 토큰 조회 실패", it.exception)
                }
            }
    }

    /** FCM이 토큰을 갈아끼웠을 때. 새 토큰을 이미 들고 오므로 다시 물어보지 않는다. */
    fun onTokenRotated(token: String) {
        if (canRegister()) upload(token)
    }

    /** 게스트·미로그인 토큰은 서버가 붙일 유저가 없어 401이 된다. */
    private fun canRegister() = Session.isInitialized && Session.api.isAuthenticated

    private fun upload(token: String) {
        Log.i(TAG, "디바이스 토큰 등록 시도: ${token.take(12)}…")
        scope.launch {
            runCatching { Session.api.registerDeviceToken(token, TimeZone.getDefault().id) }
                // Ktor는 4xx/5xx에 예외를 안 던진다(expectSuccess 기본 false). 상태를 직접
                // 보지 않으면 401로 거부당한 요청이 성공으로 보이고 로그가 한 줄도 안 남는다.
                .onSuccess {
                    if (it.status.isSuccess()) Log.i(TAG, "디바이스 토큰 등록 성공")
                    else Log.w(TAG, "디바이스 토큰 등록 거부: ${it.status}")
                }
                .onFailure { Log.w(TAG, "디바이스 토큰 등록 실패", it) }
        }
    }

    private const val TAG = "Push"
}

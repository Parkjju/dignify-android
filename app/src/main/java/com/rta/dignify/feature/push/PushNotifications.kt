package com.rta.dignify.feature.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.rta.dignify.MainActivity
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 광고성 알림(큐레이션·신곡·주간 추천)을 **앱이 직접** 만든다. 서버가 이 타입만 data 메시지로 보낸다 —
 * 시스템이 그리는 알림엔 「수신거부」 버튼을 달 수 없고, 알림에서 바로 거부할 수 없으면 위반이다
 * (정보통신망법 제50조, KISA 안내서 44쪽). 나머지 알림(픽 반응·공지)은 지금처럼 시스템이 그린다.
 */
object PushNotifications {
    /** 이 앱이 만든 알림을 눌렀다는 표식. 시스템 알림의 `google.message_id` 자리다. */
    const val EXTRA_FROM_PUSH = "from_push"

    private const val EXTRA_PUSH_ID = "push_id"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"

    /** data 메시지 하나를 알림으로. `title`이 없으면 이 앱이 그릴 알림이 아니다. */
    fun show(context: Context, data: Map<String, String>) {
        val title = data["title"] ?: return
        val pushId = data[EXTRA_PUSH_ID]
        val id = (pushId ?: System.currentTimeMillis().toString()).hashCode()

        val open = Intent(context, MainActivity::class.java).apply {
            // 이미 떠 있으면 새로 만들지 않고 onNewIntent로 받는다.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FROM_PUSH, true)
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val optOut = Intent(context, OptOutReceiver::class.java)
            .putExtra(EXTRA_PUSH_ID, pushId)
            .putExtra(EXTRA_NOTIFICATION_ID, id)

        notify(
            context, id,
            base(context, title, data["body"].orEmpty())
                // 요청 코드를 알림마다 다르게 — 같으면 앞 알림의 extras(trackIds)가 덮인다.
                .setContentIntent(PendingIntent.getActivity(context, id, open, FLAGS))
                .addAction(0, context.getString(R.string.ad_push_opt_out), PendingIntent.getBroadcast(context, id, optOut, FLAGS)),
        )
    }

    /** 결과 통지(제50조 7항) 문구. 날짜는 서버가 처리한 시각을 기기 날짜로. */
    fun resultText(context: Context, consent: Boolean, processedAt: String): String =
        context.getString(if (consent) R.string.ad_consent_result_accept else R.string.ad_consent_result_decline, processedDate(processedAt))

    /** 서버 처리 시각(ISO-8601 Instant)을 기기 날짜 YYYY-MM-DD로. */
    fun processedDate(processedAt: String): String =
        runCatching { Instant.parse(processedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
            .getOrDefault(processedAt.take(10))

    /**
     * 디버그 메뉴의 테스트 푸시(iOS `test-push` 폴더의 apns 파일 자리). 서버와 같은 data 맵을 [show]에 넘겨
     * 실제 수신과 같은 경로를 탄다. 곡 id는 운영에 있는 것(2026-10-30 확인). `push_id`가 가짜라
     * 수신거부는 서버 응답에 따라 성공·실패 안내 중 하나가 뜬다.
     */
    val debugPushes: List<Map<String, String>> = listOf(
        mapOf("type" to "new_release", "title" to "(광고) Leo Kottke 신곡", "body" to "좋아요한 아티스트의 신곡 3곡이 나왔어요", "trackIds" to "1,2,3"),
        mapOf("type" to "new_release", "title" to "(광고) 신곡 1곡", "body" to "좋아요한 아티스트의 신곡이 나왔어요", "trackIds" to "11"),
        mapOf("type" to "new_release", "title" to "(광고) 없는 곡", "body" to "내려간 곡만 있는 알림", "trackIds" to "999999999"),
        mapOf("type" to "weekly_pick", "title" to "(광고) 이번 주 추천", "body" to "좋아요한 곡을 바탕으로 고른 5곡", "trackIds" to "6,7,8,9,10"),
        mapOf("type" to "curation", "title" to "(광고) 이번 주 특집", "body" to "큐레이션 세트"),
    ).mapIndexed { i, m -> m + (EXTRA_PUSH_ID to "test-${m["type"]}-$i") }

    private fun base(context: Context, title: String, body: String) =
        NotificationCompat.Builder(context, Push.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)

    // 권한이 없으면 애초에 보일 알림이 없다. 결과 알림도 같은 권한으로 뜬다.
    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) manager.notify(id, builder.build())
    }

    /**
     * 「수신거부」를 누르면 앱을 열지 않고 여기서 끝낸다. 피드도 안 건드린다.
     * 로그인 없이 기기 토큰으로 처리하고, 결과는 같은 알림을 갈아 끼워 알린다.
     */
    class OptOutReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            Session.init(context.applicationContext)
            val pushId = intent.getStringExtra(EXTRA_PUSH_ID) ?: "unknown"
            val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            Session.appScope.launch {
                val text = runCatching {
                    val result = Session.api.optOutPush(pushId, fcmToken())
                    Session.onAdConsentProcessed(result.adPush)
                    resultText(context, result.adPush, result.processedAt)
                }.onFailure { Log.w(TAG, "opt-out failed", it) }
                    .getOrNull()
                if (text != null) {
                    notify(context, id, base(context, context.getString(R.string.ad_push_opt_out), text))
                } else {
                    // 실패를 삼키면 거부된 줄 안다. 다시 누를 수 있게 버튼을 남긴다.
                    notify(
                        context, id,
                        base(context, context.getString(R.string.ad_push_opt_out), context.getString(R.string.ad_push_opt_out_failed))
                            .addAction(0, context.getString(R.string.ad_push_opt_out), PendingIntent.getBroadcast(context, id, intent, FLAGS)),
                    )
                }
                pending.finish()
            }
        }

        private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener {
                if (it.isSuccessful) cont.resume(it.result) else cont.resumeWithException(it.exception ?: IllegalStateException())
            }
        }
    }

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private const val TAG = "PushNotifications"
}

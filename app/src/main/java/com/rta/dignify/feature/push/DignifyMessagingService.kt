package com.rta.dignify.feature.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.rta.dignify.core.auth.Session

/**
 * FCM 토큰 회전을 받는 자리. 이것만 있으면 되는 게, 서버가 `android.notification`으로 보내는
 * 알림은 앱이 백그라운드일 때 시스템이 직접 띄우기 때문이다 — loc-key도 시스템이
 * strings.xml에서 꺼내 렌더한다.
 *
 * 토큰은 재설치·데이터 복원·앱 데이터 삭제로 바뀐다. 여기서 다시 올리지 않으면 그 기기는
 * 조용히 알림이 끊긴 채로 남는다(서버는 죽은 토큰인 걸 발송해봐야 안다).
 *
 * ponytail: `onMessageReceived`는 안 만든다. 앱이 **포그라운드일 때만** 불리는 콜백이라,
 * 붙이면 화면을 보고 있는 사람에게 배너를 띄우는 일(iOS가 하는 것)만 추가된다. 그러려면
 * loc-key를 직접 풀어 알림을 손으로 조립해야 하는데, 그 코드가 지금 있는 알림 두 종류보다 크다.
 */
class DignifyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // 푸시 때문에 프로세스가 처음 깨어난 경우 Session이 아직 비어 있다.
        // (이미 초기화됐으면 init은 그냥 돌아간다.)
        Session.init(applicationContext)
        Push.onTokenRotated(token)
    }
}

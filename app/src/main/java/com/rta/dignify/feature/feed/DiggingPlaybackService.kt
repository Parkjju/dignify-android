package com.rta.dignify.feature.feed

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 화면을 꺼도 재생이 이어지게 붙잡아 두는 포그라운드 서비스.
 *
 * **iOS에는 대응물이 없다.** 거긴 `UIBackgroundModes: audio` 한 줄이면 오디오가 나오는 동안
 * 프로세스가 그냥 살아있지만, 안드로이드는 미디어 알림을 띄운 포그라운드 서비스 없이는
 * 앱이 뒤로 가는 순간 프로세스가 정리된다. iOS가 하입 버튼 하나를 놓으려고 만든 Live Activity도
 * 여기선 필요 없다 — 그 자리는 이 서비스가 띄우는 미디어 알림이 이미 갖고 있고,
 * 하입은 세션 커스텀 레이아웃의 `CommandButton` 하나로 끝난다.
 *
 * 플레이어를 이 서비스가 소유하지 않는다. 재생은 [FeedAudioController]의 슬라이딩 윈도우가
 * 들고 있고, 서비스는 그 세션을 시스템(잠금화면·블루투스·Wear·Android Auto)에 내놓는 창구다.
 * ponytail: 액티비티가 죽으면 세션도 같이 없어진다 — 포그라운드 서비스가 프로세스를 붙잡고 있는
 * 동안엔 그럴 일이 없다. 플레이어를 서비스로 옮기는 건 그게 실제로 터진 다음에.
 */
@OptIn(UnstableApi::class)
class DiggingPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // 컨트롤러가 붙기 전에도 알림이 떠야 하므로 여기서 한 번 넣는다.
        // 컨트롤러 접속 시엔 onGetSession 결과가 다시 들어오므로 중복만 막는다.
        val active = session ?: return
        if (sessions.none { it.id == active.id }) addSession(active)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * 최근 목록에서 앱을 치웠을 때. 알림만 남고 서비스가 떠 있으면 유저는 끌 방법이 없다.
     * 알림을 밀어 없앤 경우도 Media3가 재생을 멈춘 뒤 이 경로로 들어온다.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.pause()
        stopSelf()
    }

    companion object {
        /** [FeedAudioController]가 만들어 넣고 지운다. 서비스는 소유하지 않고 내놓기만 한다. */
        @Volatile
        var session: MediaSession? = null
    }
}

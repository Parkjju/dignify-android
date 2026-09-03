package com.rta.dignify.feature.feed

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rta.dignify.R
import com.rta.dignify.core.model.Feed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * current-1 / current / current+1 세 트랙만 플레이어로 유지하는 슬라이딩 윈도우.
 * current 하나만 재생하고 인접 트랙은 버퍼링만 시켜, 스와이프 즉시 소리가 나게 한다.
 * 윈도우 밖 트랙은 인스턴스를 해제한다(메타데이터는 화면이 들고 있음).
 *
 * iOS `FeedAudioController`의 이식이지만 한 군데가 짧다: 오디오 포커스와 이어폰 탈거는
 * ExoPlayer 옵션이 처리한다(iOS는 둘 다 옵저버를 직접 달았다).
 *
 * 반대로 **한 군데는 길다.** 잠금화면·알림·블루투스·차량 컨트롤을 붙이려면 `MediaSession`과
 * 포그라운드 서비스가 필요하다 — iOS가 Info.plist 한 줄로 끝낸 자리다.
 * 세션은 [updateWindow]를 쓰는 **피드 세션에서만** 만든다. 목록 프리뷰([togglePreview])는
 * 화면을 떠나면 멈춰야 하는 지면이라 알림에 올릴 것도 없다.
 */
@OptIn(UnstableApi::class)
class FeedAudioController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /**
     * 세션과 서비스가 쥐는 컨텍스트. [DiggingPlaybackService.session]이 정적 슬롯이라
     * 액티비티를 넘기면 화면이 죽어도 안 놓아준다.
     */
    private val appContext = context.applicationContext

    /** current 트랙의 일시정지 여부 — 재생 상태의 단일 소스. 화면은 이것만 읽는다. */
    var isPaused by mutableStateOf(false)
        private set

    /** 임계값 이상 재생된 트랙을 트랙당 한 번 알린다. 서버 기록은 화면이 한다. */
    var onListen: ((Int) -> Unit)? = null

    /**
     * 트랙을 떠날 때 실제 재생된 시간(초)과, 그동안 앱이 백그라운드로 나갔는지.
     * 루프 누적, 일시정지 구간 제외. 계측 전용.
     */
    var onDwell: ((Int, Double, Boolean) -> Unit)? = null

    /**
     * 다음(+1)·이전(-1)으로 옮겨 달라. 알림·잠금화면·이어폰·차량 버튼과 백그라운드 자동 넘김이
     * 전부 이 하나로 들어온다.
     *
     * 인덱스는 화면(뷰모델)이 들고 있어서 컨트롤러가 혼자 못 옮긴다 — iOS `onRemoteSeek`과 같은 분업.
     * **받는 쪽은 윈도우 이동·노출 계측·페이징을 직접 불러야 한다.** 백그라운드에선 재구성이 없어
     * `LaunchedEffect`에 매달아 둔 부수효과가 하나도 안 돈다(iOS는 SwiftUI가 재평가를 멈춰서 같은 문제).
     */
    var onRemoteSeek: ((Int) -> Unit)? = null

    /** 알림의 하입 버튼. 화면의 하입 버튼과 **같은 함수로** 가야 낙관적 갱신·롤백·계측이 안 갈린다. */
    var onHype: ((Int) -> Unit)? = null

    /**
     * 지금 위치 다음에 트랙이 있는가. 백그라운드 자동 넘김이 피드 끝에서 되감기로 떨어지는 근거다.
     *
     * 스냅샷으로 들고 있으면 안 된다 — 끝에 닿아 페이지네이션이 뒤를 붙여도 백그라운드에선
     * 재구성이 없어 갱신될 자리가 없고, 그러면 마지막 곡에서 영영 되감기만 한다.
     * 프리뷰 지면은 null이라 항상 되감는다(넘길 목록 자체가 없다).
     */
    var hasNextTrack: (() -> Boolean)? = null

    private val players = linkedMapOf<Int, ExoPlayer>()
    /** 리스너는 트랙별로 만든다 — 어느 플레이어가 낸 이벤트인지 알아야 해서. */
    private val listeners = mutableMapOf<Int, Player.Listener>()
    /**
     * Compose 상태다. 하입 컬렉션처럼 **어느 셀이 재생 중인지를 그리는 화면**이 이 값을 읽는데,
     * 일반 var로 두면 값이 바뀌어도 재구성이 안 걸려 재생 표시가 영영 안 붙는다
     * (피드는 이 값을 UI에 안 써서 안 드러났다).
     */
    private var currentTrackId: Int? by mutableStateOf(null)
    private var ticker: Job? = null
    private val dwell = DwellTracker()
    private val listens = ListenTracker()

    /** 피드 세션인가(= [updateWindow]로 굴러가는가). 목록 프리뷰면 false. */
    private var isFeedSession = false
    /** 알림 하입 버튼의 상태 근거. */
    private var currentFeed: Feed? = null

    private var session: MediaSession? = null
    /** 세션이 지금 어느 트랙의 플레이어를 물고 있는지. 재지정 여부 판단용. */
    private var sessionTrackId: Int? = null

    /** 현재 재생(또는 일시정지) 중인 트랙 id. */
    val activeTrackId: Int? get() = currentTrackId

    /**
     * 백그라운드 전환. **화면이 아니라 여기서 받는다** — 멈출지 말지는 어느 컴포저블이 resumed인지가
     * 아니라 어떤 재생 세션이 살아있는지로 갈리기 때문이다(iOS가 `scenePhase` 핸들러를 뷰에서
     * 걷어내고 `isFeedSession`을 둔 것과 같은 이유).
     *
     * 람다를 프로퍼티로 잡아두는 건 [AppForeground]가 인스턴스 동일성으로 중복을 거르기 때문이다.
     */
    private val onForegroundChange: (Boolean) -> Unit = { background ->
        if (background) {
            dwell.markBackground()
            // 피드만 이어진다. 마이페이지 프리뷰·픽 작성·시드 고르기는 화면을 떠나면 멈춘다.
            if (!isFeedSession) pauseCurrent()
        }
    }

    /**
     * 단일 트랙 프리뷰 토글. 하입 컬렉션처럼 **목록에서 한 곡만** 듣는 지면이 쓴다.
     * 같은 트랙이면 재생/일시정지 토글, 다른 트랙이면 기존 걸 전부 걷어내고 새로 만든다.
     *
     * 슬라이딩 윈도우를 안 쓰는 이유: 목록은 다음에 뭘 누를지 모르는 지면이라 미리 받아둘
     * 이웃이 없다. 피드처럼 3칸을 잡아두면 안 들을 곡까지 버퍼링한다.
     */
    fun togglePreview(trackId: Int, url: String) {
        if (currentTrackId == trackId) {
            toggleCurrentPlayback()
            return
        }
        if (url.isBlank()) return
        isFeedSession = false
        currentFeed = null
        releaseSession()
        AppForeground.addListener(onForegroundChange)
        players.keys.toList().forEach(::teardown)
        stopTicker()
        currentTrackId = null   // setCurrent의 "같은 트랙이면 무시" 가드를 통과시킨다.
        players[trackId] = newPlayer(MediaItem.fromUri(url), trackId)
        setCurrent(trackId)
    }

    /**
     * 스와이프가 끝나(= current 변경) 호출. current 기준 3칸 윈도우를 재구성한다.
     * previewUrl이 빈 트랙은 아예 윈도우에 안 넣는다 — 플레이어만 만들고 소리는 안 나는 상태가 된다.
     */
    fun updateWindow(feeds: List<Feed>, current: Int) {
        if (current !in feeds.indices) {
            stop()
            return
        }
        isFeedSession = true
        AppForeground.addListener(onForegroundChange)
        currentFeed = feeds[current]

        val keep = (-1..1)
            .map { current + it }
            .filter { it in feeds.indices }
            .map { feeds[it] }
            .filter { it.previewUrl.isNotBlank() }
            .associateBy { it.trackId }

        (players.keys - keep.keys).toList().forEach(::teardown)

        keep.forEach { (id, feed) ->
            if (players[id] != null) return@forEach
            players[id] = newPlayer(mediaItem(feed), id)
        }

        setCurrent(feeds[current].trackId)
        // setCurrent 밖에 있는 게 중요하다 — 같은 트랙이면 setCurrent는 곧바로 빠져나가는데,
        // 화면에서 하입을 누르면 feeds만 바뀐 채 이 함수가 다시 불린다. 알림 버튼은 그때 갱신된다.
        syncSession()
    }

    /** 윈도우/프리뷰 공통 플레이어 생성. 볼륨 0으로 시작해 ticker가 페이드 인을 올린다. */
    private fun newPlayer(item: MediaItem, trackId: Int): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            setHandleAudioBecomingNoisy(true)   // 이어폰 탈거 → 일시정지
            // REPEAT_MODE_ONE이 아니다. 루프냐 다음 곡이냐를 STATE_ENDED에서 갈라야 하는데,
            // 안드로이드엔 "반복 항목이 한 바퀴 돌았다"는 통보가 없어 루프를 걸면 그 지점이 사라진다.
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            addListener(trackListener(trackId).also { listeners[trackId] = it })
            setMediaItem(item)
            prepare()                           // 이 시점부터 버퍼링, 재생은 안 함
        }

    /** 알림·잠금화면에 뜰 제목/아티스트/아트워크. 이게 없으면 알림이 빈 카드로 뜬다. */
    private fun mediaItem(feed: Feed) = MediaItem.Builder()
        .setUri(feed.previewUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(feed.trackName)
                .setArtist(feed.artistName)
                .setArtworkUri(Uri.parse(feed.artworkUrl(ARTWORK_SIZE)))
                .build(),
        )
        .build()

    /**
     * 인터럽션(전화·다른 앱·이어폰 탈거)에는 멈추기만 하고 자동 재개는 하지 않는다 — 음악이 혼자
     * 다시 나오는 쪽이 더 놀랍다. 명시적으로 `pause()`를 불러야 포커스 복귀 시 재개 플래그가 꺼진다.
     *
     * 리스너가 트랙별인 이유는 [Playback.isRealInterruption]에 적어뒀다. 요약하면, 트랙을 넘길 때
     * 직전 플레이어가 내는 포커스 손실을 인터럽션으로 오인해 방금 켠 트랙을 꺼버리기 때문이다.
     */
    private fun trackListener(trackId: Int) = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // 알림·잠금화면·이어폰·차량에서 누른 재생/일시정지도 여기로 들어온다. 화면 버튼 모양이
            // 이 값을 읽으므로 안 맞추면 잠금화면에서 멈춘 뒤 앱을 열었을 때 재생 중으로 보인다.
            // 직전 플레이어가 내는 이벤트를 걸러야 하는 건 인터럽션 때와 같은 이유다.
            if (trackId == currentTrackId) isPaused = !playWhenReady

            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                reason != Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            ) {
                return
            }
            if (Playback.isRealInterruption(trackId, currentTrackId, playWhenReady)) pauseCurrent()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val decision = Playback.handleTrackEnd(
                eventTrackId = trackId,
                currentTrackId = currentTrackId,
                isBackground = AppForeground.isBackground,
                hasNext = hasNextTrack?.invoke() == true,
            )
            when (decision) {
                Playback.TrackEnd.LOOP -> players[trackId]?.apply { seekTo(0); play() }
                // 다음 틱으로 미룬다 — 넘기는 길에 세션 플레이어를 갈아끼우는데,
                // 지금은 그 플레이어의 콜백 안이다.
                Playback.TrackEnd.ADVANCE -> scope.launch { onRemoteSeek?.invoke(1) }
                null -> Unit
            }
        }
    }

    /** 현재 재생 트랙을 전환한다. 같은 트랙이면 재시작하지 않는다. */
    private fun setCurrent(trackId: Int) {
        if (trackId == currentTrackId) return

        players[currentTrackId]?.apply {
            pause()
            seekTo(0)
        }
        stopTicker()

        currentTrackId = trackId
        val player = players[trackId] ?: return
        player.seekTo(0)
        player.volume = 0f
        player.play()
        isPaused = false        // 새 current는 항상 재생 상태로 시작
        startTicker(player, trackId)
    }

    /**
     * 50ms마다 재생 위치를 읽어 볼륨 페이드·청취 판정·체류 누적을 한 번에 처리한다.
     * iOS의 `addPeriodicTimeObserver`와 같은 자리.
     */
    private fun startTicker(player: ExoPlayer, trackId: Int) {
        ticker = scope.launch {
            while (isActive) {
                val duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0) {
                    val t = player.currentPosition / 1000.0
                    player.volume = Playback.fadeVolume(t, duration / 1000.0).toFloat()
                    if (listens.shouldRecord(trackId, t)) onListen?.invoke(trackId)
                    dwell.advance(t)
                }
                delay(50)
            }
        }
    }

    /** 트랙 전환·정지가 전부 이 지점을 지나므로 체류 flush도 여기서 한다. */
    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        val result = dwell.flush()
        val id = currentTrackId
        if (id != null && result.seconds > 0) onDwell?.invoke(id, result.seconds, result.hadBackground)
    }

    fun pauseCurrent() {
        val player = players[currentTrackId] ?: return
        player.pause()
        isPaused = true
    }

    fun resumeCurrent() {
        val player = players[currentTrackId] ?: return
        player.play()
        isPaused = false
    }

    fun toggleCurrentPlayback() {
        if (isPaused) resumeCurrent() else pauseCurrent()
    }

    /**
     * 알림 하입 버튼을 지금 상태로 맞춘다. 화면에서 눌렀든 알림에서 눌렀든 여기를 지난다 —
     * iOS `syncHype`과 같은 자리다.
     * ponytail: 낙관적 상태를 그대로 받는다. 서버 실패로 화면이 되돌아가도 알림은 안 따라간다.
     */
    fun syncHype(hyped: Boolean) {
        currentFeed = currentFeed?.copy(isHyped = hyped) ?: return
        session?.setCustomLayout(customLayout())
    }

    /** 화면 이탈 시 전체 해제. 재진입은 updateWindow가 다시 세운다. */
    fun stop() {
        releaseSession()
        AppForeground.removeListener(onForegroundChange)
        stopTicker()
        players.keys.toList().forEach(::teardown)
        currentTrackId = null
        currentFeed = null
        isFeedSession = false
        isPaused = false
    }

    private fun teardown(id: Int) {
        if (id == currentTrackId) stopTicker()
        players.remove(id)?.apply {
            listeners.remove(id)?.let(::removeListener)
            release()
        }
    }

    // --- MediaSession ------------------------------------------------------------------------

    /**
     * 세션을 current 플레이어에 맞춘다. 없으면 만들고 포그라운드 서비스를 띄운다.
     *
     * 윈도우에 플레이어가 셋이라 세션에 넘길 단일 `Player`가 없다. **current를 가리키는 래퍼를
     * 매번 다시 물린다** — 셋을 하나로 합치는 것보다 이쪽이 짧다. 세션 쪽으로 윈도우 사정이
     * 새기 시작하면 그때 `ForwardingPlayer`로 창구를 하나로 만든다.
     */
    private fun syncSession() {
        val player = players[currentTrackId] ?: return
        val existing = session
        if (existing == null) {
            session = MediaSession.Builder(appContext, remotePlayer(player))
                .setId(SESSION_ID)
                .setCallback(sessionCallback)
                .setCustomLayout(customLayout())
                .apply { launchIntent()?.let(::setSessionActivity) }
                .build()
            sessionTrackId = currentTrackId
            DiggingPlaybackService.session = session
            // startService는 앱이 앞에 있을 때만 허용된다. 이 경로는 피드 화면이 떠 있을 때만
            // 처음 지나가고, 그 뒤 넘김은 세션이 이미 있어 여기로 안 온다.
            appContext.startService(Intent(appContext, DiggingPlaybackService::class.java))
            return
        }
        if (sessionTrackId != currentTrackId) {
            existing.player = remotePlayer(player)
            sessionTrackId = currentTrackId
        }
        existing.setCustomLayout(customLayout())
    }

    private fun releaseSession() {
        val active = session ?: return
        session = null
        sessionTrackId = null
        DiggingPlaybackService.session = null
        appContext.stopService(Intent(appContext, DiggingPlaybackService::class.java))
        active.release()
    }

    /**
     * 알림·잠금화면·이어폰의 다음/이전을 살린다.
     *
     * 플레이어 하나에 트랙 하나뿐이라 ExoPlayer는 "다음 곡 없음"을 보고하고, 그러면 시스템이
     * 버튼 자체를 안 그린다. 넘기기는 피드 인덱스를 아는 화면 몫이므로 여기선 명령만 열어 두고
     * [onRemoteSeek]으로 넘긴다.
     */
    private fun remotePlayer(inner: ExoPlayer): ForwardingPlayer = object : ForwardingPlayer(inner) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                )
                .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun hasNextMediaItem(): Boolean = true

        override fun hasPreviousMediaItem(): Boolean = true

        override fun seekToNext() = seekBy(1)

        override fun seekToNextMediaItem() = seekBy(1)

        override fun seekToPrevious() = seekBy(-1)

        override fun seekToPreviousMediaItem() = seekBy(-1)

        // 세션이 명령을 넘겨주는 도중이라 다음 틱으로 미룬다 — 그 자리에서 세션 플레이어를
        // 갈아끼우게 된다.
        private fun seekBy(delta: Int) {
            scope.launch { onRemoteSeek?.invoke(delta) }
        }
    }

    /**
     * iOS가 Live Activity라는 별도 위젯 익스텐션까지 만들어 얻은 하입 버튼이, 여기선 이 한 줄이다.
     * 미디어 알림이 이미 그 자리라 위젯도 두 번째 프로세스도 필요 없다.
     *
     * **상태를 색이 아니라 모양으로 알린다.** 브랜드는 하입을 색으로 구분하지만(디자인 시스템의
     * #4B3FD8 / #9CA3AF), 미디어 알림 액션 아이콘은 시스템이 단색으로 틴트해 앱이 지정한 색을
     * 벗겨낸다 — 실기기로 확인했다. 그래서 여기서만 삽 ↔ 체크로 간다([R.drawable.ic_hype_on]).
     * 삽의 변주(외곽선·원 노크아웃)를 먼저 시도했다가 접었다. 이유는 그 드로어블 주석에 있다.
     */
    private fun customLayout(): List<CommandButton> {
        val hyped = currentFeed?.isHyped == true
        return listOf(
            CommandButton.Builder()
                .setSessionCommand(SessionCommand(ACTION_HYPE, Bundle.EMPTY))
                .setIconResId(if (hyped) R.drawable.ic_hype_on else R.drawable.ic_hype)
                .setDisplayName(appContext.getString(if (hyped) R.string.unhype else R.string.hype))
                .build(),
        )
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.accept(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_HYPE, Bundle.EMPTY))
                .build(),
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
        )

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_HYPE) {
                // 알림·잠금화면은 눈으로 디버깅할 수 없는 지면이다. 눌림이 여기까지 왔는지가
                // 안 보이면 "하입이 안 된다"의 원인을 UI 쪽에서 가를 방법이 없다.
                Log.i(TAG, "remote hype (track=$currentTrackId, handler=${onHype != null})")
                currentTrackId?.let { onHype?.invoke(it) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** 알림을 누르면 앱으로 돌아온다. 없으면(런처 인텐트 부재) 세션 액티비티 없이 그냥 둔다. */
    private fun launchIntent(): PendingIntent? =
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let {
            PendingIntent.getActivity(appContext, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private companion object {
        const val TAG = "DignifyFeed"
        const val SESSION_ID = "dignify-feed"
        const val ACTION_HYPE = "com.rta.dignify.HYPE"
        /** 알림/잠금화면 아트워크. 카드에 쓰는 크기와 같아 이미 캐시에 있다. */
        const val ARTWORK_SIZE = 600
    }
}

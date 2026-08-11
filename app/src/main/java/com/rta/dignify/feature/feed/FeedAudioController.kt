package com.rta.dignify.feature.feed

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
 * iOS `FeedAudioController`의 이식이지만 두 군데가 짧다: 루프는 `REPEAT_MODE_ONE`이,
 * 오디오 포커스와 이어폰 탈거는 ExoPlayer 옵션이 처리한다(iOS는 둘 다 옵저버를 직접 달았다).
 */
class FeedAudioController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** current 트랙의 일시정지 여부 — 재생 상태의 단일 소스. 화면은 이것만 읽는다. */
    var isPaused by mutableStateOf(false)
        private set

    /** 임계값 이상 재생된 트랙을 트랙당 한 번 알린다. 서버 기록은 화면이 한다. */
    var onListen: ((Int) -> Unit)? = null

    /** 트랙을 떠날 때 실제 재생된 시간(초). 루프 누적, 일시정지 구간 제외. 계측 전용. */
    var onDwell: ((Int, Double) -> Unit)? = null

    private val players = linkedMapOf<Int, ExoPlayer>()
    private var currentTrackId: Int? = null
    private var ticker: Job? = null
    private val dwell = DwellTracker()
    private val listens = ListenTracker()

    /** 현재 재생(또는 일시정지) 중인 트랙 id. */
    val activeTrackId: Int? get() = currentTrackId

    /**
     * 스와이프가 끝나(= current 변경) 호출. current 기준 3칸 윈도우를 재구성한다.
     * previewUrl이 빈 트랙은 아예 윈도우에 안 넣는다 — 플레이어만 만들고 소리는 안 나는 상태가 된다.
     */
    fun updateWindow(feeds: List<Feed>, current: Int) {
        if (current !in feeds.indices) {
            stop()
            return
        }
        val keep = (-1..1)
            .map { current + it }
            .filter { it in feeds.indices }
            .map { feeds[it] }
            .filter { it.previewUrl.isNotBlank() }
            .associate { it.trackId to it.previewUrl }

        (players.keys - keep.keys).toList().forEach(::teardown)

        keep.forEach { (id, url) ->
            if (players[id] != null) return@forEach
            players[id] = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                setHandleAudioBecomingNoisy(true)   // 이어폰 탈거 → 일시정지
                repeatMode = Player.REPEAT_MODE_ONE // 프리뷰 30초 무한 루프
                volume = 0f                         // 페이드 인은 ticker가 올린다
                addListener(interruptionListener)
                setMediaItem(MediaItem.fromUri(url))
                prepare()                           // 이 시점부터 버퍼링, 재생은 안 함
            }
        }

        setCurrent(feeds[current].trackId)
    }

    /**
     * 인터럽션(전화·다른 앱)에는 멈추기만 하고 자동 재개는 하지 않는다 — 음악이 혼자
     * 다시 나오는 쪽이 더 놀랍다. 명시적으로 `pause()`를 불러야 포커스 복귀 시 재개 플래그가 꺼진다.
     */
    private val interruptionListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) return
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            ) {
                pauseCurrent()
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
        val total = dwell.flush()
        val id = currentTrackId
        if (id != null && total > 0) onDwell?.invoke(id, total)
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

    /** 화면 이탈 시 전체 해제. 재진입은 updateWindow가 다시 세운다. */
    fun stop() {
        stopTicker()
        players.keys.toList().forEach(::teardown)
        currentTrackId = null
        isPaused = false
    }

    private fun teardown(id: Int) {
        if (id == currentTrackId) stopTicker()
        players.remove(id)?.apply {
            removeListener(interruptionListener)
            release()
        }
    }
}

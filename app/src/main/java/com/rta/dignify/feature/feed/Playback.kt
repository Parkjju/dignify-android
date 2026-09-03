package com.rta.dignify.feature.feed

/**
 * 재생 컨트롤러에서 플레이어를 안 건드리는 계산만 떼어낸 것. ExoPlayer 없이 검증 가능하게
 * 분리했다 — 페이드 곡선과 체류 누적은 눈으로 봐선 틀린 걸 못 잡는다.
 */
object Playback {
    /** 훑고 지나간 스와이프와 실제 청취를 가르는 값. iOS와 같은 값이어야 지표가 합쳐진다. */
    const val LISTEN_THRESHOLD_SEC = 5.0
    const val FADE_IN_SEC = 1.0
    const val FADE_OUT_SEC = 2.0

    /**
     * 포커스 손실 이벤트를 인터럽션으로 받아들일지.
     *
     * 슬라이딩 윈도우라 플레이어가 셋이고, 트랙을 넘기면 **새 트랙이 포커스를 가져가면서
     * 직전 플레이어가 포커스 손실 이벤트를 낸다.** 그걸 그대로 받아 "지금 트랙을 멈춰라"로
     * 처리하면 방금 켠 트랙이 자기 때문에 꺼진다 — 스와이프할 때마다 소리가 죽었던 원인이다.
     * 이벤트를 낸 플레이어가 지금 재생 중인 그 플레이어일 때만 진짜 인터럽션이다.
     */
    fun isRealInterruption(eventTrackId: Int, currentTrackId: Int?, playWhenReady: Boolean): Boolean =
        !playWhenReady && currentTrackId != null && eventTrackId == currentTrackId

    /** 프리뷰가 끝까지 갔을 때 할 일. */
    enum class TrackEnd { LOOP, ADVANCE }

    /**
     * 30초 프리뷰가 끝났다. 되감아 다시 트나, 다음 곡으로 넘기나.
     *
     * 포그라운드에선 **되감는다** — 언제든 스와이프할 수 있으니 고민하는 동안 계속 나오는 게 맞다.
     * 백그라운드에선 **넘긴다** — 스와이프할 손이 없는 자리라 되감으면 같은 30초가 주머니 속에서
     * 무한 반복된다. iOS `handleTrackEnd(id:)`와 같은 판단이다.
     *
     * 피드 끝이면(`hasNext == false`) 넘길 데가 없으니 되감는다. 페이지네이션이 뒤를 붙이면
     * 다음 종료에서 자연히 넘어간다.
     *
     * @return 이벤트를 낸 플레이어가 지금 재생 중인 그 플레이어가 아니면 null(무시).
     *   윈도우에 플레이어가 셋이라 [isRealInterruption]과 같은 오인이 여기서도 난다.
     */
    fun handleTrackEnd(
        eventTrackId: Int,
        currentTrackId: Int?,
        isBackground: Boolean,
        hasNext: Boolean,
    ): TrackEnd? {
        if (currentTrackId == null || eventTrackId != currentTrackId) return null
        return if (isBackground && hasNext) TrackEnd.ADVANCE else TrackEnd.LOOP
    }

    /** 종료 fadeOut초 전부터 1→0, 시작 fadeIn초 동안 0→1. 그 외 1.0. */
    fun fadeVolume(
        t: Double,
        duration: Double,
        fadeIn: Double = FADE_IN_SEC,
        fadeOut: Double = FADE_OUT_SEC,
    ): Double {
        if (t < fadeIn) return maxOf(0.0, t / fadeIn)
        val remaining = duration - t
        if (remaining < fadeOut) return maxOf(0.0, remaining / fadeOut)
        return 1.0
    }
}

/** 한 트랙의 체류 결과. */
data class Dwell(val seconds: Double, val hadBackground: Boolean)

/**
 * 트랙 하나에 머문 실제 재생 시간. 프리뷰가 루프라 재생 위치만 보면 되감길 때마다
 * 시간이 사라진다 — 되감김을 감지해 직전 위치를 누적한다.
 * 일시정지 중엔 틱이 안 돌아 자동으로 제외된다.
 */
class DwellTracker {
    private var loops = 0.0
    private var position = 0.0
    private var background = false

    fun advance(seconds: Double) {
        if (seconds < position) loops += position
        position = seconds
    }

    /**
     * 이 트랙을 듣는 동안 앱이 백그라운드로 나갔다.
     *
     * ⚠️ **발사 시점에 읽으면 안 되기 때문에 여기서 표시한다.** 주머니에 넣고 10분 듣다가
     * 돌아와서 스와이프하면 `track_dwell`은 포그라운드에서 나간다. 그때 플래그를 읽으면
     * 600초짜리 주머니 체류가 포그라운드 체류로 기록되고, 중앙값 2.0초짜리 분포가
     * 표본 하나에 망가진다 — `LISTEN_THRESHOLD_SEC`를 어디로 옮길지가 이 분포로 정해진다.
     */
    fun markBackground() {
        background = true
    }

    /** 누적값을 돌려주고 리셋한다. 트랙 전환·정지에서만 호출. */
    fun flush(): Dwell {
        val result = Dwell(loops + position, background)
        loops = 0.0
        position = 0.0
        background = false
        return result
    }
}

/**
 * 임계값을 넘긴 트랙을 트랙당 한 번만 통과시킨다. 루프로 위치가 0으로 돌아가거나
 * 탭 복귀로 같은 트랙이 다시 current가 돼도 재발사되지 않는다.
 */
class ListenTracker(private val threshold: Double = Playback.LISTEN_THRESHOLD_SEC) {
    private val fired = mutableSetOf<Int>()

    /** 이번 호출로 처음 임계값을 넘었으면 true. */
    fun shouldRecord(trackId: Int, playedFor: Double): Boolean {
        if (playedFor < threshold || trackId in fired) return false
        fired += trackId
        return true
    }
}

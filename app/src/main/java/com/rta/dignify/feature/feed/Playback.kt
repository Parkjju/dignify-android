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

/**
 * 트랙 하나에 머문 실제 재생 시간. 프리뷰가 루프라 재생 위치만 보면 되감길 때마다
 * 시간이 사라진다 — 되감김을 감지해 직전 위치를 누적한다.
 * 일시정지 중엔 틱이 안 돌아 자동으로 제외된다.
 */
class DwellTracker {
    private var loops = 0.0
    private var position = 0.0

    fun advance(seconds: Double) {
        if (seconds < position) loops += position
        position = seconds
    }

    /** 누적값을 돌려주고 리셋한다. 트랙 전환·정지에서만 호출. */
    fun flush(): Double {
        val total = loops + position
        loops = 0.0
        position = 0.0
        return total
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

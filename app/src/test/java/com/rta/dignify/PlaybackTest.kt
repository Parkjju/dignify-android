package com.rta.dignify

import com.rta.dignify.feature.feed.DwellTracker
import com.rta.dignify.feature.feed.ListenTracker
import com.rta.dignify.feature.feed.Playback
import com.rta.dignify.core.network.itunesArtworkUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {

    @Test
    fun `fadeVolume ramps in, holds, then ramps out`() {
        // 30초 프리뷰: 0→1s 페이드 인, 28→30s 페이드 아웃, 사이는 1.0.
        assertEquals(0.0, Playback.fadeVolume(0.0, 30.0), 1e-9)
        assertEquals(0.5, Playback.fadeVolume(0.5, 30.0), 1e-9)
        assertEquals(1.0, Playback.fadeVolume(15.0, 30.0), 1e-9)
        assertEquals(0.5, Playback.fadeVolume(29.0, 30.0), 1e-9)
        assertEquals(0.0, Playback.fadeVolume(30.0, 30.0), 1e-9)
    }

    @Test
    fun `dwell accumulates across loops`() {
        val dwell = DwellTracker()
        dwell.advance(10.0)
        dwell.advance(28.0)
        dwell.advance(2.0)   // 루프로 되감김 → 직전 28초가 누적돼야 한다
        assertEquals(30.0, dwell.flush(), 1e-9)
        assertEquals(0.0, dwell.flush(), 1e-9)   // flush는 리셋까지 한다
    }

    @Test
    fun `listen fires once per track even after looping`() {
        val listens = ListenTracker()
        assertFalse(listens.shouldRecord(1, 4.9))
        assertTrue(listens.shouldRecord(1, 5.0))
        assertFalse(listens.shouldRecord(1, 12.0))   // 같은 트랙 재발사 금지
        assertFalse(listens.shouldRecord(1, 0.1))    // 루프로 0으로 돌아와도 마찬가지
        assertTrue(listens.shouldRecord(2, 6.0))
    }

    @Test
    fun `artwork url size segment is rewritten`() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/abc/600x600bb.jpg",
            "https://is1-ssl.mzstatic.com/image/thumb/abc/100x100bb.jpg".itunesArtworkUrl(600),
        )
    }
}

package com.rta.dignify

import com.rta.dignify.core.network.Api
import com.rta.dignify.feature.mypage.HypeGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 그룹핑·페이지네이션 규칙 검증. 이 로직이 조용히 틀리면 화면은 멀쩡한데 하입이 사라진다 —
 * iOS에서 실제로 그랬기 때문에 여기만은 테스트를 남긴다.
 */
class HypeGroupingTest {

    private val utc = ZoneId.of("UTC")

    private fun item(id: Long, at: String) = Api.HypeItem(
        userHypeTrackId = id,
        trackId = id.toInt(),
        trackName = "t$id",
        artistName = "a$id",
        artworkUrl = "",
        previewUrl = "",
        hypedAt = at,
    )

    @Test
    fun `날짜별로 묶되 서버가 준 최신순 순서를 유지한다`() {
        val items = listOf(
            item(3, "2026-08-11T09:00:00Z"),
            item(2, "2026-08-11T01:00:00Z"),
            item(1, "2026-08-09T23:00:00Z"),
        )
        val groups = HypeGrouping.dayGroups(items, zone = utc)

        assertEquals(2, groups.size)
        assertEquals(listOf(3L, 2L), groups[0].tracks.map { it.userHypeTrackId })
        assertEquals(listOf(1L), groups[1].tracks.map { it.userHypeTrackId })
    }

    /**
     * 픽 작성 화면이 자기 타입으로 같은 묶음을 쓴다. 검색에서 고른 곡은 `hypedAt`이 없는데,
     * 그게 날짜 그룹에 섞여 들어가면 "그날 뭘 팠는지"라는 목록의 전제가 깨진다.
     */
    @Test
    fun `byDay는 아무 타입이나 묶고 하입 시각이 없으면 따로 모은다`() {
        data class Row(val id: Int, val at: String?)

        val rows = listOf(
            Row(1, null),
            Row(2, "2026-08-11T09:00:00Z"),
            Row(3, "2026-08-11T01:00:00Z"),
            Row(4, "2026-08-09T23:00:00Z"),
        )
        val groups = HypeGrouping.byDay(rows, utc) { it.at }

        // epoch(시각 없음) 한 덩어리 + 날짜 둘. 등장 순서 그대로다.
        assertEquals(3, groups.size)
        assertEquals(listOf(1), groups[0].tracks.map { it.id })
        assertEquals(listOf(2, 3), groups[1].tracks.map { it.id })
        assertEquals(listOf(4), groups[2].tracks.map { it.id })
    }

    @Test
    fun `maxGroups와 perDayLimit이 각각 날짜 수와 날짜당 개수를 자른다`() {
        val items = listOf(
            item(5, "2026-08-11T09:00:00Z"),
            item(4, "2026-08-11T08:00:00Z"),
            item(3, "2026-08-10T09:00:00Z"),
            item(2, "2026-08-09T09:00:00Z"),
        )
        val groups = HypeGrouping.dayGroups(items, maxGroups = 2, perDayLimit = 1, zone = utc)

        assertEquals(2, groups.size)
        assertEquals(1, groups[0].tracks.size)
        assertEquals(5L, groups[0].tracks.first().userHypeTrackId)
    }

    /** 페이지네이션 앵커는 마지막 "트랙"이어야 한다. 날짜로 잡으면 같은 날 페이지에서 안 바뀐다. */
    @Test
    fun `페이징 앵커는 마지막 트랙의 id다`() {
        val sameDay = listOf(
            item(9, "2026-08-11T09:00:00Z"),
            item(8, "2026-08-11T08:00:00Z"),
        )
        assertEquals(8L, HypeGrouping.pagingAnchor(sameDay))
        assertNull(HypeGrouping.pagingAnchor(emptyList()))
    }

    @Test
    fun `hasMore는 커서_날짜초과_날짜당초과 셋 중 하나면 참이다`() {
        val one = listOf(item(1, "2026-08-11T09:00:00Z"))

        // 커서가 남아 있으면 무조건 더 있다.
        assertTrue(HypeGrouping.hasMore(one, cursor = 42L, maxGroups = 3, perDayLimit = 10, zone = utc))
        // 커서도 없고 한도 안이면 없다.
        assertFalse(HypeGrouping.hasMore(one, cursor = null, maxGroups = 3, perDayLimit = 10, zone = utc))

        val fourDays = (1..4).map { item(it.toLong(), "2026-08-0${it}T09:00:00Z") }
        assertTrue(HypeGrouping.hasMore(fourDays, cursor = null, maxGroups = 3, perDayLimit = 10, zone = utc))

        val manySameDay = (1..11).map { item(it.toLong(), "2026-08-11T09:00:00Z") }
        assertTrue(HypeGrouping.hasMore(manySameDay, cursor = null, maxGroups = 3, perDayLimit = 10, zone = utc))
    }
}

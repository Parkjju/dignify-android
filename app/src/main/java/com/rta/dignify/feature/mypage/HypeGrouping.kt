package com.rta.dignify.feature.mypage

import com.rta.dignify.core.network.Api
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 하입 목록의 그룹핑·페이지네이션 규칙. iOS `HypeGrouping` 이식.
 *
 * 화면에서 떼어 낸 이유는 iOS와 같다 — **조용히 틀리는 부류**라서다. 화면은 멀쩡한데
 * 다음 페이지가 안 불려 하입이 사라진 채로 몇 릴리즈를 갔던 전력이 있다(iOS 1.0.9에서 수정).
 */
object HypeGrouping {

    data class DayGroup<T>(val day: LocalDate, val tracks: List<T>)

    /** `LocalDate.EPOCH`는 API 34부터라 minSdk 26에서 쓰면 구형 기기에서 터진다. */
    private val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)

    /** ISO date-time → 기기 시간대의 날짜. 못 읽거나 없으면 epoch로 떨어뜨려 한 무더기로 모은다. */
    fun dayOf(hypedAt: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        runCatching { Instant.parse(hypedAt).atZone(zone).toLocalDate() }
            .getOrDefault(EPOCH)

    /**
     * 아무 타입이나 날짜별로 묶는다. **등장 순서를 유지한다**(정렬하지 않는다) —
     * 백엔드가 최신순으로 주기 때문이다.
     *
     * 픽 작성 화면이 자기 타입으로 같은 묶음을 만들어야 해서 밖으로 뺐다. 그루퍼를 두 개
     * 만들면 두 목록의 날짜 기준이 언젠가 갈린다(iOS도 같은 이유로 제네릭으로 뺐다).
     *
     * @param hypedAt 항목의 하입 시각(ISO date-time). null이면 epoch 무더기로 간다.
     */
    fun <T> byDay(
        items: List<T>,
        zone: ZoneId = ZoneId.systemDefault(),
        hypedAt: (T) -> String?,
    ): List<DayGroup<T>> {
        val order = LinkedHashMap<LocalDate, MutableList<T>>()
        items.forEach { order.getOrPut(dayOf(hypedAt(it), zone)) { mutableListOf() }.add(it) }
        return order.map { (day, tracks) -> DayGroup(day, tracks) }
    }

    /**
     * 백엔드가 최신순으로 주므로 **등장 순서를 유지해** 날짜별로 묶는다(정렬하지 않는다).
     *
     * @param maxGroups 최근 N일 그룹만(미리보기). null이면 전체.
     * @param perDayLimit 날짜당 앞 N개만. null이면 전체.
     */
    fun dayGroups(
        items: List<Api.HypeItem>,
        maxGroups: Int? = null,
        perDayLimit: Int? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayGroup<Api.HypeItem>> {
        val all = byDay(items, zone) { it.hypedAt }.map { group ->
            if (perDayLimit != null) group.copy(tracks = group.tracks.take(perDayLimit)) else group
        }
        return if (maxGroups != null) all.take(maxGroups) else all
    }

    /**
     * 다음 페이지 트리거가 물어야 할 기준값 — **마지막 트랙**이지 마지막 날짜 그룹이 아니다.
     * 그룹 키는 날짜라 새 페이지가 전부 같은 날이면 값이 그대로고, 그러면 목록이
     * 항목을 재사용해 트리거가 다시 안 불려 페이지네이션이 죽는다.
     */
    fun pagingAnchor(items: List<Api.HypeItem>): Long? = items.lastOrNull()?.userHypeTrackId

    /** 미리보기 밖에 더 볼 하입이 있는가 — 다음 페이지 존재 / 날짜 초과 / 특정 날짜 트랙 초과. */
    fun hasMore(
        items: List<Api.HypeItem>,
        cursor: Long?,
        maxGroups: Int,
        perDayLimit: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (cursor != null) return true
        val groups = items.groupBy { dayOf(it.hypedAt, zone) }
        if (groups.size > maxGroups) return true
        return groups.values.any { it.size > perDayLimit }
    }
}

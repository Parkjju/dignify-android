package com.rta.dignify.core.model

import com.rta.dignify.core.network.Api

/**
 * Digging Profile 도메인 모델. iOS `DiggingStats.swift` 1:1 이식.
 *
 * 백엔드는 숫자만 준다. 유형·격차 헤드라인·잠금 판정 같은 파생은 전부 여기서 계산한다 —
 * 임계값이 클라이언트 상수라 데이터를 보고 튜닝해도 서버를 다시 배포할 필요가 없다.
 *
 * **임계값을 바꿀 땐 iOS도 같이 바꿔야 한다.** 두 앱이 다른 기준으로 유형을 정하면
 * 같은 유저가 기기에 따라 다른 사람이 된다.
 */
data class DiggingStats(
    val distinctListenedCount: Int,
    val hypeCount: Int,
    /** count 내림차순 */
    val listenedByGenre: List<Count>,
    val hypedByGenre: List<Count>,
    /** 상위 5개만 */
    val listenedByArtist: List<Count>,
    val hypedByArtist: List<Count>,
) {
    data class Count(val name: String, val count: Int)

    /** 유형 해제 조건 — 청취·하입 둘 다 충분해야 한다(하입 행동도 유도하려고). */
    val isUnlocked: Boolean
        get() = distinctListenedCount >= MIN_LISTENS && hypeCount >= MIN_HYPES

    val type: DiggingType?
        get() {
            if (!isUnlocked || distinctListenedCount == 0) return null
            val selective = hypeCount.toDouble() / distinctListenedCount < SELECTIVITY_THRESHOLD
            val topShare = (listenedByGenre.firstOrNull()?.count ?: 0).toDouble() / distinctListenedCount
            return DiggingType.of(selective, concentrated = topShare > BREADTH_THRESHOLD)
        }

    /** 유형 라벨 뒤에 붙는 향미("deep in Hip-Hop"). */
    val flavorGenre: String? get() = listenedByGenre.firstOrNull()?.name

    /** 파는 장르 vs 담는 장르. 둘 중 하나라도 없으면 숨긴다. */
    fun headline(same: (String) -> String, differ: (String, String) -> String): String? {
        val dig = listenedByGenre.firstOrNull()?.name ?: return null
        val keep = hypedByGenre.firstOrNull()?.name ?: return null
        return if (dig == keep) same(keep) else differ(dig, keep)
    }

    companion object {
        // 클라 튜닝 지점. iOS DiggingStats.swift와 값이 같아야 한다.
        private const val MIN_LISTENS = 10
        private const val MIN_HYPES = 3
        private const val SELECTIVITY_THRESHOLD = 0.25   // 미만 = 까다로움
        private const val BREADTH_THRESHOLD = 0.5        // top 장르 점유율 초과 = 집중

        /** 장르/아티스트는 화면에선 같은 (이름, 개수) 행이라 `Count` 하나로 합친다. */
        fun from(dto: Api.UserStats) = DiggingStats(
            distinctListenedCount = dto.distinctListenedCount,
            hypeCount = dto.hypeCount,
            listenedByGenre = dto.listenedByGenre.map { Count(it.genreName, it.count) },
            hypedByGenre = dto.hypedByGenre.map { Count(it.genreName, it.count) },
            listenedByArtist = dto.listenedByArtist.map { Count(it.artistName, it.count) },
            hypedByArtist = dto.hypedByArtist.map { Count(it.artistName, it.count) },
        )
    }
}

/**
 * 2축 4유형. iOS `DiggingType`과 같은 매핑이어야 한다 — 갈라지면 "예상과 확정" 비교가 무의미해진다.
 *
 * `key`는 온보딩 예상 유형을 저장할 때 쓰므로 값을 바꾸지 말 것(iOS rawValue와 동일).
 * 표시명·설명은 문자열 리소스에 있다(번역 대상이라).
 */
enum class DiggingType(val key: String, val emoji: String) {
    RESTLESS_CURATOR("restlessCurator", "🧭"),
    PURIST("purist", "💎"),
    OMNIVORE("omnivore", "🍽"),
    LOYALIST("loyalist", "🔁");

    companion object {
        fun of(selective: Boolean, concentrated: Boolean) = when {
            selective && concentrated -> PURIST
            selective -> RESTLESS_CURATOR
            concentrated -> LOYALIST
            else -> OMNIVORE
        }

        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

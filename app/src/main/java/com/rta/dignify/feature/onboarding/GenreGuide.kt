package com.rta.dignify.feature.onboarding

import com.rta.dignify.R

/**
 * 장르 한 줄 설명. iOS `GenreGuide` 이식.
 *
 * 음악을 잘 모르는 사람이 "R&B/Soul이 뭔데"에서 막히지 않을 만큼만 준다. 음악사·악기 편성
 * 강의는 하지 않는다 — 온보딩에서 읽을 분량이 아니다. 한 줄, 반말체, 판단에 필요한 것만.
 *
 * ponytail: 서버 컬럼이 아니라 클라 상수. 카피를 고치는 데 백엔드 배포가 필요 없고,
 * 장르가 늘어도 매칭 실패 시 설명만 빠지고 화면은 멀쩡하다.
 *
 * 키는 iTunes `primaryGenreName` 원문이라 `genres.genre_name_en`과 정확히 일치해야 한다.
 */
object GenreGuide {
    private val blurbs = mapOf(
        "Hip-Hop/Rap" to R.string.genre_hiphop,
        "Rock" to R.string.genre_rock,
        "Pop" to R.string.genre_pop,
        "Jazz" to R.string.genre_jazz,
        "Dance" to R.string.genre_dance,
        "Country" to R.string.genre_country,
        "R&B/Soul" to R.string.genre_rnb,
        "Electronic" to R.string.genre_electronic,
        "K-Pop" to R.string.genre_kpop,
        "Latin" to R.string.genre_latin,
        "CCM" to R.string.genre_ccm,
        // 아래 셋은 카탈로그가 얇다. 그래도 /genres에 나오므로 직접 고르는 화면에서
        // 설명 없이 뜨지 않게 채워 둔다. 퀴즈는 이들을 추천하지 않는다 —
        // 곡이 몇 개뿐이라 추천해봐야 피드가 바로 마른다.
        "Alternative" to R.string.genre_alternative,
        "Bass" to R.string.genre_bass,
        "Dubstep" to R.string.genre_dubstep,
    )

    /** 모르는 장르는 null — 설명만 빠지고 칩은 그대로 뜬다. */
    fun blurb(nameEn: String?): Int? = nameEn?.let { blurbs[it] }
}

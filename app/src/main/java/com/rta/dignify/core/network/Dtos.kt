package com.rta.dignify.core.network

import kotlinx.serialization.Serializable

/**
 * 서버 wire 타입 (openapi.yaml 스키마 그대로). iOS `DTOs.swift`와 이름·구조를 맞춘다.
 *
 * ponytail: 지금 호출하는 엔드포인트의 스키마만 있다. 픽·마이페이지·통계는 인증이 붙는
 * 시점에 같이 들어온다 — 부르지도 않는 DTO를 미리 깔면 스키마가 조용히 낡는다.
 * 날짜 필드도 그때 필요해지므로 지금은 하나도 없다(Instant 직렬화기 생략).
 */
object Api {

    @Serializable
    data class Genre(
        val genreId: Int,
        val genreName: String,
        /** 배포 순서 때문에 nullable — 백엔드보다 앱이 먼저 나가도 장르 목록이 안 죽는다. */
        val genreNameEn: String? = null,
    )

    @Serializable
    data class GenresResponse(val genres: List<Genre>)

    @Serializable
    data class FeedItem(
        val trackId: Int,
        val trackName: String,
        val artistName: String,
        val artworkUrl: String,
        val previewUrl: String,
        val trackViewUrl: String,
        val isHyped: Boolean = false,
        /** 이 트랙이 왜 떴는지 보여주는 장르 라벨(서버가 Accept-Language로 현지화). */
        val genreName: String? = null,
        /** 집계용. 로케일을 안 타서 Rock/록으로 쪼개지지 않는다. 화면엔 안 쓴다. */
        val genreNameEn: String? = null,
    )

    /** 이번 주 큐레이션 세트. setKey는 세트 교체 때만 바뀌는 식별자. */
    @Serializable
    data class CurationResponse(val setKey: String, val items: List<FeedItem>)

    @Serializable
    data class UserProfile(
        val nickname: String,
        val isOnboardingComplete: Boolean,
        val genres: List<ProfileGenre> = emptyList(),
    ) {
        @Serializable
        data class ProfileGenre(val genreId: Int, val genreName: String)
    }

    /** 피드/검색 공통 응답. nextCursor 없으면 hasMore=false (피드 소진). */
    @Serializable
    data class FeedResponse(
        val items: List<FeedItem>,
        val nextCursor: String? = null,
        val hasMore: Boolean = false,
        /** 이 페이지가 유저 장르 풀을 소진했는지. 검색 응답엔 없다. */
        val genreExhausted: Boolean? = null,
    )
}

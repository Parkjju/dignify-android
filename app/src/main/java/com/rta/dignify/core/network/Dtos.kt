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
        /**
         * 이 카드를 띄운 **내 하입 곡**. 무드로 뽑힌 카드에만 있고 콜드스타트·무작위·검색·
         * 큐레이션엔 없다(그래서 nullable이 기본값이 아니라 실제 상태다).
         */
        val similarTo: SimilarTrack? = null,
    )

    @Serializable
    data class SimilarTrack(
        val trackId: Int,
        val trackName: String,
        val artistName: String,
    )

    /**
     * `GET /onboarding/seed-pool`. 온보딩에서 직접 고르는 인기곡 풀.
     *
     * **정적이고 손으로 고른 목록이라 커서가 없다** — 한 응답에 전부 온다. 풀을 바꾸는 건
     * 배포가 아니라 운영 작업이다(`ops/onboarding-seed-pool.sql`).
     */
    @Serializable
    data class SeedPoolResponse(val items: List<FeedItem> = emptyList())

    /** 이번 주 큐레이션 세트. setKey는 세트 교체 때만 바뀌는 식별자. */
    @Serializable
    data class CurationResponse(val setKey: String, val items: List<FeedItem>)

    /**
     * `GET /tracks/{id}`. 피드 아이템보다 필드가 많다 — 앨범명·발매일·먼저 하입한 사람.
     * 상세 시트가 카드와 다른 화면인 이유가 이 세 가지다.
     *
     * 날짜는 String 그대로 받는다. `releaseDate`는 앞 10자만 쓰고(`2024-03-15` → `2024.03.15`)
     * `hypedAt`만 파싱하면 되므로, Instant 직렬화기를 다는 것보다 쓰는 쪽에서 다루는 게 싸다.
     */
    @Serializable
    data class TrackDetail(
        val trackId: Int,
        val trackName: String,
        val artistName: String,
        val collectionName: String? = null,
        val artworkUrl: String,
        val trackViewUrl: String,
        val releaseDate: String? = null,
        val genreName: String? = null,
        val firstHypers: List<UserSummary> = emptyList(),
    )

    /** 하입한 사람 요약. `hypedAt`은 ISO-8601 date-time. */
    @Serializable
    data class UserSummary(
        val userId: Long,
        val nickname: String,
        val hypedAt: String? = null,
    )

    /** `GET /users/me/hypes`. cursor는 마지막으로 받은 userHypeTrackId. */
    @Serializable
    data class HypeListResponse(
        val items: List<HypeItem> = emptyList(),
        val nextCursor: Long? = null,
    )

    @Serializable
    data class HypeItem(
        val userHypeTrackId: Long,
        val trackId: Int,
        val trackName: String,
        val artistName: String,
        val artworkUrl: String,
        val previewUrl: String,
        /** 하입 시각. 날짜별 섹션 그룹핑은 클라이언트 책임이라 이 값으로 묶는다. */
        val hypedAt: String,
        /** 추천 기준 곡으로 고정돼 있는지. 배포 순서 때문에 nullable. */
        val isSeed: Boolean? = null,
    )

    /**
     * `GET /users/me/stats`. **숫자만 온다** — 유형·헤드라인·잠금 판정은 전부 클라이언트 계산이다
     * (문구나 임계값을 바꿔도 서버를 다시 배포하지 않으려고). 계산은 `DiggingStats`가 한다.
     *
     * 모든 개수는 재생 횟수가 아니라 곡 종류 수(COUNT DISTINCT)다.
     */
    @Serializable
    data class UserStats(
        val range: String = "all",
        val distinctListenedCount: Int = 0,
        val hypeCount: Int = 0,
        val listenedByGenre: List<GenreCount> = emptyList(),
        val hypedByGenre: List<GenreCount> = emptyList(),
        /** 상위 5개만 온다 — 전체 합계 계산에 쓰면 안 된다. */
        val listenedByArtist: List<ArtistCount> = emptyList(),
        val hypedByArtist: List<ArtistCount> = emptyList(),
    )

    @Serializable
    data class GenreCount(val genreName: String, val count: Int)

    @Serializable
    data class ArtistCount(val artistName: String, val count: Int)

    @Serializable
    data class NicknameResponse(val nickname: String)

    // MARK: Picks

    /**
     * `GET /picks`. cursor는 `{official}_{pickId}` 형태의 불투명 문자열.
     *
     * `hasMore`는 `FeedResponse`와 같은 이유로 nullable이다 — 서버가 명시적 null을 보내면
     * 기본값이 안 먹고 파싱이 터진다. 읽는 쪽은 `== true`로 판정한다.
     */
    @Serializable
    data class PickListResponse(
        val items: List<Pick> = emptyList(),
        val nextCursor: String? = null,
        val hasMore: Boolean? = null,
    )

    /**
     * 픽 목록의 한 장. 트랙 전체가 아니라 **요약**만 온다 —
     * 실제 트랙은 `GET /picks/{id}`가 피드와 같은 `FeedResponse`로 준다(그대로 재생 지면에 태운다).
     *
     * `reactions`는 이모지 → 개수. `myReaction`은 내가 누른 이모지(없으면 null).
     */
    @Serializable
    data class Pick(
        val pickId: Long,
        val title: String? = null,
        val nickname: String,
        val isMine: Boolean = false,
        /** 운영이 만든 픽. 목록에서 위쪽에 고정된다. */
        val isOfficial: Boolean = false,
        val createdAt: String,
        val trackCount: Int = 0,
        val distinctArtistCount: Int = 0,
        val firstArtistName: String? = null,
        val firstTrackName: String? = null,
        /** 최대 3장. 카드 썸네일 스택에 쓴다. */
        val thumbnails: List<String> = emptyList(),
        val reactions: Map<String, Long> = emptyMap(),
        val myReaction: String? = null,
        /**
         * 픽 상세를 연 횟수 = 재생 진입 횟수. 서버가 `GET /picks/{id}`에서만 올리므로
         * 클라가 보낼 건 없다. `isOfficial`과 같은 이유로 nullable이다(배포 순서).
         */
        val playCount: Int? = null,
    )

    // MARK: Artist requests

    @Serializable
    data class ArtistRequestListResponse(val items: List<ArtistRequest> = emptyList())

    /** status는 서버 enum 문자열. 화면이 라벨을 붙이므로 String으로 받는다. */
    @Serializable
    data class ArtistRequest(
        val id: Long,
        val artistName: String,
        val status: String,
        val cancelReason: String? = null,
        val createdAt: String,
    )

    @Serializable
    data class UserProfile(
        val nickname: String,
        val isOnboardingComplete: Boolean,
        /** 하입 따라가기. 옛 서버는 안 내려주므로 nullable — 그때는 켜진 것으로 본다. */
        val diggingMode: Boolean? = null,
    )

    /**
     * 피드/검색/픽 상세 공통 응답. nextCursor 없으면 더 받을 게 없다.
     *
     * `hasMore`가 **nullable인 게 중요하다.** 픽 상세(`GET /picks/{id}`)는 페이지네이션이
     * 없어서 이 값을 `null`로 보내는데, 기본값(`= false`)은 **키가 아예 없을 때만** 쓰인다.
     * non-null 타입에 명시적 null이 오면 파싱이 통째로 터진다(픽 재생이 "불러오지 못했어요"로
     * 죽던 원인). 읽는 쪽은 `hasMore == true`로 판정한다.
     */
    @Serializable
    data class FeedResponse(
        val items: List<FeedItem>,
        val nextCursor: String? = null,
        val hasMore: Boolean? = null,
    )
}

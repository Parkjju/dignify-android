package com.rta.dignify.core.model

import com.rta.dignify.core.network.Api
import com.rta.dignify.core.network.itunesArtworkUrl

/** 도메인 모델. 서버 wire 타입(`Api.FeedItem`)과 분리 — 매핑은 이 파일 책임. */
data class Feed(
    val trackId: Int,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String,
    val previewUrl: String,
    val trackViewUrl: String,
    val isHyped: Boolean,
    val genreName: String?,
    /** 표시용은 genreName, 분석용은 이쪽. 로케일을 안 타므로 집계가 쪼개지지 않는다. */
    val genreNameEn: String?,
) {
    fun artworkUrl(size: Int): String = artworkUrl.itunesArtworkUrl(size)
}

fun Api.FeedItem.toFeed() = Feed(
    trackId = trackId,
    trackName = trackName,
    artistName = artistName,
    artworkUrl = artworkUrl,
    previewUrl = previewUrl,
    trackViewUrl = trackViewUrl,
    isHyped = isHyped,
    genreName = genreName,
    genreNameEn = genreNameEn,
)

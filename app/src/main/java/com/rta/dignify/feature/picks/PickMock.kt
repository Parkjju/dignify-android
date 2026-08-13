package com.rta.dignify.feature.picks

import com.rta.dignify.BuildConfig
import androidx.compose.runtime.mutableStateListOf
import com.rta.dignify.core.network.Api
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 픽 목업. **디버그 빌드에서 [ENABLED]가 true일 때만** 실 API 대신 쓰인다.
 *
 * 존재 이유: 픽 지면의 확인 대상(반응 표시, 운영자 씰, 내 픽/남의 픽 메뉴 분기, 요약 행의
 * "N+" 표기)은 **여러 사람이 올린 여러 픽**이 있어야 드러나는데, 계정 하나로는 그 상태를
 * 만들 수 없다. 남의 픽을 만들려면 다른 계정이 필요하고 운영자 픽은 서버에서만 찍힌다.
 *
 * 확인이 끝나면 [ENABLED]를 false로. 릴리즈는 `BuildConfig.DEBUG` 가드에 걸려 항상 실 API다.
 */
object PickMock {

    /** 목업을 쓸지. 실데이터로 확인하려면 false. */
    const val ENABLED = false

    val active: Boolean get() = BuildConfig.DEBUG && ENABLED

    private const val ART_A = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/77/86/4e/77864e55-ac11-8638-7358-c3c1ed4f636d/8809851792506.jpg/100x100bb.jpg"
    private const val ART_B = "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/3e/6d/df/3e6ddfda-8b1a-d16b-e2a5-f220020e3d4c/972440.jpg/100x100bb.jpg"

    /**
     * 5장. 각 장이 확인할 상태를 하나씩 맡는다:
     *
     *  1. 내 픽 + 제목 있음 — 요약 행의 얼굴이 되고, `···`에 **삭제만** 떠야 한다.
     *  2. 남의 픽 + **내가 🔥를 누른 상태** — 버블이 브랜드색으로 채워져야 한다.
     *  3. 운영자 픽 — 닉네임 옆 씰. 반응 0이라 안 채워진 버블과 비교된다.
     *  4. 제목 없음 + 아티스트 여러 명 — 폴백 제목이 "%s 외 N명"으로 조립돼야 한다.
     *  5. 제목 없음 + 1아티스트 여러 곡 — 폴백이 "%s의 %s 외 N곡"으로 갈려야 한다.
     */
    /**
     * 목업 상태에서 "올리기"로 만든 픽. 서버에 안 보내고 여기 쌓아서 결과 화면만 확인한다 —
     * 목업 trackId는 실제로 없는 트랙이라 서버가 받아주지 않고, 받아준대도 남의 지면에
     * 쓰레기 픽이 남는다.
     */
    private val created = mutableStateListOf<Api.Pick>()

    /**
     * 목업 상태면 서버 호출을 건너뛴다. 목업 pickId·trackId는 서버에 없는 값이라
     * 보내봐야 거절당하고, 통과하면 오히려 실제 데이터가 오염된다.
     *
     * 호출부마다 `if (!PickMock.active)`를 흩뿌리면 API를 새로 붙일 때 반드시 하나를
     * 빠뜨린다. 서버로 나가는 픽 관련 쓰기는 전부 이걸 통과시킨다.
     */
    suspend fun skipIfMock(block: suspend () -> Unit) {
        if (active) return
        runCatching { block() }
    }

    fun addLocal(
        title: String?,
        trackCount: Int,
        distinctArtistCount: Int,
        firstArtistName: String?,
        firstTrackName: String?,
        thumbnails: List<String>,
    ) {
        created.add(
            0,
            Api.Pick(
                pickId = 8000L + created.size,
                title = title,
                nickname = "digger_bacfb688",
                isMine = true,
                createdAt = Instant.now().toString(),
                trackCount = trackCount,
                distinctArtistCount = distinctArtistCount,
                firstArtistName = firstArtistName,
                firstTrackName = firstTrackName,
                thumbnails = thumbnails,
            ),
        )
    }

    fun items(mineOnly: Boolean = false): List<Api.Pick> {
        val now = Instant.now()
        val all = listOf(
            Api.Pick(
                pickId = 9001,
                title = "새벽에 듣는 것들",
                nickname = "digger_bacfb688",
                isMine = true,
                createdAt = now.minus(20, ChronoUnit.MINUTES).toString(),
                trackCount = 5,
                distinctArtistCount = 4,
                firstArtistName = "The Poles",
                firstTrackName = "Find Me!",
                thumbnails = listOf(ART_A, ART_B, ART_A),
                reactions = mapOf(PickReaction.PRIMARY to 3L),
            ),
            Api.Pick(
                pickId = 9002,
                title = "운전할 때",
                nickname = "night_owl",
                createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
                trackCount = 8,
                distinctArtistCount = 6,
                firstArtistName = "퍼플웨일",
                firstTrackName = "첫눈",
                thumbnails = listOf(ART_B, ART_A, ART_B),
                reactions = mapOf(PickReaction.PRIMARY to 12L),
                // 내가 누른 상태 — 버블이 채워져야 한다.
                myReaction = PickReaction.PRIMARY,
            ),
            Api.Pick(
                pickId = 9003,
                title = "이번 주 디깅",
                nickname = "dignify",
                isOfficial = true,
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
                trackCount = 7,
                distinctArtistCount = 7,
                firstArtistName = "Ellegarden",
                firstTrackName = "Strawberry Margarita",
                thumbnails = listOf(ART_A, ART_B),
                reactions = emptyMap(),
            ),
            Api.Pick(
                pickId = 9004,
                nickname = "crate_digger",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                trackCount = 4,
                distinctArtistCount = 3,
                firstArtistName = "강유정",
                firstTrackName = "Alone",
                thumbnails = listOf(ART_B),
                reactions = mapOf(PickReaction.PRIMARY to 1L),
            ),
            Api.Pick(
                pickId = 9005,
                nickname = "digger_bacfb688",
                isMine = true,
                createdAt = now.minus(5, ChronoUnit.DAYS).toString(),
                trackCount = 3,
                distinctArtistCount = 1,
                firstArtistName = "존박",
                firstTrackName = "Love Again",
                thumbnails = listOf(ART_A, ART_B, ART_A),
                reactions = mapOf(PickReaction.PRIMARY to 5L),
            ),
        )
        // 방금 만든 것이 맨 앞 — 최신순 목록이므로.
        val merged = created + all
        return if (mineOnly) merged.filter { it.isMine } else merged
    }
}

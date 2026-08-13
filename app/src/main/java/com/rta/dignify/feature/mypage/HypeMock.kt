package com.rta.dignify.feature.mypage

import com.rta.dignify.BuildConfig
import com.rta.dignify.core.network.Api
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 하입 목록 목업. **디버그 빌드에서 [ENABLED]가 true일 때만** 실 API 대신 쓰인다.
 *
 * 존재 이유: 크레이트의 핵심 동작(일별 행 분리, 날짜당 개수 제한, 행마다 붙는 See all,
 * 당김 애니메이션)은 **여러 날짜에 걸친 데이터**가 있어야 확인되는데, `hypedAt`을 서버가
 * 현재 시각으로 박아서 API로는 과거 날짜를 만들 수 없다. 운영 DB를 고쳐서 날짜를 조작하느니
 * 화면에 들어가는 값만 갈아끼우는 쪽이 싸고 되돌리기 쉽다.
 *
 * 확인이 끝나면 [ENABLED]를 false로 되돌린다. 릴리즈 빌드는 `BuildConfig.DEBUG` 가드에
 * 걸려 이 값과 무관하게 항상 실 API를 쓴다.
 */
object HypeMock {

    /** 실제 프리뷰 URL. 셀 탭 재생을 확인하려면 진짜 소리가 나야 한다. */
    private const val PREVIEW =
        "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview116/v4/22/01/8a/22018a55-3003-0a59-e343-98323f5b99ad/mzaf_17552383513481230621.plus.aac.p.m4a"

    /** 목업을 쓸지. 실데이터로 확인하려면 false. */
    const val ENABLED = false

    /** 디버그 빌드에서만 켜진다 — 릴리즈에 목업이 새어 나가지 않게. */
    val active: Boolean get() = BuildConfig.DEBUG && ENABLED

    /**
     * 4일치. 각 날짜가 서로 다른 경우를 하나씩 맡는다:
     *
     *  - 0일 전(14개) — 날짜당 상한(10)에 잘린다. 잘렸으므로 See all이 뜬다.
     *  - 1일 전(3개)  — 화면 폭 안에 다 들어온다(`fits`). **See all이 안 뜬다.**
     *  - 2일 전(8개)  — 상한엔 안 걸리지만 가로로 넘친다. See all이 뜬다.
     *  - 3일 전(5개)  — 미리보기 날짜 상한(3일) 밖이라 프로필에선 아예 안 보이고,
     *                   하입 기록 전체 화면에서만 보인다.
     *
     * 마지막 조건이 `hasMore`(날짜 3일 초과)를 참으로 만들어 See all 경로 자체를 연다.
     */
    fun items(): List<Api.HypeItem> {
        val counts = listOf(14, 3, 8, 5)
        var id = 9000L
        return counts.flatMapIndexed { dayAgo, count ->
            val day = Instant.now().minus(dayAgo.toLong(), ChronoUnit.DAYS)
            List(count) { i ->
                // 같은 날 안에서도 시각을 벌려 최신순 정렬이 눈에 보이게 한다.
                val at = day.minus(i.toLong(), ChronoUnit.MINUTES)
                Api.HypeItem(
                    userHypeTrackId = id--,
                    trackId = (10_000 + dayAgo * 100 + i),
                    trackName = "Mock ${dayAgo}일전 ${i + 1}",
                    artistName = "Mock Artist ${i + 1}",
                    // 실 아트워크 URL — 이미지 로딩·라운딩까지 같이 확인하려고 진짜 걸 쓴다.
                    artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/77/86/4e/77864e55-ac11-8638-7358-c3c1ed4f636d/8809851792506.jpg/100x100bb.jpg",
                    // 실제 프리뷰 URL. 빈 문자열이면 재생이 즉시 리턴돼 셀 탭이 먹통이 된다.
                    previewUrl = PREVIEW,
                    hypedAt = at.toString(),
                )
            }
        }
    }
}

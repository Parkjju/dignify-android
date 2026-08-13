package com.rta.dignify.feature.picks

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rta.dignify.R
import com.rta.dignify.core.network.Api

/**
 * 제목을 비운 픽의 표시 제목을 **클라이언트에서 조립한다**. iOS `PickTitle` 이식.
 *
 * 서버에 저장하지 않는 이유(iOS와 동일): ①게시 시점 로케일로 굳어 영어 유저가 한국어를 본다
 * ②선곡에서 파생되는 중복값 ③곡이 비활성화되면 틀린 말이 된다.
 * 작성 화면의 플레이스홀더도 같은 문자열을 쓴다 — 비우면 실제로 나올 제목을 미리 보여준다.
 */
object PickTitle {
    /**
     * 입력 필드 상한. Postgres `varchar(120)`는 code point로 세는데 화면은 글자로 세므로
     * 두 쪽이 "글자 수"에 합의할 수 없다. 클라가 30자로 자르고 서버는 컬럼 상한만 지킨다.
     */
    const val MAX_LENGTH = 30

    /**
     * 서버에 넘길 제목. 공백만 남으면 null — 빈 문자열과 null이 둘 다 "제목 없음"이면
     * 폴백 판정이 두 갈래로 갈린다.
     */
    fun normalized(input: String): String? = input.trim().ifEmpty { null }

    /** 화면 밖(문자열 리소스 없이)에서 쓸 때. 작성 화면 플레이스홀더가 이걸 쓴다. */
    fun fallback(context: Context, pick: Api.Pick): String {
        val artist = pick.firstArtistName.orEmpty()
        val track = pick.firstTrackName.orEmpty()
        return when {
            pick.distinctArtistCount > 1 ->
                context.getString(R.string.pick_title_many_artists, artist, pick.distinctArtistCount - 1)

            pick.trackCount > 1 ->
                context.getString(R.string.pick_title_many_tracks, track, artist, pick.trackCount - 1)

            else -> context.getString(R.string.pick_title_single, track, artist)
        }
    }
}

/** 표시용 제목. 제목이 비었으면 폴백을 조립한다. */
@Composable
fun Api.Pick.displayTitle(): String {
    title?.takeIf { it.isNotBlank() }?.let { return it }
    val artist = firstArtistName.orEmpty()
    val track = firstTrackName.orEmpty()
    return when {
        distinctArtistCount > 1 ->
            stringResource(R.string.pick_title_many_artists, artist, distinctArtistCount - 1)

        trackCount > 1 ->
            stringResource(R.string.pick_title_many_tracks, track, artist, trackCount - 1)

        else -> stringResource(R.string.pick_title_single, track, artist)
    }
}

/**
 * 픽 반응 이모지. iOS와 같이 시딩 단계엔 **한 종류만** 쓴다 — 5종을 깔면 얼마 안 되는 반응이
 * 잘게 쪼개져 카드마다 0이 도배되고, 그게 "아무도 안 쓰는 앱"으로 읽힌다.
 * 서버 화이트리스트는 5종 그대로라 늘릴 땐 클라만 고치면 된다.
 */
object PickReaction {
    const val PRIMARY = "🔥"
}

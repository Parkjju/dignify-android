package com.rta.dignify.feature.whatsnew

import com.rta.dignify.R

/**
 * 릴리즈 노트. iOS `Changelog` 이식.
 *
 * **1.0.0이 안드로이드의 첫 출시라 로그가 여기서 시작한다.** 그 앞의 빌드는 전부 내부 테스트라
 * 유저에게는 없던 버전이고, iOS 노트를 옮겨 적으면 "새로 생긴 것"이 아니라 "원래 있던 것"을
 * 새 소식이라 말하게 된다. 그래서 1.0.0 항목은 변경점이 아니라 **이 앱이 무엇인지**를 적는다
 * ("이제", "더 이상" 같은 상대 표현이 한 줄도 없는 이유).
 *
 * 첫 실행에 이게 뜨면 안 된다 — 갓 깐 사람에게 "새 소식"은 뜻이 없다. 막는 건
 * [shouldShow]의 `isReturningUser` 하나뿐이니 그 판정을 건드릴 땐 이 문단을 같이 본다.
 */
data class Release(val version: String, val notes: List<Int>)

object Changelog {
    // 최신이 위. WhatsNewSheet가 이 순서 그대로 그린다.
    val releases: List<Release> = listOf(
        Release(
            version = "1.0.0",
            notes = listOf(
                R.string.whatsnew_100_hypefeed,
                R.string.whatsnew_100_feed,
                R.string.whatsnew_100_picks,
                R.string.whatsnew_100_profile,
                R.string.whatsnew_100_share,
            ),
        ),
    )

    fun has(version: String) = releases.any { it.version == version }

    /**
     * 업데이트로 들어온 유저에게만 What's New를 띄운다.
     *
     *  - `lastSeen` 있음: 다른 버전이면 표시(일반 업데이트).
     *  - `lastSeen` 빈 값: 이 키가 처음 생긴 빌드의 첫 실행 → **기존 유저면 표시**,
     *    신규 설치(튜토리얼 대상)는 제외. 갓 깔고 들어온 사람에게 "새 소식"은 뜻이 없다.
     *
     * 노트 없는 버전은 항상 제외 — 보여줄 게 없는데 화면만 뜨는 걸 막는다.
     */
    fun shouldShow(lastSeen: String, current: String, isReturningUser: Boolean): Boolean {
        if (!has(current)) return false
        if (lastSeen.isEmpty()) return isReturningUser
        return lastSeen != current
    }
}

package com.rta.dignify.feature.whatsnew

import com.rta.dignify.R

/**
 * 릴리즈 노트. iOS `Changelog` 이식.
 *
 * 안드로이드는 iOS와 버전이 따로 가므로 **iOS 노트를 그대로 옮기지 않는다** — 안드로이드에
 * 실제로 나간 것만 적는다. iOS 1.0.9의 내용이 안드로이드 1.0.0에 처음부터 다 들어 있어서,
 * 옮겨 적으면 "새로 생긴 것"이 아니라 "원래 있던 것"을 새 소식이라 말하게 된다.
 */
data class Release(val version: String, val notes: List<Int>)

object Changelog {
    // 최신이 위. WhatsNewSheet가 이 순서 그대로 그린다.
    val releases: List<Release> = listOf(
        Release(
            version = "1.0.1",
            notes = listOf(
                R.string.whatsnew_101_session,
                R.string.whatsnew_101_curation,
                R.string.whatsnew_101_android16,
            ),
        ),
        Release(
            version = "1.0.0",
            notes = listOf(
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

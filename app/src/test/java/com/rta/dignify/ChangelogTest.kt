package com.rta.dignify

import com.rta.dignify.feature.whatsnew.Changelog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 새 소식 노출 판정. 틀리면 두 방향으로 조용히 망가진다 —
 * 안 떠서 아무도 새 기능을 모르거나, 매 실행마다 떠서 성가신 앱이 된다.
 */
class ChangelogTest {

    private val known = Changelog.releases.first().version

    @Test
    fun `노트가 없는 버전은 절대 안 띄운다`() {
        assertFalse(Changelog.shouldShow(lastSeen = "0.9.0", current = "9.9.9", isReturningUser = true))
    }

    @Test
    fun `버전이 올라가면 띄운다`() {
        assertTrue(Changelog.shouldShow(lastSeen = "0.9.0", current = known, isReturningUser = false))
    }

    @Test
    fun `같은 버전을 이미 봤으면 안 띄운다`() {
        assertFalse(Changelog.shouldShow(lastSeen = known, current = known, isReturningUser = true))
    }

    /** 이 키가 처음 생긴 빌드: 기존 유저에겐 띄우고, 갓 깐 사람에겐 안 띄운다(튜토리얼 대상). */
    @Test
    fun `기록이 없으면 기존 유저에게만 띄운다`() {
        assertTrue(Changelog.shouldShow(lastSeen = "", current = known, isReturningUser = true))
        assertFalse(Changelog.shouldShow(lastSeen = "", current = known, isReturningUser = false))
    }
}

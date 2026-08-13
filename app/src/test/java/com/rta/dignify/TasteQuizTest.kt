package com.rta.dignify

import com.rta.dignify.core.model.DiggingType
import com.rta.dignify.feature.onboarding.TasteQuiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 점수표 균형 검증. iOS가 전수 시뮬레이션으로 맞춘 값을 그대로 옮겼으므로, 한 칸이라도
 * 어긋나면 여기서 잡혀야 한다 — 안 잡히면 특정 장르가 **영영 추천되지 않는** 상태로
 * 출시된다(화면은 멀쩡해서 눈으로는 절대 안 보인다).
 */
class TasteQuizTest {

    /** 장르 문항은 앞 5개. 유형 문항(뒤 6개)은 장르 점수가 없다. */
    private val genreQuestionCount = 5

    @Test
    fun `모든 카탈로그 장르가 top3에 도달 가능하다`() {
        val reachable = mutableSetOf<String>()
        // 장르 문항 전 조합을 돈다(4×5×4×4×4 = 1280).
        forEachGenreCombo { answers ->
            reachable += TasteQuiz.result(answers).genreNames
        }
        val unreachable = TasteQuiz.catalogOrder - reachable
        assertTrue("추천 불가 장르: $unreachable", unreachable.isEmpty())
    }

    @Test
    fun `어느 장르도 조합의 과반을 먹지 않는다`() {
        val counts = mutableMapOf<String, Int>()
        var total = 0
        forEachGenreCombo { answers ->
            total++
            TasteQuiz.result(answers).genreNames.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val hog = counts.filter { it.value > total / 2 }
        assertTrue("과반 점유 장르: $hog (총 $total 조합)", hog.isEmpty())
    }

    @Test
    fun `유형 축은 3문항 다수결로 갈린다`() {
        // 장르 5문항은 0번, 유형 6문항은 선택성 3 + 폭 3.
        val selectiveConcentrated = List(genreQuestionCount) { 0 } + listOf(0, 0, 0, 0, 0, 0)
        assertEquals(DiggingType.PURIST, TasteQuiz.result(selectiveConcentrated).type)

        val openBroad = List(genreQuestionCount) { 0 } + listOf(1, 1, 1, 1, 1, 1)
        assertEquals(DiggingType.OMNIVORE, TasteQuiz.result(openBroad).type)
    }

    /** 답이 아예 없으면 가장 무난한 기본값(Omnivore)으로 떨어진다. */
    @Test
    fun `미응답은 Omnivore로 떨어진다`() {
        assertEquals(DiggingType.OMNIVORE, TasteQuiz.result(emptyList()).type)
    }

    private fun forEachGenreCombo(body: (List<Int>) -> Unit) {
        val sizes = (0 until genreQuestionCount).map { TasteQuiz.questions[it].options.size }
        fun recurse(i: Int, acc: List<Int>) {
            if (i == sizes.size) { body(acc); return }
            repeat(sizes[i]) { recurse(i + 1, acc + it) }
        }
        recurse(0, emptyList())
    }
}

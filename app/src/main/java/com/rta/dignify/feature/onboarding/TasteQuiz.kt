package com.rta.dignify.feature.onboarding

import com.rta.dignify.R
import com.rta.dignify.core.model.DiggingType

/**
 * 온보딩 취향 테스트의 문항·채점. iOS `TasteQuiz` 이식. 순수 로직이라 화면 없이 테스트된다.
 *
 * 두 가지를 동시에 뽑는다:
 *  - **유형** — 선택성·폭 2축(각 3문항, 홀수라 동점 없음) → `DiggingType` 4유형.
 *  - **추천 장르** — 별도 5문항의 장르 점수 합 상위 3개.
 *
 * 유형에서 장르를 유추하지 않는다. Purist라고 재즈를 좋아할 이유가 없어서, 장르는
 * 장르 문항에서만 나온다.
 *
 * **점수표를 임의로 고치지 말 것.** iOS가 전수 시뮬레이션(1280 조합)으로 균형을 맞춘 값이라
 * 한 칸만 바꿔도 특정 장르가 도달 불가가 되거나 과반을 먹는다. `TasteQuizTest`가 회귀를 잡는다.
 */
object TasteQuiz {

    data class Option(
        val label: Int,
        /** genreNameEn → 점수. 유형 문항은 비어 있다. */
        val genres: Map<String, Int> = emptyMap(),
        /** 선택성 축 기여(true = 까다로움). null이면 이 축과 무관. */
        val selective: Boolean? = null,
        /** 폭 축 기여(true = 집중). null이면 이 축과 무관. */
        val concentrated: Boolean? = null,
    )

    data class Question(val prompt: Int, val options: List<Option>)

    data class Result(val type: DiggingType, val genreNames: List<String>)

    /**
     * 퀴즈가 추천할 수 있는 장르 전체 + 동점 처리 순서(카탈로그 트랙 수 내림차순 —
     * 같은 점수면 피드가 더 안 마르는 쪽을 준다).
     *
     * `Alternative`·`Bass`·`Dubstep`은 일부러 뺐다. 고르자마자 피드가 말라 GENERAL 폴백으로
     * 넘어가서다. 직접 고르는 화면엔 그대로 노출된다.
     */
    val catalogOrder = listOf(
        "Hip-Hop/Rap", "Rock", "Pop", "Jazz", "Dance", "Country",
        "R&B/Soul", "Electronic", "K-Pop", "Latin", "CCM",
    )

    /** 장르 문항이 먼저다 — 구체적이고 답하기 쉬워서 이탈이 덜하다. */
    private val genreQuestions = listOf(
        Question(R.string.quiz_q_loud, listOf(
            Option(R.string.quiz_a_workout, mapOf("Hip-Hop/Rap" to 3, "Dance" to 3, "Electronic" to 2)),
            Option(R.string.quiz_a_driving, mapOf("Rock" to 3, "Country" to 3, "Pop" to 2)),
            Option(R.string.quiz_a_working, mapOf("Jazz" to 3, "Electronic" to 2, "R&B/Soul" to 2)),
            Option(R.string.quiz_a_hangout, mapOf("Latin" to 3, "Dance" to 2, "K-Pop" to 2)),
        )),
        Question(R.string.quiz_q_first, listOf(
            Option(R.string.quiz_a_flow, mapOf("Hip-Hop/Rap" to 4)),
            Option(R.string.quiz_a_story, mapOf("Country" to 3, "CCM" to 2, "Hip-Hop/Rap" to 1)),
            Option(R.string.quiz_a_voice, mapOf("R&B/Soul" to 3, "Pop" to 2, "K-Pop" to 1)),
            Option(R.string.quiz_a_guitars, mapOf("Rock" to 4, "Country" to 1)),
            Option(R.string.quiz_a_beat, mapOf("Electronic" to 3, "Dance" to 2, "K-Pop" to 1)),
        )),
        Question(R.string.quiz_q_night, listOf(
            Option(R.string.quiz_a_club, mapOf("Dance" to 3, "Electronic" to 2, "Latin" to 2)),
            Option(R.string.quiz_a_live, mapOf("Rock" to 3, "K-Pop" to 2)),
            Option(R.string.quiz_a_jazzbar, mapOf("Jazz" to 3, "R&B/Soul" to 3)),
            Option(R.string.quiz_a_home, mapOf("CCM" to 3, "R&B/Soul" to 2, "Country" to 2, "Pop" to 1)),
        )),
        Question(R.string.quiz_q_language, listOf(
            Option(R.string.quiz_a_korean, mapOf("K-Pop" to 4)),
            Option(R.string.quiz_a_spanish, mapOf("Latin" to 4)),
            Option(R.string.quiz_a_english, mapOf("Pop" to 2, "Rock" to 1, "Hip-Hop/Rap" to 1, "Country" to 1)),
            Option(R.string.quiz_a_nolyrics, mapOf("Electronic" to 3, "Jazz" to 3)),
        )),
        Question(R.string.quiz_q_purpose, listOf(
            Option(R.string.quiz_a_hyped, mapOf("Dance" to 2, "Hip-Hop/Rap" to 2, "K-Pop" to 2)),
            Option(R.string.quiz_a_sit, mapOf("CCM" to 3, "R&B/Soul" to 3, "Country" to 1)),
            Option(R.string.quiz_a_immerse, mapOf("Jazz" to 2, "Electronic" to 2, "Rock" to 1)),
            Option(R.string.quiz_a_mood, mapOf("Pop" to 3, "Latin" to 1, "K-Pop" to 1)),
        )),
    )

    /** 선택성 3문항 + 폭 3문항. 각 축 홀수라 동점이 안 난다. */
    private val typeQuestions = listOf(
        Question(R.string.quiz_q_playlist, listOf(
            Option(R.string.quiz_a_30tracks, selective = true),
            Option(R.string.quiz_a_300tracks, selective = false),
        )),
        Question(R.string.quiz_q_notdoing, listOf(
            Option(R.string.quiz_a_skip, selective = true),
            Option(R.string.quiz_a_letitride, selective = false),
        )),
        Question(R.string.quiz_q_saving, listOf(
            Option(R.string.quiz_a_fewmore, selective = true),
            Option(R.string.quiz_a_initgoes, selective = false),
        )),
        Question(R.string.quiz_q_loveartist, listOf(
            Option(R.string.quiz_a_discography, concentrated = true),
            Option(R.string.quiz_a_similar, concentrated = false),
        )),
        Question(R.string.quiz_q_newgenre, listOf(
            Option(R.string.quiz_a_notmything, concentrated = true),
            Option(R.string.quiz_a_pressplay, concentrated = false),
        )),
        Question(R.string.quiz_q_thisweek, listOf(
            Option(R.string.quiz_a_samelane, concentrated = true),
            Option(R.string.quiz_a_allover, concentrated = false),
        )),
    )

    val questions: List<Question> = genreQuestions + typeQuestions

    /** `answers[i]` = i번 문항에서 고른 선택지 인덱스. 범위를 벗어난 답은 무시한다. */
    fun result(answers: List<Int>): Result {
        val scores = mutableMapOf<String, Int>()
        var selectiveVotes = 0
        var concentratedVotes = 0

        answers.forEachIndexed { i, choice ->
            val question = questions.getOrNull(i) ?: return@forEachIndexed
            val option = question.options.getOrNull(choice) ?: return@forEachIndexed
            option.genres.forEach { (genre, points) -> scores[genre] = (scores[genre] ?: 0) + points }
            option.selective?.let { selectiveVotes += if (it) 1 else -1 }
            option.concentrated?.let { concentratedVotes += if (it) 1 else -1 }
        }

        // 축이 0표(전부 미응답)면 관대함/폭넓음 = Omnivore로 떨어진다. 가장 무난한 기본값.
        val type = DiggingType.of(selectiveVotes > 0, concentratedVotes > 0)

        val ranked = scores.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { catalogRank(it.key) }
        )
        return Result(type, ranked.take(3).map { it.key })
    }

    /** 카탈로그에 없는 장르는 맨 뒤로. */
    fun catalogRank(name: String) = catalogOrder.indexOf(name).takeIf { it >= 0 } ?: catalogOrder.size
}

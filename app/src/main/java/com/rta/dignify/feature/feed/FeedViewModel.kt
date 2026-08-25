package com.rta.dignify.feature.feed

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rta.dignify.core.analytics.Analytics
import com.rta.dignify.core.model.Feed
import com.rta.dignify.core.model.toFeed
import com.rta.dignify.core.auth.AuthState
import com.rta.dignify.core.auth.Session
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch

/**
 * 피드 화면의 상태 전부. iOS는 `FeedView`의 @State로 들고 있지만 안드로이드는 회전·프로세스
 * 재생성이 있어 ViewModel이 소유한다 — 그래야 스크롤 위치가 화면 재생성에 안 날아간다.
 */
private const val TAG = "DignifyFeed"

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val api = Session.api
    private val prefs = app.getSharedPreferences("dignify", Context.MODE_PRIVATE)

    var feeds by mutableStateOf<List<Feed>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    /** 피드 맨 앞에 붙은 이번 주 큐레이션 곡 수. 0이면 세트가 없거나 이미 완주한 세트. */
    var curationCount by mutableStateOf(0)
        private set

    /**
     * 이번 주 세트를 막 완주한 순간 한 번 true. 알림 권한을 물어보기에 이때가 제일 나은 자리다 —
     * 세트를 끝까지 본 사람이라야 "다음 세트 나오면 알려줄까?"가 말이 된다.
     */
    var justFinishedSet by mutableStateOf(false)
        private set

    /**
     * 마지막으로 머문 페이지. **탭을 옮겼다 돌아와도 보던 자리로 복귀하려고** 여기 둔다 —
     * `rememberPagerState`는 화면이 사라지면 같이 없어져서, 피드 탭에 다시 들어올 때마다
     * 처음부터 다시 훑어야 했다.
     */
    var lastPage by mutableStateOf(0)

    /** 확정된 검색어(빈 문자열이면 일반 피드). 비어있지 않으면 feeds는 검색 결과다. */
    var activeQuery by mutableStateOf("")
        private set

    private var nextCursor: String? = null
    private var isPaging = false
    private var curationSetKey = ""
    /** 마지막으로 노출 이벤트를 찍은 트랙. 같은 트랙 재정착을 걸러낸다. */
    private var lastViewedTrackId: Int? = null

    /** 검색 진입 시 일반 피드 상태를 보관 → 검색 종료 시 재페치 없이 복원. */
    private var savedFeed: Snapshot? = null

    private data class Snapshot(val list: List<Feed>, val index: Int, val cursor: String?)

    /**
     * 픽 재생 모드로 목록을 통째로 갈아끼운다. iOS가 `FeedMode.pick`으로 하는 일과 같다 —
     * **두 번째 플레이어를 만들지 않는다.** 서버가 픽 상세를 피드와 같은 `FeedResponse`로
     * 주기 때문에 이 화면이 그대로 재생 지면이 된다.
     */
    /**
     * 픽 재생 중이면 그 픽 주인의 닉네임. 상단 배지가 "@누구의 픽 1/7"로 갈리는 근거다.
     * 일반 피드에선 null이라 특집 배지 쪽으로 간다.
     */
    var pickNickname by mutableStateOf<String?>(null)
        private set

    /** 픽 재생 모드인가. 이 뷰모델은 일반 피드를 절대 안 받는다. */
    private val isPickMode: Boolean get() = pickNickname != null

    fun loadPick(pickId: Long, nickname: String) {
        pickNickname = nickname
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            curationCount = 0       // 픽 모드엔 특집 배지가 없다(픽 배지가 그 자리를 쓴다).
            activeQuery = ""
            nextCursor = null       // 픽은 페이지네이션이 없다(한 번에 다 온다).
            lastPage = 0
            runCatching { api.pickDetail(pickId) }
                .onSuccess { feeds = it.items.map { item -> item.toFeed() } }
                .onFailure {
                    // 로그를 남기는 이유는 로그인 실패 때와 같다 — 화면은 "불러오지 못했어요"만
                    // 띄우고 끝이라, 이게 없으면 왜 실패했는지 밖에서 알 방법이 없다.
                    Log.w(TAG, "pick detail load failed (pickId=$pickId)", it)
                    loadFailed = true
                }
            isLoading = false
        }
    }

    /**
     * 첫 진입 페치. 이미 로드돼 있으면 건너뛴다(force로 재시도).
     * 백엔드 커서는 시드+오프셋을 담고 있어, 저장해둔 커서로 이어보면 앱을 껐다 켜도
     * 같은 순서로 이어진다. cursor=null이면 새 시드라 처음부터 다시 나온다.
     */
    fun loadInitial(force: Boolean = false) {
        // 픽 재생 중이면 일반 피드를 받지 않는다. 화면은 같은 컴포저블이라 진입할 때마다
        // 이걸 부르는데, 막지 않으면 픽 트랙이 일반 피드로 덮인다.
        if (isPickMode) return
        if (!force && feeds.isNotEmpty()) return
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            try {
                val saved = prefs.getString(KEY_CURSOR, null)?.takeIf { it.isNotEmpty() }
                var res = api.feed(saved)
                // 저장된 커서가 소진/무효(빈 결과)면 새 세션으로 폴백.
                if (res.items.isEmpty() && saved != null) {
                    prefs.edit().remove(KEY_CURSOR).apply()
                    res = api.feed(null)
                }
                feeds = curationPrefix() + res.items.map { it.toFeed() }
                nextCursor = res.nextCursor?.takeIf { res.hasMore == true }
                // 소진되면 비워 다음 세션은 새 시드로.
                prefs.edit().putString(KEY_CURSOR, nextCursor.orEmpty()).apply()
            } catch (e: Exception) {
                loadFailed = true
            }
            isLoading = false
        }
    }

    /**
     * 이번 주 큐레이션 세트를 일반 피드 앞에 붙인다. 세트가 끝나면 그대로 일반 피드로
     * 이어지므로 별도 화면도 종료 처리도 없다.
     * ponytail: 실패하면 그냥 일반 피드 — 큐레이션이 없다고 피드가 비면 안 된다.
     */
    private suspend fun curationPrefix(): List<Feed> {
        curationCount = 0
        curationSetKey = ""
        // 세트는 로그인 유저 전용이다. 게스트는 완주해도 그 사실을 계정에 못 붙이므로
        // "이번 주 세트"라는 약속 자체가 성립하지 않는다 — 아예 앞세우지 않는다.
        if (Session.state != AuthState.SIGNED_IN) return emptyList()
        val set = try {
            api.curation()
        } catch (e: Exception) {
            return emptyList()
        }
        if (set.items.isEmpty() || set.setKey == prefs.getString(KEY_SEEN_SET, "")) return emptyList()
        curationCount = set.items.size
        curationSetKey = set.setKey
        return set.items.map { it.toFeed() }
    }

    /**
     * 이미 처리한 `Session.feedReloadTick`. **화면이 아니라 여기 있어야 한다** —
     * `LaunchedEffect(tick)`은 탭을 옮겼다 돌아올 때마다 다시 도는데, 틱이 그대로여도
     * 매번 재조회하면 보던 자리가 초기화된다(피드 탭이 매번 처음으로 돌아가던 원인).
     */
    private var handledReloadTick = 0

    /** 틱이 실제로 올라갔을 때만 다시 받는다. 같은 값이면 화면이 다시 붙은 것뿐이다. */
    fun onReloadTick(tick: Int) {
        // 픽 모드에선 무시. 새 뷰모델은 handledReloadTick이 0이라 틱이 올라가 있으면
        // 곧바로 reloadFromStart()가 돌아 픽 트랙을 일반 피드로 갈아치운다.
        if (isPickMode) {
            handledReloadTick = tick
            return
        }
        if (tick == handledReloadTick) return
        handledReloadTick = tick
        if (tick > 0) reloadFromStart()
    }

    /**
     * 성향 토글·기준 곡 변경·로그인처럼 **피드 구성 근거 자체가 달라졌을 때**.
     * 저장된 커서를 버리고 처음부터 받는다.
     *
     * 커서를 안 버리면 아무것도 안 바뀐 것처럼 보인다 — 커서에 시드와 오프셋이 박혀 있어서,
     * 그걸로 이어받는 한 서버가 옛 기준으로 뽑은 페이지를 계속 준다.
     *
     * 실패 후 `retry()`가 이걸 안 쓰는 건 의도다 — 같은 요청이 실패한 것뿐이라 이어보는 게 맞다.
     */
    fun reloadFromStart() {
        prefs.edit().remove(KEY_CURSOR).apply()
        loadInitial(force = true)
    }

    /** 끝 3장 이내로 접근하면 다음 페이지를 붙인다. 커서 소진 시 정지. */
    fun loadMoreIfNeeded(currentIndex: Int) {
        val cursor = nextCursor ?: return
        if (isPaging || currentIndex < feeds.size - 3) return
        isPaging = true
        viewModelScope.launch {
            try {
                val res = if (activeQuery.isEmpty()) api.feed(cursor) else api.search(activeQuery, cursor)
                feeds = feeds + res.items.map { it.toFeed() }
                nextCursor = res.nextCursor?.takeIf { res.hasMore == true }
                // 검색 커서는 세션 한정이라 저장하지 않는다.
                if (activeQuery.isEmpty()) {
                    prefs.edit().putString(KEY_CURSOR, nextCursor.orEmpty()).apply()
                }
            } catch (e: Exception) {
                // ponytail: 페이징 실패는 조용히 무시 — 다음 스와이프에 재시도된다.
            }
            isPaging = false
        }
    }

    /**
     * 세트를 지나 일반 피드로 넘어온 시점 = 완주. 다음 진입부터 이 세트를 안 앞세운다.
     * 매 트랙에서 돌지만 SharedPreferences 쓰기는 값이 같으면 사실상 공짜다.
     */
    fun onTrackViewed(index: Int) {
        // 검색 중엔 목록이 통째로 검색 결과다 — 여기서 curationCount를 넘겼다고 세트를 완주한
        // 게 아니다. 빼면 검색만 하고도 이번 주 세트를 못 본 채 날린다.
        if (activeQuery.isEmpty() &&
            curationCount > 0 && index >= curationCount && curationSetKey.isNotEmpty()
        ) {
            // 이미 같은 값이면 완주 순간이 아니라 그 뒤로 계속 스와이프 중인 것이다.
            if (prefs.getString(KEY_SEEN_SET, "") != curationSetKey) justFinishedSet = true
            prefs.edit().putString(KEY_SEEN_SET, curationSetKey).apply()
        }
        // 스킵률의 분모. current가 **실제로 바뀌었을 때만** 찍는다 — 목록이 갱신돼 같은 자리가
        // 다시 정착해도 노출이 늘면 분모가 부풀어 스킵률이 실제보다 나빠 보인다.
        val feed = feeds.getOrNull(index) ?: return
        if (feed.trackId == lastViewedTrackId) return
        lastViewedTrackId = feed.trackId
        Analytics.capture(
            "track_viewed",
            mapOf(
                "track_id" to feed.trackId,
                "artist" to feed.artistName,
                // 로케일 무관한 영문명. 표시용 라벨을 보내면 Rock/록이 다른 값으로 집계된다.
                "genre" to (feed.genreNameEn ?: feed.genreName ?: ""),
            ),
        )
    }

    /** 화면이 신호를 받아 처리했음. 다시 올라오면 또 물어보게 된다. */
    fun onSetCompletionHandled() {
        justFinishedSet = false
    }

    /** 검색 확정. 일반 피드를 스냅샷에 보관하고 결과로 교체한다. */
    fun runSearch(raw: String, currentIndex: Int) {
        val query = raw.trim()
        if (query.isEmpty()) return
        if (savedFeed == null) savedFeed = Snapshot(feeds, currentIndex, nextCursor)
        activeQuery = query
        viewModelScope.launch {
            isLoading = true
            loadFailed = false
            try {
                val res = api.search(query)
                feeds = res.items.map { it.toFeed() }
                nextCursor = res.nextCursor?.takeIf { res.hasMore == true }
            } catch (e: Exception) {
                loadFailed = true
            }
            isLoading = false
        }
    }

    /** 검색 종료 → 보관해둔 일반 피드로 복원(재페치 없이). 복원된 인덱스를 돌려준다. */
    fun clearSearch(): Int {
        activeQuery = ""
        val saved = savedFeed ?: return 0
        feeds = saved.list
        nextCursor = saved.cursor
        savedFeed = null
        return saved.index
    }

    /**
     * 하입 토글. 화면을 먼저 바꾸고 서버를 맞춘다 — 왕복을 기다리면 더블탭의 손맛이 죽는다.
     * 이미 목표 상태인 경우(409/404)는 성공으로 친다. 그 외 실패만 되돌린다.
     *
     * @return 로그인이 필요해서 아무것도 안 했으면 false. 화면이 로그인 유도를 띄운다.
     */
    fun toggleHype(trackId: Int): Boolean {
        if (Session.state != AuthState.SIGNED_IN) return false
        val index = feeds.indexOfFirst { it.trackId == trackId }
        if (index < 0) return true

        val target = !feeds[index].isHyped
        setHypeLocally(trackId, target)
        // 켤 때만 찍는다 — 해제는 하입의 반대 행동이라 같은 이벤트로 합치면 하입 수가 부풀려진다.
        if (target) Analytics.capture("track_hyped", mapOf("track_id" to trackId))
        viewModelScope.launch {
            try {
                val response = if (target) api.hype(trackId) else api.unhype(trackId)
                // 409(이미 하입) / 404(기록 없음)는 목표 상태와 일치하므로 되돌리지 않는다.
                if (!response.status.isSuccess() &&
                    response.status.value != 409 && response.status.value != 404
                ) {
                    setHypeLocally(trackId, !target)
                    return@launch
                }
                // 디깅 프로필 통계는 하입 수에서 파생된다. **서버에 반영된 뒤에** 알린다 —
                // 낙관적 시점에 알리면 재조회가 변경을 앞질러 옛 숫자를 그대로 받아온다.
                Session.onHypeChanged()
            } catch (e: Exception) {
                setHypeLocally(trackId, !target)
            }
        }
        return true
    }

    /**
     * 더블탭용. iOS와 같이 **켜기만** 한다 — 이미 하입한 곡을 더블탭해도 해제되지 않는다.
     * 실수로 두 번 두드려 하입이 풀리면 그게 풀린 줄도 모르기 때문이다. 해제는 하입 버튼으로만.
     *
     * @return 로그인이 필요해서 아무것도 안 했으면 false. 화면이 로그인 유도를 띄운다.
     */
    fun hypeOn(trackId: Int): Boolean {
        if (Session.state != AuthState.SIGNED_IN) return false
        if (feeds.any { it.trackId == trackId && it.isHyped }) return true
        return toggleHype(trackId)
    }

    private fun setHypeLocally(trackId: Int, value: Boolean) {
        feeds = feeds.map { if (it.trackId == trackId) it.copy(isHyped = value) else it }
    }

    /**
     * 임계 시간 이상 재생된 트랙을 서버에 기록한다. 집계용이라 실패해도 재시도하지 않는다.
     * 게스트는 조용히 건너뛴다 — 인증 엔드포인트라 401이고, 익명 기록은 유저별 분석에 못 쓴다.
     */
    fun recordListen(trackId: Int) {
        // 스킵률 = 1 - track_listened/track_viewed. 게스트도 세야 하므로 서버 가드보다 먼저 찍는다.
        Analytics.capture("track_listened", mapOf("track_id" to trackId))
        if (Session.state != AuthState.SIGNED_IN) return
        viewModelScope.launch { runCatching { api.listen(trackId) } }
    }

    fun retry(currentIndex: Int) {
        if (activeQuery.isEmpty()) loadInitial(force = true) else runSearch(activeQuery, currentIndex)
    }

    private companion object {
        const val KEY_CURSOR = "feedCursor"
        const val KEY_SEEN_SET = "seenCurationSet"
    }
}

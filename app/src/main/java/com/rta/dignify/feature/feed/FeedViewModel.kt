package com.rta.dignify.feature.feed

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    /** 확정된 검색어(빈 문자열이면 일반 피드). 비어있지 않으면 feeds는 검색 결과다. */
    var activeQuery by mutableStateOf("")
        private set

    private var nextCursor: String? = null
    private var isPaging = false
    private var curationSetKey = ""

    /** 검색 진입 시 일반 피드 상태를 보관 → 검색 종료 시 재페치 없이 복원. */
    private var savedFeed: Snapshot? = null

    private data class Snapshot(val list: List<Feed>, val index: Int, val cursor: String?)

    /**
     * 첫 진입 페치. 이미 로드돼 있으면 건너뛴다(force로 재시도).
     * 백엔드 커서는 시드+오프셋을 담고 있어, 저장해둔 커서로 이어보면 앱을 껐다 켜도
     * 같은 순서로 이어진다. cursor=null이면 새 시드라 처음부터 다시 나온다.
     */
    fun loadInitial(force: Boolean = false) {
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
                nextCursor = res.nextCursor?.takeIf { res.hasMore }
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

    /** 끝 3장 이내로 접근하면 다음 페이지를 붙인다. 커서 소진 시 정지. */
    fun loadMoreIfNeeded(currentIndex: Int) {
        val cursor = nextCursor ?: return
        if (isPaging || currentIndex < feeds.size - 3) return
        isPaging = true
        viewModelScope.launch {
            try {
                val res = if (activeQuery.isEmpty()) api.feed(cursor) else api.search(activeQuery, cursor)
                feeds = feeds + res.items.map { it.toFeed() }
                nextCursor = res.nextCursor?.takeIf { res.hasMore }
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
        if (curationCount > 0 && index >= curationCount && curationSetKey.isNotEmpty()) {
            prefs.edit().putString(KEY_SEEN_SET, curationSetKey).apply()
        }
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
                nextCursor = res.nextCursor?.takeIf { res.hasMore }
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
        viewModelScope.launch {
            try {
                val response = if (target) api.hype(trackId) else api.unhype(trackId)
                // 409(이미 하입) / 404(기록 없음)는 목표 상태와 일치하므로 되돌리지 않는다.
                if (!response.status.isSuccess() &&
                    response.status.value != 409 && response.status.value != 404
                ) {
                    setHypeLocally(trackId, !target)
                }
            } catch (e: Exception) {
                setHypeLocally(trackId, !target)
            }
        }
        return true
    }

    private fun setHypeLocally(trackId: Int, value: Boolean) {
        feeds = feeds.map { if (it.trackId == trackId) it.copy(isHyped = value) else it }
    }

    /**
     * 임계 시간 이상 재생된 트랙을 서버에 기록한다. 집계용이라 실패해도 재시도하지 않는다.
     * 게스트는 조용히 건너뛴다 — 인증 엔드포인트라 401이고, 익명 기록은 유저별 분석에 못 쓴다.
     */
    fun recordListen(trackId: Int) {
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

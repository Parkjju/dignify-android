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
import com.rta.dignify.core.auth.PushOpen
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

    /**
     * 평소 피드 앞에 붙은 구간들(신곡·연관·주간 추천·큐레이션), 순서대로. 배지와 계측 `source`가
     * 이걸 본다. 비어 있으면 처음부터 평소 피드다.
     */
    var segments by mutableStateOf<List<Segment>>(emptyList())
        private set

    /**
     * 앱을 열 때 붙은 신곡 구간을 막 지난 순간 한 번 true. "다음 신곡도 알림으로 받기"를
     * 제안할 자리다 — 신곡을 받아 본 사람이라야 그 제안이 말이 된다.
     */
    var justFinishedNewReleases by mutableStateOf(false)
        private set

    /** 알림이 앞 구간을 갈아 끼웠으니 첫 장으로 가라는 신호. 화면이 처리하고 내린다. */
    var jumpToTop by mutableStateOf(false)

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
    private var lastViewedIndex = -1

    /** 신곡 연관 구간을 이미 요청했나. 신곡 구간 하나에 한 번이다. */
    private var relatedRequested = false
    /** 연관 구간에서 듣지도 좋아하지도 않고 넘긴 횟수. 5가 되면 남은 연관 곡을 버린다. */
    private var relatedSkips = 0

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
            segments = emptyList()  // 픽 모드엔 특집 배지가 없다(픽 배지가 그 자리를 쓴다).
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
                // 앞 구간은 평소 피드를 받은 **뒤에** 정한다 — 받는 사이 도착한 알림 탭도 여기서 잡힌다.
                assemble(prefixParts(Session.consumePushOpen()), res.items.map { it.toFeed() })
                nextCursor = res.nextCursor?.takeIf { res.hasMore == true }
                // 소진되면 비워 다음 세션은 새 시드로.
                prefs.edit().putString(KEY_CURSOR, nextCursor.orEmpty()).apply()
            } catch (e: Exception) {
                loadFailed = true
            }
            isLoading = false
        }
    }

    /** 앞 구간 하나를 만드는 재료. [assemble]이 위치를 매겨 [Segment]로 바꾼다. */
    private class Part(val kind: String, val tracks: List<Feed>, val fromPush: Boolean = false)

    /**
     * 평소 피드 앞에 붙일 구간. 알림 탭이면 그 알림의 곡만(나중 탭이 앞 것을 덮는다), 아니면
     * 좋아요한 아티스트의 안 본 신곡 → 이번 주 큐레이션 순서다. 구간이 끝나면 그대로 평소 피드로
     * 이어지므로 별도 화면도 종료 처리도 없다.
     *
     * 로그인 유저 전용이다. 게스트는 완주해도 그 사실을 계정에 못 붙이고, 신곡은 좋아요가 계정에 있다.
     * ponytail: 실패하면 그 구간만 빠진다 — 앞 구간이 없다고 피드가 비면 안 된다.
     */
    private suspend fun prefixParts(open: PushOpen?): List<Part> {
        curationSetKey = ""
        relatedRequested = false
        relatedSkips = 0
        justFinishedNewReleases = false
        if (Session.state != AuthState.SIGNED_IN) return emptyList()
        if (open != null && open.trackIds.isNotEmpty()) {
            // 곡이 내려갔으면 서버가 빼고 준다. 다 내려갔으면 조용히 평소 피드.
            val tracks = runCatching { api.feedTracks(open.trackIds).items.map { it.toFeed() } }
                .onFailure { Log.w(TAG, "push tracks load failed (${open.type})", it) }
                .getOrDefault(emptyList())
            val kind = if (open.type == "weekly_pick") SOURCE_WEEKLY else SOURCE_NEW_RELEASE
            return listOf(Part(kind, tracks, fromPush = true))
        }
        // 큐레이션 알림을 눌렀으면 큐레이션만. 신곡을 앞에 끼우면 누른 알림과 다른 곡이 먼저 나온다.
        if (open?.type == "curation") return listOf(curationPart(fromPush = true))
        return listOf(newReleasePart(), curationPart(fromPush = false))
    }

    /**
     * 좋아요한 아티스트의 신곡(앱 안 표시라 알림·광고성 동의와 무관하다). 본 곡은 기기에 남겨
     * 다시 앞세우지 않는다 — 5초 안에 넘긴 곡은 서버 청취 기록에 없어서 서버만으로는 못 거른다.
     */
    private suspend fun newReleasePart(): Part {
        val seen = prefs.getStringSet(KEY_SEEN_NEW_RELEASES, emptySet()).orEmpty()
        val tracks = runCatching { api.newReleases().items }.getOrDefault(emptyList())
            .filter { it.trackId.toString() !in seen }
            .map { it.toFeed() }
        return Part(SOURCE_NEW_RELEASE, tracks)
    }

    /**
     * 이번 주 큐레이션 세트. 큐레이션 알림으로 들어온 진입이면 **이미 완주한 세트라도 다시 앞세운다** —
     * 눌렀는데 없으면 안 되기 때문이다.
     */
    private suspend fun curationPart(fromPush: Boolean): Part {
        val set = runCatching { api.curation() }.getOrNull()
        if (set == null || set.items.isEmpty()) return Part(SOURCE_CURATION, emptyList())
        if (!fromPush && set.setKey == prefs.getString(KEY_SEEN_SET, "")) return Part(SOURCE_CURATION, emptyList())
        curationSetKey = set.setKey
        return Part(SOURCE_CURATION, set.items.map { it.toFeed() })
    }

    /** 앞 구간 + 평소 피드. 앞에서 보여 줄 곡은 뒤 평소 피드에서 뺀다(같은 곡이 두 번 나오지 않게). */
    private fun assemble(parts: List<Part>, rest: List<Feed>) {
        var start = 0
        segments = parts.filter { it.tracks.isNotEmpty() }.map { part ->
            Segment(part.kind, start, part.tracks.size, part.fromPush).also { start += part.tracks.size }
        }
        val head = parts.flatMap { it.tracks }
        val ids = head.mapTo(HashSet()) { it.trackId }
        feeds = head + rest.filter { it.trackId !in ids }
        lastViewedIndex = -1
    }

    /** 이미 처리한 `Session.pushOpenTick`. 이유는 [handledReloadTick]과 같다. */
    private var handledPushOpenTick = 0

    /**
     * 알림 탭으로 앞 구간을 갈아 끼운다. 앞서 붙어 있던 구간(신곡·연관·주간·큐레이션)은 버리고,
     * 평소 피드는 **보던 자리 다음부터** 그대로 잇는다 — 알림이 평소 피드의 진행 위치를 바꾸면 안 된다.
     *
     * 피드가 아직 없거나 받는 중이면 건드리지 않는다. 대기 칸에 남아 [loadInitial]이 읽는다.
     */
    fun onPushOpenTick(tick: Int) {
        if (isPickMode) {
            handledPushOpenTick = tick
            return
        }
        if (tick == handledPushOpenTick) return
        handledPushOpenTick = tick
        if (feeds.isEmpty() || isLoading) return
        val open = Session.consumePushOpen() ?: return
        // 검색 결과를 보던 중이면 평소 피드로 되돌린 뒤 붙인다.
        if (activeQuery.isNotEmpty()) clearSearch()
        viewModelScope.launch {
            val restFrom = maxOf(segments.maxOfOrNull { it.end } ?: 0, lastPage + 1)
            val rest = feeds.drop(restFrom)
            assemble(prefixParts(open), rest)
            lastPage = 0
            jumpToTop = true
        }
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
        // 검색 중엔 목록이 통째로 검색 결과다 — 여기서 구간을 넘겼다고 세트를 완주한 게 아니다.
        // 빼면 검색만 하고도 이번 주 세트를 못 본 채 날린다.
        if (activeQuery.isEmpty() && !isPickMode) onSegmentProgress(index)
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
                // 잠금화면 넘김·백그라운드 자동 넘김으로 생긴 노출인지. 노출은 생기는 그 순간
                // 발사되므로 여기선 지금 값을 그대로 읽어도 된다(track_dwell은 아니다).
                //
                // 알려진 한계, iOS도 같다: 자동 넘김과 유저가 직접 누른 넘김이 둘 다
                // `background: true`라 구분되지 않는다. "백그라운드를 쓰긴 하나"엔 답하지만
                // "직접 넘기고 있나"엔 답 못 한다. 물어볼 이유가 생기기 전엔 속성을 더 안 붙인다.
                "background" to AppForeground.isBackground,
                "source" to source(feed.trackId),
            ),
        )
    }

    /**
     * 이 트랙이 어느 경로로 화면에 떴는지(iOS `source(for:)`와 같은 값). 검색은 장르 필터가 없고
     * 큐레이션은 전 유저 동일이라, 셋을 안 가르면 취향 밖 청취를 경로 탓으로 돌릴 수 없다.
     * 인덱스가 아니라 trackId로 판정한다 — dwell은 트랙을 떠난 뒤에 발사된다.
     * 픽 재생도 이 뷰모델을 쓰므로 맨 먼저 "pick"으로 가른다. 그 전 빌드에선 "feed"로 섞여 있다.
     */
    fun source(trackId: Int): String {
        if (isPickMode) return "pick"
        if (activeQuery.isNotEmpty()) return "search"
        val index = feeds.indexOfFirst { it.trackId == trackId }
        return segments.firstOrNull { index in it }?.kind ?: "feed"
    }

    /** 이 자리가 들어 있는 앞 구간. 배지가 쓴다. */
    fun segmentAt(index: Int): Segment? = segments.firstOrNull { index in it }

    /**
     * 구간을 지나며 할 일. 매 트랙에서 돌지만 SharedPreferences 쓰기는 값이 같으면 사실상 공짜다.
     * - 큐레이션: 지나면 완주 — 다음 진입부터 이 세트를 안 앞세운다
     * - 신곡: 본 곡을 기록 / 앱을 열 때 붙은 구간을 지나면 알림 제안 / 알림 구간 끝에서 연관 곡 요청
     * - 연관: 연속 5번 넘기면 남은 연관 곡을 버리고 평소 피드로
     */
    private fun onSegmentProgress(index: Int) {
        val previous = lastViewedIndex
        lastViewedIndex = index
        segments.firstOrNull { it.kind == SOURCE_CURATION }?.let { seg ->
            if (index >= seg.end && curationSetKey.isNotEmpty()) prefs.edit().putString(KEY_SEEN_SET, curationSetKey).apply()
        }
        val newRelease = segments.firstOrNull { it.kind == SOURCE_NEW_RELEASE }
        if (newRelease != null) {
            if (index in newRelease) {
                val seen = prefs.getStringSet(KEY_SEEN_NEW_RELEASES, emptySet()).orEmpty()
                prefs.edit().putStringSet(KEY_SEEN_NEW_RELEASES, seen + feeds[index].trackId.toString()).apply()
            }
            if (!newRelease.fromPush && previous in newRelease && index == newRelease.end) justFinishedNewReleases = true
            // ponytail: 마지막 신곡 카드에 **닿을 때** 요청한다. 지난 뒤에 받으면 이미 그려진 평소 피드 카드가
            // 연관 곡으로 바뀐다. 그래서 마지막 카드에서 누른 좋아요는 시드에 못 들어간다(한 곡이면 차이 없음).
            if (newRelease.fromPush && !relatedRequested && index == newRelease.end - 1) {
                relatedRequested = true
                loadRelated(newRelease)
            }
        }
        val related = segments.firstOrNull { it.kind == SOURCE_RELATED } ?: return
        if (previous in related && index > previous && ++relatedSkips >= RELATED_SKIP_LIMIT && index in related) {
            // 보고 있는 카드는 두고 그 뒤 연관 곡만 버린다. 보던 카드가 바뀌면 안 된다.
            feeds = feeds.take(index + 1) + feeds.drop(related.end)
            segments = segments.map { if (it === related) it.copy(size = index + 1 - it.start) else it }
        }
    }

    /** 시드 = 신곡 구간에서 좋아요한 곡, 없으면 신곡 중 무작위 1곡. 신곡 구간 바로 뒤에 끼운다. */
    private fun loadRelated(newRelease: Segment) {
        val tracks = feeds.subList(newRelease.start, newRelease.end).toList()
        val seeds = tracks.filter { it.isHyped }.map { it.trackId }.ifEmpty { listOf(tracks.random().trackId) }
        viewModelScope.launch {
            val related = runCatching { api.related(seeds).items.map { it.toFeed() } }
                .onFailure { Log.w(TAG, "related load failed", it) }
                .getOrNull()
            // 받는 사이 알림 탭·재조회로 목록이 바뀌었으면 버린다.
            if (related.isNullOrEmpty() || segments.firstOrNull { it.kind == SOURCE_NEW_RELEASE } != newRelease) return@launch
            val ids = feeds.mapTo(HashSet()) { it.trackId }
            val fresh = related.filter { it.trackId !in ids }
            if (fresh.isEmpty()) return@launch
            feeds = feeds.take(newRelease.end) + fresh + feeds.drop(newRelease.end)
            segments = segments + Segment(SOURCE_RELATED, newRelease.end, fresh.size)
            relatedSkips = 0
        }
    }

    /** 연관 구간에서 듣거나 좋아했으면 "넘김" 연속을 끊는다. */
    private fun onEngaged(trackId: Int) {
        if (source(trackId) == SOURCE_RELATED) relatedSkips = 0
    }

    /** 화면이 신호를 받아 처리했음. */
    fun onNewReleasesFinishedHandled() {
        justFinishedNewReleases = false
    }

    /** 첫 실행 광고성 동의 팝업을 띄운 적이 있나. 팝업마다 기록을 따로 둔다(공유하면 한쪽이 영영 안 뜬다). */
    val didOfferAdConsent: Boolean get() = prefs.getBoolean(KEY_DID_OFFER_AD_CONSENT, false)

    fun markAdConsentOffered() {
        prefs.edit().putBoolean(KEY_DID_OFFER_AD_CONSENT, true).apply()
    }

    /**
     * 이 아티스트 좋아요로 신곡 알림 팝업을 띄워도 되나. 띄우기로 하면 기록까지 한다.
     * 첫 팝업은 아무 아티스트로나 1회, 「나중에」 뒤엔 **그 아티스트 곡을 다시 좋아할 때** 1회 더, 그 뒤론 안 묻는다.
     * ponytail: 아티스트를 이름으로 센다 — 피드 응답에 아티스트 id가 없다.
     */
    fun claimHypeOffer(artist: String): Boolean {
        val offered = prefs.getStringSet(KEY_HYPE_OFFER_ARTISTS, emptySet()).orEmpty()
        val claim = when {
            offered.isEmpty() -> true
            artist in offered && !prefs.getBoolean(KEY_HYPE_OFFER_REASKED, false) -> {
                prefs.edit().putBoolean(KEY_HYPE_OFFER_REASKED, true).apply()
                true
            }
            else -> false
        }
        if (claim) prefs.edit().putStringSet(KEY_HYPE_OFFER_ARTISTS, offered + artist).apply()
        return claim
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
                // query를 담는 이유: 찾는데 없는 아티스트가 곧 다음 큐레이션·수집 후보다.
                Analytics.capture(
                    "search_performed",
                    mapOf("query" to query, "result_count" to res.items.size),
                )
                feeds = res.items.map { it.toFeed() }
                nextCursor = res.nextCursor?.takeIf { res.hasMore == true }
            } catch (e: Exception) {
                loadFailed = true
                Analytics.capture("search_performed", mapOf("query" to query, "failed" to true))
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
        if (index < 0) {
            Log.w(TAG, "hype ignored: track $trackId is not in the current feed")
            return true
        }

        val target = !feeds[index].isHyped
        setHypeLocally(trackId, target)
        // 켤 때만 찍는다 — 해제는 하입의 반대 행동이라 같은 이벤트로 합치면 하입 수가 부풀려진다.
        if (target) {
            Analytics.capture("track_hyped", mapOf("track_id" to trackId, "source" to source(trackId)))
            onEngaged(trackId)
        }
        viewModelScope.launch {
            try {
                val response = if (target) api.hype(trackId) else api.unhype(trackId)
                // 409(이미 하입) / 404(기록 없음)는 목표 상태와 일치하므로 되돌리지 않는다.
                if (!response.status.isSuccess() &&
                    response.status.value != 409 && response.status.value != 404
                ) {
                    // 되돌리기만 하고 화면엔 아무 말도 안 하는 자리다. 로그가 없으면
                    // "눌렀는데 안 된다"를 밖에서 가를 방법이 없다(픽 게시 4xx 때와 같은 함정).
                    Log.w(TAG, "hype failed (track=$trackId on=$target status=${response.status.value})")
                    setHypeLocally(trackId, !target)
                    return@launch
                }
                // 디깅 프로필 통계는 하입 수에서 파생된다. **서버에 반영된 뒤에** 알린다 —
                // 낙관적 시점에 알리면 재조회가 변경을 앞질러 옛 숫자를 그대로 받아온다.
                Session.onHypeChanged()
            } catch (e: Exception) {
                Log.w(TAG, "hype failed (track=$trackId on=$target)", e)
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

    /**
     * 게스트가 코치마크에서 한 첫 하입. 로그인 시트를 띄우지 않고 화면엔 하입된 채로 두고,
     * 곡은 [Session.holdGuestHype]가 맡았다가 로그인하면 올린다. 두 번째부터는 지금처럼 로그인 시트다.
     */
    fun hypeAsGuest(trackId: Int): Boolean {
        if (Session.state != AuthState.GUEST) return false
        if (feeds.none { it.trackId == trackId && !it.isHyped }) return true
        setHypeLocally(trackId, true)
        Analytics.capture("track_hyped", mapOf("track_id" to trackId, "source" to source(trackId)))
        Session.holdGuestHype(trackId)
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
        // 스킵률 = 1 - track_listened/track_viewed. 게스트도 세야 하므로 서버 가드보다 먼저 찍는다.
        Analytics.capture("track_listened", mapOf("track_id" to trackId, "source" to source(trackId)))
        onEngaged(trackId)
        if (Session.state != AuthState.SIGNED_IN) return
        viewModelScope.launch { runCatching { api.listen(trackId) } }
    }

    fun retry(currentIndex: Int) {
        if (activeQuery.isEmpty()) loadInitial(force = true) else runSearch(activeQuery, currentIndex)
    }

    private companion object {
        const val KEY_CURSOR = "feedCursor"
        const val KEY_SEEN_SET = "seenCurationSet"
        // ponytail: 본 신곡 id는 쌓이기만 한다. 신곡은 7일 지나면 서버가 안 주니 지워도 되지만, 수가 작다.
        const val KEY_SEEN_NEW_RELEASES = "seenNewReleaseIds"
        const val KEY_DID_OFFER_AD_CONSENT = "didOfferAdConsent"
        const val KEY_HYPE_OFFER_ARTISTS = "hypeOfferArtistIds"
        const val KEY_HYPE_OFFER_REASKED = "hypeOfferReasked"
        const val RELATED_SKIP_LIMIT = 5
    }
}

// 계측 `source` 값 그대로다(iOS `source(for:)`와 같은 값).
const val SOURCE_CURATION = "curation"
const val SOURCE_NEW_RELEASE = "new_release"
const val SOURCE_RELATED = "new_release_related"
const val SOURCE_WEEKLY = "weekly"

/** 평소 피드 앞의 한 구간. `kind`는 계측 `source` 값이다. `fromPush`는 알림 탭으로 붙은 구간. */
data class Segment(val kind: String, val start: Int, val size: Int, val fromPush: Boolean = false) {
    val end: Int get() = start + size
    operator fun contains(index: Int) = index in start until end
}

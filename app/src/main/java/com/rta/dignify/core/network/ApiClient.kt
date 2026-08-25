package com.rta.dignify.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.rta.dignify.BuildConfig
import java.util.Locale

/**
 * 인증 엔드포인트 표식. 이게 붙은 요청은 **Authorization을 아예 안 달고 나가고**, 401이 나도
 * 갱신을 트리거하지 않는다(refresh가 스스로를 다시 부르면 무한 루프다).
 *
 * 헤더를 안 다는 게 핵심이다 — 백엔드 d9a3e12부터 permitAll 경로도 유효하지 않은 Bearer면
 * 401이라, 만료된 액세스 토큰을 붙여 `/auth/refresh`로 가면 갱신 자체가 막힌다.
 */
private val SkipAuth = AttributeKey<Unit>("SkipAuth")

@Serializable
private data class GoogleSignInBody(val idToken: String)

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class DiggingModeBody(val enabled: Boolean)

@Serializable
private data class SeedTracksBody(val trackIds: List<Long>)

@Serializable
private data class NicknameBody(val nickname: String)

@Serializable
private data class PickCreateBody(val title: String?, val trackIds: List<Long>)

@Serializable
private data class PickTitleBody(val title: String?)

@Serializable
private data class EmojiBody(val emoji: String)

@Serializable
private data class ReportBody(val pickId: Long, val reason: String, val detail: String?)

@Serializable
private data class ArtistNameBody(val artistName: String)

/**
 * `environment`는 APNs의 sandbox/production 분기용이라 FCM엔 대응 개념이 없는데,
 * 서버가 @NotBlank로 받으므로 비울 수 없다. 안드로이드는 아무 값이나 발송에 영향이 없다.
 */
@Serializable
private data class DeviceTokenBody(
    val token: String,
    val environment: String,
    val platform: String,
    val timeZone: String,
)

/**
 * 백엔드 호출 전부. iOS는 `Endpoint` 값 타입 + `APIClient` 액터로 나눠져 있지만,
 * Ktor가 요청 조립과 직렬화를 해주므로 여기선 함수 하나 = 엔드포인트 하나다.
 *
 * 인증은 Ktor `Auth` 플러그인 대신 손으로 붙였다. 그 플러그인은 401 응답의
 * `WWW-Authenticate` 헤더를 보고 갱신을 트리거하는데, **이 서버는 그 헤더를 안 보낸다**
 * (실측: 401 본문만 `{"code":"AUTH_TOKEN_INVALID"}`). 그래서 상태 코드만 보고 판단한다.
 */
class ApiClient(
    private val store: TokenStore,
    private val baseUrl: String = BASE_URL,
    engine: HttpClientEngine? = null,
    /** refresh까지 실패해 세션이 끊겼을 때. 화면이 로그아웃 상태로 돌아가는 데 쓴다. */
    private val onAuthFailure: () -> Unit = {},
) {
    private val refreshMutex = Mutex()

    private val client: HttpClient = buildClient(engine).apply {
        plugin(HttpSend).intercept { request ->
            val sent = if (request.attributes.contains(SkipAuth)) null else store.tokens
            // `header()`가 아니라 `headers[...] =` 인 게 중요하다 — 전자는 덧붙이기라
            // 재시도 때 낡은 Authorization이 남고 서버는 첫 번째 것을 읽어 또 401을 낸다.
            sent?.let { request.headers[HttpHeaders.Authorization] = "Bearer ${it.accessToken}" }

            val call = execute(request)
            // sent == null이면 붙일 토큰이 없었다는 뜻 = 게스트이거나 인증 엔드포인트다. 둘 다 갱신 대상이 아니다.
            if (call.response.status != HttpStatusCode.Unauthorized || sent == null) {
                return@intercept call
            }

            // 401 → 갱신 후 원 요청 1회 재시도. 실패하면 원래 401을 그대로 올려보낸다.
            val fresh = refresh(sent.accessToken) ?: return@intercept call
            request.headers[HttpHeaders.Authorization] = "Bearer ${fresh.accessToken}"
            execute(request)
        }
    }

    /**
     * refresh는 동시에 여러 요청이 401을 받아도 **한 번만** 돈다. 서버가 refresh 토큰을
     * rotation하기 때문에, 두 번 돌면 나중 것이 이미 폐기된 토큰을 들고 가 세션이 끊긴다.
     *
     * @param seenAccessToken 401을 받은 요청이 들고 갔던 access token. 락을 잡은 사이
     *   다른 요청이 이미 갱신했으면 이 값과 달라지므로, 그땐 갱신하지 않고 새 토큰만 돌려준다.
     */
    private suspend fun refresh(seenAccessToken: String): AuthTokens? = refreshMutex.withLock {
        val current = store.tokens ?: return null
        if (current.accessToken != seenAccessToken) return current

        return try {
            val new: AuthTokens = client.post("$baseUrl/auth/refresh") {
                attributes.put(SkipAuth, Unit)
                contentType(ContentType.Application.Json)
                setBody(RefreshBody(current.refreshToken))
            }.body()
            store.tokens = new
            new
        } catch (e: Exception) {
            // 슬라이딩 윈도우 refresh 실패 → 재로그인 필요.
            store.tokens = null
            onAuthFailure()
            null
        }
    }

    // MARK: Auth

    /** Credential Manager가 받아온 Google ID 토큰을 우리 세션으로 바꾼다. */
    suspend fun signInWithGoogle(idToken: String): AuthTokens {
        val tokens: AuthTokens = client.post("$baseUrl/auth/google") {
            attributes.put(SkipAuth, Unit)
            contentType(ContentType.Application.Json)
            setBody(GoogleSignInBody(idToken))
        }.body()
        store.tokens = tokens
        return tokens
    }

    /** 서버에 refresh 토큰 폐기를 요청하고 로컬을 비운다. 서버 실패해도 로컬은 정리한다. */
    suspend fun logout() {
        val refreshToken = store.tokens?.refreshToken
        if (refreshToken != null) {
            runCatching {
                client.post("$baseUrl/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshBody(refreshToken))
                }
            }
        }
        store.tokens = null
    }

    val isAuthenticated: Boolean get() = store.tokens != null

    // MARK: Feed

    suspend fun feed(cursor: String? = null): Api.FeedResponse =
        client.get("$baseUrl/feed") { cursor?.let { parameter("cursor", it) } }.body()

    /** 전 유저 동일 내용이라 개인화 파라미터도 커서도 없다. */
    suspend fun curation(): Api.CurationResponse =
        client.get("$baseUrl/feed/curation").body()

    suspend fun search(query: String, cursor: String? = null): Api.FeedResponse =
        client.get("$baseUrl/feed/search") {
            parameter("q", query)
            cursor?.let { parameter("cursor", it) }
        }.body()

    // MARK: Tracks

    /** 트랙 상세 + 먼저 하입한 유저 최대 5명. 인증 필수라 게스트는 호출하면 401이다. */
    suspend fun trackDetail(trackId: Int): Api.TrackDetail =
        client.get("$baseUrl/tracks/$trackId").body()

    suspend fun hype(trackId: Int): HttpResponse = client.post("$baseUrl/tracks/$trackId/hype")

    suspend fun unhype(trackId: Int): HttpResponse = client.delete("$baseUrl/tracks/$trackId/hype")

    suspend fun listen(trackId: Int): HttpResponse = client.post("$baseUrl/tracks/$trackId/listen")

    // MARK: Users

    suspend fun myProfile(): Api.UserProfile = client.get("$baseUrl/users/me").body()

    /** 하입 따라가기 on/off. 204만 온다 — 몸통이 없으므로 응답을 읽지 않는다. */
    suspend fun setDiggingMode(enabled: Boolean) {
        client.patch("$baseUrl/users/me/digging-mode") {
            contentType(ContentType.Application.Json)
            setBody(DiggingModeBody(enabled))
        }
    }

    /**
     * 추천 기준 곡 고정. 최대 3개, 빈 배열이면 해제(= 최근 하입 3곡으로 복귀).
     * 하입한 적 없는 트랙은 서버가 조용히 버린다.
     */
    suspend fun setSeedTracks(trackIds: List<Int>) {
        client.put("$baseUrl/users/me/seeds") {
            contentType(ContentType.Application.Json)
            setBody(SeedTracksBody(trackIds.map { it.toLong() }))
        }
    }

    /** 소리 2지선다 후보. 인증 필수 — 게스트가 부르면 401이다. */
    suspend fun onboardingCandidates(): Api.OnboardingCandidates =
        client.get("$baseUrl/onboarding/candidates").body()

    suspend fun completeOnboarding() {
        client.post("$baseUrl/users/me/onboarding/complete")
    }

    /** 내가 하입한 트랙. 최신순, 페이지 10 고정. cursor는 마지막으로 받은 userHypeTrackId. */
    suspend fun myHypes(cursor: Long? = null): Api.HypeListResponse =
        client.get("$baseUrl/users/me/hypes") { cursor?.let { parameter("cursor", it) } }.body()

    /** @param range "all"(기본) 또는 "week"(최근 7일). 그 외 값은 서버가 전체로 처리한다. */
    suspend fun myStats(range: String = "all"): Api.UserStats =
        client.get("$baseUrl/users/me/stats") { parameter("range", range) }.body()

    /** 409=중복, 400=규칙 위반. 화면이 상태 코드로 갈라 문구를 낸다(서버 문구는 한국어 고정). */
    suspend fun updateNickname(nickname: String): Api.NicknameResponse =
        client.patch("$baseUrl/users/me/nickname") {
            contentType(ContentType.Application.Json)
            setBody(NicknameBody(nickname))
        }.body()

    /** 회원 탈퇴. 서버가 픽·하입·장르까지 CASCADE로 지운다. 성공하면 로컬 토큰도 비운다. */
    suspend fun withdraw() {
        client.post("$baseUrl/auth/withdraw")
        store.tokens = null
    }

    // MARK: Picks

    /** @param mine true면 내 픽만. 인증이 없으면 서버가 401을 낸다(공개 목록은 mine=false). */
    suspend fun picks(cursor: String? = null, mine: Boolean = false): Api.PickListResponse =
        client.get("$baseUrl/picks") {
            cursor?.let { parameter("cursor", it) }
            if (mine) parameter("mine", true)
        }.body()

    /**
     * 픽 상세. **피드와 같은 `FeedResponse`가 온다** — 그래서 픽 재생은 새 플레이어를 만들지 않고
     * 피드 화면을 모드만 바꿔 그대로 태운다.
     */
    suspend fun pickDetail(pickId: Long): Api.FeedResponse =
        client.get("$baseUrl/picks/$pickId").body()

    /** @param trackIds 1~30개. 서버가 개수를 검증한다. */
    suspend fun createPick(title: String?, trackIds: List<Int>): HttpResponse =
        client.post("$baseUrl/picks") {
            contentType(ContentType.Application.Json)
            setBody(PickCreateBody(title, trackIds.map { it.toLong() }))
        }

    suspend fun deletePick(pickId: Long): HttpResponse = client.delete("$baseUrl/picks/$pickId")

    /** 이모지 하나. 같은 픽에 다시 부르면 교체된다(반응은 유저당 하나). */
    suspend fun setReaction(pickId: Long, emoji: String): HttpResponse =
        client.put("$baseUrl/picks/$pickId/reaction") {
            contentType(ContentType.Application.Json)
            setBody(EmojiBody(emoji))
        }

    suspend fun deleteReaction(pickId: Long): HttpResponse =
        client.delete("$baseUrl/picks/$pickId/reaction")

    suspend fun updatePickTitle(pickId: Long, title: String?): HttpResponse =
        client.put("$baseUrl/picks/$pickId/title") {
            contentType(ContentType.Application.Json)
            setBody(PickTitleBody(title))
        }

    /** @param reason NICKNAME / CONTENT / OTHER */
    suspend fun reportPick(pickId: Long, reason: String, detail: String?): HttpResponse =
        client.post("$baseUrl/reports") {
            contentType(ContentType.Application.Json)
            setBody(ReportBody(pickId, reason, detail))
        }

    // MARK: Artist requests

    suspend fun requestArtist(artistName: String): HttpResponse =
        client.post("$baseUrl/artist-requests") {
            contentType(ContentType.Application.Json)
            setBody(ArtistNameBody(artistName))
        }

    suspend fun artistRequests(): Api.ArtistRequestListResponse =
        client.get("$baseUrl/artist-requests").body()

    suspend fun deleteArtistRequest(id: Long): HttpResponse =
        client.delete("$baseUrl/artist-requests/$id")

    // MARK: Push

    /**
     * FCM 토큰 등록. 같은 엔드포인트를 iOS가 APNs 토큰으로 쓰고 있고, `platform`이 발송 경로를 가른다.
     *
     * 타임존을 같이 올려야 서버가 기기 로컬 시각으로 공지를 쏜다 — 유저가 한국과 미국으로 갈려 있어
     * UTC 고정 발송은 한쪽이 반드시 새벽이 된다.
     */
    suspend fun registerDeviceToken(token: String, timeZone: String): HttpResponse =
        client.post("$baseUrl/users/me/device-token") {
            contentType(ContentType.Application.Json)
            setBody(DeviceTokenBody(token, environment = "production", platform = "android", timeZone = timeZone))
        }

    companion object {
        // ponytail: base URL 상수 하나. 환경 분기 필요해지면 그때 BuildConfig로.
        const val BASE_URL = "https://dignify-backend-460750160818.us-central1.run.app"

        private fun buildClient(engine: HttpClientEngine?): HttpClient {
            val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
                install(ContentNegotiation) {
                    // 서버가 필드를 추가해도 앱이 안 깨져야 한다 — iOS가 optional로 푸는 문제와 같다.
                    json(Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    // 장르명·에러 문구를 서버가 이 헤더로 현지화한다. 안 보내면 영어로 온다.
                    headers.append("Accept-Language", Locale.getDefault().toLanguageTag())
                    // 서버가 UA에서 `dignify/<빌드번호>`를 주워 담아 푸시를 버전별로 갈라 쏜다
                    // (iOS는 URLSession 기본 UA가 이미 이 모양이라 안 건드렸다). 기본 UA인
                    // "okhttp/4.x"를 두면 빌드가 null로 남아 minBuild 조건부 발송에서 통째로 빠진다.
                    headers.append(HttpHeaders.UserAgent, "dignify/${BuildConfig.VERSION_CODE}")
                }
            }
            return if (engine == null) HttpClient(OkHttp, config) else HttpClient(engine, config)
        }
    }
}

/**
 * iTunes 아트워크 URL의 `WxH` 사이즈 세그먼트를 임의 크기로 바꾼다.
 * 'x'가 숫자 사이에 오는 곳은 사이즈 세그먼트뿐이라(경로 해시는 hex) 정규식이 안전.
 * 백엔드가 iTunes 원본 URL(대부분 100×100)을 그대로 저장하므로 표시 전 키워야 한다.
 */
fun String.itunesArtworkUrl(size: Int): String =
    replace(Regex("[0-9]+x[0-9]+"), "${size}x$size")

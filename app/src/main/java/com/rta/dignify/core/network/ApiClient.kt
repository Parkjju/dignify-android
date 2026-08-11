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
import java.util.Locale

/** refresh 요청 자신은 401이 나도 다시 refresh하면 안 된다. 그 표식. */
private val SkipAuthRetry = AttributeKey<Unit>("SkipAuthRetry")

@Serializable
private data class GoogleSignInBody(val idToken: String)

@Serializable
private data class RefreshBody(val refreshToken: String)

@Serializable
private data class GenreIdsBody(val genreIds: List<Int>)

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
            val sent = store.tokens
            // `header()`가 아니라 `headers[...] =` 인 게 중요하다 — 전자는 덧붙이기라
            // 재시도 때 낡은 Authorization이 남고 서버는 첫 번째 것을 읽어 또 401을 낸다.
            sent?.let { request.headers[HttpHeaders.Authorization] = "Bearer ${it.accessToken}" }

            val call = execute(request)
            if (call.response.status != HttpStatusCode.Unauthorized ||
                request.attributes.contains(SkipAuthRetry) ||
                sent == null
            ) {
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
                attributes.put(SkipAuthRetry, Unit)
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
            attributes.put(SkipAuthRetry, Unit)
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

    suspend fun hype(trackId: Int): HttpResponse = client.post("$baseUrl/tracks/$trackId/hype")

    suspend fun unhype(trackId: Int): HttpResponse = client.delete("$baseUrl/tracks/$trackId/hype")

    suspend fun listen(trackId: Int): HttpResponse = client.post("$baseUrl/tracks/$trackId/listen")

    // MARK: Users

    suspend fun genres(): Api.GenresResponse = client.get("$baseUrl/genres").body()

    suspend fun myProfile(): Api.UserProfile = client.get("$baseUrl/users/me").body()

    suspend fun updateGenres(ids: List<Int>) {
        client.put("$baseUrl/users/me/genres") {
            contentType(ContentType.Application.Json)
            setBody(GenreIdsBody(ids))
        }
    }

    suspend fun completeOnboarding() {
        client.post("$baseUrl/users/me/onboarding/complete")
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

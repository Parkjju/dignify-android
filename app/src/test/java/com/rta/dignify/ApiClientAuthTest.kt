package com.rta.dignify

import com.rta.dignify.core.network.ApiClient
import com.rta.dignify.core.network.AuthTokens
import com.rta.dignify.core.network.TokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 401 → refresh → 재시도. 서버가 refresh 토큰을 rotation하기 때문에 **갱신이 두 번 돌면
 * 나중 것이 폐기된 토큰을 들고 가 세션이 끊긴다.** 눈으로는 못 잡는 종류라 테스트로 박아둔다.
 */
class ApiClientAuthTest {

    private class MemoryTokenStore(override var tokens: AuthTokens?) : TokenStore

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `401을 받으면 갱신 후 원 요청을 다시 보낸다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("old-access", "old-refresh"))
        val seenAuth = mutableListOf<String?>()

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" ->
                    respond("""{"accessToken":"new-access","refreshToken":"new-refresh"}""", headers = json)

                else -> {
                    seenAuth += request.headers[HttpHeaders.Authorization]
                    if (seenAuth.size == 1) {
                        respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized, json)
                    } else {
                        respond("""{"items":[],"hasMore":false}""", headers = json)
                    }
                }
            }
        }

        val api = ApiClient(store, engine = engine)
        api.feed()

        assertEquals(listOf("Bearer old-access", "Bearer new-access"), seenAuth)
        assertEquals("new-access", store.tokens?.accessToken)
        // rotation된 refresh 토큰까지 저장해야 다음 갱신이 성공한다.
        assertEquals("new-refresh", store.tokens?.refreshToken)
    }

    @Test
    fun `동시에 401을 받아도 갱신은 한 번만 돈다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("old-access", "old-refresh"))
        val refreshCount = AtomicInteger()

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> {
                    refreshCount.incrementAndGet()
                    respond("""{"accessToken":"new-access","refreshToken":"new-refresh"}""", headers = json)
                }
                // 낡은 토큰으로 온 요청만 401. 갱신된 토큰이면 통과시킨다.
                request.headers[HttpHeaders.Authorization] == "Bearer old-access" ->
                    respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized, json)

                else -> respond("""{"items":[],"hasMore":false}""", headers = json)
            }
        }

        val api = ApiClient(store, engine = engine)
        val calls = List(5) { async { api.feed() } }
        calls.forEach { it.await() }

        assertEquals(1, refreshCount.get())
    }

    @Test
    fun `갱신까지 실패하면 토큰을 버리고 세션 만료를 알린다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("old-access", "dead-refresh"))
        var authFailed = false

        val engine = MockEngine {
            respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized, json)
        }

        val api = ApiClient(store, engine = engine, onAuthFailure = { authFailed = true })
        runCatching { api.feed() }

        assertNull(store.tokens)
        assertTrue(authFailed)
    }

    @Test
    fun `게스트는 Authorization 헤더 없이 나가고 401이어도 갱신을 시도하지 않는다`() = runTest {
        val store = MemoryTokenStore(null)
        var refreshCalled = false

        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/auth/refresh") refreshCalled = true
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("""{"items":[],"hasMore":false}""", headers = json)
        }

        ApiClient(store, engine = engine).feed()

        assertTrue(!refreshCalled)
    }

    /**
     * 백엔드 d9a3e12부터 permitAll 경로(`/auth/refresh` 포함)도 **유효하지 않은 Bearer면 401**이다.
     * 갱신 요청에 만료된 액세스 토큰을 붙이면 갱신 자체가 401로 막히고, 그 401은
     * AuthTokens로 역직렬화되지 않아 `refresh()`의 catch로 떨어진다 → 토큰 삭제 + 강제 로그아웃.
     * 액세스 토큰 수명이 1시간이라 전 유저가 매시간 로그아웃된다.
     */
    @Test
    fun `갱신 요청에는 만료된 액세스 토큰을 붙이지 않는다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("expired-access", "good-refresh"))
        var refreshAuth: String? = "refresh가 아예 안 불렸다"

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" -> {
                    refreshAuth = request.headers[HttpHeaders.Authorization]
                    respond("""{"accessToken":"new-access","refreshToken":"new-refresh"}""", headers = json)
                }
                // 서버 흉내: Bearer가 붙었는데 만료면 permitAll 경로여도 401.
                request.headers[HttpHeaders.Authorization] == "Bearer expired-access" ->
                    respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized, json)

                else -> respond("""{"items":[],"hasMore":false}""", headers = json)
            }
        }

        ApiClient(store, engine = engine).feed()

        assertNull(refreshAuth)
        assertEquals("new-access", store.tokens?.accessToken)
    }

    /** 재시도는 인터셉터에 있어 경로를 안 가리지만, 만료 창에 실제로 401을 받는 경로라 박아둔다. */
    @Test
    fun `픽 목록도 401을 받으면 갱신 후 다시 보낸다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("old-access", "old-refresh"))
        val seenAuth = mutableListOf<String?>()

        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/auth/refresh" ->
                    respond("""{"accessToken":"new-access","refreshToken":"new-refresh"}""", headers = json)

                else -> {
                    seenAuth += request.headers[HttpHeaders.Authorization]
                    if (seenAuth.size == 1) {
                        respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized, json)
                    } else {
                        respond("""{"items":[],"hasMore":false}""", headers = json)
                    }
                }
            }
        }

        ApiClient(store, engine = engine).picks()

        assertEquals(listOf("Bearer old-access", "Bearer new-access"), seenAuth)
    }

    @Test
    fun `로그인하면 토큰을 저장한다`() = runTest {
        val store = MemoryTokenStore(null)
        val engine = MockEngine {
            respond("""{"accessToken":"a","refreshToken":"r"}""", headers = json)
        }

        ApiClient(store, engine = engine).signInWithGoogle("google-id-token")

        assertEquals("a", store.tokens?.accessToken)
    }

    /**
     * 탈퇴가 **조용히 실패하던** 회귀. 서버는 refreshToken을 바디로 받아 유저를 찾는데
     * (`AuthController.withdraw`), 1.0.1까지 바디 없이 POST만 던져 400으로 떨어졌다.
     * `expectSuccess`가 없어 예외도 안 났으므로 앱은 탈퇴에 성공한 화면을 보여주고
     * 서버 데이터는 그대로 남았다. 눈으로는 성공과 구별이 안 되는 종류라 박아둔다.
     */
    @Test
    fun `탈퇴는 refresh 토큰을 바디에 담아 보낸다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("access", "refresh-1"))
        var body: String? = null

        val engine = MockEngine { request ->
            assertEquals("/auth/withdraw", request.url.encodedPath)
            body = (request.body as io.ktor.http.content.TextContent).text
            respond("", HttpStatusCode.OK)
        }

        ApiClient(store, engine = engine).withdraw()

        assertTrue("바디에 refresh 토큰이 있어야 한다: $body", body!!.contains("refresh-1"))
        assertNull(store.tokens)
    }

    @Test
    fun `탈퇴가 실패하면 예외를 던지고 토큰을 지우지 않는다`() = runTest {
        val store = MemoryTokenStore(AuthTokens("access", "refresh-1"))
        val engine = MockEngine { respond("""{"code":"AUTH_TOKEN_INVALID"}""", HttpStatusCode.BadRequest, json) }

        val api = ApiClient(store, engine = engine)
        var threw = false
        try {
            api.withdraw()
        } catch (e: IllegalStateException) {
            threw = true
        }

        assertTrue("실패를 삼키면 유저가 탈퇴된 줄 안다", threw)
        // 토큰이 남아야 로그아웃도 안 되고, 계정 화면에 그대로 있는 것이 실패의 표시가 된다.
        assertEquals("refresh-1", store.tokens?.refreshToken)
    }
}

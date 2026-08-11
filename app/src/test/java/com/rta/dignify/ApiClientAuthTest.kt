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

    @Test
    fun `로그인하면 토큰을 저장한다`() = runTest {
        val store = MemoryTokenStore(null)
        val engine = MockEngine {
            respond("""{"accessToken":"a","refreshToken":"r"}""", headers = json)
        }

        ApiClient(store, engine = engine).signInWithGoogle("google-id-token")

        assertEquals("a", store.tokens?.accessToken)
    }
}

package com.rta.dignify.core.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 서버가 발급하는 인증 토큰 묶음. `/auth/google`, `/auth/refresh` 응답 그대로.
 *
 * ponytail: `accessTokenExpiresAt`은 받아만 두고 안 쓴다. 만료를 시계로 예측하는 대신
 * 401을 보고 갱신하기 때문 — 기기 시계가 틀어져 있어도 동작이 같다.
 */
@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String? = null,
)

/**
 * 토큰 보관소. 구현이 둘인 이유는 테스트다 — 401 재시도와 single-flight refresh는
 * 안드로이드 없이 검증돼야 해서, 테스트는 메모리 구현을 꽂는다.
 */
interface TokenStore {
    var tokens: AuthTokens?
}

/**
 * SharedPreferences에 JSON 한 덩어리로 저장한다(iOS가 Keychain에 blob으로 넣는 것과 같은 모양).
 *
 * ponytail: 암호화를 안 건다. `androidx.security-crypto`는 deprecated고, 앱 전용 저장소는
 * 루팅되지 않은 기기에서 다른 앱이 못 읽는다. 토큰이 새면 서버에서 폐기하는 쪽이 실질적이다.
 */
class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dignify_auth", Context.MODE_PRIVATE)

    // 매 요청마다 디스크를 읽지 않도록 메모리에 들고 있는다. 쓰기는 양쪽 다 한다.
    private var cached: AuthTokens? = prefs.getString(KEY, null)?.let {
        runCatching { json.decodeFromString<AuthTokens>(it) }.getOrNull()
    }

    override var tokens: AuthTokens?
        get() = cached
        set(value) {
            cached = value
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY)
            } else {
                editor.putString(KEY, json.encodeToString(AuthTokens.serializer(), value))
            }
            editor.apply()
        }

    private companion object {
        const val KEY = "authTokens"
        val json = Json { ignoreUnknownKeys = true }
    }
}

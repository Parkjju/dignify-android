package com.rta.dignify.feature.picks

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 차단·신고 숨김은 **전부 로컬이다**. iOS `LocalModeration` 이식.
 *
 * 서버엔 차단 테이블이 없고 신고는 쌓이기만 한다(운영자가 직접 처리). 그래서 차단한 유저의
 * 픽을 안 보이게 하는 건 클라이언트 책임이다.
 *
 * ponytail: 개행으로 이어붙인 문자열 하나. 목록이 커질 일이 없다(내가 차단한 사람 수).
 * 커지면 그때 JSON으로.
 */
object LocalModeration {
    private const val PREFS = "dignify"
    private const val KEY_BLOCKED = "blockedNicknames"
    private const val KEY_HIDDEN = "hiddenPickIds"

    /** 화면이 관찰할 수 있게 Compose 상태로 들고 있는다. 차단 즉시 목록에서 사라져야 한다. */
    var blocked by mutableStateOf<Set<String>>(emptySet())
        private set
    var hiddenPickIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        blocked = read(KEY_BLOCKED)
        hiddenPickIds = read(KEY_HIDDEN)
    }

    fun block(nickname: String) {
        blocked = blocked + nickname
        write(KEY_BLOCKED, blocked)
    }

    fun unblock(nickname: String) {
        blocked = blocked - nickname
        write(KEY_BLOCKED, blocked)
    }

    /** 신고한 픽은 되돌릴 경로를 두지 않는다 — 신고해놓고 계속 보이면 신고가 무의미하다. */
    fun hidePick(pickId: Long) {
        hiddenPickIds = hiddenPickIds + pickId.toString()
        write(KEY_HIDDEN, hiddenPickIds)
    }

    private fun read(key: String): Set<String> =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }.toSet()

    private fun write(key: String, value: Set<String>) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, value.joinToString("\n")).apply()
    }
}

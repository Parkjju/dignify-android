package com.rta.dignify.feature.onboarding

import android.content.Context
import com.rta.dignify.core.model.DiggingType

/**
 * 온보딩 취향 테스트가 예측한 유형. 행동 기반 유형이 확정되기 전까지의 자리표시로,
 * 디깅 프로필 히어로가 "LIKELY YOUR TYPE"으로 이 값을 쓴다.
 *
 * 저장 키·값은 iOS와 같아야 한다(`predictedType`, `DiggingType.key`) — 같은 유저가
 * 기기를 옮겼을 때 다른 유형이 나오면 안 된다.
 */
object PredictedType {
    private const val PREFS = "dignify"
    private const val KEY = "predictedType"

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    fun save(type: DiggingType) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, type.key).apply()
    }

    /** 퀴즈를 건너뛴 유저는 null. */
    fun get(): DiggingType? = DiggingType.fromKey(
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    )
}

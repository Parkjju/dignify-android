package com.rta.dignify.core.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * PostHog. iOS `dignifyApp.init()` + 각 뷰의 `PostHogSDK.shared.capture` 이식.
 *
 * **이벤트 이름과 프로퍼티 키는 iOS와 글자 하나까지 같아야 한다.** 두 앱이 같은 프로젝트로
 * 쏘기 때문에, 한 글자만 달라도 대시보드에서 안드로이드가 별개 이벤트로 갈라져
 * 퍼널이 반토막 난다. 새 이벤트를 여기 추가할 땐 iOS에도 같이 넣을 것.
 *
 * 화면 자동수집은 iOS와 같은 이유로 끈다 — Compose에서 자동 화면 이름은 의미가 없다.
 * 라이프사이클(앱 오픈/설치)은 켜둔다. 리텐션의 원천이라서.
 */
object Analytics {

    // ponytail: 분석 키는 공개 전제라 시크릿 아님 — iOS와 같이 하드코딩.
    private const val PROJECT_TOKEN = "phc_p4fbGm8GPStEkWCqTnCmFt5f6SgErTjrtaZBHQa9i57a"
    private const val HOST = "https://us.i.posthog.com"

    fun init(context: Context) {
        val config = PostHogAndroidConfig(apiKey = PROJECT_TOKEN, host = HOST).apply {
            captureScreenViews = false
        }
        PostHogAndroid.setup(context, config)
    }

    fun capture(event: String, properties: Map<String, Any>? = null) {
        PostHog.capture(event, properties = properties)
    }
}

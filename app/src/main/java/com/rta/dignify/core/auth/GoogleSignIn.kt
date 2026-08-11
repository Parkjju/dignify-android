package com.rta.dignify.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rta.dignify.BuildConfig

/**
 * 구글 계정 선택 UI를 띄우고 ID 토큰을 받아온다. 이 토큰을 백엔드(`/auth/google`)가
 * 구글 JWKS로 검증하고 우리 세션 토큰으로 바꿔준다 — 즉 이 토큰 자체는 앱이 저장하지 않는다.
 *
 * `GetSignInWithGoogleOption`을 쓰는 이유는 명시적인 "구글로 로그인" 버튼이기 때문이다.
 * (`GetGoogleIdOption`은 이미 로그인된 계정이 있을 때 조용히 뜨는 바텀시트용이라, 계정이 없으면
 * 아무것도 안 뜨고 실패한다.)
 *
 * @param context Activity 컨텍스트여야 한다 — 계정 선택 화면을 띄워야 해서.
 */
suspend fun requestGoogleIdToken(context: Context): String {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    // 구글 외 자격증명(패스키·비밀번호)을 요청한 적이 없으므로 여기 오면 설정이 틀어진 것이다.
    error("Unexpected credential type: ${credential.type}")
}

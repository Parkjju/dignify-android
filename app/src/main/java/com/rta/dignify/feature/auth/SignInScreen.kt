package com.rta.dignify.feature.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.auth.requestGoogleIdToken
import com.rta.dignify.core.designsystem.DSBrandMark
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSRadius
import com.rta.dignify.core.designsystem.DSTypography
import kotlinx.coroutines.launch

private const val TAG = "DignifySignIn"

/**
 * 로그인 화면. iOS `OnboardingFlowView`의 로그인 단계에 대응하되 튜토리얼·취향 테스트는 아직 없다.
 *
 * 게스트 입구를 같이 두는 건 iOS와 같은 판단이다 — 로그인 벽을 세우면 첫 곡을 듣기 전에
 * 계정부터 만들라는 얘기가 되고, 이 앱은 "10초면 판단 가능"이 전제다.
 */
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(DSColor.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DSBrandMark(size = 72.dp)
        Text(
            "Dignify",
            style = DSTypography.display,
            color = DSColor.textPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.signin_tagline),
            style = DSTypography.body,
            color = DSColor.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )

        Button(
            onClick = {
                if (isSigningIn) return@Button
                error = null
                isSigningIn = true
                scope.launch {
                    try {
                        Session.signInWithGoogle(requestGoogleIdToken(context))
                    } catch (e: GetCredentialCancellationException) {
                        // 유저가 계정 선택을 닫은 것 — 실패 문구를 띄우면 혼내는 것처럼 보인다.
                        Log.d(TAG, "sign-in cancelled by user")
                    } catch (e: Exception) {
                        // 로그를 남기는 이유: 실패해도 화면은 로그인 화면으로 돌아갈 뿐이라,
                        // 이게 없으면 "왜 로그인이 안 되는지"를 밖에서 알 방법이 전혀 없다.
                        Log.w(TAG, "sign-in failed", e)
                        error = context.getString(R.string.signin_failed)
                    }
                    isSigningIn = false
                }
            },
            enabled = !isSigningIn,
            shape = RoundedCornerShape(DSRadius.medium),
            colors = ButtonDefaults.buttonColors(containerColor = DSColor.brand),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text(stringResource(R.string.signin_google))
            }
        }

        TextButton(onClick = { Session.enterGuest() }, enabled = !isSigningIn) {
            Text(stringResource(R.string.signin_browse), color = DSColor.textSecondary)
        }

        error?.let {
            Text(it, color = DSColor.destructive, style = DSTypography.caption)
        }
    }
}

package com.rta.dignify.feature.picks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rta.dignify.R
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.designsystem.ScreenScaffold

/**
 * 차단한 유저 목록. iOS `BlockedUsersView` 이식.
 *
 * 이 화면이 있어야 하는 이유: 차단은 로컬 저장이라 **해제 경로가 여기밖에 없다.**
 * 되돌릴 수 없는 차단은 유저를 자기 결정에 가둔다.
 */
@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.blocked_users), onBack = onBack) {
        val blocked = LocalModeration.blocked.toList()
        if (blocked.isEmpty()) {
            Text(
                stringResource(R.string.no_blocked_users),
                style = DSTypography.body,
                color = DSColor.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 20.dp),
            )
            return@ScreenScaffold
        }
        Column {
            blocked.forEach { nickname ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("@$nickname", fontSize = 15.sp, color = DSColor.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.unblock),
                        fontSize = 14.sp,
                        color = DSColor.brand,
                        modifier = Modifier.clickable { LocalModeration.unblock(nickname) },
                    )
                }
            }
        }
    }
}

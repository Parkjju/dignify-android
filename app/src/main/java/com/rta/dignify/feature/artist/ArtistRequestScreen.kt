package com.rta.dignify.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rta.dignify.R
import com.rta.dignify.core.auth.Session
import com.rta.dignify.core.designsystem.DSColor
import com.rta.dignify.core.designsystem.DSTypography
import com.rta.dignify.core.network.Api
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 내가 요청한 아티스트 히스토리. iOS `ArtistRequestHistoryView` 이식.
 *
 * **우상단 +로 요청 시트를 띄운다** — 요청하는 자리가 검색 빈결과 한 곳뿐이면, 나중에
 * 생각난 아티스트를 요청하려고 일부러 없는 검색을 해야 한다.
 *
 * 삭제는 스와이프가 아니라 행의 휴지통 아이콘이다. 안드로이드엔 iOS `swipeActions`에
 * 대응하는 표준이 없고, 직접 만들면 가로 스와이프가 뒤로가기 제스처와 싸운다.
 */
@Composable
fun ArtistRequestScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Api.ArtistRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    suspend fun load() {
        isLoading = true
        loadFailed = false
        runCatching { Session.api.artistRequests() }
            .onSuccess { items = it.items }
            .onFailure { loadFailed = true }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().background(DSColor.background).statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DSColor.textPrimary)
            }
            Text(
                stringResource(R.string.artist_requests),
                style = DSTypography.title2,
                color = DSColor.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = DSColor.brand)
            }
        }

        when {
            isLoading && items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = DSColor.brand) }

            items.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(
                        if (loadFailed) R.string.feed_load_failed else R.string.no_artist_requests
                    ),
                    style = DSTypography.body,
                    color = DSColor.textSecondary,
                )
                if (!loadFailed) {
                    Text(
                        stringResource(R.string.artist_request_empty_hint),
                        style = DSTypography.caption,
                        color = DSColor.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { request ->
                    RequestRow(
                        request = request,
                        onDelete = {
                            // 낙관적 제거 후 서버 삭제. 실패하면 목록을 원복한다.
                            val previous = items
                            items = items.filterNot { it.id == request.id }
                            scope.launch {
                                runCatching { Session.api.deleteArtistRequest(request.id) }
                                    .onFailure { items = previous }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSheet) {
        ArtistRequestSheet(
            onDismiss = {
                showSheet = false
                scope.launch { load() }
            },
        )
    }
}

@Composable
private fun RequestRow(request: Api.ArtistRequest, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                request.artistName,
                style = DSTypography.bodyMedium,
                color = DSColor.textPrimary,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(request.status)
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = DSColor.textTertiary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(18.dp)
                    .clickable(onClick = onDelete),
            )
        }
        // 취소 사유는 있을 때만.
        if (request.status.equals("CANCELED", true) && !request.cancelReason.isNullOrBlank()) {
            Text(
                request.cancelReason,
                style = DSTypography.caption,
                color = DSColor.textSecondary,
            )
        }
        Text(
            formatDate(request.createdAt),
            style = DSTypography.caption,
            color = DSColor.textTertiary,
        )
    }
}

/**
 * 상태 배지. 서버가 값을 늘려도 앱이 안 깨지도록 **모르는 상태는 원문 그대로** 보여준다 —
 * 라벨을 강제로 매핑하면 새 상태가 "검토 중"으로 잘못 읽힌다.
 */
@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status.uppercase()) {
        "PENDING" -> stringResource(R.string.artist_status_pending) to DSColor.textSecondary
        "ADDED" -> stringResource(R.string.artist_status_added) to DSColor.brand
        "CANCELED" -> stringResource(R.string.artist_status_canceled) to DSColor.destructive
        else -> status to DSColor.textSecondary
    }
    Text(
        label,
        style = DSTypography.micro,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun formatDate(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault("")

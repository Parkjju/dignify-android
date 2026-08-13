package com.rta.dignify.core.share

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * 공유 카드가 그려질 때 필요한 아트워크를 **미리** 비트맵으로 받아둔다.
 *
 * 렌더는 두 프레임 안에 끝나는데 그 사이 원격 이미지가 도착할 보장이 없다. 카드 안에서
 * `AsyncImage`로 불러오면 십중팔구 빈 자리로 찍힌다 — 그래서 진입점이 먼저 받아 주입한다.
 * 실패하면 null을 넘기고 카드는 브랜드 그라디언트로 폴백한다.
 */
suspend fun loadArtwork(context: Context, url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
    val result = ImageLoader(context).execute(request)
    return (result as? SuccessResult)?.drawable?.let { drawable ->
        runCatching {
            (drawable as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap()
        }.getOrNull()
    }
}

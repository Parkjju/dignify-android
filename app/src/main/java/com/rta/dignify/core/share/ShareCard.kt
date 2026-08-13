package com.rta.dignify.core.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.runtime.withFrameNanos
import java.io.File
import java.io.FileOutputStream

/** 카드 논리 크기. iOS와 같은 360×640(9:16) — 인스타 스토리 비율이다. */
val ShareCardWidth = 360.dp
val ShareCardHeight = 640.dp

/** 렌더 배율. 360×640 × 3 = 1080×1920 — iOS `ImageRenderer.scale = 3`과 같은 결과. */
private const val SHARE_CARD_SCALE = 3f

/**
 * 공유 카드를 **화면에 안 보이게 렌더해서 비트맵으로 뽑는다.**
 *
 * iOS는 `ImageRenderer`가 이 일을 해주지만 Compose엔 대응물이 없어 `GraphicsLayer`로 만든다.
 * 카드를 **실제로 그리되 거의 투명하게** 두고, 그 그리기를 레이어에 기록해 비트맵으로 뽑는다.
 * 안 보이게 하려고 0dp 부모에 숨기면 안 된다 — 클립 밖이라 그리기가 통째로 스킵돼
 * 빈 이미지가 나온다. alpha가 0이 아니라 0.004인 것도 같은 이유다.
 *
 * @param onRendered 비트맵이 나온 뒤. 공유 시트를 여는 건 호출부 몫이다.
 */
@Composable
fun ShareCardRenderer(onRendered: (Bitmap) -> Unit, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()

    // **밀도를 3으로 고정한다.** 기기 밀도를 그대로 쓰면 저밀도 기기에서 630×1120 같은
    // 작은 이미지가 나와 인스타 스토리에서 뭉갠다. iOS도 scale 3으로 1080×1920을 뽑는다.
    // fontScale도 1로 고정 — 유저의 글꼴 크기 설정이 카드 레이아웃을 흔들면 안 된다.
    CompositionLocalProvider(LocalDensity provides Density(SHARE_CARD_SCALE, fontScale = 1f)) {
        Box(
            Modifier
                .requiredSize(ShareCardWidth, ShareCardHeight)
                .graphicsLayer { alpha = 0.004f }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) { content() }
    }

    LaunchedEffect(Unit) {
        // 첫 프레임엔 아직 기록이 안 끝났다. 레이아웃·이미지가 자리를 잡을 여유를 준다.
        withFrameNanos {}
        withFrameNanos {}
        withFrameNanos {}
        onRendered(layer.toImageBitmap().asAndroidBitmap())
    }
}

/**
 * 비트맵을 캐시에 쓰고 공유 시트를 연다.
 *
 * `FileProvider`를 거치는 건 안드로이드 7부터 `file://` URI를 다른 앱에 넘기면
 * `FileUriExposedException`으로 죽기 때문이다.
 */
fun shareBitmap(context: Context, bitmap: Bitmap, chooserTitle: String, text: String? = null) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    // 파일명을 고정해 캐시가 무한히 늘지 않게 한다. 공유 시트가 읽는 동안만 살아 있으면 된다.
    val file = File(dir, "dignify-card.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        // 링크를 같이 실으면 이미지를 못 받는 앱(메신저 등)에서도 곡을 찾아갈 수 있다.
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

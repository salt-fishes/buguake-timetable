package com.buguake.timetable.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 背景解码结果的进程级缓存：key = path@mtime，容量 2 张（1440px 下采样每张约 15MB）。 */
private val bgDecodeCache = object : android.util.LruCache<String, Bitmap>(2) {}

/**
 * 贴纸设计系统 · 表面原语。
 *
 * 历史上这里是磨砂玻璃实现；v1.6 贴纸风改版后，所有"玻璃面"统一渲染为
 * 贴纸卡片：实色贴纸面 + 墨线描边 + 硬投影。保留原函数名与参数，
 * 调用点无需改动即可整体换装。
 */
enum class GlassLevel(val tintAlpha: Float) {
    Air(tintAlpha = 0.34f),
    Glass(tintAlpha = 0.46f),
}

/** 当前是否暗色主题（以 surface 亮度判断）。 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** 贴纸描边色：亮色 = 墨线；暗色 = 中调灰紫（勾勒可辨但不刺眼）。 */
@Composable
fun stickerBorderColor(): Color = MaterialTheme.colorScheme.outline

/** 贴纸硬投影色：亮色 = 墨线；暗色 = 比纸面更深的暗色（压出层次而非加亮）。 */
@Composable
fun stickerShadowColor(): Color =
    if (isDarkTheme()) ShadowDark else MaterialTheme.colorScheme.outline

/** 硬投影（贴纸语言）：在内容后方偏移 [offsetDp] 处画一圈墨色实心轮廓。 */
internal fun Modifier.stickerShadow(
    shape: androidx.compose.ui.graphics.Shape,
    offsetDp: Float,
    inkColor: Color,
): Modifier = drawBehind {
    val off = offsetDp.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(off, off) {
        when (val o = outline) {
            is androidx.compose.ui.graphics.Outline.Rectangle -> drawRect(inkColor)
            is androidx.compose.ui.graphics.Outline.Rounded ->
                drawPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(o.roundRect) }, inkColor)
            is androidx.compose.ui.graphics.Outline.Generic -> drawPath(o.path, inkColor)
        }
    }
}

/**
 * 贴纸表面：实色贴纸面 + 1.5dp 墨线描边 + 2dp 硬投影。
 * 覆盖层不拦截点击，内容交互不受影响；tintAlpha 参数保留兼容调用点，已不再使用。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    level: GlassLevel = GlassLevel.Glass,
    /** 历史参数（磨砂玻璃着色强度），贴纸风下不再使用。 */
    @Suppress("UNUSED_PARAMETER") tintAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .stickerShadow(shape, offsetDp = 2f, inkColor = stickerShadowColor())
            .clip(shape)
            .background(cs.surfaceContainerLowest)
            .border(width = 1.5.dp, color = stickerBorderColor(), shape = shape)
    ) {
        content()
    }
}

/**
 * 纸面背景层：纸色打底（可选自定义图片直铺 + 轻遮罩保证前景可读）。
 * 历史上的模糊/scrim/内置渐变光斑随磨砂玻璃一并退役。
 */
@Composable
fun CustomBackgroundLayer(
    enabled: Boolean,
    imagePath: String,
    /** 历史参数（模糊强度），贴纸风下不再使用。 */
    @Suppress("UNUSED_PARAMETER") blurDp: Int = 0,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val cs = MaterialTheme.colorScheme
    // 以文件 mtime 作为缓存键：覆盖选择新图后立即刷新（路径不变也能重解码）。
    // 解码在 IO 线程异步做：首帧先渲染纸色占位，解码完成后换图。
    val stamp = if (imagePath.isNotBlank()) File(imagePath).lastModified() else 0L
    val bitmap by produceState<Bitmap?>(null, imagePath, stamp) {
        if (imagePath.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            val key = "$imagePath@$stamp"
            bgDecodeCache.get(key) ?: decodeDownsampled(imagePath, maxDim = 1440)?.also {
                bgDecodeCache.put(key, it)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // 轻遮罩：贴纸卡片压在图片上仍需前景文字可读
            Box(Modifier.matchParentSize().background(cs.surface.copy(alpha = 0.30f)))
        } else {
            Box(Modifier.matchParentSize().background(cs.surface))
        }
        content()
    }
}

/** 解码本地图片，按最大边降采样（避免整图内存）。 */
private fun decodeDownsampled(path: String, maxDim: Int): Bitmap? = runCatching {
    val f = File(path)
    if (!f.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}.getOrNull()

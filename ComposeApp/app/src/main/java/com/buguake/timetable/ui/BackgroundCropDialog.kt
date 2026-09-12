package com.buguake.timetable.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 背景图应用内裁剪：视口即屏幕比例，单指拖动 / 双指缩放取景，
 * 确认后按当前变换从原图裁出视口对应的区域。
 *
 * 约束：图必须始终盖满视口（最小缩放 = cover）；
 * 手势跟手直改状态，事件停顿（松手）后弹簧收敛回最近合法位置。
 */
@Composable
fun BackgroundCropDialog(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    // 手势直改的即时状态；scale = 0 表示尚未量到视口尺寸
    var scale by remember(source) { mutableFloatStateOf(0f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var viewportPx by remember { mutableStateOf(IntSize.Zero) }
    val dialogScope = androidx.compose.runtime.rememberCoroutineScope()

    // 视口尺寸量到后初始化：按 cover 缩放并居中
    LaunchedEffect(viewportPx) {
        if (scale <= 0f && viewportPx.width > 0 && viewportPx.height > 0) {
            val cover = max(
                viewportPx.width.toFloat() / source.width,
                viewportPx.height.toFloat() / source.height,
            ) * 1.02f
            scale = cover
            offset = Offset(
                (viewportPx.width - source.width * cover) / 2f,
                (viewportPx.height - source.height * cover) / 2f,
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                // ---- 顶栏：取消 / 标题 / 确认 ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Text("调整背景位置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = scale > 0f,
                        onClick = {
                            val vw = viewportPx.width.toFloat()
                            val vh = viewportPx.height.toFloat()
                            if (vw <= 0f || vh <= 0f) return@TextButton
                            // 视口在原图坐标系里的矩形（反推当前变换）
                            val x = (-offset.x / scale).toInt().coerceIn(0, source.width - 1)
                            val y = (-offset.y / scale).toInt().coerceIn(0, source.height - 1)
                            val w = min((vw / scale).toInt().coerceAtLeast(1), source.width - x)
                            val h = min((vh / scale).toInt().coerceAtLeast(1), source.height - y)
                            if (w <= 1 || h <= 1) return@TextButton
                            onConfirm(Bitmap.createBitmap(source, x, y, w, h))
                        },
                    ) { Text("确认") }
                }
                Text(
                    "拖动 / 双指缩放选择要显示的区域",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // ---- 取景视口（填满剩余空间，比例即最终背景比例） ----
                val density = LocalDensity.current
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { viewportPx = it }
                        .clipToBounds()
                        .pointerInput(source) {
                            // 松手收敛：手势事件停顿 ~90ms 后，弹簧把值拉回最近合法位置
                            var lastGestureAt = 0L
                            var settleJob: kotlinx.coroutines.Job? = null
                            dialogScope.launch {
                                while (true) {
                                    delay(90)
                                    val idle = System.currentTimeMillis() - lastGestureAt > 90
                                    if (lastGestureAt > 0L && idle && scale > 0f) {
                                        val vw = size.width.toFloat()
                                        val vh = size.height.toFloat()
                                        val minScale = max(vw / source.width, vh / source.height)
                                        val targetScale = scale.coerceAtLeast(minScale)
                                        val target = Offset(
                                            offset.x.coerceIn(vw - source.width * targetScale, 0f),
                                            offset.y.coerceIn(vh - source.height * targetScale, 0f),
                                        )
                                        if (targetScale != scale || target != offset) {
                                            settleJob?.cancel()
                                            settleJob = launch {
                                                val s0 = scale
                                                val o0 = offset
                                                val p = Animatable(0f)
                                                val flowJob = launch {
                                                    snapshotFlow { p.value }.collect { t ->
                                                        scale = s0 + (targetScale - s0) * t
                                                        offset = Offset(
                                                            o0.x + (target.x - o0.x) * t,
                                                            o0.y + (target.y - o0.y) * t,
                                                        )
                                                    }
                                                }
                                                p.animateTo(
                                                    1f,
                                                    spring(
                                                        stiffness = Spring.StiffnessMediumLow,
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                    ),
                                                )
                                                flowJob.cancel()
                                                scale = targetScale
                                                offset = target
                                            }
                                        }
                                    }
                                }
                            }
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                lastGestureAt = System.currentTimeMillis()
                                if (scale <= 0f) return@detectTransformGestures
                                val vw = size.width.toFloat()
                                val vh = size.height.toFloat()
                                val minScale = max(vw / source.width, vh / source.height)
                                val old = scale
                                val newScale = (old * zoom).coerceAtLeast(minScale)
                                // 以双指中心为锚点缩放：保持取景内容不跳
                                var ox = centroid.x - (centroid.x - offset.x) * (newScale / old)
                                var oy = centroid.y - (centroid.y - offset.y) * (newScale / old)
                                scale = newScale
                                ox += pan.x; oy += pan.y
                                offset = Offset(
                                    ox.coerceIn(vw - source.width * newScale, 0f),
                                    oy.coerceIn(vh - source.height * newScale, 0f),
                                )
                            }
                        }
                ) {
                    val wDp = with(density) { (source.width * scale).toDp() }
                    val hDp = with(density) { (source.height * scale).toDp() }
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .size(wDp, hDp),
                    )
                }
            }
        }
    }
}

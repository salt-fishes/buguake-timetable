package com.buguake.timetable.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import kotlin.math.hypot
import kotlin.math.max

/**
 * 圆形主题切换（Radial Theme Transition）。
 *
 * 换主题是「两套完整界面之间的替换」，变化应该从用户按下主题按钮的那一点长出来，
 * 而不是全屏闪一下。做法：
 *  1. 点主题按钮时同步把当前界面抓成一张位图（旧主题）；
 *  2. 立刻写设置——底层组合换成新主题；
 *  3. 旧图作为最上层遮罩，按「整个屏幕 − 以触点为中心扩张的圆」裁掉，
 *     露出下面长出来的新主题，圆扩到最远角后撤掉遮罩。
 *
 * 全程只组合一份界面（不复制整棵 UI 树），所以不会重复触发副作用
 * （导入、日历同步、通知重排这类 LaunchedEffect 都只跑一次）。
 * 关掉动画或抓图失败时退化成硬切——绝不留一层空白遮罩。
 */
object ThemeReveal {

    private var host: Host? = null

    /** 主题按钮里调用：先抓图，再在 [block] 里写设置（真正切主题）。 */
    fun captureThen(block: () -> Unit) {
        val h = host
        if (h == null || !AppMotion.enabled) {
            block()
            return
        }
        h.captureThen(block)
    }

    internal fun attach(h: Host) { host = h }

    internal fun detach(h: Host) { if (host === h) host = null }

    internal interface Host {
        fun captureThen(block: () -> Unit)
    }
}

/** 最近一次按下的位置与时间：作为圆形揭示的圆心（键盘触发时用屏幕中心兜底）。 */
private var lastDownPosition: Offset = Offset.Unspecified
private var lastDownAtMillis: Long = 0L

/** 键盘触发时"最近的触摸"已经不算数了，超过这个时间就用屏幕中心。 */
private const val TOUCH_ORIGIN_FRESH_MS = 1500L

/**
 * 在应用根部包一层（放在主题内部、内容外层）：记录触点 + 承载揭示遮罩。
 */
@Composable
fun ThemeRevealHost(content: @Composable () -> Unit) {
    val view = LocalView.current
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var progress by remember { mutableFloatStateOf(0f) }
    var revealToken by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val host = object : ThemeReveal.Host {
            override fun captureThen(block: () -> Unit) {
                // 抓图必须写在切主题之前：切完再抓就是新主题了
                val bitmap = view.captureToBitmap()
                block()
                if (bitmap == null) return
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                val fresh = System.currentTimeMillis() - lastDownAtMillis <= TOUCH_ORIGIN_FRESH_MS
                origin = if (fresh && lastDownPosition != Offset.Unspecified) {
                    lastDownPosition
                } else {
                    Offset(w / 2f, h / 2f)
                }
                snapshot = bitmap.asImageBitmap()
                progress = 0f   // 同帧起手：遮罩先完全盖住，避免用上一轮的进度闪一下
                revealToken++
            }
        }
        ThemeReveal.attach(host)
        onDispose { ThemeReveal.detach(host) }
    }

    LaunchedEffect(revealToken) {
        if (revealToken == 0 || snapshot == null) return@LaunchedEffect
        progress = 0f
        animate(0f, 1f, animationSpec = AppMotion.reveal()) { value, _ -> progress = value }
        snapshot = null
        progress = 0f
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Initial pass 且不消费：只记圆心，点击照旧归子内容（不抢手势）
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    lastDownPosition = down.position
                    lastDownAtMillis = System.currentTimeMillis()
                }
            },
    ) {
        content()
        val bitmap = snapshot
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        // 半径取到最远角落：圆一定盖满全屏，不会留下没被替换的角
                        val maxRadius = hypot(
                            max(origin.x, w - origin.x),
                            max(origin.y, h - origin.y),
                        )
                        val radius = maxRadius * progress
                        val full = Path().apply { addRect(Rect(0f, 0f, w, h)) }
                        val hole = Path().apply {
                            addOval(
                                Rect(
                                    origin.x - radius,
                                    origin.y - radius,
                                    origin.x + radius,
                                    origin.y + radius,
                                ),
                            )
                        }
                        val outside = Path().apply { op(full, hole, PathOperation.Difference) }
                        clipPath(outside) { this@drawWithContent.drawContent() }
                    },
            )
        }
    }
}

/**
 * 把当前界面同步抓成位图（主线程）。硬件加速视图在个别机型上抓不到内容，
 * 这里抽稀采样做一次空白校验：拿不到内容就返回 null，让上层退化成硬切。
 */
private fun android.view.View.captureToBitmap(): Bitmap? = runCatching {
    if (width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap))

    val stepX = (width / 8).coerceAtLeast(1)
    val stepY = (height / 16).coerceAtLeast(1)
    var painted = 0
    var y = 0
    while (y < height && painted == 0) {
        var x = 0
        while (x < width) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) != 0) painted++
            x += stepX
        }
        y += stepY
    }
    if (painted == 0) null else bitmap
}.getOrNull()

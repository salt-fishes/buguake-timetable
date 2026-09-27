package com.buguake.timetable.ui.timetable

import androidx.compose.animation.core.animate
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buguake.timetable.ui.theme.AppMotion
import com.buguake.timetable.ui.theme.stickerBorderColor
import com.buguake.timetable.ui.theme.stickerShadowColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** 松手速度超过这个值（px/s）才让滑块越过分档中点再被吸回；以下直接吸附、不过冲。 */
private const val FLING_VELOCITY_PX_PER_S = 350f

/**
 * 带惯性吸附的周次滑杆（分档）。
 *
 * 与 M3 Slider 的差别在「最后停在哪里」：M3 是松手即跳到最近档，没有速度语义；
 * 这里把释放速度交给弹簧当初始速度——甩得快就让滑块短暂滑过目标刻度、再吸回来，
 * 几乎静止松手则直接吸附到位，不出现过冲。最终值恒被钳在 [range] 内。
 *
 * 位置只有一个真源 [pos]（周，允许小数）：拖动直接跟手、落位动画、外部跳周都只写它，
 * 避免"状态变了画面不动"。
 *
 * @param week 当前周次（外部真源；"回到本周"、切换课表时滑杆跟过去）
 * @param onPreview 拖动中预览（用于实时显示周次数字）
 * @param onSettle 落定回调（切页 + 触感放这里，只在松手/键盘操作时各调一次）
 */
@Composable
fun WeekSnapSlider(
    week: Int,
    range: IntRange,
    onPreview: (Int) -> Unit,
    onSettle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = range.first
    val last = range.last.coerceAtLeast(first)
    val span = (last - first).coerceAtLeast(1)

    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 11.dp.toPx() }
    val shadowPx = with(density) { 2.dp.toPx() }
    val trackHeightPx = with(density) { 6.dp.toPx() }
    val thumbBorderPx = with(density) { 2.dp.toPx() }
    val tickPx = with(density) { 1.6.dp.toPx() }
    val tickHotPx = with(density) { 2.6.dp.toPx() }

    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.secondaryContainer
    val inkColor = stickerBorderColor()
    val shadowColor = stickerShadowColor()

    // 位置唯一真源（周，允许小数）
    var pos by remember { mutableFloatStateOf(week.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    // 落位动画运行中：此时不接受外部同步，避免两个动画同时写 pos
    var settling by remember { mutableStateOf(false) }
    var settleToken by remember { mutableIntStateOf(0) }
    var settleTarget by remember { mutableIntStateOf(week) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }

    var widthPx by remember { mutableFloatStateOf(0f) }
    // 滑块圆心能走到的范围：两端各让出一个半径
    val usablePx = (widthPx - thumbRadiusPx * 2f).coerceAtLeast(1f)

    fun weekAt(x: Float): Float =
        (first + (x - thumbRadiusPx) / usablePx * span).coerceIn(first.toFloat(), last.toFloat())

    // 外部改周次（回到本周 / 切换课表）：滑杆过去，但不与落位动画抢
    LaunchedEffect(week, dragging, settling) {
        if (dragging || settling) return@LaunchedEffect
        val target = week.toFloat()
        if (abs(pos - target) > 0.01f) {
            animate(
                initialValue = pos,
                targetValue = target,
                animationSpec = AppMotion.spatialFast(),
            ) { value, _ -> pos = value }
        }
    }

    // 落位：释放速度作为弹簧初始速度，速度大就会越过目标刻度再吸回
    LaunchedEffect(settleToken) {
        if (settleToken == 0) return@LaunchedEffect
        settling = true
        val target = settleTarget.coerceIn(first, last).toFloat()
        val initialVelocity = if (AppMotion.enabled) releaseVelocity / usablePx * span else 0f
        val fast = AppMotion.enabled && abs(releaseVelocity) > FLING_VELOCITY_PX_PER_S
        animate(
            initialValue = pos,
            targetValue = target,
            initialVelocity = initialVelocity,
            animationSpec = if (fast) AppMotion.spatial() else AppMotion.spatialFast(),
        ) { value, _ -> pos = value }
        pos = target
        settling = false
    }

    fun settleAt(target: Int) {
        val clamped = target.coerceIn(first, last)
        settleTarget = clamped
        onPreview(clamped)
        onSettle(clamped)
        settleToken++
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)   // 触控目标：轨道很细，热区必须撑满 48dp
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = pos,
                    range = first.toFloat()..last.toFloat(),
                    steps = span - 1,
                )
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                // 键盘没有速度语义：直接落到下一档
                releaseVelocity = 0f
                settleAt(pos.roundToInt() + step)
                true
            }
            .pointerInput(usablePx, first, last) {
                detectHorizontalDragGestures(
                    onDragStart = { down ->
                        dragging = true
                        velocityTracker.resetTracking()
                        pos = weekAt(down.x)
                        onPreview(pos.roundToInt())
                    },
                    onDragEnd = {
                        dragging = false
                        releaseVelocity = velocityTracker.calculateVelocity().x
                        settleAt(pos.roundToInt())
                    },
                    onDragCancel = {
                        dragging = false
                        releaseVelocity = 0f
                        settleAt(pos.roundToInt())
                    },
                ) { change, _ ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    pos = weekAt(change.position.x)
                    onPreview(pos.roundToInt())
                }
            }
            .drawBehind {
                val cy = size.height / 2f
                val left = thumbRadiusPx
                val right = size.width - thumbRadiusPx
                val travel = (right - left).coerceAtLeast(1f)
                val thumbX = left + (pos - first) / span * travel

                drawLine(
                    color = trackColor,
                    start = Offset(left, cy),
                    end = Offset(right, cy),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = fillColor,
                    start = Offset(left, cy),
                    end = Offset(thumbX, cy),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round,
                )
                // 分档刻度：一周一个点，贴着当前值的那个点放大
                if (span <= 24) {
                    for (w in first..last) {
                        val x = left + (w - first).toFloat() / span * travel
                        val near = abs(w - pos) < 0.5f
                        drawCircle(
                            color = if (near) fillColor else trackColor,
                            radius = if (near) tickHotPx else tickPx,
                            center = Offset(x, cy),
                        )
                    }
                }
                // 滑块：硬投影 + 面 + 墨线描边（与贴纸风格一致）
                drawCircle(shadowColor, thumbRadiusPx, Offset(thumbX + shadowPx, cy + shadowPx))
                drawCircle(thumbColor, thumbRadiusPx, Offset(thumbX, cy))
                drawCircle(
                    color = inkColor,
                    radius = thumbRadiusPx,
                    center = Offset(thumbX, cy),
                    style = Stroke(width = thumbBorderPx),
                )
            },
    )
}

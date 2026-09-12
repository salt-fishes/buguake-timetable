package com.buguake.timetable.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 共享细节动效原语：数字滚动、入场 stagger、分钟级时间 tick。
 * 规格一律走 AppMotion，保证全局同一套动效语言。
 */

/** 整数变化时上下滚动（方向与数值增减一致），用于步进器 / 倒计时 / 节数等。 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            if (up) {
                (slideInVertically(AppMotion.spatialFast()) { it } + fadeIn(AppMotion.effectsFast()))
                    .togetherWith(
                        slideOutVertically(AppMotion.spatialFast()) { -it } + fadeOut(AppMotion.effectsFast())
                    )
            } else {
                (slideInVertically(AppMotion.spatialFast()) { -it } + fadeIn(AppMotion.effectsFast()))
                    .togetherWith(
                        slideOutVertically(AppMotion.spatialFast()) { it } + fadeOut(AppMotion.effectsFast())
                    )
            }
        },
        label = "rollingNumber",
        modifier = modifier,
    ) { v ->
        Text(
            "$v",
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * 入场 stagger：首次组合时按 [index] 依次淡入。
 * 纯淡入不做位移：面板/弹窗本身已有平台入位动画，行内再滑动会与相邻行产生视觉叠压。
 * 内容容器为 Column：允许把「标签 + 控件」等多个兄弟元素包进同一个 StaggerIn。
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val delayMillis = (index.coerceAtLeast(0) * 45).coerceAtMost(360)
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(240, delayMillis = delayMillis)),
        modifier = modifier,
    ) {
        Column { content() }
    }
}

/**
 * 二/三级页统一转场（共享 Z 轴深度感 + 淡切）：
 * 进入更深一层 = 新页从 92% 弹簧放大「迎面而来」、旧页放大到 108% 退远淡出；
 * 返回上层则反向（新页从 108% 缩回、旧页缩小到 92% 退场）。
 * 位移只发生在缩放上，页面不横向平移——透明玻璃页不会出现新旧文字互相穿透。
 */

/** 进入更深一层：新页迎面而来。 */
fun pageEnterCloser(): EnterTransition =
    fadeIn(AppMotion.effects()) + scaleIn(AppMotion.spatial(), initialScale = 0.92f)

/** 进入更深一层：旧页退远淡出。 */
fun pageExitFurther(): ExitTransition =
    fadeOut(AppMotion.effectsFast()) + scaleOut(AppMotion.effectsFast(), targetScale = 1.08f)

/** 返回上层：新页从远处缩回。 */
fun pageEnterFurther(): EnterTransition =
    fadeIn(AppMotion.effects()) + scaleIn(AppMotion.spatial(), initialScale = 1.08f)

/** 返回上层：旧页迎面缩小退场。 */
fun pageExitCloser(): ExitTransition =
    fadeOut(AppMotion.effectsFast()) + scaleOut(AppMotion.effectsFast(), targetScale = 0.92f)

/**
 * 边缘侧滑退出：从左边缘横向拖动时整页跟手位移，超过页宽 1/3 松手即 [onBack]，
 * 否则弹簧回弹。内部滚动（纵向列表 / 横向 LazyRow）按主导方向判定，不受影响。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeBackBox(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragX by mutableFloatStateOf(0f)
    var boxWidth by mutableStateOf(0)
    // 回弹/收尾动画：手势循环是受限挂起作用域，动画放到 LaunchedEffect 里执行
    var settleTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(settleTarget) {
        settleTarget?.let { target ->
            val start = dragX
            if (start != target) {
                val p = Animatable(start)
                val flowJob = launch {
                    snapshotFlow { p.value }.collect { dragX = it }
                }
                p.animateTo(
                    target,
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy,
                    ),
                )
                flowJob.cancel()
                dragX = target
            }
        }
    }
    Box(modifier.onGloballyPositioned { boxWidth = it.size.width }) {
        Box(
            Modifier
                .graphicsLayer { translationX = dragX }
                .pointerInput(enabled, onBack) {
                    if (!enabled) return@pointerInput
                    val edgePx = with(density) { 48.dp.toPx() }
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        var finalDrag = -1f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var decided = false
                            var armed = false
                            var accX = 0f
                            var accY = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                    ?: return@awaitEachGesture
                                if (!change.pressed) {
                                    if (armed) finalDrag = dragX
                                    break
                                }
                                if (!decided) {
                                    accX += change.positionChange().x
                                    accY += change.positionChange().y
                                    if (kotlin.math.abs(accX) > slop ||
                                        kotlin.math.abs(accY) > slop
                                    ) {
                                        decided = true
                                        armed = down.position.x <= edgePx &&
                                            kotlin.math.abs(accX) > kotlin.math.abs(accY) &&
                                            accX > 0f
                                        if (armed) Haptics.tick(context)
                                    }
                                }
                                if (armed) {
                                    dragX = (dragX + change.positionChange().x).coerceAtLeast(0f)
                                    change.consume()
                                }
                            }
                        }
                        // 手势结束（非受限作用域）：回弹或退出
                        if (finalDrag >= 0f) {
                            if (boxWidth > 0 && finalDrag > boxWidth * 0.30f) {
                                onBack()
                                settleTarget = 0f
                            } else {
                                settleTarget = 0f
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/** 分钟级「现在」时间：每过一分钟自动更新一次（今日页进度 / 当前时间线依赖它）。 */
@Composable
fun rememberNowMinute(): LocalTime {
    val now by produceState(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) {
        while (true) {
            val next = LocalTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(java.time.Duration.between(LocalTime.now(), next).toMillis() + 80)
            value = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    return now
}

/** 通用出现/消失弹入：勾选、徽章等小元素的 scaleIn。 */
@Composable
fun rememberPopScale(visible: Boolean): Float {
    val anim = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        anim.animateTo(
            if (visible) 1f else 0f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    return anim.value
}

/** 透明度渐变（已结束课程淡化等场景的平滑版）。 */
@Composable
fun animateFadeAlpha(target: Float): Float =
    animateFloatAsState(
        targetValue = target,
        animationSpec = AppMotion.effects(),
        label = "fadeAlpha",
    ).value

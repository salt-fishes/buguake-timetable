package com.buguake.timetable.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 应用动效中枢（Pixel2Motion 动效纪律落地）。
 *
 * 贴纸品牌的个性词：**俏皮 · 跟手 · 软弹**——位移都带一次可见但收敛的过冲
 * （软弹弹簧），透明度/遮罩走短时值缓出；编排遵循 预备 20% : 主动作 50% : 跟随 30%。
 * 命名沿 MotionScheme 惯例：位移/尺寸等「空间」属性用弹簧，透明度等「效果」属性用短时值。
 * 全应用自定义动画统一从这里取规格，调性只需调这一个文件。
 */
object AppMotion {

    /** 空间默认：软弹弹簧（页签滑移、面板展开、二级页缩放转场——带一次俏皮过冲）。 */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 空间快速：跟手但不生硬（胶囊吸附、行内小元素——快到位、微回弹）。 */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 效果默认：透明度/遮罩类属性，缓出曲线让淡入有「落纸」感。 */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(260, easing = FastOutSlowInEasing)

    /** 效果快速：短时长缓出（快速淡入淡出同样不带匀速感）。 */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = tween(170, easing = FastOutSlowInEasing)
}

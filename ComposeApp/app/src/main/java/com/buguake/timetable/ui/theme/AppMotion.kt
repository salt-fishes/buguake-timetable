package com.buguake.timetable.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/**
 * 应用动效中枢（Pixel2Motion 动效纪律落地）。
 *
 * 贴纸品牌的个性词：**俏皮 · 跟手 · 软弹**——位移都带一次可见但收敛的过冲
 * （软弹弹簧），透明度/遮罩走短时值缓出；编排遵循 预备 20% : 主动作 50% : 跟随 30%。
 * 命名沿 MotionScheme 惯例：位移/尺寸等「空间」属性用弹簧，透明度等「效果」属性用短时值。
 * 全应用自定义动画统一从这里取规格，调性只需调这一个文件（[enabled] 例外，见下）。
 */
object AppMotion {

    /**
     * 全局动效开关：由 [RememberSystemMotion] 跟随系统「动画时长缩放」。
     * 用户关掉动画（开发者选项 / 无障碍减少动效）时全部规格退化为瞬时——
     * 本应用不做"慢一点"的中间态：半速弹簧会让跟手位移的手感漂移，不如直接落到终态。
     * 规格工厂在调用时读取本值，故在途动画要等下一次取规格才切换（无需重建整棵树）。
     */
    @Volatile
    var enabled: Boolean = true
        private set

    /** 供根部同步与单元测试直接设置。 */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** 空间默认：软弹弹簧（页签滑移、面板展开、二级页缩放转场——带一次俏皮过冲）。 */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (enabled) {
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        } else {
            snap()
        }

    /** 空间快速：跟手但不生硬（胶囊吸附、行内小元素——快到位、微回弹）。 */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        if (enabled) {
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        }

    /** 效果默认：透明度/遮罩类属性，缓出曲线让淡入有「落纸」感。 */
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (enabled) tween(260, easing = FastOutSlowInEasing) else snap()

    /** 效果快速：短时长缓出（快速淡入淡出同样不带匀速感）。 */
    fun <T> effectsFast(): FiniteAnimationSpec<T> =
        if (enabled) tween(170, easing = FastOutSlowInEasing) else snap()

    /**
     * 俏皮回弹：小元素的弹入、按压回弹、落位回弹（图标 pop、课程块按压/落位）。
     * 比 [spatial] 更活（过冲更大、更快到位），但只用于尺寸很小的元素，
     * 大面板/整页位移一律用 [spatial]，否则会晕。
     */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium) else snap()

    /**
     * 主题揭示：全屏圆形遮罩的扩张时长。比常规淡切长一点，
     * 让「新主题从触点长出来」这件事看得清（短到 200ms 就只剩"闪一下"）。
     */
    fun <T> reveal(): FiniteAnimationSpec<T> =
        if (enabled) tween(420, easing = FastOutSlowInEasing) else snap()

    /**
     * 单调规格：进度、百分比这类**只能前进、不允许过冲**的量。
     * 位移用弹簧是为了俏皮，但弹簧（dampingRatio < 1）会让进度瞬时冲过 100% 再回落，
     * 读起来像"课时变长了"——进度类属性一律用本规格。
     */
    fun <T> monotonic(): FiniteAnimationSpec<T> =
        if (enabled) tween(600, easing = FastOutSlowInEasing) else snap()
}

/**
 * 在应用根部调用一次：把系统「动画时长缩放」同步到 [AppMotion]。
 * 读的是框架静态开关（用户改完设置通常要重启应用，这里每次组合重读一次即可，
 * 从设置页返回时的重组也会带上新值）。
 */
@Composable
fun RememberSystemMotion() {
    SideEffect { AppMotion.setEnabled(ValueAnimator.areAnimatorsEnabled()) }
}

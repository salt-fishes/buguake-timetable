package com.buguake.timetable.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 主题色模式取值（与 ScheduleSettings.themeColorMode 的字符串一一对应）。 */
const val THEME_COLOR_DEFAULT = "default"   // 贴纸原色（品牌深蓝 + 荧光黄）
const val THEME_COLOR_SYSTEM = "system"     // 跟随系统壁纸取种（Android 12+）
const val THEME_COLOR_CUSTOM = "custom"     // 用户自己选主题色 + 文字色

/**
 * 主题色（品牌底 + 压在它上面的文字色）——用户自己选的一对。
 * 全部"品牌底"槽位用 [brand]，全部"压在品牌底上"的槽位用 [text]。
 */
data class ThemeColorPair(val brand: Color, val text: Color)

/**
 * 主题色生成与取档。
 *
 * 纪律：主题色只替换「品牌色族 + 其上的文字色」，纸面 / 卡片面 / 墨色 / 描边一律不动——
 * 贴纸风的基调由纸与墨决定（见 Color.kt 的基础 token），换主题色不该把可读性和辨识度一起换掉。
 *
 * 两种来源：
 *  - **自定义**：用户用色盘自己选主题色与文字色，**原样使用**（选低对比是用户的本意，
 *    取色器里有实况预览 + 一键"用推荐文字色"兜底）；
 *  - **跟随系统**：从壁纸方案取种子色相，再按推荐配比推出一对（[recommendFor]），
 *    直接用系统的 Material 色会和纸面、墨线打架。
 *
 * 推荐配比（也是自定义模式的预填值）：品牌底压到深档（S0.49 / V0.39，与默认 #333464 同档，
 * 黄绿一带另按亮度上限再压），文字取**补色**色相、亮度反解到"刚好够 4.5:1"——
 * 同色相深浅配在蓝/青上只有 2:1 上下，深底上的亮字会糊（`ThemeColorSeedTest` 守这条）。
 * 暗色下另派三档浅/深/中调，因为 primary 在暗色里主要当**强调文字色**用，深底读不出来。
 */
data class SeededPalette(
    /** 品牌底：主按钮底、底栏选中胶囊底、选中徽章底。 */
    val brand: Color,
    /** 压在品牌底上的文字/图标色。 */
    val onBrand: Color,
    /** 暗色下 primary 的替身（强调文字/图标用，必须够亮）。 */
    val brandSoft: Color,
    /** 暗色下 onPrimary（作按钮文字用）。 */
    val brandDeep: Color,
    /** 暗色下 primaryContainer。 */
    val brandMid: Color,
)

object ThemeColorSeed {

    /** 品牌底档位：S0.49 / V0.39，与默认品牌色 #333464 同档。 */
    private const val BRAND_SAT = 0.49f
    private const val BRAND_VALUE = 0.39f

    /** 品牌底的亮度上限：黄绿一带用默认档 V0.39 仍然偏亮（压不住文字色），按亮度再压一档。 */
    private const val MAX_BRAND_LUMINANCE = 0.072f

    /** 推荐文字色的基准亮度：约等于默认高亮 #FFD666（≈0.70）。 */
    private const val BASE_TEXT_LUMINANCE = 0.70f

    /** 品牌底与文字色的推荐对比度下限（WCAG 正文级）。 */
    const val MIN_CONTRAST = 4.5f

    // ---- 推荐配比 ----

    /**
     * 品牌底：种子色相压到深档（S0.49 / V0.39，与默认品牌色 #333464 同档）。
     * 蓝紫一带这个档位已经够深；黄绿一带亮度天然更高，会再压一档明度。
     */
    fun brandOf(hue: Float): Color {
        val full = hsvToColor(hue, BRAND_SAT, BRAND_VALUE)
        if (full.luminance() <= MAX_BRAND_LUMINANCE) return full
        var low = 0f
        var high = BRAND_VALUE
        repeat(16) {
            val mid = (low + high) / 2f
            if (hsvToColor(hue, BRAND_SAT, mid).luminance() > MAX_BRAND_LUMINANCE) {
                high = mid
            } else {
                low = mid
            }
        }
        return hsvToColor(hue, BRAND_SAT, (low + high) / 2f)
    }

    /**
     * 推荐的文字色：**补色**色相 + 亮度按需抬高。
     *
     * 先量一遍品牌底的实际亮度、反解"刚好够 [MIN_CONTRAST] 所需亮度"，再与基准亮度取大：
     * 蓝紫一带因此仍得到原样的荧光黄（同默认贴纸的 navy + yellow），只有品牌底偏亮时才调淡一点。
     */
    fun textOf(brandHue: Float): Color {
        val needed = MIN_CONTRAST * (brandOf(brandHue).luminance() + 0.05f) - 0.05f
        return tintAtLuminance(
            (brandHue + 180f) % 360f,
            maxOf(BASE_TEXT_LUMINANCE, needed),
        )
    }

    /** 由色相推出一对推荐配色（品牌底 + 文字色）。 */
    fun recommendFor(seed: Color): ThemeColorPair {
        val hue = rgbToHsv(seed)[0]
        return ThemeColorPair(brand = brandOf(hue), text = textOf(hue))
    }

    /** 换一对里的文字色：品牌底色相不变，文字色换成推荐值（取色器"用推荐文字色"用）。 */
    fun recommendTextFor(brand: Color): Color = textOf(rgbToHsv(brand)[0])

    // ---- 由一对颜色铺开成整套槽位 ----

    /**
     * 用户自己选的一对颜色 → 整套槽位。
     * [brand]/[text] 原样使用；暗色专用的浅/深/中调按**品牌色的色相**派生，
     * 保证暗色下当强调文字色用的那一档始终够亮。
     */
    fun fromPair(brand: Color, text: Color): SeededPalette {
        val hue = rgbToHsv(brand)[0]
        return SeededPalette(
            brand = brand,
            onBrand = text,
            brandSoft = lightenForDark(brand, hue),
            brandDeep = hsvToColor(hue, 0.60f, 0.31f),
            brandMid = hsvToColor(hue, 0.38f, 0.43f),
        )
    }

    /** 暗色下的强调文字色：品牌色的浅色调（用户选的深色直接当文字会是黑底黑字）。 */
    private fun lightenForDark(brand: Color, hue: Float): Color {
        val pale = hsvToColor(hue, 0.20f, 0.95f)
        // 用户选的颜色本身就够亮时保留它，别把颜色洗掉
        return if (brand.luminance() >= pale.luminance() * 0.7f) brand else pale
    }

    // ---- 解析当前模式 ----

    /**
     * 系统动态取色的种子：Android 12+ 从壁纸方案里取 primary。
     * 只取"种"（色相），仍按推荐配比重推。
     */
    fun systemSeed(context: Context, dark: Boolean): Color? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme =
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            scheme.primary
        } else {
            null
        }

    /** 当前模式下的一套主题色；null = 不启用主题色（用贴纸原色）。 */
    fun resolve(
        context: Context,
        mode: String,
        brandColor: Int,
        textColor: Int,
        dark: Boolean,
    ): SeededPalette? = when (mode) {
        THEME_COLOR_SYSTEM -> systemSeed(context, dark)?.let { seed ->
            val pair = recommendFor(seed)
            fromPair(pair.brand, pair.text)
        }
        // 自定义：用户选的一对原样生效（未选过时由 UI 用推荐值预填）
        THEME_COLOR_CUSTOM -> fromPair(Color(brandColor), Color(textColor))
        else -> null
    }

    // ---- 工具 ----

    /**
     * 取某色相在给定相对亮度下的颜色：V 顶到 1，对 S 做二分。
     * V=1 时亮度随 S 单调递减，所以二分一定收敛；12 次迭代误差远小于 1/255。
     */
    private fun tintAtLuminance(hue: Float, targetLuminance: Float): Color {
        var low = 0f
        var high = 1f
        repeat(12) {
            val mid = (low + high) / 2f
            if (hsvToColor(hue, mid, 1f).luminance() > targetLuminance) low = mid else high = mid
        }
        return hsvToColor(hue, (low + high) / 2f, 1f)
    }

    /** 两个颜色的对比度（取色器提示与测试核验用）。 */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}

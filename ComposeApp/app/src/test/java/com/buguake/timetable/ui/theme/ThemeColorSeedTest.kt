package com.buguake.timetable.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题色的硬约束：
 * 1. **推荐配比**（自定义模式的预填值 / 跟随系统）里，品牌底与文字色必须是对比色。
 *    这条踩过坑：早先文字色取的是同色相亮档，在蓝/青这类"亮也不亮"的色相上
 *    对比度只有 2:1 上下，深底上的亮字直接糊掉。
 * 2. **自定义模式下用户选的一对原样生效**——不做二次加工，避免"我选的颜色被改掉了"。
 */
class ThemeColorSeedTest {

    @Test
    fun recommendedPairKeepsContrastAcrossAllHues() {
        var hue = 0f
        while (hue < 360f) {
            val pair = ThemeColorSeed.recommendFor(hsvToColor(hue, 1f, 1f))
            val ratio = ThemeColorSeed.contrastRatio(pair.brand, pair.text)
            assertTrue(
                "色相 $hue 下推荐配对的对比度只有 %.2f".format(ratio),
                ratio >= ThemeColorSeed.MIN_CONTRAST,
            )
            hue += 10f
        }
    }

    @Test
    fun recommendedPairReproducesStickerPairing() {
        // 拿默认品牌色 #333464 当种子：品牌底色相应还原，文字色应落在补色（黄系）一侧
        val pair = ThemeColorSeed.recommendFor(Color(0xFF333464))
        assertTrue(
            "品牌色应与 token 里的 #333464 基本一致，实际=${pair.brand}",
            nearColor(pair.brand, Color(0xFF333464)),
        )
        assertTrue(
            "推荐文字色应偏暖（黄系）：实际=${pair.text}",
            pair.text.red > pair.text.blue,
        )
    }

    @Test
    fun customPairIsUsedVerbatim() {
        // 用户自己选的一对必须原样落地：不做压暗、不换色相（对比度由取色器提示，不强制）
        val brand = Color(0xFF2E7D5B)
        val text = Color(0xFFFFE082)
        val palette = ThemeColorSeed.fromPair(brand, text)
        assertTrue("品牌底应原样使用，实际=${palette.brand}", nearColor(palette.brand, brand))
        assertTrue("文字色应原样使用，实际=${palette.onBrand}", nearColor(palette.onBrand, text))
    }

    private fun nearColor(a: Color, b: Color, tolerance: Float = 0.03f): Boolean =
        kotlin.math.abs(a.red - b.red) < tolerance &&
            kotlin.math.abs(a.green - b.green) < tolerance &&
            kotlin.math.abs(a.blue - b.blue) < tolerance
}

package com.buguake.timetable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * 贴纸设计系统 · 全局色彩 token（与官网 website/assets/css/main.css 1:1 对应）。
 * 颜色按「纸面 / 墨线 / 荧光黄 / 品牌色」组织；下方再映射进 MaterialTheme 的
 * colorScheme 槽位作为兼容过渡层，界面代码继续从 colorScheme 取值即可拿到贴纸配色。
 */

// ---- 基础 token ----
val Paper = Color(0xFFFFF8EC)          // 亮色纸面（页面底）
val Paper2 = Color(0xFFFDF1DE)         // 次级纸面（输入框底、斑马纹）
val Ink = Color(0xFF2D2A45)            // 墨色（正文 + 描边）
val InkDim = Color(0xFF6B6685)         // 次级文字
val InkFaint = Color(0xFF8A849F)       // 弱文字
val Highlight = Color(0xFFFFD666)      // 荧光黄（高亮/选中/徽章）
val Brand = Color(0xFF333464)          // 品牌色（主按钮/选中胶囊底）
val StickerRed = Color(0xFFC0392B)     // 警示红（错误/今日表头/时间红线）
val LineSoft = Color(0xFFC9BFAE)       // 软线（虚线 chip 描边）
val StickerCard = Color(0xFFFFFFFF)    // 贴纸面

// ---- 暗色纸面（夜间手帐：正文柔光、描边中调、投影比纸面更深）----
val PaperDark = Color(0xFF1C1926)      // 暗色纸面
val Paper2Dark = Color(0xFF262230)
val CardDark = Color(0xFF2C2839)       // 暗色贴纸面
val InkDark = Color(0xFFE3DEE9)        // 暗色正文（柔光薰衣草白，不再刺眼）
val InkDimDark = Color(0xFFA9A3BE)
val InkFaintDark = Color(0xFF8F88A6)
val LineSoftDark = Color(0xFF3A3547)
val StickerRedDark = Color(0xFFE57368)
val BorderDark = Color(0xFF7E7796)     // 暗色描边：中调灰紫，勾勒可辨但不发光
val ShadowDark = Color(0xFF120F1A)     // 暗色硬投影：比纸面更深，压出层次而非加亮

// ---- 兼容过渡层：贴纸 token → colorScheme 槽位映射 ----
val LightPrimary = Brand
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Highlight
val LightOnPrimaryContainer = Ink
val LightSecondary = InkDim
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Brand          // 底栏选中胶囊：品牌底 + 荧光字（同官网手机 mock）
val LightOnSecondaryContainer = Highlight
val LightTertiary = Color(0xFF7A5B3A)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Paper2
val LightOnTertiaryContainer = Ink
val LightError = StickerRed
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE1E1)
val LightOnErrorContainer = Color(0xFF5A1210)
val LightSurface = Paper
val LightOnSurface = Ink
val LightOnSurfaceVariant = InkDim
val LightSurfaceContainerLowest = StickerCard
val LightSurfaceContainerLow = Paper2
val LightSurfaceContainer = Color(0xFFFAF0DC)
val LightSurfaceContainerHigh = Color(0xFFF4E6CC)
val LightSurfaceContainerHighest = Color(0xFFECD8B8)
val LightOutline = Ink                       // 描边 token：亮色下即墨线（贴纸轮廓）
val LightOutlineVariant = LineSoft
val LightInverseSurface = Ink
val LightInverseOnSurface = Paper
val LightInversePrimary = Highlight
val LightSurfaceDim = Color(0xFFEADFC8)
val LightSurfaceBright = Color(0xFFFFFBF2)

// 暗色下 primary 翻转为浅色调：它大量用作文字/图标的强调色（标题、入口图标、链接），
// 深蓝 #333464 在暗纸面上不可读；作按钮底色时由 onPrimary（深navy）保证对比。
val DarkPrimary = Color(0xFFC5C4F2)
val DarkOnPrimary = Color(0xFF1F1E4F)
val DarkPrimaryContainer = Color(0xFF45446E)
val DarkOnPrimaryContainer = Highlight
val DarkSecondary = InkDimDark
val DarkOnSecondary = InkDark
val DarkSecondaryContainer = Brand
val DarkOnSecondaryContainer = Highlight
val DarkTertiary = Color(0xFFC9B48F)
val DarkOnTertiary = InkDark
val DarkTertiaryContainer = Color(0xFF3A3450)
val DarkOnTertiaryContainer = Color(0xFFE8DCC2)
val DarkError = StickerRedDark
val DarkOnError = Color(0xFF3A0E0A)
val DarkErrorContainer = Color(0xFF5A1B15)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkSurface = PaperDark
val DarkOnSurface = InkDark
val DarkOnSurfaceVariant = InkDimDark
val DarkSurfaceContainerLowest = CardDark
val DarkSurfaceContainerLow = Paper2Dark
val DarkSurfaceContainer = Color(0xFF292535)
val DarkSurfaceContainerHigh = Color(0xFF322E43)
val DarkSurfaceContainerHighest = Color(0xFF3A3450)
val DarkOutline = BorderDark
val DarkOutlineVariant = LineSoftDark
val DarkInverseSurface = InkDark
val DarkInverseOnSurface = PaperDark
val DarkInversePrimary = Highlight
val DarkSurfaceDim = Color(0xFF17141F)
val DarkSurfaceBright = Color(0xFF2A2639)

// ---- 课程块粉彩色（官网五组 --c-*-bg/fg × 深浅 = 10 组，亮暗各配）----
data class BlockPalette(
    val containerLight: Color,
    val onContainerLight: Color,
    val containerDark: Color,
    val onContainerDark: Color,
)

val CourseBlockPalettes: List<BlockPalette> = listOf(
    // 蓝
    BlockPalette(Color(0xFFDEE4FF), Color(0xFF3D51C8), Color(0xFF2E3050), Color(0xFFAAB6F5)),
    BlockPalette(Color(0xFFCBD5FF), Color(0xFF3347B0), Color(0xFF383B66), Color(0xFFBDC7F8)),
    // 橙
    BlockPalette(Color(0xFFFFE1CC), Color(0xFFBC5C1A), Color(0xFF4A3325), Color(0xFFF0A878)),
    BlockPalette(Color(0xFFFFD1B0), Color(0xFFA34E10), Color(0xFF573D2C), Color(0xFFF4B88E)),
    // 绿
    BlockPalette(Color(0xFFD0F1E1), Color(0xFF1F7A55), Color(0xFF1F4034), Color(0xFF7CC7A6)),
    BlockPalette(Color(0xFFBCE7D7), Color(0xFF196747), Color(0xFF275040), Color(0xFF8ED2B2)),
    // 紫
    BlockPalette(Color(0xFFEDDBFF), Color(0xFF7737AE), Color(0xFF3E2F50), Color(0xFFC39BE0)),
    BlockPalette(Color(0xFFE1CBFF), Color(0xFF652D96), Color(0xFF4A3860), Color(0xFFCDA9E8)),
    // 黄
    BlockPalette(Color(0xFFFFF1B8), Color(0xFF8A700D), Color(0xFF453C1E), Color(0xFFE3C46A)),
    BlockPalette(Color(0xFFFFE999), Color(0xFF756009), Color(0xFF524723), Color(0xFFEACB7C)),
)

/** 课程 colorIndex -> (容器色, 内容色)，自动适配亮暗主题。 */
fun courseBlockColors(index: Int, darkTheme: Boolean): Pair<Color, Color> {
    val p = CourseBlockPalettes[(((index % CourseBlockPalettes.size) + CourseBlockPalettes.size) % CourseBlockPalettes.size)]
    return if (darkTheme) p.containerDark to p.onContainerDark
    else p.containerLight to p.onContainerLight
}

/** RGB -> HSV（h 0..360, s 0..1, v 0..1）。 */
private fun rgbToHsv(c: Color): FloatArray {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val hue = (h + 360f) % 360f
    val s = if (mx == 0f) 0f else d / mx
    return floatArrayOf(hue, s, mx)
}

/**
 * 课程块颜色（兼容入口，历史上跟随壁纸取色，贴纸设计系统改为固定品牌色板）：
 * 直接返回当前主题下的标准粉彩色，行为与 courseBlockColors 一致。
 */
@Composable
fun courseBlockColorsDynamic(index: Int): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return courseBlockColors(index, dark)
}

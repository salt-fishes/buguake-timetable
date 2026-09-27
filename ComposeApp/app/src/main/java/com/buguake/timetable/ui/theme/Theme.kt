package com.buguake.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
)

/**
 * 贴纸设计系统主题（颜色 token 见 Color.kt，与官网同源）：
 * - 亮/暗两套纸面配色，全部为固定品牌色，不跟随壁纸
 * - colorScheme 仅作兼容过渡层（界面代码沿用旧取值路径），语义已由贴纸 token 决定
 * - 动效统一走 AppMotion（全局同一套弹簧规格）
 */
@Composable
fun ComposeAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 主题色模式：default（贴纸原色）/ system（跟随壁纸取种）/ custom（用下面两个颜色）。 */
    themeColorMode: String = THEME_COLOR_DEFAULT,
    /** 自定义主题色（品牌底，ARGB；仅 mode=custom 时生效）。 */
    themeSeedColor: Int = 0,
    /** 自定义文字色（压在主题色上的文字/图标，ARGB；仅 mode=custom 时生效）。 */
    themeTextColor: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 只替换「品牌色族 + 其上的文字色」，纸面/卡片/墨色/描边一律保持贴纸 token（见 ThemeColor.kt）
    val palette = remember(themeColorMode, themeSeedColor, themeTextColor, darkTheme) {
        ThemeColorSeed.resolve(context, themeColorMode, themeSeedColor, themeTextColor, darkTheme)
    }
    val base = if (darkTheme) DarkColors else LightColors
    val colorScheme = palette?.let { base.seeded(it, darkTheme) } ?: base

    // 玻璃模式下 Scaffold/Surface 用透明 containerColor，contentColorFor(Transparent)
    // 返回未指定，LocalContentColor 会保留默认黑色——暗色模式正文全变黑。
    // 在主题根部统一提供 onSurface，透明容器内的默认色文字始终跟随当前色系。
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(),
            typography = Typography(),
            content = content
        )
    }
}

/**
 * 把用户/系统给的那一对颜色铺进方案：所有「品牌底」槽位用 brand，
 * 所有「压在品牌底上」的槽位用 text（原样，不做二次加工）；纸面、卡片面、墨色、描边保持贴纸 token。
 */
private fun ColorScheme.seeded(p: SeededPalette, dark: Boolean): ColorScheme = if (dark) {
    copy(
        // 暗色下 primary 大量用作强调文字/图标，直接用深色品牌会读不出来，走浅色调
        primary = p.brandSoft,
        onPrimary = p.brandDeep,
        primaryContainer = p.brandMid,
        onPrimaryContainer = p.onBrand,
        secondaryContainer = p.brand,     // 底栏选中胶囊
        onSecondaryContainer = p.onBrand,
        inversePrimary = p.onBrand,
    )
} else {
    copy(
        primary = p.brand,
        onPrimary = p.onBrand,
        primaryContainer = p.brand,
        onPrimaryContainer = p.onBrand,
        secondaryContainer = p.brand,
        onSecondaryContainer = p.onBrand,
        inversePrimary = p.onBrand,
    )
}

package com.buguake.timetable.ui.mine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.buguake.timetable.ui.theme.ThemeColorSeed
import com.buguake.timetable.ui.theme.hsvToColor
import com.buguake.timetable.ui.theme.rgbToHsv
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** 贴纸原品牌色（#333464）的色相。 */
private const val BRAND_HUE = 239f

/** 切到「自定义」但还没选过颜色时的预填值：贴纸原色 + 它的推荐文字色。 */
fun defaultThemePair(): Pair<Int, Int> = ThemeColorSeed.brandOf(BRAND_HUE).toArgb() to
    ThemeColorSeed.textOf(BRAND_HUE).toArgb()

/** 已选过就保留，没选过（0）则用推荐值预填。 */
fun themePairOrDefault(brandColor: Int, textColor: Int): Pair<Int, Int> =
    if (brandColor != 0 && textColor != 0) {
        brandColor to textColor
    } else {
        defaultThemePair()
    }

/**
 * 自定义主题色：**色盘**选主题色与文字色，各自独立。
 *
 * 不做"自动配对比色"的强制：用户选什么就是什么（下面给实况预览 + 对比度读数，
 * 低了会提示，文字色还提供「用推荐值」一键兜底）。盘面 = 色相（角度）× 饱和度（半径），
 * 明度由盘下的滑杆控制，并且整盘会按当前明度压暗——盘面看到的颜色就是最终落地的颜色。
 */
@Composable
fun ThemeColorPicker(
    brandColor: Int,
    textColor: Int,
    onBrand: (Int) -> Unit,
    onText: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = Color(brandColor)
    val text = Color(textColor)
    val contrast = ThemeColorSeed.contrastRatio(brand, text)

    Column(modifier.fillMaxWidth()) {
        // 实况预览：与底栏选中胶囊、主按钮同构
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .width(86.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(brand),
                contentAlignment = Alignment.Center,
            ) {
                Text("课表", style = MaterialTheme.typography.labelMedium, color = text)
            }
            Text(
                if (contrast >= ThemeColorSeed.MIN_CONTRAST) {
                    "对比度 %.1f:1".format(contrast)
                } else {
                    "对比度 %.1f:1 · 偏低，字会不好读".format(contrast)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (contrast >= ThemeColorSeed.MIN_CONTRAST) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        ColorDisc(
            label = "主题色（底）",
            color = brand,
            onChange = { onBrand(it.toArgb()) },
        )

        Spacer(Modifier.height(14.dp))
        ColorDisc(
            label = "文字颜色",
            color = text,
            onChange = { onText(it.toArgb()) },
            extra = {
                TextButton(
                    onClick = { onText(ThemeColorSeed.recommendTextFor(brand).toArgb()) },
                ) { Text("用推荐值") }
            },
        )
    }
}

/**
 * 色盘：角度 = 色相、半径 = 饱和度；明度由下方滑杆控制（盘面按明度整体压暗，所见即所得）。
 * 按下即取色、跟手拖动连续取色——不复用 Slider 是因为这里要读二维坐标。
 */
@Composable
private fun ColorDisc(
    label: String,
    color: Color,
    onChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable (() -> Unit)? = null,
) {
    val hsv = remember(color) { rgbToHsv(color) }
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]
    var size by remember { mutableStateOf(IntSize.Zero) }
    val discSize = 164.dp

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            extra?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(discSize)
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        fun pick(pos: Offset) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w <= 0f || h <= 0f) return
                            val radius = min(w, h) / 2f
                            val dx = pos.x - w / 2f
                            val dy = pos.y - h / 2f
                            val dist = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                            val angle = (
                                Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f
                                ) % 360f
                            onChange(hsvToColor(angle, dist, value))
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            pick(down.position)
                            down.consume()
                            // 按住不放拖动 = 连续取色（与底栏胶囊同一套绝对坐标写法）
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pick(change.position)
                                change.consume()
                            }
                        }
                    },
            ) {
                val radius = min(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                // 色相：绕圈扫过 0..360
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = (0..12).map { hsvToColor(it * 30f, 1f, 1f) },
                        center = center,
                    ),
                    radius = radius,
                    center = center,
                )
                // 饱和度：圆心白 → 边缘纯色
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                // 明度：整盘压黑，盘面观感即最终观感
                if (value < 1f) {
                    drawCircle(Color.Black.copy(alpha = 1f - value), radius = radius, center = center)
                }
                // 当前取色点
                val angle = Math.toRadians(hue.toDouble())
                val dist = saturation * radius
                val marker = Offset(
                    center.x + (cos(angle) * dist).toFloat(),
                    center.y + (sin(angle) * dist).toFloat(),
                )
                drawCircle(Color.White, 9.dp.toPx(), marker)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = 9.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "明暗",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = value,
                onValueChange = { onChange(hsvToColor(hue, saturation, it)) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }
    }
}

package com.buguake.timetable.ui.today

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.data.EntryWithCourse
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.TimeUtils
import com.buguake.timetable.data.WeekCalculator
import com.buguake.timetable.ui.theme.AppMotion
import java.time.LocalDate
import java.time.LocalTime

private val DAY_NAMES = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

/** 今日页：正在上课 Hero 卡 + 下一节课 + 今日时间轴。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    entries: List<EntryWithCourse>,
    settings: ScheduleSettings,
    glass: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val currentWeek = remember(settings.semesterStart) {
        WeekCalculator.currentWeek(settings.semesterStartDate, today)
    }
    val todayEntries = remember(entries, currentWeek, today, settings.sectionTimes) {
        entries
            .filter { it.dayOfWeek == today.dayOfWeek.value && it.isInWeek(currentWeek) }
            .sortedBy { it.minutesOfDay(settings.sectionTimes)?.first ?: 99 * 60 }
    }
    val now = com.buguake.timetable.ui.theme.rememberNowMinute()  // 每分钟自动更新
    val nowMinutes = now.hour * 60 + now.minute

    val ongoing = todayEntries.firstOrNull { e ->
        val span = e.minutesOfDay(settings.sectionTimes)?.let { (s, en) -> s..en }
            ?: return@firstOrNull false
        nowMinutes in span
    }
    val next = todayEntries.firstOrNull { e ->
        val sM = e.minutesOfDay(settings.sectionTimes)?.first ?: 24 * 60
        sM > nowMinutes
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${today.monthValue} 月 ${today.dayOfMonth} 日 · ${DAY_NAMES[today.dayOfWeek.value - 1]}",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        when {
            todayEntries.isEmpty() -> item {
                // 空态：淡入 + 上滑入场
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                androidx.compose.animation.AnimatedVisibility(
                    visible = shown,
                    enter = androidx.compose.animation.fadeIn(AppMotion.effects()) +
                        androidx.compose.animation.slideInVertically(AppMotion.spatial()) { it / 10 },
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text("今日无课", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                // 正在上课 / 下一节课：课程边界跨过时平滑滑动切换，而不是硬切
                item {
                    androidx.compose.animation.AnimatedContent(
                        targetState = ongoing,
                        transitionSpec = {
                            val move = AppMotion.spatial<androidx.compose.ui.unit.IntOffset>()
                            if (targetState != null && initialState == null) {
                                (androidx.compose.animation.slideInVertically(move) { it / 3 } +
                                    androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(androidx.compose.animation.fadeOut(AppMotion.effectsFast()))
                            } else if (targetState == null) {
                                (androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(
                                        androidx.compose.animation.slideOutVertically(move) { -it / 3 } +
                                            androidx.compose.animation.fadeOut(AppMotion.effectsFast())
                                    )
                            } else {
                                (androidx.compose.animation.slideInHorizontally(move) { it / 4 } +
                                    androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(
                                        androidx.compose.animation.slideOutHorizontally(move) { -it / 4 } +
                                            androidx.compose.animation.fadeOut(AppMotion.effectsFast())
                                    )
                            }
                        },
                        label = "ongoingHero",
                    ) { e ->
                        e?.let { OngoingCard(it, settings, nowMinutes, glass) }
                    }
                }
                item {
                    androidx.compose.animation.AnimatedContent(
                        targetState = next,
                        transitionSpec = {
                            val move = AppMotion.spatial<androidx.compose.ui.unit.IntOffset>()
                            if (targetState != null && initialState == null) {
                                (androidx.compose.animation.slideInVertically(move) { it / 3 } +
                                    androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(androidx.compose.animation.fadeOut(AppMotion.effectsFast()))
                            } else if (targetState == null) {
                                (androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(androidx.compose.animation.fadeOut(AppMotion.effectsFast()))
                            } else {
                                (androidx.compose.animation.slideInHorizontally(move) { it / 4 } +
                                    androidx.compose.animation.fadeIn(AppMotion.effectsFast()))
                                    .togetherWith(
                                        androidx.compose.animation.slideOutHorizontally(move) { -it / 4 } +
                                            androidx.compose.animation.fadeOut(AppMotion.effectsFast())
                                    )
                            }
                        },
                        label = "nextHero",
                    ) { e ->
                        e?.let { n ->
                            if (ongoing == null || ongoing.entryId != n.entryId) {
                                NextCard(n, settings, nowMinutes, glass)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "今日课程",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (glass) {
                    // 玻璃模式：整段时间轴收进单层玻璃面，避免玻璃上叠玻璃
                    item {
                        com.buguake.timetable.ui.theme.GlassSurface(Modifier.fillMaxWidth()) {
                            Column {
                                todayEntries.forEach { e ->
                                    TimelineItem(e, settings, nowMinutes, transparent = true)
                                }
                            }
                        }
                    }
                } else {
                    items(todayEntries, key = { it.entryId }) { e ->
                        TimelineItem(e, settings, nowMinutes, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun colorFor(entry: EntryWithCourse): androidx.compose.ui.graphics.Color =
    when (((entry.colorIndex % 3) + 3) % 3) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

@Composable
private fun OngoingCard(
    e: EntryWithCourse,
    settings: ScheduleSettings,
    nowMinutes: Int,
    glass: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(colorFor(e), CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text("正在上课", style = MaterialTheme.typography.labelLarge,
                    color = if (glass) cs.primary else cs.onPrimaryContainer)
            }
            Text(
                e.courseName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                buildString {
                    append(e.shortLocation.ifBlank { "未排地点" })
                    if (e.teacher.isNotBlank()) append(" · ").append(e.teacher)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (glass) cs.onSurfaceVariant else cs.onPrimaryContainer.copy(alpha = 0.75f),
            )
            val span = e.minutesOfDay(settings.sectionTimes)
            if (span != null) {
                val fraction = ((nowMinutes - span.first).toFloat() / (span.second - span.first))
                    .coerceIn(0f, 1f)
                // 进度条平滑流动，剩余分钟数字滚动，不再逐帧跳变
                val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = AppMotion.spatial(),
                    label = "classProgress",
                )
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    color = cs.primary,
                    trackColor = cs.primaryContainer,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "还剩 ",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (glass) cs.onSurfaceVariant else cs.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    com.buguake.timetable.ui.theme.RollingNumber(
                        value = (span.second - nowMinutes).coerceAtLeast(0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (glass) cs.onSurfaceVariant else cs.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        " 分钟 · ${TimeUtils.hm(LocalTime.of(span.second / 60, span.second % 60))} 下课",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (glass) cs.onSurfaceVariant else cs.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
    if (glass) {
        com.buguake.timetable.ui.theme.GlassSurface(Modifier.fillMaxWidth()) { body() }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
            shape = MaterialTheme.shapes.large,
        ) { body() }
    }
}

@Composable
private fun NextCard(
    e: EntryWithCourse,
    settings: ScheduleSettings,
    nowMinutes: Int,
    glass: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val sM = e.minutesOfDay(settings.sectionTimes)?.first ?: 24 * 60
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colorFor(e), CircleShape))
                Spacer(Modifier.size(8.dp))
                Text("下一节课", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (sM > nowMinutes) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.buguake.timetable.ui.theme.RollingNumber(
                            value = sM - nowMinutes,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                        Text(
                            " 分钟后",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.primary,
                        )
                    }
                }
            }
            Text(e.courseName, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp))
            Text(
                buildString {
                    append(sectionLabel(e)).append(" · ").append(e.shortLocation.ifBlank { "未排地点" })
                },
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
    if (glass) {
        com.buguake.timetable.ui.theme.GlassSurface(Modifier.fillMaxWidth()) { body() }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
            shape = MaterialTheme.shapes.large,
        ) { body() }
    }
}

@Composable
private fun TimelineItem(
    e: EntryWithCourse,
    settings: ScheduleSettings,
    nowMinutes: Int,
    transparent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val span = e.minutesOfDay(settings.sectionTimes)
    val endMinutes = span?.second ?: 0
    val finished = span != null && nowMinutes > endMinutes
    ListItem(
        colors = if (transparent) ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) else ListItemDefaults.colors(),
        leadingContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                span?.let {
                    Text(TimeUtils.hm(LocalTime.of(it.first / 60, it.first % 60)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium)
                    Text(TimeUtils.hm(LocalTime.of(it.second / 60, it.second % 60)),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.outline)
                }
            }
        },
        headlineContent = {
            Text(
                e.courseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                sectionLabel(e) + " · " + e.shortLocation.ifBlank { "未排地点" } +
                    if (e.teacher.isNotBlank()) " · ${e.teacher}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Box(Modifier.size(10.dp).background(colorFor(e), CircleShape))
        },
        modifier = modifier.alpha(
            com.buguake.timetable.ui.theme.animateFadeAlpha(if (finished) 0.55f else 1f)
        ),
    )
}

private fun sectionLabel(e: EntryWithCourse): String =
    if (e.sectionRangeLabel.isBlank()) "" else e.sectionRangeLabel

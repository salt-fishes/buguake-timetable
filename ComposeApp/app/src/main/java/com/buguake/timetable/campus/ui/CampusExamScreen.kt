package com.buguake.timetable.campus.ui

import com.buguake.timetable.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.buguake.timetable.ui.theme.AppMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.exam.CampusExam
import com.buguake.timetable.campus.exam.CampusExamBundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ZONE_EXAM: ZoneId = ZoneId.of("Asia/Shanghai")
internal val FMT_EXAM_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
internal val FMT_EXAM_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FMT_EXAM_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/**
 * 校园「考试安排」主页：只展示考试列表（按学期分组）。
 *
 * 周次网格展示已移到首页课表（考试合并进课表网格），本页不再提供视图切换。
 * 页面上不放任何动作按钮（读取、日历、导出都在设置页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusExamScreen(
    glass: Boolean = false,
    bundle: CampusExamBundle?,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val exams = bundle?.exams.orEmpty()

    BackHandler { onBack() }

    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("考试安排") },
                // 校园功能页嵌在 AppShell 内容区里，顶层已经避让过状态栏：
                // 这里再吃一次 inset 会出现明显的顶部空白
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SketchArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(SketchSettings, contentDescription = "考试设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (exams.isEmpty()) "暂无考试数据" else "${exams.size} 场考试",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    subtitle(bundle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }

            // 空态 ↔ 列表：淡入淡出过渡，不再硬切
            androidx.compose.animation.AnimatedContent(
                targetState = exams.isEmpty(),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(AppMotion.effects())
                        .togetherWith(androidx.compose.animation.fadeOut(AppMotion.effectsFast()))
                },
                label = "examStage",
            ) { empty ->
                when {
                    empty -> ExamEmptyState(onOpenSettings)
                    else -> ExamListView(exams = exams, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun subtitle(bundle: CampusExamBundle?): String {
    if (bundle == null) return "尚未从教务系统读取"
    val at = bundle.updatedAt.takeIf { it > 0 }
        ?.let { FMT_EXAM_STAMP.format(Instant.ofEpochMilli(it).atZone(ZONE_EXAM)) }
    return listOfNotNull(bundle.termLabel.takeIf { it.isNotBlank() }, at?.let { "上次读取 $it" })
        .joinToString(" · ")
        .ifBlank { "尚未从教务系统读取" }
}

@Composable
private fun ExamEmptyState(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("还没有考试数据", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "在设置里选择学校并读取：应用内登录正方教务系统后点一下按钮，" +
                        "会自动进入考试信息查询、按学年/学期筛选并把结果存到本机。" +
                        "账号密码只留在应用内的登录会话中，不会保存到别处。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) { Text("去设置读取") }
            }
        }
    }
}

/** 列表视图：按学期分组，组内按时间先后。 */
@Composable
private fun ExamListView(exams: List<CampusExam>, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    val grouped = exams.groupBy { it.termText.ifBlank { "未标注学期" } }
        .toList()
        .sortedByDescending { (term, _) -> term }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
    ) {
        grouped.forEach { (term, list) ->
            item(key = "head-$term") { ExamSectionTitle(term) }
            items(list.sortedBy { it.startAt }, key = { it.courseCode + it.timeText + it.seat }) { e ->
                ExamCard(e, past = e.hasTime && e.startAt < now, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun ExamSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
internal fun ExamCard(e: CampusExam, past: Boolean = false, modifier: Modifier = Modifier) {
    // 已结束考试的置灰平滑过渡
    val pastAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (past) 0.6f else 1f,
        animationSpec = AppMotion.effects(),
        label = "examPast",
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (past) 0.32f else 0.55f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .alpha(pastAlpha),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    examDateText(e),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (past) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                val badge = when {
                    past -> "已结束"
                    e.method.isNotBlank() -> e.method
                    else -> ""
                }
                if (badge.isNotBlank()) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(e.courseName, style = MaterialTheme.typography.titleMedium)
            Text(
                examTimeText(e),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val place = examPlaceText(e)
            if (place.isNotBlank()) {
                Text(place, style = MaterialTheme.typography.bodySmall)
            }
            val meta = listOfNotNull(
                e.teacher.takeIf { it.isNotBlank() }?.let { "教师 $it" },
                e.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun examDateText(e: CampusExam): String =
    if (e.hasTime) FMT_EXAM_DATE.format(Instant.ofEpochMilli(e.startAt).atZone(ZONE_EXAM)) else "时间待定"

internal fun examTimeText(e: CampusExam): String =
    if (e.hasTime) {
        FMT_EXAM_HM.format(Instant.ofEpochMilli(e.startAt).atZone(ZONE_EXAM)) + "-" +
            FMT_EXAM_HM.format(Instant.ofEpochMilli(e.endAt).atZone(ZONE_EXAM))
    } else {
        e.timeText.ifBlank { "教务系统未给出时间" }
    }

internal fun examPlaceText(e: CampusExam): String {
    val place = listOf(e.campus, e.location).filter { it.isNotBlank() }.joinToString(" ")
    return when {
        place.isBlank() -> ""
        e.seat.isNotBlank() -> "$place · ${e.seat} 号"
        else -> place
    }
}

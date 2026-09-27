package com.buguake.timetable.ui.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.buguake.timetable.ui.theme.AppMotion
import kotlinx.coroutines.delay

/** 新增排课：课程名 / 教师 / 地点 / 星期 / 节次 / 自定义周次。
 *  @param maxWeek 已识别课表的最长周（默认选中 1..maxWeek；0 表示无课表，默认 1-17）
 *  @param prefillDay / prefillSection 长按网格空白处的预填落点（星期 / 起始节） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseSheet(
    maxWeek: Int = 17,
    prefillDay: Int? = null,
    prefillSection: Int? = null,
    onSave: (
        name: String, teacher: String, location: String,
        dayOfWeek: Int, startSection: Int?, endSection: Int?, weeks: List<Int>,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableIntStateOf(prefillDay ?: 1) }
    var startSec by rememberSaveable { mutableIntStateOf(prefillSection ?: 1) }
    var endSec by rememberSaveable { mutableIntStateOf(prefillSection ?: 2) }
    // 自定义周次：默认按识别到的最长周选中 1..maxWeek（可手动增删）
    val defaultMax = maxWeek.coerceIn(1, 17)
    var selectedWeeks by rememberSaveable { mutableStateOf((1..defaultMax).toSet()) }
    val allWeeksRange = 1..17
    // 批量勾选的视觉错峰：状态同帧落库（「已选 N 周」立刻正确、不阻塞继续点击），
    // 只有勾选外观按周次次序依次弹出。token 一变就重放，连点不会排队积压
    var staggerToken by remember { mutableIntStateOf(0) }
    var staggerDelays by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    fun applyBulk(target: Set<Int>) {
        val newly = (target - selectedWeeks).sorted()
        selectedWeeks = target
        // 每项约 35ms，整体压在约 400ms 内（周次多时自动收紧间隔）
        val step = if (newly.isEmpty()) 0L else minOf(35L, 400L / newly.size)
        staggerDelays = newly.withIndex().associate { (i, w) -> w to i * step }
        staggerToken++
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.buguake.timetable.ui.theme.StaggerIn(0) {
                Text("新增课程", style = MaterialTheme.typography.titleLarge)
            }
            com.buguake.timetable.ui.theme.StaggerIn(1) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            com.buguake.timetable.ui.theme.StaggerIn(2) {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            com.buguake.timetable.ui.theme.StaggerIn(3) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点（校区/楼号/教室，如：下沙 环宇楼 A404）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 星期选择（周一~周日）
            com.buguake.timetable.ui.theme.StaggerIn(4) {
                Text("星期", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                        FilterChip(
                            selected = day == i + 1,
                            onClick = { day = i + 1 },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // 节次范围（下拉选择 1..12）
            com.buguake.timetable.ui.theme.StaggerIn(5) {
                Text("节次", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("从", style = MaterialTheme.typography.bodyMedium)
                    SectionDropdown(selected = startSec, onSelect = { startSec = it })
                    Text("到", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                    SectionDropdown(selected = endSec, onSelect = { endSec = it })
                }
            }

            // 自定义周次（1-17 多选；默认选中识别到的最长周范围）
            com.buguake.timetable.ui.theme.StaggerIn(6) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("周次", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        "已选 ${selectedWeeks.size} 周" + if (defaultMax < 17) " · 默认至第 ${defaultMax} 周" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { applyBulk((1..defaultMax).toSet()) }) { Text("重置") }
                    TextButton(onClick = { applyBulk(allWeeksRange.toSet()) }) { Text("全选") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(allWeeksRange.toList()) { w ->
                        // 每个 chip 自己的弹入（1.0→1.08→1.0）：批量操作时按周次次序依次出现；
                        // 单个点击不叠加这段动画（点击反馈交给 chip 自身），避免连点抖动
                        val pop = remember { Animatable(1f) }
                        val delayMs = staggerDelays[w]
                        LaunchedEffect(staggerToken, delayMs) {
                            if (delayMs == null || !AppMotion.enabled) return@LaunchedEffect
                            delay(delayMs)
                            pop.animateTo(1.08f, AppMotion.spatialFast())
                            pop.animateTo(1f, AppMotion.bouncy())
                        }
                        FilterChip(
                            selected = w in selectedWeeks,
                            onClick = {
                                selectedWeeks = if (w in selectedWeeks) selectedWeeks - w
                                else selectedWeeks + w
                            },
                            label = { Text("$w") },
                            modifier = Modifier.graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            },
                        )
                    }
                }
            }

            com.buguake.timetable.ui.theme.StaggerIn(7) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (name.trim().isNotEmpty()) {
                                val s = startSec.coerceAtMost(endSec)
                                val e = endSec.coerceAtLeast(startSec)
                                onSave(name, teacher, location, day, s, e, selectedWeeks.sorted())
                            }
                            onDismiss()
                        },
                        enabled = name.trim().isNotEmpty(),
                    ) { Text("保存") }
                }
            }
        }
    }
}

/** 节次下拉（1..12）。 */
@Composable
private fun SectionDropdown(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.padding(start = 8.dp)) {
        Text("$selected 节")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        (1..12).forEach { v ->
            DropdownMenuItem(
                text = { Text("第 $v 节") },
                onClick = { onSelect(v); expanded = false },
            )
        }
    }
}

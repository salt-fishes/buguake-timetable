package com.buguake.timetable.ui.mine

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.buguake.timetable.R
import com.buguake.timetable.ui.theme.AppMotion

/** 更新记录数据：新版本在前。 */
private val CHANGELOG: List<Pair<String, List<String>>> = listOf(
    "0.1.0" to listOf(
        "自简课表派生：保留多课表 / 周视图 / 小组件 / 提醒 / 日历同步 / 课表对比",
        "教务网页导入上线：学校列表、统一身份登录、一键导入（基于拾光开源适配生态）",
        "支持自定义时间段课次与课程备注；课表对比识别改纯 Kotlin 实现",
        "液态玻璃界面风格（卡顿可关闭）；全新应用图标",
        "\"校园\"页上线：云莓宿舍蓝牙开门",
    ),
)

/** 更新记录按大版本系列分组（1.x / 2.x），保持新系列在前。 */
private val CHANGELOG_SERIES: List<Pair<String, List<Pair<String, List<String>>>>> = run {
    val bySeries = LinkedHashMap<String, MutableList<Pair<String, List<String>>>>()
    CHANGELOG.forEach { (v, items) ->
        bySeries.getOrPut("${v.substringBefore('.')}.x") { mutableListOf() }.add(v to items)
    }
    bySeries.map { it.key to it.value }
}

/** 关于页：应用介绍 / 主要功能 / 更新记录（点击展开）/ 开发者信息。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    versionName: String,
    glass: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // ---- 头部：真实应用图标 + 名称 + 版本 + 一句话定位 ----
            Image(
                painter = painterResource(R.drawable.ic_launcher_bg),
                contentDescription = "应用图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text("不挂科课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "版本 $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "选学校 · 登教务 · 一键导入课表",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            // ---- 主要功能：收纳为一张分组卡片，行间细分隔线 ----
            SectionTitle("主要功能")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    FeatureRow("教务网页导入", "选择学校登录教务，一键导入课程、周次、地点与教师（覆盖 190+ 学校/教务系统）")
                    CardDivider()
                    FeatureRow("多课表管理", "班级课表 / 个人课表 / 同学的课表并存，随时切换")
                    CardDivider()
                    FeatureRow("课表周视图", "左右滑动切换周次，今日课程高亮，当前时间线提示")
                    CardDivider()
                    FeatureRow("今日页", "正在上课 / 下一节课 / 今日课程时间轴")
                    CardDivider()
                    FeatureRow("校园开门", "连接宿舍蓝牙门锁一键开门（云莓第三方接口）")
                    CardDivider()
                    FeatureRow("课表对比", "截图识别占用，与同学/其他课表 App 找共同空闲")
                    CardDivider()
                    FeatureRow("长按拖拽调课", "长按课程块即可跨天、跨节次移动")
                    CardDivider()
                    FeatureRow("系统日历同步", "课程直接写入系统日历，随系统提醒，可一键清空")
                    CardDivider()
                    FeatureRow("课表分享", "一键生成整周课表图片，调起系统分享")
                    CardDivider()
                    FeatureRow("桌面小组件", "2×2 / 2×3 / 2×4 三种规格，可分别绑定课表")
                    CardDivider()
                    FeatureRow("上课提醒", "课前 5/10/15/20 分钟本地通知，准点触发")
                    CardDivider()
                    FeatureRow("个性化", "深色模式、动态取色、磨砂玻璃、自定义背景与作息时间")
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 更新记录：按 1.x / 2.x 系列分组，默认全部收起，点击逐级展开 ----
            SectionTitle("更新记录")
            val currentVersion = CHANGELOG.first().first
            var expandedSeries by rememberSaveable { mutableStateOf(setOf<String>()) }
            var expandedVersions by rememberSaveable { mutableStateOf(setOf<String>()) }
            CHANGELOG_SERIES.forEachIndexed { si, (series, entries) ->
                val seriesOpen = series in expandedSeries
                val seriesRotation by animateFloatAsState(
                    targetValue = if (seriesOpen) 180f else 0f,
                    animationSpec = AppMotion.spatialFast(),
                    label = "seriesChevron$si",
                )
                GlassCard(glass, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedSeries =
                                        if (seriesOpen) expandedSeries - series
                                        else expandedSeries + series
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                "v$series 系列",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "最新 v${entries.first().first} · ${entries.size} 个版本",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (seriesOpen) "收起系列" else "展开系列",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = seriesRotation },
                            )
                        }
                        AnimatedVisibility(
                            visible = seriesOpen,
                            enter = expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects()),
                            exit = shrinkVertically(AppMotion.spatialFast()) + fadeOut(AppMotion.effectsFast()),
                        ) {
                            Column(Modifier.padding(bottom = 6.dp)) {
                                entries.forEachIndexed { index, (version, items) ->
                                    val isExpanded = version in expandedVersions
                                    val versionRotation by animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = AppMotion.spatialFast(),
                                        label = "changelogChevron$si$index",
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedVersions =
                                                    if (isExpanded) expandedVersions - version
                                                    else expandedVersions + version
                                            }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Text(
                                            "v$version",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (version == currentVersion) "当前版本 · ${items.size} 项更新"
                                            else "${items.size} 项更新",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (version == currentVersion)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "收起" else "展开",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.graphicsLayer { rotationZ = versionRotation },
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects()),
                                        exit = shrinkVertically(AppMotion.spatialFast()) + fadeOut(AppMotion.effectsFast()),
                                    ) {
                                        Column(Modifier.padding(bottom = 8.dp)) {
                                            items.forEach { line ->
                                                Text(
                                                    "• $line",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("优势与致谢")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "• 全程离线运行，不联网、不收集任何数据\n" +
                            "• 课程自动识别，省去手动录入\n" +
                            "• 界面简洁，Material You 设计",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "感谢拾光课程表适配生态（Apache-2.0）、yunmei_unintelligent（MIT）、Jetpack Compose · Material 3、Room、Kotlin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("兼容性")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "• 支持 Android 8.0 及以上系统\n" +
                            "• 课表识别当前适配正方教务导出的 PDF 与班级课表 Excel\n" +
                            "• 其他教务系统如有适配需求，欢迎发邮件反馈",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("开发者")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    InfoRow("开发者", "咸鱼")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    InfoRow("联系邮箱", "xunguang255@163.com")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/salt-fishes/buguake-timetable".toUri(),
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            "开源仓库",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "github.com/salt-fishes/buguake-timetable",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "如果你在使用中遇到问题或有建议，欢迎通过邮箱联系。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** 卡片内分组行之间的细分隔线。 */
@Composable
private fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun FeatureRow(title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 玻璃开关卡片容器：glass 开启时为磨砂玻璃面，否则为普通实色 Card。 */
@Composable
private fun GlassCard(
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (glass) {
        com.buguake.timetable.ui.theme.GlassSurface(modifier = modifier) { content() }
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = modifier,
        ) { content() }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

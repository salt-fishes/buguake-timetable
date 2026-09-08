package com.saltfish.simple.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 隐私政策页：核心承诺 + 权限清单（申请哪些权限、用作何用）+
 * 不联网承诺 + 数据存储 + 兼容性说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPage(
    glass: Boolean = false,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("隐私政策") },
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
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- 核心承诺：课表数据只存本地 ----
            if (glass) {
                com.saltfish.simple.ui.theme.GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) { CorePromise() }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) { CorePromise() }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("权限说明：申请了哪些权限，用来做什么")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    PermissionRow(
                        "日历（读取与写入）",
                        "把课程写入系统日历「不挂科课表」、执行「清空系统日历中的课程」撤销同步。" +
                            "仅在你点击「同步到系统日历」或「清空」按钮时使用，不会读取你其他日历的内容。",
                    )
                    CardDivider()
                    PermissionRow(
                        "通知",
                        "仅用于课前上课提醒（Android 13 及以上需你授权），无营销或推广通知。",
                    )
                    CardDivider()
                    PermissionRow(
                        "振动",
                        "操作反馈的轻微震动：底栏切换、调课落位、同步完成等关键节点，不用于提醒通知。",
                    )
                    CardDivider()
                    PermissionRow(
                        "精确闹钟",
                        "让课前提醒在设定时刻准点触发，不用于任何其他目的。",
                    )
                    CardDivider()
                    PermissionRow(
                        "网络（INTERNET）",
                        "仅用于：下载学校适配脚本（来自导入源仓库）以及在内嵌浏览器中加载" +
                            "你主动选择的教务网站完成导入。除此之外不发起任何网络请求，无广告、无统计埋点。",
                    )
                    CardDivider()
                    PermissionRow(
                        "开机自启",
                        "手机重启后自动恢复课前提醒闹钟，避免提醒静默失效。",
                    )
                    CardDivider()
                    PermissionRow(
                        "系统文件选择器 / 照片选择器（非存储权限）",
                        "仅读取你主动选择的背景图片；导出 .ics 时写入你指定的位置。" +
                            "本应用不申请「存储空间」权限。",
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("联网行为说明")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "联网只做两件事：一是从导入源仓库下载学校适配脚本，二是在内嵌浏览器中" +
                            "加载你主动选择的教务网站。教务账号密码只在浏览器会话内使用，" +
                            "本应用不读取、不保存、不上传。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "除此之外：无数据上传、无广告 SDK、无第三方统计埋点、无远程配置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("数据存放在哪里")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "课程数据保存在应用私有的本地数据库（Room）中；背景图片与学校适配脚本缓存保存在应用私有目录。" +
                            "这些位置其他应用无法访问，卸载应用后全部随之删除。" +
                            "写入系统日历的课程事件保存在系统日历的「不挂科课表」日历中，可随时在应用内一键清空。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("唯一的对外数据出口")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "只有一种情况数据会离开本应用：你主动点击「分享」时，应用把生成的课表图片交给" +
                            "系统分享面板中你选择的应用（如微信、QQ）处理。除此之外不存在任何数据出口。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("兼容性说明")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "• 支持 Android 8.0 及以上系统\n" +
                            "• 背景模糊与动态取色需要 Android 12 及以上，低版本自动降级，不影响核心功能\n" +
                            "• 荣耀 / 华为等系统日历没有 .ics 文件导入入口，推荐使用应用内「同步到系统日历」直接写入\n" +
                            "• 教务网页导入能力正在开发中，将基于开源社区的学校适配脚本持续扩充覆盖范围",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("联系我们与政策更新")
            GlassCard(glass, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "如对本政策有任何疑问，请联系：xunguang255@163.com",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本政策如有更新，将在应用内「关于」页同步展示最新版本。\n更新日期：2026-09-03（随 v1.7 更新）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "开发者：咸鱼",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorePromise() {
    Text(
        "课表数据只存在你的手机里",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "教务导入时，你的教务账号密码只在内嵌浏览器会话内使用，本应用不读取、不保存、不上传。" +
            "\n课表数据全部保存在手机本地，绝不回传我们的服务器。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
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

/** 玻璃开关卡片容器：glass 开启时为磨砂玻璃面，否则为普通实色 Card。 */
@Composable
private fun GlassCard(
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (glass) {
        com.saltfish.simple.ui.theme.GlassSurface(modifier = modifier) { content() }
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = modifier,
        ) { content() }
    }
}

/** 权限清单行：权限名 + 用途说明。 */
@Composable
private fun PermissionRow(name: String, purpose: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            purpose,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 卡片内分组行之间的细分隔线。 */
@Composable
private fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

package com.buguake.timetable.campus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.YmLock
import com.buguake.timetable.campus.YmSchool
import com.buguake.timetable.campus.YmSession

/**
 * 数据详情页：展示本次登录云莓后获取到的全部数据（会话、学校、门锁 BLE 参数）。
 * 默认脱敏，打开「显示敏感信息」后才明文展示 token / secret / MAC 等。
 */
@Composable
fun CampusDataScreen(
    account: String,
    session: YmSession?,
    school: YmSchool?,
    schoolToken: String,
    locks: List<YmLock>,
    learnedMac: (YmLock) -> String,
    glass: Boolean = false,
    onBack: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
    else MaterialTheme.colorScheme.surface) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "数据详情",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "本次登录后从云莓获取的全部数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("显示敏感信息", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (reveal) "token / secret / MAC 已明文展示" else "默认以圆点脱敏",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = reveal, onCheckedChange = { reveal = it })
                }
            }

            // ---- 账号 ----
            SectionTitle("账号")
            DataCard {
                DataRow("云莓账号", maskValue(account, reveal))
                CardDivider()
                DataRow("用户 ID", maskValue(session?.userId ?: "", reveal))
                CardDivider()
                DataRow("账号 MD5", maskValue(session?.accountMd5 ?: "", reveal), mono = true)
                CardDivider()
                DataRow("密码 MD5", maskValue(session?.passwordMd5 ?: "", reveal), mono = true)
                CardDivider()
                DataRow("会话 token", maskValue(session?.token ?: "", reveal), mono = true)
            }

            // ---- 学校 / 服务器 ----
            SectionTitle("学校 / 服务器")
            DataCard {
                DataRow("学校名称", school?.name ?: "—")
                CardDivider()
                DataRow("学校编号", school?.schoolNo ?: "—")
                CardDivider()
                DataRow("服务器地址", school?.serverUrl ?: "—", mono = true)
                CardDivider()
                DataRow("学校 token", maskValue(schoolToken.ifBlank { school?.token ?: "" }, reveal), mono = true)
            }

            // ---- 门锁 ----
            SectionTitle("门锁（${locks.size} 把）")
            if (locks.isEmpty()) {
                DataCard { DataRow("门锁", "账号下没有绑定的门锁") }
            } else {
                locks.forEachIndexed { index, lock ->
                    val learned = learnedMac(lock)
                    val macText: String
                    val macHint: String
                    if (learned.isNotBlank()) {
                        macText = learned
                        macHint = "本地已学习（快速连接）"
                    } else if (lock.mac.isNotBlank()) {
                        macText = lock.mac
                        macHint = "服务器下发 lockNo（非真实 MAC，需扫描）"
                    } else {
                        macText = ""
                        macHint = "暂无"
                    }
                    DataCard {
                        DataRow("门锁 ${index + 1}", lock.label)
                        CardDivider()
                        DataRow("服务 UUID", lock.serviceUuid, mono = true)
                        CardDivider()
                        DataRow("写特征 UUID", lock.writeCharUuid, mono = true)
                        CardDivider()
                        DataRow("通知特征 UUID", lock.notifyCharUuid, mono = true)
                        CardDivider()
                        DataRow("secret", maskValue(lock.secret, reveal), mono = true)
                        CardDivider()
                        DataRow("MAC", maskValue(macText, reveal), mono = true, hint = macHint)
                    }
                }
            }

            Text(
                "以上数据均来自云莓服务器接口，仅保存在本机；接口为第三方逆向所得，可能随官方客户端更新而变化。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun DataCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
    mono: Boolean = false,
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(104.dp),
            )
            Spacer(Modifier.width(8.dp))
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (mono) FontFamily.Monospace else null,
                )
            }
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 112.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** 默认脱敏：保留首尾各 3 位，中间以圆点代替；开启明文后原样返回。 */
private fun maskValue(value: String, reveal: Boolean): String {
    if (value.isBlank()) return ""
    if (reveal) return value
    if (value.length <= 6) return "•".repeat(value.length.coerceAtLeast(4))
    return value.take(3) + "•".repeat((value.length - 6).coerceAtMost(12)) + value.takeLast(3)
}

package com.buguake.timetable.ui.mine

import com.buguake.timetable.ui.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.webimport.RepoStore
import com.buguake.timetable.webimport.SchoolIndexStore
import kotlinx.coroutines.launch

/**
 * 「我的 → 导入源仓库」设置页：选择适配器脚本与学校索引的来源仓库。
 *
 * - 预置：本项目镜像（默认，上游失效时我们在此先同步修复）与拾光官方上游；
 * - 支持添加自定义仓库（owner/name 或 GitHub 网址），可删除；
 * - 缓存按仓库隔离，切换后首次导入会自动下载该仓库的索引。
 */
@Composable
fun RepoSettingsContent(
    glass: Boolean = false,
    onShowSnackbar: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoStore = remember { RepoStore.getInstance(context) }
    val indexStore = remember { SchoolIndexStore.getInstance(context) }

    var choices by remember { mutableStateOf(repoStore.choices()) }
    var selectedId by remember { mutableStateOf(repoStore.selected().id) }
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMsg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            "适配器脚本与学校索引从这里下载。官方上游失效时可切换到本项目镜像；" +
                "切换后首次导入会自动下载新仓库的索引，缓存互不干扰。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )

        choices.forEach { repo ->
            val isPreset = repo.id == com.buguake.timetable.webimport.RepoDescriptor.OURS.id ||
                repo.id == com.buguake.timetable.webimport.RepoDescriptor.OFFICIAL.id
            Card(
                onClick = {
                    if (repo.id != selectedId) {
                        repoStore.select(repo)
                        selectedId = repo.id
                        refreshMsg = null
                        onShowSnackbar("已切换到 ${repo.owner}/${repo.name}，首次导入将下载该仓库索引")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (repo.id == selectedId)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = repo.id == selectedId,
                        onClick = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${repo.owner}/${repo.name}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            repo.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isPreset) {
                        IconButton(onClick = {
                            repoStore.removeCustom(repo.id)
                            choices = repoStore.choices()
                            selectedId = repoStore.selected().id
                        }) {
                            Icon(
                                SketchClose,
                                contentDescription = "删除自定义仓库",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; inputError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("添加自定义仓库") },
            placeholder = { Text("owner/name 或 GitHub 网址") },
            isError = inputError != null,
            supportingText = { inputError?.let { Text(it) } },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        TextButton(
            onClick = {
                val repo = com.buguake.timetable.webimport.RepoDescriptor.fromInput(input)
                if (repo == null) {
                    inputError = "格式不对：请输入 owner/name 或完整 GitHub 网址"
                } else {
                    repoStore.addCustom(repo)
                    choices = repoStore.choices()
                    input = ""
                    onShowSnackbar("已添加 ${repo.owner}/${repo.name}")
                }
            },
            enabled = input.isNotBlank(),
        ) { Text("添加仓库") }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        TextButton(
            onClick = {
                scope.launch {
                    refreshing = true
                    refreshMsg = null
                    indexStore.refresh(force = true)
                        .onSuccess {
                            refreshMsg = "已更新：${it.schools.size} 所学校（${it.versionId}）"
                        }
                        .onFailure { refreshMsg = "刷新失败：${it.message}" }
                    refreshing = false
                }
            },
            enabled = !refreshing,
        ) {
            Text(if (refreshing) "刷新中…" else "刷新当前仓库索引")
        }
        refreshMsg?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = if (it.startsWith("刷新失败")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

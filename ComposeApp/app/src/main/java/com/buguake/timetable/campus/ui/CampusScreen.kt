package com.buguake.timetable.campus.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.BleUnlocker
import com.buguake.timetable.campus.CampusStore
import com.buguake.timetable.campus.YmLock
import com.buguake.timetable.campus.YmSchool
import com.buguake.timetable.campus.YunmeiClient
import com.buguake.timetable.campus.YunmeiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 云莓 HTTP 通道（okhttp 表单 POST）。 */
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private suspend fun httpPost(
    url: String,
    form: Map<String, String>,
    headers: Map<String, String>,
): String = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val rb = Request.Builder().url(url).post(body)
    headers.forEach { (k, v) -> rb.header(k, v) }
    httpClient.newCall(rb.build()).execute().use { resp ->
        if (!resp.isSuccessful) throw YunmeiException("服务器返回 HTTP ${resp.code}")
        resp.body?.string() ?: ""
    }
}

/**
 * 校园页（云莓宿舍开门）：登录云莓账号 → 选择学校 → 拉取门锁 → 蓝牙一键开门。
 * 账号密码只存本机，直连云莓服务器；接口为第三方逆向所得，可能随官方更新失效。
 */
@Composable
fun CampusScreen(glass: Boolean = false, showSnackbar: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CampusStore.getInstance(context) }
    val unlocker = remember { BleUnlocker(context) }
    val unlockState by unlocker.state.collectAsState()

    // 登录态
    var account by remember { mutableStateOf(store.load()?.account ?: "") }
    var password by remember { mutableStateOf("") }
    var client by remember { mutableStateOf<YunmeiClient?>(null) }
    var locks by remember { mutableStateOf<List<YmLock>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var schoolPicker by remember { mutableStateOf<List<YmSchool>?>(null) }

    // 待执行的开门请求（等待权限/蓝牙就绪后继续）
    var pendingUnlock by remember { mutableStateOf<YmLock?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            pendingUnlock?.let { unlocker.unlock(it) }
        } else {
            showSnackbar("需要蓝牙权限才能开门")
        }
        pendingUnlock = null
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingUnlock?.let { lock ->
            val adapter = unlocker.bluetoothAdapter()
            if (adapter?.isEnabled == true) unlocker.unlock(lock)
            else showSnackbar("蓝牙未开启")
        }
        pendingUnlock = null
    }

    fun requestUnlock(lock: YmLock) {
        val needed = if (android.os.Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingUnlock = lock
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        val adapter = unlocker.bluetoothAdapter()
        if (adapter?.isEnabled != true) {
            pendingUnlock = lock
            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        unlocker.unlock(lock)
    }

    fun doLogin(saved: CampusStore.Saved? = null) {
        scope.launch {
            busy = true
            error = null
            try {
                val (c, schools) = withContext(Dispatchers.IO) {
                    if (saved != null) {
                        YunmeiClient.login(saved.account, saved.passwordMd5, { u, f, h -> httpPost(u, f, h) }, passwordAlreadyMd5 = true)
                    } else {
                        YunmeiClient.login(account.trim(), password, { u, f, h -> httpPost(u, f, h) })
                    }
                }
                client = c
                when {
                    schools.isEmpty() -> {
                        busy = false
                        error = "账号未绑定学校"
                        return@launch
                    }
                    schools.size == 1 -> {
                        c.selectSchool(schools.first())
                        locks = withContext(Dispatchers.IO) { c.getLocks() }
                        if (saved == null) {
                            // 首次登录：保存凭据供下次静默恢复
                            c.session?.let { ses ->
                                store.save(
                                    CampusStore.Saved(
                                        account = ses.account, passwordMd5 = ses.passwordMd5,
                                        userId = ses.userId, token = ses.token,
                                        schoolNo = schools.first().schoolNo,
                                        schoolName = schools.first().name,
                                        serverUrl = schools.first().serverUrl,
                                        schoolToken = schools.first().token,
                                    )
                                )
                            }
                        }
                    }
                    else -> schoolPicker = schools
                }
                password = ""
            } catch (e: YunmeiException) {
                if (saved != null) store.clear()  // 静默恢复失败：凭据过期，回登录表单
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "未知错误"
            }
            busy = false
        }
    }

    fun pickSchool(school: YmSchool) {
        val c = client ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    c.selectSchool(school)
                    locks = c.getLocks()
                }
                c.session?.let { ses ->
                    store.save(
                        CampusStore.Saved(
                            account = ses.account, passwordMd5 = ses.passwordMd5,
                            userId = ses.userId, token = ses.token,
                            schoolNo = school.schoolNo, schoolName = school.name,
                            serverUrl = school.serverUrl, schoolToken = school.token,
                        )
                    )
                }
            } catch (e: Exception) {
                error = e.message ?: "门锁获取失败"
            }
            busy = false
        }
    }

    // 静默恢复会话
    LaunchedEffect(Unit) {
        store.load()?.let { saved -> doLogin(saved) }
    }

    Scaffold(containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
    else MaterialTheme.colorScheme.surface) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "校园 · 宿舍开门",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                "第三方接口实现，可能随云莓官方客户端更新而失效",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (locks == null) {
                // ---- 登录表单 ----
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("云莓账号（学号）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { doLogin() },
                    enabled = !busy && account.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "登录中…" else "登录") }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    "账号密码仅保存在本机，登录请求直连云莓服务器，本应用不中转、不上传。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                // ---- 门锁列表 ----
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        client?.school?.name ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        store.clear()
                        client = null
                        locks = null
                        account = ""
                    }) { Text("退出登录") }
                }
                val st = unlockState
                if (st.running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        st.phase,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                } else if (st.phase.isNotBlank()) {
                    Text(
                        st.phase,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (st.failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                locks!!.forEach { lock ->
                    Card(
                        onClick = { requestUnlock(lock) },
                        enabled = !st.running,
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(lock.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "点击开门",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "开门",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (locks!!.isEmpty()) {
                    Text(
                        "账号下没有绑定的门锁",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    // ---- 多学校选择 ----
    schoolPicker?.let { schools ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { schoolPicker = null; busy = false },
            title = { Text("选择学校") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    schools.forEach { school ->
                        Text(
                            school.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    schoolPicker = null
                                    pickSchool(school)
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { schoolPicker = null; busy = false }) { Text("取消") }
            },
        )
    }
}

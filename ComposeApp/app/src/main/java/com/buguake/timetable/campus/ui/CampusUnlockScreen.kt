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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.BiometricGate
import com.buguake.timetable.campus.BleUnlocker
import com.buguake.timetable.campus.CampusFailure
import com.buguake.timetable.campus.CampusSaved
import com.buguake.timetable.campus.CampusStore
import com.buguake.timetable.campus.CampusSyncPolicy
import com.buguake.timetable.campus.YmLock
import com.buguake.timetable.campus.YmSchool
import com.buguake.timetable.campus.YmSession
import com.buguake.timetable.campus.YunmeiAuthException
import com.buguake.timetable.campus.YunmeiClient
import com.buguake.timetable.campus.httpPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 宿舍开门（云莓），离线优先：
 *
 * 1. 本机有门锁缓存 → **立刻**渲染列表并可直接开门，不联网、不登录（开门只需 secret + BLE 参数）；
 * 2. 后台再静默同步一次：先复用保存的 token，失效才用本机密码 MD5 静默重登；
 * 3. 只有鉴权类失败才回登录表单，网络失败一律保留缓存与凭据（只标记「离线」）；
 * 4. 可选门闸：开启后开门前验证指纹/面容（60 秒内免重复验证）；
 * 5. 支持「长按应用图标 → 快速开锁」直达：进来就用默认门锁（列表第一把）开门。
 *
 * 账号密码与门锁密钥只存本机（Keystore 加密），直连云莓服务器，本应用不中转、不上传。
 */
@Composable
fun CampusUnlockScreen(
    glass: Boolean = false,
    showSnackbar: (String) -> Unit,
    autoUnlockSeq: Int = 0,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CampusStore.getInstance(context) }
    val unlocker = remember { BleUnlocker(context) }
    val unlockState by unlocker.state.collectAsState()

    // ---- 本机缓存：离线开门的数据源 ----
    var saved by remember { mutableStateOf(store.load()) }
    var locks by remember { mutableStateOf(saved?.locks.orEmpty()) }
    var defaultLabel by remember { mutableStateOf(saved?.defaultLabel.orEmpty()) }
    var lastSyncAt by remember { mutableStateOf(saved?.updatedAt ?: 0L) }
    var offline by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<YunmeiClient?>(null) }

    // ---- 登录表单 / 同步状态 ----
    var account by remember { mutableStateOf(saved?.account ?: store.lastAccount()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 静默同步的在途标记（与 UI 的 busy 分开：syncNow 需要在同一帧内同步置位，
    // 否则"进页面自动同步"与"快捷方式自动开门"两个 effect 会竞争）
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var schoolPicker by remember { mutableStateOf<List<YmSchool>?>(null) }

    // ---- 开关（可选生物识别 + 开门后自动退出） ----
    var gateEnabled by remember { mutableStateOf(store.gateEnabled) }
    var autoExit by remember { mutableStateOf(store.autoExitOnUnlock) }
    var lastGateAt by remember { mutableStateOf(0L) }

    var pendingUnlock by remember { mutableStateOf<YmLock?>(null) }
    var pendingGate by remember { mutableStateOf<YmLock?>(null) }
    var showData by remember { mutableStateOf(false) }

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

    /** 蓝牙权限 / 开关就绪后真正下发开门指令。 */
    fun continueUnlock(lock: YmLock) {
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

    // API 26/27：没有指纹对话框，用系统锁屏密码界面验证
    val legacyGateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val lock = pendingGate
        pendingGate = null
        if (lock != null && result.resultCode == android.app.Activity.RESULT_OK) {
            lastGateAt = System.currentTimeMillis()
            continueUnlock(lock)
        } else if (lock != null) {
            showSnackbar("已取消验证")
        }
    }

    /** 开门入口：可选门闸 → 蓝牙检查 → 下发指令。 */
    fun requestUnlock(lock: YmLock) {
        val gateOpen = !gateEnabled || System.currentTimeMillis() - lastGateAt < GATE_GRACE_MS
        if (gateOpen) {
            continueUnlock(lock)
            return
        }
        // 本机没有任何验证方式：不启用门闸，也不把用户挡在门外
        if (BiometricGate.availability(context) == BiometricGate.Availability.NONE) {
            continueUnlock(lock)
            return
        }
        if (BiometricGate.needsLegacyPrompt(context)) {
            val intent = BiometricGate.legacyPromptIntent(context)
            if (intent == null) {
                continueUnlock(lock)
                return
            }
            pendingGate = lock
            legacyGateLauncher.launch(intent)
            return
        }
        val activity = context as? android.app.Activity ?: return
        scope.launch {
            val ok = BiometricGate.authenticate(activity, "开门验证", "验证指纹或面容后开启宿舍门锁")
            if (ok) {
                lastGateAt = System.currentTimeMillis()
                continueUnlock(lock)
            } else {
                showSnackbar("已取消验证")
            }
        }
    }

    /** 缓存写入 + 内存态同步（列表顺序：默认锁置顶）。 */
    fun applyLocks(
        fresh: List<YmLock>,
        userId: String?,
        token: String?,
        school: YmSchool?,
        schoolToken: String?,
    ) {
        val base = saved ?: return
        val newDefault = defaultLabel
            .takeIf { d -> d.isNotBlank() && fresh.any { it.label == d } }
            ?: fresh.firstOrNull()?.label.orEmpty()
        val updated = base.copy(
            userId = userId ?: base.userId,
            token = token ?: base.token,
            schoolNo = school?.schoolNo ?: base.schoolNo,
            schoolName = school?.name ?: base.schoolName,
            serverUrl = school?.serverUrl ?: base.serverUrl,
            schoolToken = schoolToken?.takeIf { it.isNotBlank() } ?: base.schoolToken,
            locks = fresh,
            defaultLabel = newDefault,
            updatedAt = System.currentTimeMillis(),
        )
        store.save(updated)
        saved = updated
        locks = fresh
        defaultLabel = newDefault
        lastSyncAt = updated.updatedAt
        offline = false
    }

    /**
     * 拉取门锁：token 复用优先，失效则密码 MD5 静默重登。
     * 网络失败会以 [com.buguake.timetable.campus.YunmeiNetworkException] 抛出，由调用方保留缓存。
     */
    suspend fun fetchLocks(base: CampusSaved): Triple<List<YmLock>, YunmeiClient, Boolean> =
        withContext(Dispatchers.IO) {
            val reused = YunmeiClient.restore(base) { u, f, h -> httpPost(u, f, h) }
            val viaToken = runCatching { reused.getLocks() }
            val tokenFailure = viaToken.exceptionOrNull()
            // 页面被销毁导致的取消必须原样抛出，不能当成失败去清凭据
            if (tokenFailure is CancellationException) throw tokenFailure
            // 断网时不必再试密码登录：直接按离线处理
            if (tokenFailure != null && CampusSyncPolicy.classify(tokenFailure) == CampusFailure.NETWORK) {
                throw tokenFailure
            }
            val tokenLocks = viaToken.getOrNull()
            if (tokenLocks != null && !CampusSyncPolicy.suspectTokenFailure(base.locks.size, tokenLocks.size)) {
                return@withContext Triple(tokenLocks, reused, false)
            }
            // token 失效 / 返回可疑空列表 → 用本机密码 MD5 静默重登
            val (fresh, schools) = YunmeiClient.login(
                base.account, base.passwordMd5, { u, f, h -> httpPost(u, f, h) }, passwordAlreadyMd5 = true,
            )
            val school = schools.firstOrNull { it.schoolNo == base.schoolNo } ?: schools.firstOrNull()
                ?: throw YunmeiAuthException("账号未绑定学校")
            fresh.selectSchool(school)
            Triple(fresh.getLocks(), fresh, true)
        }

    /** 后台静默同步（进页面自动跑一次，「同步门锁」按钮也用它）。 */
    fun syncNow() {
        val base = saved ?: return
        if (syncing) return
        syncing = true
        busy = true
        scope.launch {
            error = null
            try {
                val (fresh, freshClient, relogged) = fetchLocks(base)
                client = freshClient
                applyLocks(
                    fresh = fresh,
                    userId = freshClient.session?.userId,
                    token = freshClient.session?.token,
                    school = freshClient.school,
                    schoolToken = freshClient.schoolToken,
                )
                if (relogged) showSnackbar("登录态已续期")
            } catch (t: CancellationException) {
                // 用户离开页面：保留一切，不下任何结论
                throw t
            } catch (t: Throwable) {
                when (CampusSyncPolicy.classify(t)) {
                    CampusFailure.NETWORK -> {
                        offline = true
                        if (locks.isEmpty()) error = "网络不可用，且本机还没有门锁缓存，请联网后重试"
                    }
                    else -> {
                        // 鉴权彻底失效（密码也不对）：清凭据回登录表单，但保留账号预填
                        store.clearCredentials()
                        saved = null
                        locks = emptyList()
                        account = base.account
                        error = t.message ?: "登录已失效，请重新登录"
                    }
                }
            }
            syncing = false
            busy = false
        }
    }

    /** 登录成功：写缓存并把第一把锁设为默认门锁。 */
    suspend fun persistLogin(fresh: YunmeiClient, school: YmSchool) {
        val fetched = withContext(Dispatchers.IO) {
            fresh.selectSchool(school)
            fresh.getLocks()
        }
        val session = fresh.session
        val newSaved = CampusSaved(
            account = account.trim().ifBlank { session?.account.orEmpty() },
            passwordMd5 = session?.passwordMd5.orEmpty(),
            userId = session?.userId.orEmpty(),
            token = session?.token.orEmpty(),
            schoolNo = school.schoolNo,
            schoolName = school.name,
            serverUrl = school.serverUrl,
            schoolToken = fresh.schoolToken.ifBlank { school.token },
            locks = fetched,
            defaultLabel = fetched.firstOrNull()?.label.orEmpty(),
            updatedAt = System.currentTimeMillis(),
        )
        store.save(newSaved)
        saved = newSaved
        locks = fetched
        defaultLabel = newSaved.defaultLabel
        lastSyncAt = newSaved.updatedAt
        offline = false
    }

    /** 首次登录（无缓存时的手动登录）。 */
    fun doLogin() {
        scope.launch {
            busy = true
            error = null
            try {
                val (fresh, schools) = withContext(Dispatchers.IO) {
                    YunmeiClient.login(account.trim(), password, { u, f, h -> httpPost(u, f, h) })
                }
                client = fresh
                when {
                    schools.isEmpty() -> error = "账号未绑定学校"
                    schools.size == 1 -> persistLogin(fresh, schools.first())
                    else -> schoolPicker = schools
                }
                password = ""
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t.message ?: "登录失败"
            }
            busy = false
        }
    }

    fun pickSchool(school: YmSchool) {
        val fresh = client ?: return
        scope.launch {
            busy = true
            try {
                persistLogin(fresh, school)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = t.message ?: "门锁获取失败"
            }
            busy = false
        }
    }

    // 进页面：有凭据就静默同步（有缓存时界面已可用，不会被阻塞）
    LaunchedEffect(Unit) {
        if (CampusSyncPolicy.initialMode(saved) != CampusSyncPolicy.Mode.FORM) syncNow()
    }

    /**
     * 长按应用图标「快速开锁」：用默认门锁开门。
     * 若本机还没有门锁缓存（升级后首次使用），先等这次静默同步拿到列表再补开，
     * 而不是立刻丢一句"请先登录"。
     */
    var handledUnlockSeq by remember { mutableStateOf(-1) }
    LaunchedEffect(autoUnlockSeq, locks.isNotEmpty(), syncing) {
        if (autoUnlockSeq <= 0 || handledUnlockSeq == autoUnlockSeq) return@LaunchedEffect
        val target = saved?.defaultLock
        if (target != null && locks.isNotEmpty()) {
            handledUnlockSeq = autoUnlockSeq
            showData = false  // 快捷方式进来必须能看到开门过程
            requestUnlock(target)
        } else if (!syncing) {
            // 同步已结束仍没有门锁：确实开不了，提示一次
            handledUnlockSeq = autoUnlockSeq
            if (saved != null) showSnackbar("尚未同步到门锁，请先登录并同步")
        }
    }

    // 开门成功后自动退出（可选，默认关）
    LaunchedEffect(unlockState.done) {
        if (unlockState.done && autoExit) {
            delay(3000)
            (context as? android.app.Activity)?.finish()
        }
    }

    // ---- 数据详情页（二级覆盖） ----
    if (showData) {
        val session = client?.session ?: saved?.let { s ->
            YmSession(s.account, "", s.passwordMd5, s.userId).also { it.token = s.token }
        }
        val school = client?.school ?: saved?.let { YmSchool(it.schoolNo, it.schoolName, it.serverUrl, it.schoolToken) }
        CampusDataScreen(
            account = client?.session?.account ?: account,
            session = session,
            school = school,
            schoolToken = client?.schoolToken ?: saved?.schoolToken.orEmpty(),
            locks = locks,
            learnedMac = { store.learnedMac(it.label) },
            glass = glass,
            onBack = { showData = false },
        )
        return
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "宿舍开门",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "一次登录后本机开门，日常无需联网",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (locks.isNotEmpty()) {
                    IconButton(onClick = { showData = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "数据详情")
                    }
                }
            }

            if (locks.isEmpty()) {
                // ---- 无门锁缓存：登录表单，或"有凭据待同步"状态 ----
                Spacer(Modifier.height(20.dp))
                if (saved == null) {
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
                    Text(
                        "登录一次即可：账号、密码与门锁密钥只存在本机（Keystore 加密），" +
                            "之后开门全部在本机完成，不再连接服务器。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "正在同步门锁…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        Text(
                            error ?: "本机还没有门锁缓存",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = { syncNow() }, enabled = !busy) { Text("重试同步") }
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = {
                                store.clearCredentials()
                                saved = null
                                locks = emptyList()
                                error = null
                            }) { Text("重新登录") }
                        }
                    }
                }
                error?.takeIf { saved == null }?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                // ---- 门锁列表（默认门锁置顶，点击即开门） ----
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            saved?.schoolName.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (offline) "离线缓存 · ${syncTimeText(lastSyncAt)}"
                            else "已同步 · ${syncTimeText(lastSyncAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (offline) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { syncNow() }, enabled = !busy) {
                        Text(if (busy) "同步中…" else "同步门锁")
                    }
                    TextButton(onClick = {
                        store.clearCredentials()
                        saved = null
                        locks = emptyList()
                        account = store.lastAccount()
                        offline = false
                        lastSyncAt = 0L
                    }) { Text("退出登录") }
                }

                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
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

                saved?.orderedLocks.orEmpty().forEach { lock ->
                    val isDefault = lock.label == defaultLabel
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
                                Text(
                                    if (isDefault) "${lock.label} · 默认" else lock.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    if (isDefault) "点按即开门（快捷方式也用它）" else "点按开门",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!isDefault) {
                                TextButton(onClick = {
                                    defaultLabel = lock.label
                                    store.setDefaultLock(lock.label)
                                    showSnackbar("已设为默认门锁")
                                }) { Text("设为默认") }
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

                Spacer(Modifier.height(10.dp))
                SettingsSwitchRow(
                    title = "开门前验证指纹/面容",
                    subtitle = "开启后每次开门先验证，验证通过 60 秒内可连续开门；关闭则点按直接开",
                    checked = gateEnabled,
                    onCheckedChange = { want ->
                        if (want && !BiometricGate.canAuthenticate(context)) {
                            showSnackbar("本机未设置指纹/面容或锁屏密码，无法开启")
                        } else {
                            gateEnabled = want
                            store.gateEnabled = want
                            if (!want) lastGateAt = 0L
                        }
                    },
                )
                SettingsSwitchRow(
                    title = "开门后自动退出",
                    subtitle = "开门成功后 3 秒关闭应用，适合从快捷方式进来即走",
                    checked = autoExit,
                    onCheckedChange = { want ->
                        autoExit = want
                        store.autoExitOnUnlock = want
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "本机保存账号、密码与门锁密钥，日常开门不联网；" +
                        "「长按应用图标 → 快速开锁」会用默认门锁直接开门。" +
                        "第三方接口可能随云莓官方更新失效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }

    // ---- 多学校选择（手动登录时才会出现） ----
    schoolPicker?.let { schools ->
        AlertDialog(
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

/** 验证通过后的免重复验证窗口。 */
private const val GATE_GRACE_MS = 60_000L

/** 「上次同步 x 分钟前」文案。 */
private fun syncTimeText(at: Long): String {
    if (at <= 0L) return "尚未同步"
    val minutes = (System.currentTimeMillis() - at) / 60_000L
    return when {
        minutes < 1 -> "刚刚同步"
        minutes < 60 -> "${minutes} 分钟前同步"
        minutes < 60 * 24 -> "${minutes / 60} 小时前同步"
        else -> "${minutes / (60 * 24)} 天前同步"
    }
}

/** 设置开关行：标题 + 说明 + Switch。 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

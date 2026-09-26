package com.buguake.timetable.campus.ui

import com.buguake.timetable.ui.theme.*

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.buguake.timetable.ui.theme.Haptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 宿舍开门（云莓），离线优先：
 *
 * 1. 本机有门锁缓存 → 立刻渲染列表并可直接开门，不联网、不登录（开门只需 secret + BLE 参数）；
 * 2. 需要最新列表时用「同步门锁」手动刷新：先复用 token，失效才用本机密码 MD5 静默重登；
 * 3. 只有鉴权类失败才回登录表单，网络失败一律保留缓存与凭据（只标记「离线」）；
 * 4. 可选门闸：开启后开门前验证指纹/面容（60 秒内免重复验证）；
 * 5. 支持「长按应用图标 → 快速开锁」直达：进来就用默认门锁（列表第一把）开门。
 *
 * 账号密码与门锁密钥只存本机（Keystore 加密），直连云莓服务器，本应用不中转、不上传。
 *
 * 结构说明（整页重写版）：状态与业务逻辑全部收敛在根 composable，
 * 主页面 / 数据详情页是两个无状态的内容段，由 [CampusStepHost] 统一转场；
 * 不再使用内层 Scaffold 与嵌套交叉淡入，学校选择对话框提到步骤宿主之外，
 * 保证同一时刻只有一段内容在组合树里，互不叠放。
 */
private enum class UnlockStep { Main, Data }

@Composable
fun CampusUnlockScreen(
    glass: Boolean = false,
    showSnackbar: (String) -> Unit,
    autoUnlockSeq: Int = 0,
    /** 本次直达请求处理完（已下发开门或确认开不了）后回传序号，上层据此放行归零。 */
    onUnlockConsumed: (Int) -> Unit = {},
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
    // 静默同步的在途标记（与 UI 的 busy 分开：syncNow 需要在同一帧内同步置位）
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var schoolPicker by remember { mutableStateOf<List<YmSchool>?>(null) }

    // ---- 开关（可选生物识别 + 开门后自动退出） ----
    var gateEnabled by remember { mutableStateOf(store.gateEnabled) }
    var autoExit by remember { mutableStateOf(store.autoExitOnUnlock) }
    var lastGateAt by remember { mutableStateOf(0L) }

    // ---- 页内步骤：主页 ↔ 数据详情 ----
    var step by remember { mutableStateOf(UnlockStep.Main) }

    var pendingUnlock by remember { mutableStateOf<YmLock?>(null) }
    var pendingGate by remember { mutableStateOf<YmLock?>(null) }

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
     * 网络失败会以 YunmeiNetworkException 抛出，由调用方保留缓存。
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

    /** 后台静默同步（「同步门锁」按钮用它；进页面不自动同步）。 */
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

    /** 清凭据退出登录（有缓存的「退出登录」与无缓存的「重新登录」共用）。 */
    fun signOut(relogin: Boolean) {
        store.clearCredentials()
        saved = null
        locks = emptyList()
        offline = false
        lastSyncAt = 0L
        error = null
        if (relogin) account = store.lastAccount()
    }

    /**
     * 长按应用图标「快速开锁」：用默认门锁开门。
     * 若本机还没有门锁缓存（升级后首次使用），先等这次静默同步拿到列表再补开。
     * 每个序号只处理一次，处理完立刻回传消费：否则切页返回重组后会拿旧序号再开一次。
     */
    var handledUnlockSeq by remember { mutableStateOf(-1) }
    LaunchedEffect(autoUnlockSeq, locks.isNotEmpty(), syncing) {
        if (autoUnlockSeq <= 0 || handledUnlockSeq == autoUnlockSeq) return@LaunchedEffect
        val target = saved?.defaultLock
        if (target != null && locks.isNotEmpty()) {
            handledUnlockSeq = autoUnlockSeq
            step = UnlockStep.Main  // 快捷方式进来必须能看到开门过程
            requestUnlock(target)
            onUnlockConsumed(autoUnlockSeq)
        } else if (!syncing) {
            // 同步已结束仍没有门锁：确实开不了，提示一次
            handledUnlockSeq = autoUnlockSeq
            if (saved != null) showSnackbar("尚未同步到门锁，请先登录并同步")
            onUnlockConsumed(autoUnlockSeq)
        }
    }

    // 开门成功后自动退出（可选，默认关）
    LaunchedEffect(unlockState.done) {
        if (unlockState.done && autoExit) {
            delay(3000)
            (context as? android.app.Activity)?.finish()
        }
    }

    // ---- 页面内容段：主页与数据详情，同一时刻只有一段在组合树里 ----

    CampusStepHost(
        step = step,
        depth = { if (it == UnlockStep.Main) 0 else 1 },
        onBack = { step = UnlockStep.Main },
    ) { current ->
        when (current) {
            UnlockStep.Main -> UnlockPageSurface(glass) {
                // 页头：返回 + 标题 + 数据详情入口
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(SketchArrowBack, contentDescription = "返回")
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
                        IconButton(onClick = { step = UnlockStep.Data }) {
                            Icon(SketchInfo, contentDescription = "数据详情")
                        }
                    }
                }

                if (locks.isEmpty()) {
                    UnlockEmptySection(
                        saved = saved,
                        busy = busy,
                        error = error,
                        account = account,
                        onAccountChange = { account = it },
                        password = password,
                        onPasswordChange = { password = it },
                        onLogin = { doLogin() },
                        onRetrySync = { syncNow() },
                        onRelogin = { signOut(relogin = true) },
                    )
                } else {
                    Spacer(Modifier.height(12.dp))

                    // 学校 / 同步状态行 + 同步 / 退出登录
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
                        TextButton(onClick = { signOut(relogin = true) }) { Text("退出登录") }
                    }

                    error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    // 开门进行时状态行
                    val st = unlockState
                    if (st.phase.isNotBlank()) {
                        Text(
                            st.phase,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (st.failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    if (st.running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    // 首次开锁学习提示：显著提示卡，避免把首次的扫描耗时当成故障
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                SketchInfo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "首次开锁需要学习门锁地址",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "第一次开门会先扫描并学习门锁的 MAC 地址（稍慢），" +
                                        "学习完成后再次开锁直接连接、速度更快；每个门锁只需学习一次。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }

                    // 门锁列表（默认门锁置顶，点按即开门）
                    saved?.orderedLocks.orEmpty().forEach { lock ->
                        UnlockLockTile(
                            lock = lock,
                            isDefault = lock.label == defaultLabel,
                            enabled = !st.running,
                            onUnlock = { requestUnlock(lock) },
                            onSetDefault = {
                                Haptics.click(context)
                                defaultLabel = lock.label
                                store.setDefaultLock(lock.label)
                                showSnackbar("已设为默认门锁")
                            },
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    UnlockSwitchRow(
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
                    UnlockSwitchRow(
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

            UnlockStep.Data -> {
                // 系统返回键：详情页先退回主页，再由上层退回校园首页
                androidx.activity.compose.BackHandler { step = UnlockStep.Main }
                UnlockPageSurface(glass) {
                    UnlockDataSection(
                        onBack = { step = UnlockStep.Main },
                        account = client?.session?.account ?: account,
                        session = client?.session ?: saved?.let { s ->
                            YmSession(s.account, "", s.passwordMd5, s.userId).also { it.token = s.token }
                        },
                        school = client?.school ?: saved?.let {
                            YmSchool(it.schoolNo, it.schoolName, it.serverUrl, it.schoolToken)
                        },
                        schoolToken = client?.schoolToken ?: saved?.schoolToken.orEmpty(),
                        locks = locks,
                        learnedMac = { store.learnedMac(it.label) },
                    )
                }
            }
        }
    }

    // ---- 多学校选择（手动登录时才会出现）：放在步骤宿主之外，转场不参与、不重建 ----
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

/**
 * 页面表面层：玻璃模式下透出全局背景，普通模式铺 surface；
 * 内容为单列可滚动布局（不再用内层 Scaffold，避免多层嵌套测量）。
 */
@Composable
private fun UnlockPageSurface(
    glass: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp),
            content = content,
        )
    }
}

/** 无门锁缓存时的内容：首次登录表单，或「有凭据待同步」状态。 */
@Composable
private fun UnlockEmptySection(
    saved: CampusSaved?,
    busy: Boolean,
    error: String?,
    account: String,
    onAccountChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRetrySync: () -> Unit,
    onRelogin: () -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    if (saved == null) {
        OutlinedTextField(
            value = account,
            onValueChange = onAccountChange,
            label = { Text("云莓账号（学号）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onLogin,
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
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
                Button(onClick = onRetrySync, enabled = !busy) { Text("重试同步") }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = onRelogin) { Text("重新登录") }
            }
        }
    }
}

/** 门锁卡片：默认锁置顶加粗、点按开门、非默认可设为默认。 */
@Composable
private fun UnlockLockTile(
    lock: YmLock,
    isDefault: Boolean,
    enabled: Boolean,
    onUnlock: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Card(
        onClick = onUnlock,
        enabled = enabled,
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
                TextButton(onClick = onSetDefault) { Text("设为默认") }
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

/** 设置开关行：标题 + 说明 + Switch（切换带轻触感）。 */
@Composable
private fun UnlockSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
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
        Switch(
            checked = checked,
            onCheckedChange = {
                Haptics.tick(context)
                onCheckedChange(it)
            },
        )
    }
}

/** 数据详情页内容段：脱敏开关 + 账号 / 学校 / 门锁全部数据。 */
@Composable
private fun UnlockDataSection(
    onBack: () -> Unit,
    account: String,
    session: YmSession?,
    school: YmSchool?,
    schoolToken: String,
    locks: List<YmLock>,
    learnedMac: (YmLock) -> String,
) {
    var reveal by remember { mutableStateOf(false) }

    // 页头：返回 + 标题
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(SketchArrowBack, contentDescription = "返回")
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
    DataSectionTitle("账号")
    DataCard {
        DataLine("云莓账号", maskSecret(account, reveal))
        CardDivider()
        DataLine("用户 ID", maskSecret(session?.userId ?: "", reveal))
        CardDivider()
        DataLine("账号 MD5", maskSecret(session?.accountMd5 ?: "", reveal), mono = true)
        CardDivider()
        DataLine("密码 MD5", maskSecret(session?.passwordMd5 ?: "", reveal), mono = true)
        CardDivider()
        DataLine("会话 token", maskSecret(session?.token ?: "", reveal), mono = true)
    }

    // ---- 学校 / 服务器 ----
    DataSectionTitle("学校 / 服务器")
    DataCard {
        DataLine("学校名称", school?.name ?: "—")
        CardDivider()
        DataLine("学校编号", school?.schoolNo ?: "—")
        CardDivider()
        DataLine("服务器地址", school?.serverUrl ?: "—", mono = true)
        CardDivider()
        DataLine("学校 token", maskSecret(schoolToken.ifBlank { school?.token ?: "" }, reveal), mono = true)
    }

    // ---- 门锁 ----
    DataSectionTitle("门锁（${locks.size} 把）")
    if (locks.isEmpty()) {
        DataCard { DataLine("门锁", "账号下没有绑定的门锁") }
    } else {
        locks.forEachIndexed { index, lock ->
            val learned = learnedMac(lock)
            val macText = when {
                learned.isNotBlank() -> learned
                lock.mac.isNotBlank() -> lock.mac
                else -> ""
            }
            val macHint = when {
                learned.isNotBlank() -> "本地已学习（快速连接）"
                lock.mac.isNotBlank() -> "服务器下发 lockNo（非真实 MAC，需扫描）"
                else -> "暂无"
            }
            DataCard {
                DataLine("门锁 ${index + 1}", lock.label)
                CardDivider()
                DataLine("服务 UUID", lock.serviceUuid, mono = true)
                CardDivider()
                DataLine("写特征 UUID", lock.writeCharUuid, mono = true)
                CardDivider()
                DataLine("通知特征 UUID", lock.notifyCharUuid, mono = true)
                CardDivider()
                DataLine("secret", maskSecret(lock.secret, reveal), mono = true)
                CardDivider()
                DataLine("MAC", maskSecret(macText, reveal), mono = true, hint = macHint)
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

@Composable
private fun DataSectionTitle(text: String) {
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
private fun DataLine(
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
private fun maskSecret(value: String, reveal: Boolean): String {
    if (value.isBlank()) return ""
    if (reveal) return value
    if (value.length <= 6) return "•".repeat(value.length.coerceAtLeast(4))
    return value.take(3) + "•".repeat((value.length - 6).coerceAtMost(12)) + value.takeLast(3)
}

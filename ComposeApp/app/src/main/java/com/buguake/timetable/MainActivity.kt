package com.buguake.timetable

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.buguake.timetable.campus.LaundryLaunch
import com.buguake.timetable.campus.QuickUnlock
import com.buguake.timetable.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 冷启动 SplashScreen：系统级启动动画，退出时自然过渡到界面
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 边到边绘制：背景图延伸至状态栏/导航条下方，内容用 insets 避让
        enableEdgeToEdge()
        // Android 13+ 前台服务通知需运行时权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        // 「长按应用图标 → 快速开锁」快捷方式：只登记序号，界面侧决定何时开门
        notifyUnlockIfTrusted(intent)
        // 洗衣房小组件 / 快捷方式：仅打开界面（无敏感副作用），不校验调用方
        LaundryLaunch.notifyIfMatches(intent)
        setContent {
            AppRoot()
        }
    }

    /** launchMode=singleTop：应用已在后台时再次点快捷方式不会走 onCreate，必须在这里接。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notifyUnlockIfTrusted(intent)
        LaundryLaunch.notifyIfMatches(intent)
    }

    private fun notifyUnlockIfTrusted(intent: Intent?) {
        if (intent?.action != QuickUnlock.ACTION_UNLOCK) return
        if (isTrustedUnlockCaller()) {
            QuickUnlock.notify(intent)
        } else {
            // 第三方应用显式带上 UNLOCK action 启动本页：忽略，避免被远程触发开门
            android.util.Log.w("MainActivity", "忽略来自 ${launchedFromPackage} 的开锁请求")
        }
    }

    /**
     * 开锁请求只接受系统组件与桌面启动器（含第三方桌面）。
     *
     * MainActivity 必须 exported 才能被启动器拉起，因此任意应用都能显式构造
     * `action = campus.UNLOCK` 的 Intent 把它调起来；若不校验，别的应用就能在你
     * 靠近宿舍门锁时远程触发一次开门。这里按调用方包名做白名单：
     * 系统应用（系统/厂商桌面、system_server 等）或能响应 HOME 的桌面应用。
     */
    private fun isTrustedUnlockCaller(): Boolean {
        val from = launchedFromPackage ?: return true  // 某些系统路径取不到调用方，放行
        if (from == packageName) return true
        val info = runCatching { packageManager.getApplicationInfo(from, 0) }.getOrNull()
            ?: return false
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return true
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            packageManager.queryIntentActivities(home, 0).any { it.activityInfo.packageName == from }
        }.getOrDefault(false)
    }
}

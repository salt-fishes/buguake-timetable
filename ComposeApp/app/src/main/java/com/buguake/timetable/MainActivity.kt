package com.buguake.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buguake.timetable.campus.QuickUnlock
import com.buguake.timetable.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
        QuickUnlock.notify(intent)
        setContent {
            AppRoot()
        }
    }

    /** launchMode=singleTop：应用已在后台时再次点快捷方式不会走 onCreate，必须在这里接。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        QuickUnlock.notify(intent)
    }
}

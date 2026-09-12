# 隐私合规自查记录

> 自查日期：2026-09-09　对应版本：v1.1（versionCode 3）
> 自查范围：权限声明、第三方组件、数据存储与备份、网络与明文传输、导出组件、WebView/JS 桥、日志与调试开关、隐私政策覆盖率。

## 一、结论

应用**不收集、不上传、不共享**任何个人信息：无账号体系、无服务端、无广告 SDK、无统计埋点、无崩溃上报。
所有用户数据（课表、背景图、云莓凭据、门锁密钥、考试安排缓存）均只存在于设备本地，卸载即删除。
本次自查修复 4 处问题（见第四节），另有 2 项残余风险与建议记录在第五节。

## 二、权限逐项核对（release 合并清单）

| 权限 | 是否使用 | 用途与触发时机 | 隐私政策已说明 |
|---|---|---|---|
| INTERNET | 是 | 下载适配脚本、内嵌浏览器加载教务站、宿舍开门登录/同步 | ✅ |
| POST_NOTIFICATIONS | 是 | 课前提醒（Android 13+ 运行时申请） | ✅ |
| USE_BIOMETRIC | 是 | 可选「开门前验证」，仅用户开启后使用 | ✅ |
| BLUETOOTH_SCAN / CONNECT | 是 | 仅点击「开门」时扫描/连接门锁（`neverForLocation`） | ✅ |
| BLUETOOTH / BLUETOOTH_ADMIN / ACCESS_FINE_LOCATION | 是（maxSdkVersion=30） | Android 11 及以下 BLE 扫描的旧机制要求 | ✅ |
| READ_CALENDAR / WRITE_CALENDAR | 是 | 写入并清理系统日历「不挂科课表」与「不挂科考试」（`CalendarSync` 需按名查询日历） | ✅ |
| VIBRATE | 是 | 操作触感反馈 | ✅ |
| RECEIVE_BOOT_COMPLETED | 是 | 重启后重排课前提醒（`BootReceiver`） | ✅ |
| SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM | 是 | 课前提醒准点触发（核心功能） | ✅ |

无 `READ_PHONE_STATE`、无 `QUERY_ALL_PACKAGES`、无 `AD_ID`、无后台定位、无存储权限（仅用系统文件/照片选择器）。

## 三、第三方与数据流向

- 依赖仅 okhttp / Room / Jetpack Compose / lifecycle / org.json：**无广告、无统计、无崩溃上报 SDK**，无远程配置与热更新。
- 出网域名固定且可枚举：`cdn.jsdelivr.net`、`raw.githubusercontent.com`（适配脚本与索引）、
  `api.github.com`（手动检查更新）、`base.yunmeitech.com` 及各校 `serverUrl`（宿舍开门）。
- 教务系统交互只发生在内嵌 WebView 中，凭据仅存在于该会话；App 不读取、不落盘（提供「清除登录」）。
- 校园「考试安排」在同一个 WebView 会话内读取本人考试数据，结果经 Keystore AES-256/GCM 加密后仅存本机（可一键清空、不参与备份）。
- 云莓凭据与门锁密钥经 Android Keystore AES-256/GCM 加密后存于应用私有 `SharedPreferences`。

## 四、本次修复

1. **WebView 远程调试在正式包被放开**（`ImportWebViewScreen`）：`setWebContentsDebuggingEnabled(true)` 无条件执行，
   正式包也存在 `chrome://inspect` 入口，可被用来查看教务会话页面。→ 改为仅 `BuildConfig.DEBUG` 开启。
2. **凭据参与系统备份**：`allowBackup=true` 时 `campus_prefs.xml`（云莓账号/密码摘要/门锁密钥）会进入云备份与换机迁移。
   → 新增 `res/xml/backup_rules.xml` 与 `res/xml/data_extraction_rules.xml`，排除该文件与 `webimport` 脚本缓存（课表数据库仍参与备份）。
3. **导出组件可被第三方应用触发开门**：`MainActivity` 需 exported 供启动器拉起，因此任意应用都能显式带
   `com.buguake.timetable.campus.UNLOCK` 启动它，等于可远程触发一次开门。
   → `MainActivity` 增加调用方白名单：仅系统应用与桌面启动器（能响应 HOME 的应用）的请求被接受，其余记录警告并忽略。
4. **日志中的敏感内容**：页面 URL（常带会话参数）、JS 控制台输出、桥消息原文会在正式包中落入 logcat。
   → 三处均改为仅 debug 构建记录，正式包只记录失败事实、不记录内容。

## 五、残余风险与建议（未改动，供决策）

1. **`usesCleartextTraffic="true"`**：内嵌浏览器需访问部分仍为 http 的教务站，故保留全局明文允许。
   影响：App 自身请求均为 https，风险集中在用户所选学校站点；已在隐私政策中明示。
   可选加固：为自有域名加 `networkSecurityConfig` 并把 WebView 的明文限制改为域名白名单（需先枚举学校站点，暂不现实）。
2. **应用内 JS 桥的能力边界**：`_shiguangNativeBridge` 暴露给教务页面，可调用 `showToast/showAlert/showPrompt/
   saveImportedCourses/saveCourseConfig/savePresetTimeSlots` —— 全部是本地写库与弹窗，**无文件读写、无网络、无凭据访问**；
   若学校站点被篡改，最坏结果是写入伪造课程（完整性问题，非泄露）。可选加固：在 `onMessageReceived` 前校验 `webView.url` 的 host 属于该校站点。
3. **`USE_EXACT_ALARM` 的应用商店口径**：Google Play 仅允许核心功能为闹钟/日历的应用声明。
   本应用核心是课表与课前提醒，可归入日历类；若审核要求，可退回仅声明 `SCHEDULE_EXACT_ALARM`（用户需在系统设置中授权「闹钟与提醒」）。

## 六、应用商店「数据安全」填报建议

| 问题 | 建议答复 |
|---|---|
| 是否收集或共享任何用户数据 | 否（不收集、不上传、不共享） |
| 数据传输是否加密 | 不适用（无数据传出）；自有请求均 https，内嵌浏览器访问的学校站点由其自身决定 |
| 用户能否请求删除数据 | 数据仅在本地，卸载即删除；另有「退出登录」「清空系统日历」等应用内入口 |
| 是否收集广告 ID | 否 |
| 敏感权限用途 | 蓝牙=开门（用户主动触发）、日历=课表同步、通知=课前提醒、指纹=可选开门校验 |

## 七、与政策文档的一致性

本记录对应的用户可见说明：应用内「关于 → 隐私政策」（`assets/html/privacy.html`）、仓库根 `PRIVACY_POLICY.md`（v1.2）。
权限清单、联网行为、数据存储与删除、免责声明四处口径一致。

## 八、v1.2 增量自查（校园考试安排）

- **新增本地数据**：考试安排缓存（课程、时间、考场、座位、方式、学期）。与云莓凭据同处 `campus_prefs.xml`，
  经 Keystore AES-256/GCM 加密；该文件已被备份规则排除，因此**不参与云备份与换机迁移**。应用内提供「清空考试缓存」。
- **新增联网行为**：在内嵌 WebView 里向本人教务系统发起考试查询（正方 V9 的 `kscx_cxXsksxxIndex`）。
  与课程导入共用同一个 WebView 外壳、同一套 Cookie 会话与「清除登录」；登录凭据不落盘。
- **权限**：未新增任何权限。日历权限复用，考试写入独立日历「不挂科考试」，与课表同步/清空互不影响。
- **能力边界**：抓取脚本是**校园本地化**自带实现（未进入拾光适配器生态，也未新增桥协议动作），
  目前仅在中国计量大学实测通过；其数据开关完全独立于云莓凭据与宿舍开门。

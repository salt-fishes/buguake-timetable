# 不挂科课表

从"简课表"进化而来的 Android 课表应用：**教务网页一键导入** + **宿舍蓝牙开门**。
导入适配由开源社区生态驱动，无需手写解析规则。

> 当前版本 **v1.5.1**｜支持 Android 8.0 及以上｜JDK 17 + Android SDK 35 构建

## 功能

以下每一项均可对照仓库源码核验（关键实现位置随文标注）：

- 🌐 **教务网页导入**：选择学校 → 内嵌浏览器登录教务 → 一键导入课程/周次/节次/地点/教师。
  学校适配遵循拾光课程表生态的桥协议（覆盖 200+ 学校，正方、超星、青果、URP 等由社区适配器支持）；
  支持自选适配器仓库（预置本项目镜像与拾光官方上游，可添加自定义）；
  通用适配器可直接在顶栏输入本校教务网址（`webimport/`）。
- 🚪 **宿舍蓝牙开门**：基于云莓协议（`campus/`，BLE 直连门锁）。登录一次后云莓凭据与门锁密钥
  经 Android Keystore AES-256/GCM 加密只存本机（`campus/LocalCrypto.kt`），**日常开门离线完成、不再联网**；
  可选「开门前验证指纹/面容」（USE_BIOMETRIC）、长按应用图标「快速开锁」、开门后自动退出。
- 📝 **考试安排**：兼容**正方教务（V9）**的学校（实测基准：中国计量大学）。在应用内登录教务后
  读取考试时间/考场/座位，可写入系统日历「不挂科考试」或导出 `考试安排.ics`；
  结果经 Keystore 加密缓存在本机，可随时清空（`campus/exam/`）。
- 🧺 **洗衣房**：直连顺水平台**只读**查询（`campus/laundry/`）：门店可搜索/按附近选择，
  查看洗衣机、烘干机（及门店实际存在的洗鞋机、吹风机等分类）哪台空着、剩余时间实时倒计时、空闲的排前面，
  一键跳官方小程序开洗；无需登录、不上传任何数据；附 2×1 桌面小组件。
- 🗂 **多课表**：多张课表并存，各自独立的开学日、总周数与作息时间（`data/ScheduleSettings.kt` 按活动课表派生）。
- 📅 **周视图 / 今日页**：滑动切周、当前时间线、考试自动上首屏、自定义时间段课次自动落格。
- ✏️ **调课**：长按拖拽换位，可选"以后每周"或"仅本周"（范围可设默认，不再每次询问）；
  v1.6 新增**按日期调课**——把某个日期的课调到另一日期（支持跨周），落点直接覆盖。
- 📱 **小组件**：2×2 / 3×2 / 4×2（今明双栏）三种规格分别绑定课表，另有多分类洗衣房 2×1 空闲速览。
- 🔔 **上课提醒**：课前 5/10/15/20 分钟本地通知，准点触发（精确闹钟），重启自动恢复（开机广播重排）。
- 🗓 **日历同步**：课程写入系统日历「不挂科课表」、考试写入「不挂科考试」，可分别清空；
  课表亦可导出 .ics（`data/CalendarSync.kt` / `data/CalendarExport.kt`）。
- 🆚 **课表对比**（实验性）：截图识别课程占用，找共同空闲时间。
- 🎨 **个性化**：贴纸风亮/暗双主题（与官网同源设计 token）、自定义纸面背景图片直铺。

## 下载与构建

前往 [Releases](https://github.com/salt-fishes/buguake-timetable/releases) 下载最新 APK，
或到**官网 <http://8.133.174.78>** 查看功能介绍 / 更新日志 / 常见问题 / 隐私政策（源码在仓库 `website/`，
发版后运行 `website/tools/deploy.ps1` 同步更新）；发版与构建全部在本地完成：

```bash
# 一键发版：自动更新版本号/about.html 更新记录/README → 本地测试与签名构建 → 打 tag → 创建 Release 上传 APK
./release.ps1 -Version 1.6 -Notes @("新增XXX", "修复YYY")

# 只构建不发包
cd ComposeApp
./gradlew :app:assembleDebug        # 调试包（与正式版共存）
./gradlew :app:assembleRelease      # 正式包（R8 压缩；未配置签名时为 unsigned）
./gradlew :app:testDebugUnitTest    # 单元测试
```

要求 JDK 17 + Android SDK 35，Release 上传需要 `gh` CLI（`gh auth login`）。

本地签名打包：在仓库根放 `keystore.properties`（`storeFile=keystore/buguake.jks`、`storePassword`、`keyAlias`、`keyPassword`）
与 `keystore/buguake.jks`，两者均已被 `.gitignore` 排除、不会入库；随后 `./gradlew :app:assembleRelease` 即产出已签名 APK。

目录速览：客户端在 `ComposeApp/`（`campus/` 宿舍开门与校园功能、`webimport/` 教务导入、
`ui/` 界面与主题、`data/` 数据层、`widget/` 桌面小组件）；`docs/` 放适配对比等文档；
隐私政策在应用内「关于 → 隐私政策」以 HTML 呈现（`assets/html/`）。

## 参与贡献：欢迎为学校适配出一份力 🙌

本项目的教务导入能力**完全建立在开源适配生态之上**——适配器是遵循桥协议的 JavaScript 脚本，
加上一份学校索引，客户端按需懒加载执行，因此**新增/修复一所学校不需要改客户端代码**，
非常适合作为第一次贡献：

1. **新增学校适配 / 修复失效适配**：到拾光适配仓库
   [XingHeYuZhuan/shiguang_warehouse](https://github.com/XingHeYuZhuan/shiguang_warehouse)
   提 Issue 或 PR（适配脚本写法见该仓库说明）。合并后本应用重新同步索引即可生效。
2. **只反馈问题也可以**：在本仓库提 Issue，附上**教务系统网址、学校名称、失败截图或日志**
   （内嵌浏览器页面里可长按复制报错），我们负责复现并同步到上游。
3. **客户端改进**：代码、界面、文案、性能、文档都欢迎提 PR；提交前请先跑
   `./gradlew :app:testDebugUnitTest`。

> 说明：导入源仓库可在应用内「我的 → 导入源仓库」自选（预置拾光官方上游与本项目镜像，默认拾光官方）。

## 隐私

- 课表数据全部保存在设备本地；无广告、无统计埋点、无数据上报；
- 教务账号密码只在内嵌浏览器会话内使用，App 不读取、不保存、不上传；
- 云莓账号密码与门锁密钥只存本机（Android Keystore AES-256/GCM 加密），登录请求直连云莓服务器；
- 考试安排（正方教务）在同一内嵌会话内解析后加密缓存在本机，不含账号密码，可随时清空；
- 洗衣房无需登录、不上传任何数据；定位仅在你点「附近门店」时使用一次，拒绝授权功能照常可用；
- 全部权限逐项说明见 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)（每条声明均标注对应实现文件，可自行核验）。

## 许可证与致谢

本项目以 **MIT License** 释出（Copyright (c) 2026 咸鱼 (salt-fishes)）。

**特别感谢拾光课程表生态**：[shiguangschedule](https://github.com/XingHeYuZhuan/shiguangschedule)（Apache-2.0）
与适配仓库 [shiguang_warehouse](https://github.com/XingHeYuZhuan/shiguang_warehouse)（MIT）。
本应用"选学校 → 登教务 → 一键导入"的能力，来自该生态长期积累的桥协议设计与学校适配脚本，
以及每一位提交过适配的贡献者；再次感谢。此外：

- **云莓不智能**（[yunmei_unintelligent](https://github.com/zxy19/yunmei_unintelligent)，MIT）：宿舍开门的通信协议
- 渊源：本项目派生自同一作者的前作"简课表"

完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 免责声明

教务导入与宿舍开门均基于**第三方逆向接口**，可能随学校或官方系统更新而失效；
洗衣房查询同样基于第三方逆向接口（顺水平台），可能失效且门店覆盖以其官方为准。
请仅将其用于个人课表管理与开门等合法用途，使用产生的后果由使用者自行承担。
本项目与任何教务系统厂商、云莓智能官方、顺水平台官方均无关联。

## 反馈

- [GitHub Issues](https://github.com/salt-fishes/buguake-timetable/issues)（推荐）
- 邮箱：xunguang255@163.com

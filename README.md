# 不挂科课表

[![Android CI](https://github.com/salt-fishes/buguake-timetable/actions/workflows/android-ci.yml/badge.svg)](https://github.com/salt-fishes/buguake-timetable/actions/workflows/android-ci.yml)

从"简课表"进化而来的 Android 课表应用：**教务网页一键导入** + **宿舍蓝牙开门**。
导入适配由开源社区生态驱动，无需手写解析规则。

> 当前版本 **v1.4**｜支持 Android 8.0 及以上｜JDK 17 + Android SDK 35 构建

> v1.4 亮点：宿舍开门页整页重构（修复文字重叠）；快捷方式快速开锁只触发一次；
> 全部二级/三级页面统一进出场转场并支持边缘侧滑返回；
> 全应用细节动效与触感反馈补全；首页周次滑动抖动修复，周数滑杆限定学期内而手动滑页不受限。

## 功能

- 🌐 **教务网页导入**：选择学校（200+ 覆盖）→ 内嵌浏览器登录教务 → 一键导入课程/周次/节次/地点/教师；
  支持正方、超星、青果、URP 等通用适配器（输入本校教务网址即可）
- 🚪 **宿舍开门**：登录一次后门锁密钥加密保存在本机，**日常开门不再联网、不再重复登录**；
  可选「开门前验证指纹/面容」，支持「长按应用图标 → 快速开锁」与开门后自动退出
- 📝 **考试安排**（校园本地化，当前适配中国计量大学）：在应用内登录教务系统读取考试时间、考场与座位，
  可写入系统日历「不挂科考试」或导出 .ics；结果只加密缓存在本机，可随时清空
- 🧺 **洗衣房**：门店可搜索/按附近选择（顺水平台），查看洗衣机/烘干机哪台空着、剩余时间实时倒计时，
  一键跳官方小程序开洗；无需登录，不上传任何数据
- 🗂 **多课表**：班级/个人课表并存，独立开学日、周数与作息
- 📅 **周视图 / 今日页**：滑动切周、当前时间线、自定义时间段课次自动落格
- ✏️ **调课**：长按拖拽换位，可选"以后每周"或"仅本周"
- 📱 **小组件**：2×2 / 3×2 / 4×2（今明双栏）三种规格，分别绑定课表
- 🔔 **上课提醒**：课前本地通知，重启自动恢复
- 🗓 **日历同步**：课程一键写入系统日历，可撤销
- 📊 **课表对比**：截图识别占用，找共同空闲
- 🎨 **个性化**：深色模式、动态取色、磨砂玻璃风格（可关闭）、自定义背景

## 下载与构建

前往 [Releases](https://github.com/salt-fishes/buguake-timetable/releases) 下载最新 APK；自行构建：

```bash
cd ComposeApp
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:assembleRelease      # 正式包（未配置签名时为 unsigned）
./gradlew :app:testDebugUnitTest    # 单元测试
```

要求 JDK 17 + Android SDK 35。推送 `v*` 标签（如 `v1.1`）会触发
[Release workflow](.github/workflows/android-release.yml) 自动构建并上传签名 APK，
需在仓库 Secrets 中配置 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
（未配置时该 job 会跳过签名打包并给出提示，不会失败）。

本地签名打包：在仓库根放 `keystore.properties`（`storeFile=keystore/buguake.jks`、`storePassword`、`keyAlias`、`keyPassword`）
与 `keystore/buguake.jks`，两者均已被 `.gitignore` 排除、不会入库；随后 `./gradlew :app:assembleRelease` 即产出已签名 APK。

目录速览：客户端在 `ComposeApp/`（`campus/` 宿舍开门、`webimport/` 教务导入、`ui/` 界面与主题）；
`docs/` 放适配对比等文档；隐私政策在应用内「关于 → 隐私政策」以 HTML 呈现（`assets/html/`）。

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
   `./gradlew :app:testDebugUnitTest`（CI 也会执行同样的检查）。

> 说明：本项目当前默认导入源指向拾光官方仓库（过渡期），
> 自有 fork [salt-fishes/shiguang_warehouse](https://github.com/salt-fishes/shiguang_warehouse)
> 审核通过后会平滑切换，届时仍会保留全部上游作者署名。

## 隐私

- 教务账号密码只在内嵌浏览器会话内使用；云莓账号密码与门锁密钥只存本机
  （Android Keystore AES-256/GCM 加密），登录请求直连云莓服务器；
- 考试安排（校园本地化）在同一内嵌会话内解析后加密缓存在本机，不含账号密码，可随时清空；
- 洗衣房无需登录、不上传任何数据；定位仅在你点「附近门店」时使用一次，拒绝授权功能照常可用；
- 课表数据全部本地存储；无广告、无统计埋点
- 详见 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

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

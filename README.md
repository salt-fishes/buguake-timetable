# 不挂科课表

从"简课表"进化而来的 Android 课表应用：**教务网页一键导入** + **宿舍蓝牙开门**，
适配由开源社区生态驱动，无需手写解析规则。

> 🚧 **项目状态**：v0.x，功能可用但仍在快速迭代。

## 功能

- 🌐 **教务网页导入**：选择学校（200+ 覆盖）→ 内嵌浏览器登录教务 → 一键导入课程/周次/节次/地点/教师；
  支持正方、超星、青果、URP 等通用适配器（输入本校教务网址即可）
- 🚪 **校园开门**：云莓宿舍蓝牙门锁一键开门（第三方接口）
- 🗂 **多课表**：班级/个人课表并存，独立开学日、周数与作息
- 📅 **周视图 / 今日页**：滑动切周、当前时间线、自定义时间段课次自动落格
- ✏️ **调课**：长按拖拽换位，可选"以后每周"或"仅本周"
- 📱 **小组件**：2×2 / 2×3 / 2×4 三种规格，分别绑定课表
- 🔔 **上课提醒**：课前本地通知，重启自动恢复
- 🗓 **日历同步**：课程一键写入系统日历，可撤销
- 📊 **课表对比**：截图识别占用，找共同空闲
- 🎨 **个性化**：深色模式、动态取色、磨砂玻璃风格（可关闭）、自定义背景

## 构建

```bash
./gradlew :app:assembleDebug
```

要求 JDK 17 + Android SDK 35。

## 隐私

- 教务账号密码只在内嵌浏览器会话内使用；云莓账号密码只存本机、直连云莓服务器；
- 课表数据全部本地存储；无广告、无统计埋点
- 详见 [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

## 许可证与致谢

MIT License（Copyright (c) 2026 咸鱼 (salt-fishes)）。第三方引用：

- **拾光课程表**（[shiguangschedule](https://github.com/XingHeYuZhuan/shiguangschedule)，Apache-2.0）：
  教务导入的桥协议与学校适配器生态（[shiguang_warehouse](https://github.com/XingHeYuZhuan/shiguang_warehouse)，MIT）
- **云莓不智能**（[yunmei_unintelligent](https://github.com/zxy19/yunmei_unintelligent)，MIT）：
  宿舍开门的通信协议
- 渊源：本项目派生自同一作者的前作"简课表"

完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 免责声明

教务导入与宿舍开门均基于**第三方逆向接口**，可能随学校或官方系统更新而失效；
请仅将其用于个人课表管理与开门等合法用途，使用产生的后果由使用者自行承担。
本项目与任何教务系统厂商、云莓智能官方均无关联。

## 反馈

- GitHub Issues（推荐）
- 邮箱：xunguang255@163.com

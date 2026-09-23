# 不挂科课表 · 官网设计（Design Spec）

> 日期：2026-09-23　状态：已确认（brainstorm 逐节过审）
> 视觉参考：`assets/2026-09-23-homepage-mockup.html`（本目录同级 `assets/` 归档，浏览器直接打开可看整页 mockup）
> 仓库：`salt-fishes/buguake-timetable`　官网源码将落于仓库根 `website/`

## 0. 背景与目标

为「不挂科课表」（Android 课表应用，当前 v1.5.1）建设宣传官网，定位**综合门户**：

- 讲清楚产品是什么（教务网页一键导入 + 宿舍蓝牙开门 + 洗衣房/考试安排）
- 提供下载（GitHub Releases）
- 长期维护内容：更新日志（自动同步）、常见问题、参与贡献、隐私政策、开源致谢
- 核心转化：访客尽快下载 APK；次核心：引导参与学校适配贡献

**非目标**：不做深色模式（首版）、不做域名/HTTPS（预留）、不做运行时调 GitHub API、不做后端/评论/统计。

## 1. 已确认的关键决策

| 决策项 | 结论 |
|---|---|
| 站型 | 综合门户（介绍 + 下载 + 更新日志 + FAQ + 反馈渠道 + 开源致谢） |
| 部署 | 用户自有 Linux 服务器（SSH 目标与凭据**不入库**，本地配置）；第一版 HTTP + IP 访问，预留换域名 |
| 更新日志 | **构建时**脚本同步 GitHub Releases（脚本失败用仓库内快照兜底） |
| 视觉风格 | 校园贴纸风（纸感底 + 硬描边贴纸卡 + 荧光笔高亮 + 口号文案），品牌种子色沿用 App `#333464` |
| 技术方案 | 方案一：`website/` 静态站进 App 仓库，无框架无打包器，脚本同步 + scp 部署 |
| 动画 | anime.js（**自托管**，不走 CDN） |
| 首页结构 | 见归档 mockup：导航 → Hero → 三大特色 → 更多功能 → 隐私横幅 → 下载 → 更新日志/FAQ 双栏 → 参与贡献 → 免责 → 致谢 → 页脚 |

## 2. 站点结构（4 页）

| 页面 | 内容 |
|---|---|
| `index.html` | 单页主站，锚点导航：Hero / 三大特色 / 更多功能 / 隐私横幅 / 下载 / 更新日志预览（近 3 条）/ FAQ 预览（4 条）/ 参与贡献 / 免责声明 / 开源致谢 / 页脚 |
| `changelog.html` | 全量版本记录：Releases notes 优先 ⊕ App 内 `assets/html/about.html` verlist 兜底合并（补齐 v1.3 / v1.2 / v1.0 / v0.1.0 等未发 Release 的版本） |
| `faq.html` | 完整 FAQ（8–10 条）：学校不在列表、适配失效反馈流程、与「简课表」渊源、账号密码安全、考试安排正方适配、双版本/数据、卸载删数据、免责声明边界等 |
| `privacy.html` | 隐私政策全文，镜像仓库根 `PRIVACY_POLICY.md`；页脚注明「与 App 内政策（v1.5，2026-09-18）一致」 |

资源目录：

```
website/
├── index.html  changelog.html  faq.html  privacy.html
├── assets/
│   ├── css/main.css
│   ├── js/main.js            # 动画与交互（anime.js 调用）
│   ├── js/anime.min.js       # 自托管 anime.js v4（~18KB gzip）
│   └── img/                  # 应用图标、截图/自制素材
├── data/changelog.json       # sync 脚本生成，提交入库作快照
└── tools/
    ├── sync.ps1              # 合并 Releases ⊕ about.html，注入版本号
    └── deploy.ps1            # sync → scp 推送服务器
```

## 3. 视觉系统（风格 C · 校园贴纸风）

**色板**

| Token | 值 | 用途 |
|---|---|---|
| `--paper` | `#fff8ec` | 页面底（纸感米黄） |
| `--ink` | `#2d2a45` | 描边、正文深色、页脚底 |
| `--brand` | `#333464` | 品牌深蓝紫（App SeedPrimary）：主按钮、主强调卡 |
| `--hl` | `#ffd666` | 荧光黄：高亮下划线、徽章、深底上的 CTA |
| `--dim` | `#6b6685` | 次级正文 |
| `--faint` | `#8a849f` | 弱文字/元信息 |
| 课程点缀色 | 蓝紫 `#eef0ff/#7c8df0`、橙 `#fff0e6/#e8894a`、绿 `#e9f8f1/#3ba57a`、紫 `#f6e9ff/#9a5ec9`、黄 `#fff9d9/#c9a227` | 特色卡/小组件示意，仅点缀 |

**贴纸语言**：2.5–3px `--ink` 描边 + 4–6px 硬阴影（无模糊 `box-shadow: Npx Npx 0 var(--ink)`）+ 16–18px 圆角；徽章/标签旋转 ±2°。

**字体**：`system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`（与 App `about.html` 一致）；大标题 900 字重、行高 1.22；英文小节标签（FEATURES / DOWNLOAD）letter-spacing 0.2em。

**响应式**：≥1024px Hero 双栏（左文右手机贴纸）；<1024px 单栏、手机贴纸居中；移动端硬阴影减半、旋转归零防溢出；功能卡 4 列 → 2 列 → 1 列。

**首版仅亮色纸感**；深色版未来以 CSS 变量翻转实现，本次不做。

**首页 Hero 右侧（已确认）**：310px 大手机周视图贴纸（周次标题、节次时间轴、课程色块、当前时间红线、连堂纵向同列贯通、4-tab 玻璃底栏）+ 右上悬浮「4×2 桌面小组件」卡 + 左下悬浮「蓝牙开门」卡（深蓝紫底、黄圆开锁钮、「密钥已存本机·无需联网」）。

## 4. 动画设计（anime.js v4，自托管）

原则：只动 `transform / opacity`；尊重 `prefers-reduced-motion`（开启时全部关闭、直接呈现终态）；单个 `main.js` 调度，无其它动画库。

1. **Hero 入场**：徽章旋入（rotate -6°→-1.5°）→ 标题逐行上浮 stagger 80ms → 右侧手机弹簧摆入（translateY+rotate 落定 2°）→ 两张悬浮卡延迟 200/320ms 跟进
2. **滚动揭示**：IntersectionObserver（threshold 0.15）触发一次；区块卡片依次「贴上去」：opacity 0→1 + translateY 24px→0 + rotate -1.5°→0，stagger 60ms
3. **悬停**：贴纸卡 hover 阴影 4px→6px + translateY -2px + 微旋（spring）；按钮按压 scale 0.96；链接下划线荧光笔擦入
4. **数字**：Hero「200+ 学校」count-up（滚动进视口时）；下载区版本号 badge 弹入
5. **changelog 时间线**：左侧荧光黄线 draw-in（scaleY 0→1），条目 stagger 淡入
6. **性能**：`will-change` 仅动画期间添加；后台标签页不跑动画

## 5. 架构与数据流

```
[GitHub Releases] --gh api--\
                              > sync.ps1 > changelog.json + 页面版本号注入
[assets/html/about.html] ----/                    |
                                                  v
本地 website/  --deploy.ps1 (scp, SSH 密钥)--> 服务器 nginx 静态目录
```

- **版本号单一来源**：`sync.ps1` 取 Releases latest tag（无则读 `ComposeApp/app/build.gradle.kts` 的 `versionName`）注入 index 下载区；页面不手写版本号
- **sync 合并规则**：同版本号时 Releases notes 优先；Releases 没有的版本用 about.html verlist 条目；输出 `data/changelog.json`（提交入库）
- **失败兜底**：gh 失败/限流 → 打印警告，直接用已提交的 `changelog.json` 继续部署，**不阻塞发版**；可选环境变量 `GITHUB_TOKEN`
- **部署**：`deploy.ps1` = 先 `sync.ps1`，再 `scp -r website/*` 到服务器 nginx 静态 root（首次部署人工探测/建目录）；**SSH 凭据不写入脚本**——首次用公钥登录（ssh-copy-id）后全程密钥；服务器地址、密码不入库
- **nginx**：静态 root + gzip；404 直接返回（无 SPA）；后续换域名时补 HTTPS
- **可选**：`release.ps1` 末尾追加调用 `website/tools/deploy.ps1`，发 APK 顺手发官网（作为后续优化，不阻塞首版）

## 6. 内容一致性规则（红线）

| 内容 | 唯一源 | 更新方式 |
|---|---|---|
| 版本号 / 下载链接 | GitHub Releases latest（兜底 build.gradle.kts） | `sync.ps1` 注入 |
| 更新日志 | Releases ⊕ `assets/html/about.html` | `sync.ps1` 合并 |
| 隐私政策 | 根 `PRIVACY_POLICY.md` | 手工镜像到 privacy.html，政策改版时同步 |
| 功能 / FAQ 文案 | `README.md` + `about.html` | 首版写定，大版本人工核对 |
| 免责声明 / 致谢 / 邮箱 / 仓库链接 | `README.md` | 首版写定 |

**表述红线**（每个官网功能句都要能在仓库文件里找到出处）：

- 考试安排 = 「应用内登录**正方教务**」（不写死具体学校，README 的校园本地化表述以正方教务为准）
- 宿舍开门 = 「登录一次后日常开门**不再联网**、不再重复登录」（隐私政策 §二原文边界）
- 洗衣房 = 「顺水平台**只读查询**、无需登录、不上传；下单跳官方小程序」
- 隐私横幅 = 「数据只留在手机上 / 本地加密 / 无广告无埋点 / 卸载即删除」——**不得**写「完全不联网 / 零权限」（本 App 需要 INTERNET）
- 免责 = 逆向接口可能失效、与教务厂商/云莓/顺水官方无关联（镜像 README）
- 示例数据全为占位（假课表、假楼栋），**不出现任何真实个人信息**；服务器凭据、真实姓名/学号等不入库

**首版写定的功能事实清单**（已对仓库核对）：200+ 学校、正方/超星/青果/URP 通用适配器、底栏 4 tab（课表/今日/校园/我的）、小组件 = 课程 2×2/3×2/4×2（今明双栏）＋洗衣房 2×1、提醒课前 5/10/15/20 分钟、日历双日历「不挂科课表」/「不挂科考试」、Android 8.0+、arm64-v8a、MIT © 2026 咸鱼 (salt-fishes)、反馈 = GitHub Issues + xunguang255@163.com、致谢 = shiguangschedule (Apache-2.0) / shiguang_warehouse (MIT) / yunmei_unintelligent (MIT)。

## 7. 测试与验收

1. 桌面 1440px / 移动 390px 两档过四页：导航互通、锚点可达、无横向溢出
2. 链接检查：Releases / Issues / 拾光适配仓库 / 致谢仓库 / mailto 全部有效
3. `sync.ps1` 双路径：正常拉取产出 changelog.json；断网时走快照兜底（拔线模拟）
4. 动画：模拟 `prefers-reduced-motion` 全关；滚动全程只动 transform/opacity 不掉帧
5. 部署后：`curl` 首页 200 + 关键资源可达；手机真机访问走一遍下载按钮（直达 Releases）

## 8. 实施范围外（明确不做）

- 深色模式、多语言、i18n
- 域名 / HTTPS（预留：换域名只改 nginx 与链接常量）
- 运行时 GitHub API、评论区、访问统计（与 App「无埋点」气质一致，官网也不加第三方统计）
- PWA / Service Worker

# 不挂科课表 · 官网实施计划

> 日期：2026-09-23　依据：`2026-09-23-buguake-website-design.md`（已过审）
> 原则：每阶段独立可验收；内容一律对仓库文件（README / about.html / PRIVACY_POLICY.md），红线见设计文档 §6

## 阶段 0 · 脚手架与素材（半天内）

- [ ] 建 `website/` 目录结构（设计文档 §2 的树）
- [ ] 应用图标入 `assets/img/`：从 `ComposeApp/app/src/main/res/mipmap-*/ic_launcher.png` 取最大尺寸 + 生成 favicon（192/512、favicon.ico）
- [ ] 下载 anime.js v4 `anime.min.js` 入 `assets/js/`（开发时一次性下载后提交，运行期零外链）
- [ ] `gitignore` 不需要新条目（`data/changelog.json` 要入库）

**验收**：目录齐全，`data/` 空目录有占位。

## 阶段 1 · 视觉基座 + index 骨架

- [ ] `assets/css/main.css`：设计文档 §3 全部 token（色板/贴纸边框硬阴影/圆角/字体/响应式断点），贴纸卡、荧光高亮、按钮、徽章四个基础组件
- [ ] `index.html`：导航 + 全部区块**空壳**（每节先放占位标题），锚点打通：features/privacy/download/changelog/faq/contribute/about

**验收**：1440px 与 390px 下无横向溢出，锚点跳转可达，纯灰盒也层次正确。

## 阶段 2 · index 内容（对齐真实文件）

- [ ] 按归档 mockup 逐区块填内容：Hero（右手机贴纸 + 两悬浮卡，含连堂纵向贯通）→ 三大特色 → 8 功能卡 → 隐私横幅 → 下载区 → 更新日志/FAQ 双栏 → 参与贡献 → 免责 → 致谢 → 页脚
- [ ] 每句功能文案对照设计文档 §6「事实清单」逐条勾核
- [ ] 下载区/更新日志预览留 `data-version` / `data-changelog` 挂载点，待阶段 5 注入

**验收**：文案核对清单全过；mockup 与实页并排观感一致。

## 阶段 3 · 三个子页

- [ ] `faq.html`：8–10 条（设计文档 §2 话题列表），贴纸折叠卡
- [ ] `privacy.html`：镜像 `PRIVACY_POLICY.md`（手工转 HTML，保留权限表格），页脚注明版本日期
- [ ] `changelog.html`：读 `data/changelog.json` 渲染（时间线组件），首版先手填快照

**验收**：四页导航互通；隐私页与 md 逐段 diff 无语义差异。

## 阶段 4 · 动画（anime.js）

- [ ] `assets/js/main.js` 实现设计文档 §4 六项：Hero 入场 / 滚动揭示（IntersectionObserver 一次性）/ 悬停 / count-up / 时间线 draw-in / reduced-motion 全关
- [ ] 动画类与内容分离（`.anim-*`），JS 失败时页面完整可读

**验收**：DevTools 模拟 reduced-motion 全静止；Performance 滚动只出现 transform/opacity 合成层动画。

## 阶段 5 · 同步与部署脚本

- [ ] `tools/sync.ps1`：`gh api repos/salt-fishes/buguake-timetable/releases` ⊕ 解析 `assets/html/about.html` verlist → 合并（Releases 优先）→ 写 `data/changelog.json` → 把 latest 版本号注入 index/changelog 挂载点；失败打印警告、沿用旧快照、退出码 0
- [ ] `tools/deploy.ps1`：先跑 sync，再 `scp -r` 五类文件到服务器（目标地址/目录来自本地参数或环境变量，**不写死进仓库**）；首次部署说明：配 SSH 公钥（密码不入库）、探测/创建 nginx root
- [ ] （可选，单独提交）`release.ps1` 末尾挂 deploy 调用

**验收**：断网跑 `sync.ps1` 走快照成功；联网跑产出合并后的 changelog.json 且含 v1.3/v1.2/v1.0 等无 Release 版本。

## 阶段 6 · 测试与上线

- [ ] 设计文档 §7 五项全过：双档视口、链接检查、sync 双路径、动画可达性、部署后 curl + 真机下载走通
- [ ] 首次上线：配 nginx（gzip、静态 root）→ `deploy.ps1` → 手机访问 `http://<服务器IP>/` 走完「看到 → 点下载 → 落到 Releases」主路径
- [ ] 上线后在 README 加官网链接（单独小提交）

**验收**：主转化路径真机走通；`gh release` 发下一个版本时跑一次 `deploy.ps1` 即完成内容更新。

## 顺序与依赖

0 → 1 → 2 可与 3 并行；4 依赖 1/2；5 独立可在 2 后开始；6 收口。
首次服务器配置（SSH 公钥 + nginx）插在 5 与 6 之间，需你配合一次性操作。

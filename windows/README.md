# Pythontopia Windows 桌面端

面向高校 Python 初学者的 **原生 Windows 桌面客户端**：`.NET 8` + `WinUI 3 (Windows App SDK)` 负责界面，`MonoGame (DesktopGL)` 负责像素小镇渲染。**不是 Electron、不是 WebView**，纯 C# 原生实现，直接对接现有 Spring Boot 后端。

## 解决方案结构

```
windows/
├── Pythontopia.sln
├── Directory.Build.props        # Nullable / LangVersion latest / ImplicitUsings
├── .gitignore
├── README.md
├── src/
    ├── Pythontopia.Core/        # net8.0 类库（跨平台，WSL 互操作可构建）
    │   ├── Config/AppConfig.cs              # BackendBaseUrl，默认公网 ECS http://47.120.29.62:18080，可用 PYT_BACKEND_URL 覆盖
    │   ├── Json/PytJson.cs                  # System.Text.Json，camelCase
    │   ├── Dto/*.cs                         # 与后端 record 1:1 的 C# record
    │   ├── Api/ApiException.cs              # 解析 {code,message,details,traceId,timestamp}
    │   ├── Api/IApiClient.cs / ApiClient.cs # HttpClient；注入 Bearer；SubmitAndPollAsync；AskTutorStreamAsync(SSE)
    │   ├── Api/SseParser.cs                 # 可单测的 SSE 帧解析器（ApiClient 流式委托给它）
    │   ├── Auth/SessionService.cs           # 登录/注册/登出，token 持久化到 %LOCALAPPDATA%/Pythontopia/session.json
    │   └── Expedition/*.cs                  # 无尽远征 Roguelike 纯逻辑（模型/配置/KC 名/地图生成/前沿选择/run 引擎，无 I/O，可单测）
    ├── Pythontopia.Game/        # net8.0 MonoGame DesktopGL（跨平台，WSL 互操作可构建）
    │   ├── TownGame.cs                      # 加载 town_hub.json + tilemap_packed.png，渲染地面/建筑/装饰层 + 玩家 + NPC + HUD
    │   ├── Tiled/TiledMap.cs                # Tiled JSON 解析
    │   ├── Entities/*.cs                    # Player（瓦片网格移动）/ NPC / 建筑
    │   ├── Rendering/PixelFont.cs           # 自带 5x7 位图字体（不依赖内容管线）
    │   └── Assets/                          # 从前端拷贝的地图与瓦片（构建期内置，运行期不引用前端）
    │       ├── maps/town_hub.json
    │       └── tilesets/tilemap_packed.png  (+ Kenney License)
    └── Pythontopia.App/         # net8.0-windows10.0.19041.0 WinUI 3（需 Windows 构建）
        ├── App.xaml(.cs)                    # DI + 品牌主题资源（Brand* 画刷 / PytCardBorderStyle）
        ├── MainWindow.xaml(.cs)             # 原生外壳：Mica 背景 + 自定义标题栏 + NavigationView + 登录门禁
        ├── Views/*.xaml(.cs)                # Login / Guide / Mainline / Path / Profile / Quests / Course / Examples / Library / Bilibili / Practice / ChapterTest / Feynman / Workshop / Media / BugMine / Boss / Endless / Expedition / ReviewHall / Insights / Classroom / Calendar / Saves / Skills / Npc / Town / Lecture / ProfileRoom / Graduation / Credits
        ├── ViewModels/*.cs                  # MVVM（CommunityToolkit.Mvvm）
        └── Converters/Converters.cs
└── tests/
    └── Pythontopia.Core.Tests/  # net8.0 xUnit（133 个单测；跨平台，WSL 互操作可运行）
```

## 先决条件

- Windows 10 (1809 / 17763) 或 Windows 11。
- **.NET 8 SDK**（本仓库使用 8.0.420 验证）。
- Windows 10 SDK / Build Tools —— 由 NuGet 包 `Microsoft.Windows.SDK.BuildTools` 自动还原。
- **无需 `dotnet workload install`**：未打包（unpackaged）WinUI 3 走 NuGet（`Microsoft.WindowsAppSDK`），不需要 MAUI/WinUI workload；但 WinUI 的 XAML 编译只能在 **Windows** 上进行。
- 可选：Visual Studio 2022（17.9+），含 “.NET 桌面开发” + “Windows App SDK C#” 组件，可直接打开 `Pythontopia.sln` 并 F5。

## 后端地址配置

- 默认指向**公网** Pythontopia 后端（阿里云 ECS）：`http://47.120.29.62:18080`，开箱即用。
- 本地联调可用 `PYT_BACKEND_URL` 覆盖为 `http://localhost:18080`（对 App 与 Game 进程同时生效）：

```powershell
setx PYT_BACKEND_URL "http://localhost:18080"   # 永久
# 或当前会话：
$env:PYT_BACKEND_URL = "http://localhost:18080"
```

- 像素小镇进程额外读取 `PYT_TOKEN`（由 App 启动时自动注入），用于叠加真实世界快照。

## 构建（在 Windows 上）

```powershell
cd windows

# 方式 A：整解决方案
dotnet build Pythontopia.sln -c Debug

# 方式 B：逐项目
dotnet build src/Pythontopia.Core/Pythontopia.Core.csproj -c Debug
dotnet build src/Pythontopia.Game/Pythontopia.Game.csproj -c Debug
dotnet build src/Pythontopia.App/Pythontopia.App.csproj  -c Debug -p:Platform=x64
```

或在 Visual Studio 2022 打开 `Pythontopia.sln`，把启动项目设为 `Pythontopia.App`，平台选 `x64`，F5。

## 运行（在 Windows 上）

WinUI 主程序：

```powershell
dotnet run --project src/Pythontopia.App/Pythontopia.App.csproj -p:Platform=x64
```

像素小镇（也可独立运行，便于调试）：

```powershell
dotnet run --project src/Pythontopia.Game/Pythontopia.Game.csproj
# 或先构建后直接运行：src/Pythontopia.Game/bin/Debug/net8.0/Pythontopia.Game.exe
```

发布完全自包含的 App（产出可直接双击运行、无需预装 .NET / Windows App SDK 的 .exe 目录）：

```powershell
dotnet publish src/Pythontopia.App/Pythontopia.App.csproj -c Release -p:Platform=x64 -r win-x64 -p:SelfContained=true
```

- 产物目录：`src/Pythontopia.App/bin/x64/Release/net8.0-windows10.0.19041.0/win-x64/publish/`，入口 `Pythontopia.App.exe`。
- 自包含：`coreclr.dll`（.NET 运行时）+ `Microsoft.WinUI.dll` / `Microsoft.WindowsAppRuntime*`（Windows App SDK，`WindowsAppSDKSelfContained=true`）一并打包，约 350 个文件、~162 MB，整目录拷贝到任意 Win10/11 机器即可运行。
- 本仓库已在 WSL 互操作 dotnet.exe 下验证该 publish 成功产出上述 .exe 目录（不在 Windows 运行期内验证）。

### 已部署（在线下载）

加固后的自包含包（R2R + 剥离 PDB）已打包为 `windows/dist/pythontopia-windows.zip`（本地，未入库）并上传到生产 ECS 的 Nginx 直出目录 `/opt/pythontopia/downloads/`：

- 下载地址：`http://47.120.29.62/downloads/pythontopia-windows.zip`（HTTP 200，`application/zip`，约 74MB，支持断点续传）
- 校验：`http://47.120.29.62/downloads/pythontopia-windows.zip.sha256`（SHA-256 `351f91a3…824e2`，上传后本地⟷远端比对一致）
- 用法：下载 zip → 解压到任意目录 → 运行 `Pythontopia.App.exe`（自包含，无需预装 .NET / Windows App SDK）。
- **guide 页接线（待前端发版）**：`guideSections.ts` 中 Windows 项 `href` 应为 `/downloads/pythontopia-windows.zip`（产物是自包含目录的 zip，非单 exe）；下载计数复用既有 `POST /api/downloads/{key}/hit` + `GET /api/downloads/{key}`（key 如 `windows_exe`，首次点击 UPSERT 自动建行）。

### 像素小镇操作

- `WASD` / 方向键：按瓦片网格移动（带补间动画）。
- `E`：靠近 NPC / 建筑时交互（顶部出现提示）。
- `ESC`：退出小镇窗口。
- 底部 HUD 显示地图、当前瓦片坐标，登录态下叠加任务数 / 天数 / 天气。

## 在本机（Linux / WSL）能构建什么

本仓库在 **WSL 下用 Windows 互操作 SDK**（`/mnt/c/Program Files/dotnet/dotnet.exe`，8.0.420）验证：

| 项目 | 目标框架 | 本机（WSL 互操作）结果 |
|---|---|---|
| Pythontopia.Core | net8.0 | ✅ 构建通过（0 警告 0 错误） |
| Pythontopia.Game | net8.0 (DesktopGL) | ✅ 构建通过（仅 “No Content References” 提示，见下） |
| Pythontopia.App | net8.0-windows10.0.19041.0 | ✅ 本次互操作下也构建通过（XAML 已编译）；**正式开发/运行仍建议在 Windows** |
| Pythontopia.Core.Tests | net8.0 (xUnit) | ✅ `dotnet test` 通过：133 个测试全绿 |

> WinUI 的 XAML 编译依赖 Windows 专用工具链；本机能过是因为调用的是 Windows 侧 `dotnet.exe`。若在纯 Linux .NET 上则无法构建 App。

### 关于 MonoGame 内容管线

Game 通过 `Texture2D.FromStream` + 直接读取 JSON 在**运行期**加载 `Assets/`（`CopyToOutputDirectory=PreserveNewest`），**不使用** MGCB/`.xnb` 内容管线。因此构建时 `MonoGame.Content.Builder.Task` 会提示 `No Content References Found`——这是**预期**的、无害的。

## 测试与 CI

`Pythontopia.Core.Tests`（net8.0 / xUnit）覆盖：SSE 帧解析（`SseParser`）、错误信封解析（`ApiException.FromResponse`）、配置解析（`AppConfig`）、`ApiClient`（Bearer 鉴权头 / 错误码映射 / 204 空响应 / 网络失败 / 查询串拼装 / 提交轮询，使用桩 `HttpMessageHandler`）、`SessionService`（登录/登出/持久化回载/过期判定，使用临时会话文件）。Core 与测试均为纯 net8.0，可在 WSL 互操作 dotnet 下运行：

```powershell
dotnet test tests/Pythontopia.Core.Tests/Pythontopia.Core.Tests.csproj -c Debug
# 已通过：失败 0，通过 133，跳过 0
```

GitHub Actions `.github/workflows/windows.yml` 在 `windows-latest` 上构建 Core / Game / WinUI App（x64）并运行上述测试，仅当 `windows/**` 或该工作流变更时触发。

## 已接线的后端端点（前缀 /api，JWT Bearer，401 触发重新登录）

- 认证：`POST /auth/login`、`POST /auth/register`
- 小镇：`POST /game/bootstrap`
- 主线地图（新增，对齐 web `MainlineMapPage`）：`GET /mainline/overview`（章 / 关卡 + 看学练进度 + 星数，`MainlinePage` 只读渲染，章节用 Expander 折叠）
- 复盘大厅（新增，对齐 web `ReviewHallPage`）：`GET /learner/twin/weekly`（数字孪生周报）、`GET /learner/oracle/risk`（遗忘/失败风险预测）
- 章节测验（新增，对齐 web `ChapterTestPage`）：`GET /mainline/chapters/{chapter}/test/latest`（某章最近一次大测试判分，只读）
- 主线关卡详情（新增，对齐 web `StageLearnPage`）：`GET /mainline/stages/{id}`（视频/例题/练习 + 看学练进度，主线页内详情面板）
- 例题库（新增，对齐 web `ExampleListPage`）：`GET /course/examples`、`GET /course/examples/{id}`
- 世界日历（新增）：`GET /world/calendar/today`、`GET /world/calendar/events`
- 学情洞察（新增）：`GET /learner/insights`、`GET /learner/memory`（仅学生，反思洞察 + 记忆笔记本）
- B 站精选（新增）：`GET /videos/bilibili?keyword=`（关键词检索外部讲解视频卡片）
- 存档（新增）：`GET /world/saves`、`POST /world/saves/{slotIndex}/activate`（RPG 存档槽列表 + 激活）
- 主线看/学/练打点（新增）：`POST /mainline/stages/{id}/video-progress`、`.../pages-read`（关卡详情面板内打点 + 刷新进度）
- 费曼学弟（新增，对齐 web `FeynmanPanel`）：`POST /feynman/sessions`、`.../teach`、`.../judge`（teach-back 讲解 + 评判）
- Boss 副本（新增，对齐 web `BossPage`）：`POST /world/boss/{chapter}/problem`、`.../attempt`（取题 + 服务端判题）
- 章测作答闭环（新增，对齐 web `ChapterTestPage`）：`POST .../test/prepare|start|{testId}/submit`（出卷 → 答题 → 判分）
- 无尽模式（新增，对齐 web `EndlessPage`）：复用 `POST /world/boss/{chapter}/problem|attempt` + `POST /mainline/endless/summary`（连续闯关 + 结算补星）
- 无尽远征 Roguelike（新增，对齐 web `EndlessExpeditionPage`）：`POST /world/boss/select-frontier`（知识前沿候选，失败回退本地选择器）、`GET|PUT|DELETE /world/expedition/run`（run 快照跨会话续局/弃局）；分层 DAG 地图与玩法在 `Pythontopia.Core/Expedition` 纯逻辑里生成与推进，战斗节点复用 boss problem/attempt 真判题、达标复用 endless/summary 补星
- 技能树（新增，对齐 web 技能）：`GET /world/skills`、`POST /world/skills/{key}/unlock`（节点列表 + 解锁状态 + 解锁）
- NPC 互动（新增，对齐 web NPC）：`GET /npc/{key}/greeting`、`POST /npc/{key}/dialog`（NPC 名册取自静态 town_hub 目录）
- 课件库 PDF（新增，对齐 web `LibraryPage`）：`GET /course/documents/{id}/pdf`（携 JWT 取字节 → 临时文件 → WebView2 渲染）
- 学习路径（新增，对齐 web `PathTreeTab`）：`GET /learner/path/active`、`POST /learner/path/plan`（个性化路径 + 重新规划）
- AI 配图 / 故事板短视频（新增，对齐 web 媒体）：`POST /media/image`（按 prompt 生成插图）、`POST /media/storyboard/video`（图序列 + TTS 经 ffmpeg 合成 mp4；无 ffmpeg 后端抛 VIDEO_COMPOSE_UNAVAILABLE，前端提示降级）；`/media/assets/{id}` 匿名直链供 `Image` / `MediaPlayerElement` 显示
- 视频讲解（新增，对齐 web `LecturePage`）：`GET /course/videos?chapterId=`（章节小节列表）+ `GET /course/videos/{id}`（公开流，`MediaPlayerElement` 播放）；无在线视频时回退 `GET /videos/bilibili?keyword=` B 站精选
- 毕业典礼（新增，对齐 web `GraduationPage`）：`GET /mainline/overview` + `GET /learner/mastery`（7 章全 ≥1★ 判定毕业，未达成显示剩余条件，只读）
- 学习档案室（新增，对齐 web `ProfileRoomPage`）：聚合 `GET /learner/profile` + `/profile/dimensions` + `/twin/weekly` + `/memory`（画像叙述 + 8 维 + 周报 + Agent 记忆），并内嵌「画像访谈」
- 画像访谈（新增，对齐 web `ProfileInterviewPanel`）：`POST /learner/profile/interview`、`.../{id}/turn`、`.../{id}/finish`（ProfileAgent 多轮对话生成 8 维画像，完成后刷新档案）
- 语音听写 ASR（新增，对齐 web `/media/asr`）：`POST /media/asr`（multipart 上传音频 → 转文字；档案室外置于「AI 配图」页，选音频文件转写）
- 异步视频合成作业（新增，对齐 web 视频工坊）：`POST /media/storyboard/video/jobs`（入队，命中幂等缓存直给 assetId）、`GET /media/jobs/{id}`（轮询进度）、`GET /media/jobs/{id}/stream`（SSE plan/trace/result/error）
- 画像/掌握度：`GET /learner/profile`(204=无)、`GET /learner/profile/dimensions`、`PUT /learner/profile/dimensions/{key}`、`GET /learner/mastery`
- 任务：`GET /quests/today`、`GET /quests?status=&limit=`、`POST /quests/{id}/start|complete|skip`
- 课程：`GET /course/pack|chapters|kcs?chapterId|pages?kcCode|problems?kcCode|videos?chapterId`、`GET /course/pages/{id}/image`、`GET /course/videos/{id}`
- 题目/判题：`GET /problems/{id}/public`、`POST /submissions`、`GET /submissions/{id}`（`SubmitAndPollAsync` 轮询至 PASSED/FAILED/ERROR；`PollSubmissionAsync` 跟进外部创建的提交）
- 班级：`GET /classrooms/mine`、`POST /classrooms/join`、`GET /classroom/quests/mine`
- 导师：`POST /tutor/ask`（SSE：plan/trace/token/result/error，`AskTutorStreamAsync` 暴露为 `IAsyncEnumerable<string>`）
- 百晓工坊（`WorkshopPage`）：`POST /intent/route`（意图分类分派）、`POST /workshop/resources`（非流式生成）+ `GET /workshop/resources/{id}`、`GET /workshop/resources?type=&kcCode=`、`POST /workshop/resources/stream`（SSE）、`POST /orchestrator/turn/stream`（学习总管 SSE，`StreamOrchestratorTurnAsync`）。ASK→导师 SSE；REVIEW→Bug 矿洞；6 类资源→工坊生成
- Bug 矿洞（`BugMinePage`）：`GET /bug-mine/errorbook`、`GET /bug-mine/echoes`、`POST /bug-mine/echoes`、`GET /bug-mine/sessions/{id}`、`POST .../cards/{cardId}/reveal`、`POST .../retry-submit`、`POST .../variant-submit`、`POST .../close?status=`（错题/遗忘回声列表 + 5 层逐解锁卡片 + 原题/变式重试服务端判题）

## 功能对齐清单（对标 web `frontend/`，分批推进）

目标：把 web 端全部学生功能逐步搬到 Windows 端。下表追踪进度（教师端与纯运营页暂不在学生客户端范围内）。

**已对齐**：登录/注册 · 像素小镇(town_hub) · 学习画像(8 维 + 掌握度) · 任务公告板 · 课程(章/KC/课件页+图) · 训练场(服务端判题) · 百晓工坊(意图路由/答疑/资源/学习总管 SSE) · Bug 矿洞 · 班级(加入/班级任务) · **主线地图** · **主线关卡详情** · **主线看学练打点** · **复盘大厅(周报+风险)** · **章节测验(最近成绩)** · **章测作答闭环** · **例题库** · **世界日历** · **学情洞察(洞察+记忆)** · **B站精选视频** · **存档(列表+激活)** · **费曼学弟(teach-back)** · **Boss 副本** · **无尽模式** · **无尽远征(Roguelike：知识前沿分支地图 + 像素远征塔/列表双视图 + 跨会话续局)** · **技能树** · **NPC 互动** · **课件库 PDF** · **学习路径** · **新手引导** · **AI 配图** · **故事板短视频** · **视频讲解** · **毕业典礼** · **学习档案室** · **画像访谈(ProfileAgent)** · **语音听写(ASR)** · **异步视频合成作业** · **致谢**。

**待对齐（后续批次）**：
- ASR 录音输入：当前以「选择音频文件转写」接入 `POST /media/asr`；浏览器式实时录音（MediaCapture 麦克风采集）待 Windows 运行期补齐（需麦克风权限，无法在 WSL 无头验证）。
- 仅教师/管理员（学生客户端不适用）：RAG 课件直查 `/api/rag/query`、教师班级看板 `/api/teacher/*`

## 设计说明 / 后续项

- **真实数据**：所有数据均来自后端真实接口，无 mock。
- **安全**：缓存的 JWT 以 Windows DPAPI（CurrentUser 作用域 + 应用熵）加密落盘（`session.json`），离 Windows 时降级明文；媒资 URL 一律相对后端 host 解析，不加载服务端返回的外部 host；API 路径参数（npcKey/skillKey/kcCode 等）均经 `Uri.EscapeDataString`。**待改进**：默认后端为 `http://`（建议上线切 HTTPS，否则 Bearer 经明文传输）；小镇子进程经 `PYT_TOKEN` 环境变量接收令牌（同用户本地 IPC，建议后续改命名管道/一次性句柄）。
- **小镇嵌入（决策）**：小镇以独立窗口（独立进程）从 `TownPage` 启动，`TownViewModel` 现做单实例生命周期管理（运行中禁用启动按钮、进程退出后自动复位状态）。**真正的窗口内嵌**需把 `Pythontopia.Game` 的 MonoGame 后端从 `DesktopGL`（SDL/OpenGL，自带窗口）切到 `WindowsDX` + `SwapChainPanel`——这会让 Game 变为**仅 Windows 可构建**，破坏当前「Core/Game 跨平台、WSL 互操作可构建」的设计属性，且无法在本机运行期验证；故保留为需在 Windows 上落地与回归验证的显式后续决策，不在本次盲改。
- **无尽远征像素塔（决策）**：`/expedition` 的「像素远征塔」用 **WinUI 原生 `Canvas`**（`ExpeditionPage` code-behind 按 run 引擎的节点图实时绘制节点按钮 + 连边，layer 0 在底、BOSS 在顶）实现，与页内 run 状态同进程联动；顶部「🗼 像素塔 / ☰ 列表」双视图（与 web 一致，列表为交互真源）。未采用 MonoGame 内嵌（同「小镇嵌入」理由：WindowsDX + SwapChainPanel 会破坏 Game 跨平台构建且无法本机验证）。
- **原生外壳**：`MainWindow` 启用 Mica 系统背景（`MicaBackdrop`，Win11 生效、旧系统优雅降级）+ `ExtendsContentIntoTitleBar` 自定义标题栏（品牌徽标），并按导航项设置页头标题、在导航面板展示当前用户。品牌色板与卡片样式集中在 `App.xaml`（`BrandPrimaryBrush` / `BrandHeaderBrush` / `PytCardBorderStyle`）。
- **Windows 原生适配**：启动时按主显示器工作区居中（`DisplayArea` + `AppWindow.Move`）；`app.manifest` 声明 PerMonitorV2 DPI 感知；`.csproj` 写入产品/版本元数据与 `NeutralLanguage=zh-CN`；`ApplicationIcon=Assets\app.ico`（7 档分辨率 16–256，嵌入 .exe）；未打包自包含发布（`WindowsPackageType=None` + `WindowsAppSDKSelfContained` + `SelfContained`）。
- **HUD 字体**：内置 ASCII 5x7 位图字体，HUD/提示使用对象的 ASCII 标识（如 `npc_mayor_py`、`/profile-room`）；地图里的中文 `label` 不由该字体渲染。
- **导师 SSE**：`ApiClient.AskTutorStreamAsync` 已实现，并由 `WorkshopPage` 的 ASK 意图分派消费（逐 `delta` 拼接答案）。
- **SSE 复用**：`tutor/ask`、`workshop/resources/stream`、`orchestrator/turn/stream` 共用一个 `StreamEventsAsync` 解析器；`AskTutorStreamAsync` 产出 `data` 字符串，资源/总管流产出带事件名的 `SseEvent`。
- **资源渲染**：`WorkshopPage` 当前用非流式 `POST /workshop/resources` + `GET /workshop/resources/{id}` 取回并渲染（markdown / mermaid / struct json 文本）；流式 `StreamResourceAsync` 已在客户端暴露备用。

## 安全加固（防反编译/破解）

- **客户端无判题/答案逻辑**：判题、AI 出题、防幻觉审查、隐藏测试全在服务端（后端权威）；反编译 Windows 客户端只暴露 API 调用形状，无可窃取的业务机密、答案或隐藏用例 —— 这是最根本的「破解无收益」设计。
- **Release 剥离符号**：`.csproj`（App 与 Core）在 `Configuration=Release` 下 `DebugType=none` / `DebugSymbols=false`，发布产物不含 `.pdb`，去掉方法/局部名等调试元数据。
- **ReadyToRun 原生预编译**：Release 发布 `PublishReadyToRun=true`，IL 预编译为 win-x64 原生映像（`Pythontopia.App.dll` ~1.9MB / `Core.dll` ~0.9MB 含原生码），冷启动更快、IL 不再以「干净托管形态」直接示人，提高反编译门槛。
- **代码签名（建议）**：分发前用企业证书做 Authenticode 签名（`signtool`），可防篡改并减少 SmartScreen 拦截（需自备证书）。
- **JWT 落盘加密**：缓存令牌以 Windows DPAPI（CurrentUser + 应用熵）加密（见 `SessionService`），非明文。
- **可选更强对抗**（需在 Windows 上回归验证，本机无法验证 GUI/运行期）：对 `Pythontopia.Core.dll` / `Pythontopia.App.dll` 运行 ConfuserEx 或 Obfuscar 做符号混淆 —— 必须排除：所有 `Dto/*`（System.Text.Json 按属性名映射）、`Views/*` 与 `ViewModels/*`（WinUI x:Bind / XAML 类型解析）、DI 按类型解析的类型，否则会破坏 XAML/序列化。.NET IL 本质可反编译，混淆只提高门槛，不等于不可破解。

## 资源与许可

地图瓦片 `tilemap_packed.png` 来自 Kenney Tiny Town（见 `src/Pythontopia.Game/Assets/tilesets/kenney-tiny-town-License.txt`）。代码层 MIT；像素资产遵循各自 CC0 / CC-BY / CC-BY-SA / OFL 许可，详见仓库根 `LICENSE-ART.md`。

应用图标 `src/Pythontopia.App/Assets/app.ico` 为本项目原创品牌图标（AI 生成的像素风 Python 学习小镇徽标，非任何商业像素游戏官方素材），用于 .exe 与窗口图标。

# Pythontopia Windows 桌面端

面向高校 Python 初学者的 **原生 Windows 桌面客户端**：`.NET 8` + `WinUI 3 (Windows App SDK)` 负责界面，`MonoGame (DesktopGL)` 负责像素小镇渲染。**不是 Electron、不是 WebView**，纯 C# 原生实现，直接对接现有 Spring Boot 后端。

## 解决方案结构

```
windows/
├── Pythontopia.sln
├── Directory.Build.props        # Nullable / LangVersion latest / ImplicitUsings
├── .gitignore
├── README.md
└── src/
    ├── Pythontopia.Core/        # net8.0 类库（跨平台，WSL 互操作可构建）
    │   ├── Config/AppConfig.cs              # BackendBaseUrl，默认 http://localhost:18080，可用 PYT_BACKEND_URL 覆盖
    │   ├── Json/PytJson.cs                  # System.Text.Json，camelCase
    │   ├── Dto/*.cs                         # 与后端 record 1:1 的 C# record
    │   ├── Api/ApiException.cs              # 解析 {code,message,details,traceId,timestamp}
    │   ├── Api/IApiClient.cs / ApiClient.cs # HttpClient；注入 Bearer；SubmitAndPollAsync；AskTutorStreamAsync(SSE)
    │   └── Auth/SessionService.cs           # 登录/注册/登出，token 持久化到 %LOCALAPPDATA%/Pythontopia/session.json
    ├── Pythontopia.Game/        # net8.0 MonoGame DesktopGL（跨平台，WSL 互操作可构建）
    │   ├── TownGame.cs                      # 加载 town_hub.json + tilemap_packed.png，渲染地面/建筑/装饰层 + 玩家 + NPC + HUD
    │   ├── Tiled/TiledMap.cs                # Tiled JSON 解析
    │   ├── Entities/*.cs                    # Player（瓦片网格移动）/ NPC / 建筑
    │   ├── Rendering/PixelFont.cs           # 自带 5x7 位图字体（不依赖内容管线）
    │   └── Assets/                          # 从前端拷贝的地图与瓦片（构建期内置，运行期不引用前端）
    │       ├── maps/town_hub.json
    │       └── tilesets/tilemap_packed.png  (+ Kenney License)
    └── Pythontopia.App/         # net8.0-windows10.0.19041.0 WinUI 3（需 Windows 构建）
        ├── App.xaml(.cs)                    # DI（Microsoft.Extensions.DependencyInjection）
        ├── MainWindow.xaml(.cs)             # NavigationView 外壳 + 登录门禁
        ├── Views/*.xaml(.cs)                # Login / Profile / Quests / Course / Practice / Workshop / BugMine / Classroom / Town
        ├── ViewModels/*.cs                  # MVVM（CommunityToolkit.Mvvm）
        └── Converters/Converters.cs
```

## 先决条件

- Windows 10 (1809 / 17763) 或 Windows 11。
- **.NET 8 SDK**（本仓库使用 8.0.420 验证）。
- Windows 10 SDK / Build Tools —— 由 NuGet 包 `Microsoft.Windows.SDK.BuildTools` 自动还原。
- **无需 `dotnet workload install`**：未打包（unpackaged）WinUI 3 走 NuGet（`Microsoft.WindowsAppSDK`），不需要 MAUI/WinUI workload；但 WinUI 的 XAML 编译只能在 **Windows** 上进行。
- 可选：Visual Studio 2022（17.9+），含 “.NET 桌面开发” + “Windows App SDK C#” 组件，可直接打开 `Pythontopia.sln` 并 F5。

## 后端地址配置

- 默认 `http://localhost:18080`。
- 覆盖方式（对 App 与 Game 进程同时生效）：

```powershell
setx PYT_BACKEND_URL "http://192.168.1.10:18080"   # 永久
# 或当前会话：
$env:PYT_BACKEND_URL = "http://192.168.1.10:18080"
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

发布自包含的 App（产出可直接分发的 .exe 目录）：

```powershell
dotnet publish src/Pythontopia.App/Pythontopia.App.csproj -c Release -p:Platform=x64 -r win-x64
```

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

> WinUI 的 XAML 编译依赖 Windows 专用工具链；本机能过是因为调用的是 Windows 侧 `dotnet.exe`。若在纯 Linux .NET 上则无法构建 App。

### 关于 MonoGame 内容管线

Game 通过 `Texture2D.FromStream` + 直接读取 JSON 在**运行期**加载 `Assets/`（`CopyToOutputDirectory=PreserveNewest`），**不使用** MGCB/`.xnb` 内容管线。因此构建时 `MonoGame.Content.Builder.Task` 会提示 `No Content References Found`——这是**预期**的、无害的。

## 已接线的后端端点（前缀 /api，JWT Bearer，401 触发重新登录）

- 认证：`POST /auth/login`、`POST /auth/register`
- 小镇：`POST /game/bootstrap`
- 画像/掌握度：`GET /learner/profile`(204=无)、`GET /learner/profile/dimensions`、`PUT /learner/profile/dimensions/{key}`、`GET /learner/mastery`
- 任务：`GET /quests/today`、`GET /quests?status=&limit=`、`POST /quests/{id}/start|complete|skip`
- 课程：`GET /course/pack|chapters|kcs?chapterId|pages?kcCode|problems?kcCode|videos?chapterId`、`GET /course/pages/{id}/image`、`GET /course/videos/{id}`
- 题目/判题：`GET /problems/{id}/public`、`POST /submissions`、`GET /submissions/{id}`（`SubmitAndPollAsync` 轮询至 PASSED/FAILED/ERROR；`PollSubmissionAsync` 跟进外部创建的提交）
- 班级：`GET /classrooms/mine`、`POST /classrooms/join`、`GET /classroom/quests/mine`
- 导师：`POST /tutor/ask`（SSE：plan/trace/token/result/error，`AskTutorStreamAsync` 暴露为 `IAsyncEnumerable<string>`）
- 百晓工坊（`WorkshopPage`）：`POST /intent/route`（意图分类分派）、`POST /workshop/resources`（非流式生成）+ `GET /workshop/resources/{id}`、`GET /workshop/resources?type=&kcCode=`、`POST /workshop/resources/stream`（SSE）、`POST /orchestrator/turn/stream`（学习总管 SSE，`StreamOrchestratorTurnAsync`）。ASK→导师 SSE；REVIEW→Bug 矿洞；6 类资源→工坊生成
- Bug 矿洞（`BugMinePage`）：`GET /bug-mine/errorbook`、`GET /bug-mine/echoes`、`POST /bug-mine/echoes`、`GET /bug-mine/sessions/{id}`、`POST .../cards/{cardId}/reveal`、`POST .../retry-submit`、`POST .../variant-submit`、`POST .../close?status=`（错题/遗忘回声列表 + 5 层逐解锁卡片 + 原题/变式重试服务端判题）

## 设计说明 / 后续项

- **真实数据**：所有数据均来自后端真实接口，无 mock。
- **小镇嵌入**：MVP 中小镇以独立窗口（独立进程）从 `TownPage` 启动；将 MonoGame 渲染到 WinUI `SwapChainPanel` 内嵌为后续项。
- **HUD 字体**：内置 ASCII 5x7 位图字体，HUD/提示使用对象的 ASCII 标识（如 `npc_mayor_py`、`/profile-room`）；地图里的中文 `label` 不由该字体渲染。
- **导师 SSE**：`ApiClient.AskTutorStreamAsync` 已实现，并由 `WorkshopPage` 的 ASK 意图分派消费（逐 `delta` 拼接答案）。
- **SSE 复用**：`tutor/ask`、`workshop/resources/stream`、`orchestrator/turn/stream` 共用一个 `StreamEventsAsync` 解析器；`AskTutorStreamAsync` 产出 `data` 字符串，资源/总管流产出带事件名的 `SseEvent`。
- **资源渲染**：`WorkshopPage` 当前用非流式 `POST /workshop/resources` + `GET /workshop/resources/{id}` 取回并渲染（markdown / mermaid / struct json 文本）；流式 `StreamResourceAsync` 已在客户端暴露备用。

## 资源与许可

地图瓦片 `tilemap_packed.png` 来自 Kenney Tiny Town（见 `src/Pythontopia.Game/Assets/tilesets/kenney-tiny-town-License.txt`）。代码层 MIT；像素资产遵循各自 CC0 / CC-BY / CC-BY-SA / OFL 许可，详见仓库根 `LICENSE-ART.md`。

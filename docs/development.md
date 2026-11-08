# 安装、启动与排查

## 百分制、剧情条件与任务外日常（2026-11-08）

当前原15176/API18083已更新并健康，迁移头f4b92c71a806；备份 `.tmp/pre-story-enrichment-20261108.sql`。27张旧业务表1769条记录完整字段不变，只有当前演出与迁移头更新。刷新浏览器后体验；没有重置演示账号。独立15177/API18084验收结束后已停止web/API/db，数据卷与产物保留。

脚本作者继续修改YAML：任务与测验结尾在 `backend/content/chapters/chapter01.yaml`，日常在 `backend/content/ambient/town-daily.yaml`。内容格式、条件和可复制节点片段见[内容配置](content-authoring.md)。先在backend运行 `.venv/Scripts/python.exe -m app.chapter_content --check`；当前应为4NPC、3活动、1任务、2演出、9题。新增日常不增加主线任务。格式中的schema_version仍是解析格式，不是内容历史版本。

数据库需要升级到 `f4b92c71a806`，仅将StoryDefinition.quest_id设为可空，允许日常独立于任务；前序AssessmentAttempt与学生数据保留。每次启动仍先迁移再执行app.seed，同ID配置原位更新，不重导入媒体。沿用既有本地预览时，从仓库根使用显式开发override，避免意外关闭已有开发菜单：

```sh
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml -f deploy/compose.dev-tools.yml up --build -d api web
```

测验分数由后端派生，满分100，向下取整；未判完score=null。随机节点仅在新互动中选择，恢复使用已落库的对白node_id，不写世界标记。日常没有教学活动、任务检查点或效果；无授权资源的条件即使嵌套not也不能启用选项。当前NPC有ready教学目标或waiting活动优先于日常，无关NPC的新任务不因闲聊而初始化。

隔离浏览器专项沿用独立15177/API18084、新学生和显式QA教师凭据，设置 `E2E_COURSE_MEDIA_IMPORTED=1`、`E2E_DEV_TOOLS=1`、`PLAYWRIGHT_CHANNEL=chrome`，在frontend运行：

```sh
npx playwright test assessmentPaper.spec.ts gameDesign.spec.ts storyChapter.spec.ts rebornJourney.spec.ts uiUsability.spec.ts worldConfiguration.spec.ts learningRules.spec.ts devTools.spec.ts --grep-invert production --project=desktop --workers=1 --output=.tmp-story-enrichment-e2e
```

storyChapter保留满分分支，rebornJourney使用真实66分交卷，并实际走随机闲聊、相同event重试、刷新恢复、章节180开放后的额外选项和账号隔离。不能通过重置原演示账号验证此流程，真实PG空schema迁移与两次seed报告保存在忽略目录 `.tmp/story-enrichment-pg-validation-20261108.json`。

## 练习、整卷测验与侧栏（2026-11-08）

当前本地入口仍为 `http://127.0.0.1:15176/world`，API18083，使用已验收的同一API/Web镜像更新。数据库升级至 `e682f9a410c7`，只新增 `assessment_attempts`；启动仍执行迁移与当前YAML导入，不重导入媒体。升级前完整SQL备份为忽略目录 `.tmp/pre-assessment-20261108.sql`，更新前后完整字段核对结果见acceptance.md。开发工具override保持显式启用，更新本身未重置任何学生。

本轮独立15177/API18084验收环境已停止web/API/db，数据卷与验收产物保留；原15176/API18083继续运行且健康。

练习使用逐题提交；测验必须通过 `/activities/{id}/assessment` 草稿和 `/assessment/submit` 整卷接口，不能再用单题正式提交接口代替交卷。整卷事务锁定答案，判题故障/未知外部分发等待对账，不盲目重交。详细契约与历史成绩兼容见api-contract.md。`AssessmentAttempt.revision`仅为草稿并发保护，不是内容版本。

浏览器验收使用独立数据库/存储的15177环境及新建学生。设置 `E2E_URL`、`E2E_TEACHER_EMAIL/PASSWORD`、`E2E_COURSE_MEDIA_IMPORTED=1`、`E2E_DEV_TOOLS=1`、`PLAYWRIGHT_CHANNEL=chrome`，在frontend运行：

```sh
npx playwright test assessmentPaper.spec.ts gameDesign.spec.ts storyChapter.spec.ts rebornJourney.spec.ts uiUsability.spec.ts worldConfiguration.spec.ts learningRules.spec.ts devTools.spec.ts --grep-invert production --project=desktop --workers=1 --output=.tmp-assessment-e2e
```

验收用教师可能已有私有TEST支线，主线检查按 `kind=main` 与稳定ID `arrival` 定位，不假定返回数组第一项就是主线。不得删除私有任务来满足测试断言。生产关闭开发工具的专项沿用上一轮独立环境，本轮未重新启动或声称复验。所有侧卡通过共享 `workspaceSidebar` / `SidebarToggle` 折叠，学习分栏可拖向右端或按End收起；不要在各页复制状态或卸载内容以清除草稿。

## 学习目标与目录验收（2026-11-07）

新学习规则见[内容配置](content-authoring.md)，运行前先升级至 `d193b6e408a1`，再执行 YAML 校验与重复导入。迁移只为 `activity_progress` 新增默认空对象的 `media_progress` 字段，既有必读页、任务、页面ID及成绩保持原语义。修改YAML后按既有流程重建API，不需要编辑器。

真实课程媒体的独立QA设置 `E2E_URL`、`E2E_TEACHER_EMAIL/PASSWORD`、`E2E_COURSE_MEDIA_IMPORTED=1`、`PLAYWRIGHT_CHANNEL=chrome`，在frontend运行：

```sh
npx playwright test gameDesign.spec.ts learningRules.spec.ts storyChapter.spec.ts uiUsability.spec.ts --project=desktop --workers=1 --output=.tmp-goals-e2e
```

学习规则专项通过教师API创建新的私有活动、支线验收任务、班级和学生，读取现有真实课件/视频，不增加内置主线、不替换材料、不重置演示账号。覆盖指定页范围、真实视频实际播放、跳播不完成、2倍速按墙钟累计、重点页/片段、服务端进度与任务推进。日志空状态使用新学生“已完成+支线”筛选，不假定QA教师从未创建过其他活动。开发重置专项仍需额外显式 `E2E_DEV_TOOLS=1`；不能把未启用时的跳过算通过。

## 开发工具与独立验收

“重新开始当前班级”只用于明确启用的本地开发环境，会删除当前学生在当前班级的任务、阅读和成绩，并断开客户端助教恢复指针；服务端助教会话、调用审计和预算记录保留。自动化验收不在真实演示账号或原预览数据上试验。默认部署不开放此工具；API 进程必须同时显式设置 `REBORN_ENV=development` 和 `REBORN_DEV_TOOLS=1`，并由服务端给学生返回 `dev_tools_enabled: true`。单独设置一个开关、前端开发构建或使用 localhost 都不会赋予该能力；生产环境始终不注册重置路由。

当前本地15176/API18083已显式叠加开发override，刷新后现有小镇菜单的设置后方显示“开发工具”（仅学生）。启用工具本身没有重置演示账号；用户通过“重新开始当前班级→确认重新开始”自主操作。基础Compose仍为生产模式。独立15177开发QA和15178生产QA验收后均停止、卷保留，原库升级前备份.tmp/pre-dev-tools-20261107.sql。

本地开发预览需要工具时，显式叠加 `deploy/compose.dev-tools.yml`；该 override 设置上述两个开关，基础 `deploy/compose.yml` 保持生产设置。自动化验收另外使用独立项目：准备新的 `.tmp/dev-tools-qa.env`，设置独立端口、数据库和存储卷，再从仓库根目录执行：

```sh
docker compose --env-file .tmp/dev-tools-qa.env -p pythontopia-dev-tools-qa -f deploy/compose.yml -f deploy/compose.dev-tools.yml up --build -d
```

自动化验收不要复用原预览项目名、数据库连接或卷。工具不删除账号、成员关系、课程内容及班级发布配置，也不影响其他学生或其他班级；当前班级的学习记录没有自动撤销入口。正在处理判题或助教调用时返回 409，应等待完成或按既有流程对账后再重试。

前端仅使用服务端能力决定 HUD“开发工具”入口。用户先点击“重新开始当前班级”，再点击内联“确认重新开始”。成功后清除对应账号/班级的阅读、练习草稿、助教恢复及图书馆筛选缓存，保留令牌、当前班级和界面偏好，重新欢迎并回到 `(town_hub,22,36)`，主线从 `0/8` 开始。不要通过 hostname 或 `import.meta.env.DEV` 绕开服务端开关。

浏览器专项 `frontend/e2e/devTools.spec.ts` 要求显式 `E2E_DEV_TOOLS=1`、独立开发 QA 的 `E2E_URL` 和 `E2E_TEACHER_EMAIL/PASSWORD`；设置 `PLAYWRIGHT_CHANNEL=chrome`。测试只新建验收班级与学生，通过真实 API 建立任务、阅读和正式成绩，并核对另一班级及另一学生不变。浏览器缓存 fixture 仅用于验证清理边界，不伪造服务端进度，也不发送助教问题。

可另设 `E2E_PRODUCTION_URL` 指向独立生产模式 QA；其新建班级使用 `E2E_PRODUCTION_TEACHER_EMAIL/PASSWORD`，缺省时复用前述教师凭据。生产专项只注册新的学生账号，检查注册/登录能力关闭、无开发菜单以及直接 POST 返回 404，不在既有账号上尝试重置。在 `frontend` 执行：

```sh
npx playwright test devTools.spec.ts --project=desktop --workers=1 --output=.tmp-dev-tools
```

未设置所需环境时明确跳过相应用例，不把跳过、用例发现或本节命令当作真实验收通过。实际运行环境与结果记录在 acceptance.md。

## 游戏界面统一规范（2026-11-07）

原本地15176已更新整套统一工作区，API18083沿用，刷新浏览器即可查看；独立15177验收后停止、卷保留。本轮只更新前端容器，数据库备份位于.tmp/pre-game-design-20261107.sql，原进度与成绩完整字段核对一致。

修改游戏工作区前阅读[设计索引](design/README.md)与[游戏界面规范](design/game-interface.md)。共享页头和外壳位于OverlayHost.vue，公共令牌与原生控件位于gameDesign.css，目录/正文及阅读/练习布局分别由townWorkspaces.css与learningUi.css承担。不要重新添加旧workspace尺寸规则或将游戏控件样式作用于教师后台。

已导入课程媒体的独立环境设置E2E_URL、E2E_TEACHER_EMAIL/PASSWORD、PLAYWRIGHT_CHANNEL=chrome、E2E_COURSE_MEDIA_IMPORTED=1后，在frontend执行 `npx playwright test gameDesign.spec.ts townWorkspaces.spec.ts uiUsability.spec.ts storyChapter.spec.ts worldConfiguration.spec.ts --project=desktop --workers=1 --output=.tmp-game-design-e2e`。新增设计专项走真实主线进入PDF和练习，检查七类工作区页头、透明间隙、控件和200%字号；单纯控件样式fixture不代表已新增学生多选题。结果与截图复核记录见acceptance.md。

## 图书馆、班级与设置工作区（2026-11-07）

原本地入口仍为http://127.0.0.1:15176/，API18083，已更新本轮界面；独立验收15177/API18084使用同一镜像验证后停止、数据卷保留。原服务数据库备份位于忽略目录.tmp/pre-town-workspaces-20261107.sql，更新没有重导入课程媒体或新增主线。

本轮统一三页近满屏深绿/纸面/暖金布局与单一主标题。图书馆按章节目录组织已授权资料，提供搜索和课件/视频筛选，返回阅读列表时恢复搜索、章节和滚动位置。我的班级展示真实教师姓名和任务进度，切换先读取目标存档再提交客户端选择，失败保留当前班级；加入后由用户显式切换。设置使用账户优先的分类标签，音量滑块直接控制环境音乐，字号/减少动效/音量为本机偏好；许可与来源在关于分类，不再从设置跳转班级或材料。

浏览器专项在独立、已导入真实课程媒体的部署中运行。设置E2E_URL指向该部署、显式E2E_TEACHER_EMAIL/PASSWORD、PLAYWRIGHT_CHANNEL=chrome和E2E_COURSE_MEDIA_IMPORTED=1；不要将账号或令牌写入测试文件。进入frontend后运行：

```sh
npx playwright test townWorkspaces.spec.ts --project=desktop --workers=1 --output=.tmp-town-workspaces-e2e
```

该专项自行建立验收班级与学生，检查图书馆目录/搜索/筛选与PDF返回位置、当前班级进度/切换/加入、账户优先设置/键盘分类/真实音量、字号和关于内容，并检查工作区切换复用同一世界画布、零附带模型调用。视觉与运行结果以acceptance.md对应条目为准，命令或用例发现成功不代表真实浏览器验收通过。GET /classrooms新增teacher_name仅需当前代码，无数据库迁移；学生响应不包含加入码或教师邮箱。

## 课程媒体导入（2026-11-07）

最终本地状态：原15176/API18083已完成7PDF+34视频接入并运行，原材料186..192和561页编号保留；视频193..226。重启无需重导入。独立验收15177/API18084已停止，卷保留；原包未改，SQL备份在.tmp/pre-course-media-final-20261107.sql。

先使用最新API镜像并完成Alembic/seed。媒体文件保存在独立私有存储卷，不放Git或frontend/public；API镜像仅增加可信video_manifest.json与video_transcripts.json，不包含884MB压缩包或视频文件。当前资料不再需要原包常驻挂载，正常启动仍按原Compose命令。

导入前备份数据库并暂时停止API/Web写入，保留db。以下为本机PowerShell从仓库根目录执行的实际导入方式；只读挂载原包，运行一次性容器并复用当前项目的数据库/存储卷：

```powershell
$courseMediaArchive = (Resolve-Path (Read-Host '课程媒体包完整路径')).Path
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml stop api web
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml run --rm --no-deps -v "${courseMediaArchive}:/tmp/course-pack-43-media.tar.gz:ro" api python -m app.course_media /tmp/course-pack-43-media.tar.gz
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml up -d --no-build
```

预期输出7份PDF、561页、34个补充视频。导入会校验与暂存约919MB文件，需预留足够临时/私有存储空间；当前默认上限压缩1GiB、总展开1GiB、单文件128MiB。相同原包重复运行保留同一视频材料ID，校验既有文件后复用；正常重启seed保留PDF类型、原页面和资料库发布信息。不自动导入或创建活动、主线和成绩。导入失败后不要在未核对数据库/存储状态时宣布媒体就绪。

真实课程媒体浏览器专项仅在已导入媒体的独立验收环境运行，显式设置E2E_URL、E2E_TEACHER_EMAIL/PASSWORD、PLAYWRIGHT_CHANNEL=chrome、E2E_COURSE_MEDIA_IMPORTED=1；全34视频首帧检查另设E2E_ALL_COURSE_VIDEOS=1，再运行 `npx playwright test courseMediaPack.spec.ts rebornJourney.spec.ts storyChapter.spec.ts --project=desktop --workers=1 --output=.tmp-course-media-e2e`。测试会创建独立班级并开放7章，不修改原班级；不将跳过或未逐段完整播放的视频计为完整播放验收。

资料库按原班级章节发布规则展示：只开放第一章时为该章PDF+3个补充视频；完整7章均开放的验收班可见7PDF+34视频。不会为接入材料修改原班级开放日期或前置，也不会恢复旧任务。


## 当前唯一主线（2026-11-07）

当前仅保留“初来小镇：第一堂 Python 课”（arrival），不再部署旧第二章委托。课程七章/65知识点/材料仍保留，旧学习成绩不清空。验证使用 `npx playwright test rebornJourney.spec.ts storyChapter.spec.ts --project=desktop --workers=1 --output=.tmp-only-arrival-e2e`：前者覆盖唯一主线完整学习/练习/测试、开放其他章不恢复旧任务、完成进度和换账号；后者覆盖同一主线的中断/刷新/续读/重复拜访等不同教学场景。不再要求保留已退出产品的第二章任务来满足历史“两章旅程”验收。以下旧旅程结果为历史记录。


## 当前配置导入与迁移（2026-11-07）

本轮完成后原预览 http://127.0.0.1:15176 （API18083）已更新并运行，账号和学生学习历史保留。独立验收15177/API18084已停止，不自动启动。升级前SQL备份保存在忽略目录.tmp/pre-current-config-final-20261107.sql，原镜像ID在.tmp/pre-current-config-images.json；均不入Git。

当前实现取消教学内容快照和版本号，以下历史验收中的固定版本、快照和旧版并行描述不再代表当前行为。YAML按稳定ID更新当前配置，学生记录独立保留。操作说明见content-authoring.md；本次迁移head为c84f1a6d209e。

本机在backend目录执行：

```sh
python -m app.chapter_content --check
python -m alembic upgrade head
python -m app.seed
python -m app.seed
python -m alembic check
```

旧库升级前备份。迁移将最新材料内容并入materials，任务/剧情按ID保留最高旧版，阅读进度合并参与页和完成标记，移除历史内容表与版本列；不删除提交、成绩、节点、事件或学习记录。历史内容版本合并后无法自动downgrade，只能用升级前备份恢复。迁移需要在线连接，不能用alembic --sql生成完整离线脚本。

容器当前启动命令仍是 `alembic upgrade head && python -m app.seed && uvicorn ...`。每次API容器启动都会同步当前配置，相同ID原位更新。YAML复制在镜像中，编辑后需 `docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml up --build -d` 才能更新15176预览；直接start旧容器不加载工作区修改。不要复用main数据库或其他项目卷。


## 后台改版与 reborn 集成（2026-11-05）

本轮以 reborn 的 b773f56a（章节脚本与配置地图）为基线，合入后台改版 0f2717ac，在隔离集成分支验证后更新本地 reborn，不推送或部署。原有未跟踪 deploy/piston-local/ 保留。合并保留新版剧情、地图、活动快照、旧任务兼容及教师后台的日期安排/分页/弹窗/章节完成数。新安排首次保存会重新读取任务定义，避免切换到编写页面后缓存旧版本；已有安排仍不自动升级。

验证使用新的临时 API18186、Web15286 与独立 SQLite/存储，迁移到 b63e1a4920d7；不升级原验收库或覆盖原前端构建。此处以下的15175/15176/15186等端口、旧分支路径、通过项数量和“当前/最新”措辞均属于各次历史验收，不能作为本次集成部署状态的证明。本轮最终集成验证：前端锁定npm ci、typecheck、lint、127项单测及独立输出构建通过；后端71项锁依赖匹配、ruff、168 passed/7 skipped（2个PostgreSQL专项与5个liveJudge0未配置），全新Alembic到b63e1a4920d7、重复seed后29表行数一致。地图校验2图/11建筑/2portal/1838可达格/353美术对象与68项资源存在审计通过。真实Chrome：后台专项、课程编写、私有媒体各1通过，旧两章1、新第一章桌面/窄屏2、地图定义/交互2，共8项通过。新建排课刷新定义的回归已纳入127项。最初后台分页用例依赖历史上传数据，在全新库失败；改为显式上传11份自制文本fixture后通过，不将首次失败计通过。未实测本轮真实AI/Judge/PG或PPTX转换。合并只更新本地源码，不替换已有验收数据库、容器或生产构建。

## 以下为历史记录


## 章节演出开发预览（2026-11-05）

当前状态：验收后已按用户要求停止pythontopia-story-qa的web/API/db，15176/18083不再监听；容器、数据卷和本机体验凭据保留。下方启动命令仅供明确恢复服务时使用。

同一15176预览现包含地图配置、建筑名称和入口导航。权威配置在data/world/world.json，修改后需重建API/Web；web镜像保留/app/frontend与/app/data/world的相对目录，避免本机可导入而容器丢配置。地图检查运行 `node scripts/validate-world.mjs`；只读素材源预检运行 `python scripts/build_srw_assets.py --source D:\godot-one --check`。素材提取脚本不要求本机Godot，不把外部源路径作为部署依赖。

本轮新增独立预览 `http://127.0.0.1:15176`（API18083），Compose项目 `pythontopia-story-qa`，使用专属数据库/存储卷。原15175演示栈检查时已停止，本轮未启动或修改它，也未恢复15174/18081。体验学生从第一章版本2起点开始；本机凭据保存在忽略文件 `.tmp/story-preview.md`，验收教师凭据在 `.tmp/story-qa-credentials.json`，均不入Git。

章节源文件校验与导入：

```sh
cd backend
python -m app.chapter_content --check
python -m alembic upgrade head
python -m app.seed
python -m app.seed
python -m alembic check
```

沿用本机已生成的独立 `.tmp/story-qa.env`（DB随机密码、WEB15176、API18083），从仓库根启动/更新该预览：

```sh
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml up --build -d
```

停止并保留数据：

```sh
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml stop
```

真实新版旅程使用 `npx playwright test storyChapter.spec.ts --project=desktop --workers=1 --output=.tmp-story-e2e`，文件内部覆盖1440×900和390×844触摸视口。先设置E2E_URL、E2E_TEACHER_EMAIL/PASSWORD和PLAYWRIGHT_CHANNEL=chrome，不将凭据写入脚本。原两章回归继续使用rebornJourney.spec.ts。新版第一章发布任务版本2；已有玩家仍固定原任务版本，可在独立班级用新学生体验，不要清空旧存档。

要求 Docker Compose v2；本机开发使用 Python 3.12/3.13、Node 22。不要复用 main 的数据库连接、数据卷、对象存储前缀或凭据。项目根目录不依赖开发者绝对路径。

## 历史演示入口（2026-11-03）

2026-11-03按用户当时要求，仅保留Docker新版http://localhost:15175（API18082）。重复的本机15174/18081已停止，旧数据文件保留，不自动重启。deploy/.env配置REBORN_WEB_PORT=15175、REBORN_API_PORT=18082；本轮新预览使用上方独立项目和环境文件。

专用“Pythontopia Reborn 演示班”包含教师、新手学生与两章已完成学生。两章完成状态、6次概念题成绩和14条任务事件来自真实界面流程；教师可直接查看学情。演示凭据、班级加入码与本机核查结果仅保存在忽略的.tmp/reborn-demo.json，不提交Git。

## 容器启动

复制 deploy/.env.example 为 deploy/.env，生成独立的随机 REBORN_DB_PASSWORD（建议32字节十六进制）。默认 REBORN_API_PORT=18081、REBORN_WEB_PORT=15174，只监听本机。然后在仓库根运行：

```sh
docker compose --env-file deploy/.env -f deploy/compose.yml up --build -d
docker compose --env-file deploy/.env -f deploy/compose.yml ps
```

也可使用 ./dev.sh 或 Windows ./dev.ps1。API 启动先执行新 Alembic 基线和幂等课程 seed。健康检查：GET /api/health。访问 http://127.0.0.1:15174。数据库和文件卷均以 pythontopia-reborn 为项目名独立创建；停止使用 compose stop，勿附加 -v。

没有默认公网账号。显式创建教师（先在当前 shell 设置只用于本次创建的密码环境变量，至少12字符）：

```sh
docker compose --env-file deploy/.env -f deploy/compose.yml exec -e REBORN_TEACHER_PASSWORD api python -m app.cli provision-user --email teacher@example.edu --name 教师 --role teacher --password-env REBORN_TEACHER_PASSWORD
```

管理员以同一 CLI 的 --role admin 创建。学生在界面注册；教师创建班级后分发加入码，按班级发布章节。更换示例邮箱为自己的账号。

## 本机开发

```sh
cd backend
python -m venv .venv
# 激活该venv后：
python -m pip install -r requirements.lock
# 显式设置 REBORN_DATABASE_URL，生产必须postgresql+psycopg://...
python -m alembic upgrade head
python -m app.seed
python -m uvicorn app.main:app --host 127.0.0.1 --port 18081
```

本机 API 配 PostgreSQL 时，可增加 -f deploy/local-db.yml，仅开放127.0.0.1:15433。开发默认 SQLite 仅作轻量验证，不作为正式部署建议。新终端：

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 15174
```

Vite 将 /api 转发至18081，API_PROXY 可覆盖。不要只启动旧 backend 或使用旧 deploy 配置。

## AI、文件与判题

AI 配置 REBORN_AI_API_KEY、REBORN_AI_BASE_URL、REBORN_AI_MODEL。未配置时主动提问返回明确503；游戏与概念题继续运行。统一 create_agent，角色来自活动 NPC，默认每请求最多3模型/5工具调用、45秒和每模型512输出 token，用户每日12请求/120000 token、班级每日100请求/1200000 token，可由 REBORN_AI_* 环境变量调整。正常翻页、对白和进度不创建模型请求。流事件为 meta、token、references、done、error，取消通过 request_id。凭据不发浏览器。

默认本地私有存储 REBORN_STORAGE_ROOT。S3 模式设 REBORN_STORAGE=s3、REBORN_S3_ENDPOINT、REBORN_S3_BUCKET、REBORN_S3_PREFIX=reborn/，凭据通过 AWS 标准后端环境；不与旧对象前缀共用。上传最大128MiB，PDF/文本解析独立进程25秒超时；Linux 子进程另限CPU15秒/512MiB，Windows没有同等内存限制。生产应使用Linux容器。PPT依赖显式配置的隔离转换镜像，未配置或转换失败必须显示failed，不能把原文件当可展示PDF。

Judge0 可选栈见 deploy/judge0.compose.yml 与 judge0.conf；使用>=1.13.1修复官方三个Critical漏洞的版本，独立随机数据库/Redis密码与 AUTHN_TOKEN，仅API绑定127.0.0.1:2359。worker需要独立受信Linux运行环境，禁止学生网络。业务设置 REBORN_JUDGE0_URL 和 REBORN_JUDGE0_AUTH_TOKEN；本机API用 http://127.0.0.1:2359，Docker API可用 http://host.docker.internal:2359（Linux需显式host-gateway）。默认Python language_id需通过该服务/languages核对。

本机Docker Desktop的cgroup v2与Judge0 1.13.1内置isolate不兼容：真实执行报缺少/sys/fs/cgroup/memory目录和/box/script.py。API连通不代表判题成功。需要兼容cgroup v1的隔离Linux VM后重跑live测试；不要通过禁用隔离来伪造通过。参考[官方安全公告](https://github.com/judge0/judge0/security/advisories)。未知送交状态保留submission_unknown由管理员对账，不自动重送。

## 检查

```sh
cd frontend
npm run typecheck
npm run lint
npm test
npm run build
# 一次性安装匹配浏览器，或设置PLAYWRIGHT_CHANNEL=chrome使用本机Chrome
npx playwright install chromium
# 显式设置E2E_URL、E2E_TEACHER_EMAIL、E2E_TEACHER_PASSWORD，使用独立验收库
npm run test:e2e
cd ../backend
python -m ruff check app tests
python -m pytest -q --basetemp .pytest-local
cd ..
node scripts/validate-world.mjs
node scripts/audit-assets.mjs
```

PostgreSQL并发测试须 REBORN_TEST_DATABASE_URL，使用临时schema并清理；Judge live测试须 REBORN_LIVE_JUDGE0_URL 与鉴权token，未设置时明确skip。`scripts/load-world.mjs` 使用显式 REBORN_LOAD_TOKEN/CLASSROOM，默认200次请求10并发，只测已登录世界读取，不能作为数百个学生AI或判题并发的证明。

实际结果和未通过项见 acceptance.md。不得把跳过的外部服务测试计为通过。主分支只通过人工审查移植独立改动，不周期merge。

## 游戏界面验收

地图布局变更后运行`node scripts/generate-library.mjs --check`、`node scripts/validate-world.mjs`与`node scripts/audit-assets.mjs`。town_hub.json的可编辑生成器为scripts/generate-maps.mjs；library.json使用原图坐标与家具对象配置，不需Godot编辑器。world.json与前端静态地图分别进入API/Web镜像，改配置后须同时重建，不能只刷新旧镜像。

地图工作区和门的真实交互使用`npx playwright test worldConfiguration.spec.ts --project=desktop --workers=1`：检查只展示当前图且裁掉外景、右卡建筑/人物筛选、玩家标记始终可见及走路后立即开图的位置、关闭后WASD、移动中提示立即消失、静默的未开放房屋、远端书架实际点击、同一画布与双向入口，并截图复核图书馆和1920×1080桌面布局。需确认标记实际落在画布可视框内，不能只检查DOM可见。地图不增加键盘选项导航；浏览器截图仍须实际打开检查。原两章旅程仍覆盖任务驱动的跨图导航及书架证据，不能仅靠地图快照替代。

设置显式E2E教师凭据、E2E_URL与PLAYWRIGHT_CHANNEL后，运行`npx playwright test uiUsability.spec.ts --workers=1`。该验收真实创建独立班级与新学生，仅覆盖1920×1080桌面Chromium；检查任务进度、键盘焦点、返回路径、200%字号、减少动效和世界画布不重建。普通操作仍须model_calls=0。手机、触屏、小屏与超宽屏不再作为CI验收条件。

两章旅程继续使用`npx playwright test rebornJourney.spec.ts --workers=1`。先冻结源码或使用编译后的独立web站点验收，避免HMR重新载入干扰正在执行的旅程。截图/trace使用忽略的输出目录，不能提交其中的临时账号或令牌。浏览器模拟视口不代表实体手机、电视或手柄已经验证。

Vite已从文件监听中排除`.tmp*`目录、`test-results`与`playwright-report`。Playwright trace会生成HTML快照，不应被当作应用页面触发整页重载；验收输出须放在上述目录中。业务源码仍正常HMR。

## 当前文档转换与时区配置

PPTX转换已在独立受限LibreOffice服务真实验收。容器部署设置REBORN_CONVERTER_URL=http://converter:8090、随机>=24字符REBORN_CONVERTER_TOKEN，然后使用--profile documents。主API与converter只在私有conversion网络交互，不挂Docker socket。

Windows本机API也可设置REBORN_CONVERTER_IMAGE=pythontopia-reborn-converter:local并保持REBORN_CONVERTER_URL为空。先构建deploy/converter镜像；每次转换启动唯一命名的受限非root容器，禁网、只读输入、512MiB/1CPU/pids64、固定宏安全、限时，PDF通过stdout回到本机写入，避免挂载权限问题。若不配置转换器则明确failed，不伪成功。此模式需要本机Docker明确可用。

教师排课/显示默认Asia/Shanghai；本机通过frontend/.env.local的VITE_TIME_ZONE配置，容器通过deploy/.env的REBORN_TIME_ZONE构建参数配置，变更后重建前端。使用IANA时区名，DST不存在/歧义时间明确拒绝。服务端所有公开timestamp带UTC偏移，SQLite与PostgreSQL判定一致。

## 历史本机预览数据（已停用）

此前15174预览的账号与进度保存在仓库.tmp/reborn-native.db，私有文件在.tmp/native-storage（均不入Git）。该预览已按用户要求停止。以下仅保留恢复参考，不作为当前演示启动步骤：

```powershell
$rebornRepo = (Resolve-Path '..').Path
$env:REBORN_DATABASE_URL = 'sqlite:///' + $rebornRepo.Replace('\','/') + '/.tmp/reborn-native.db'
$env:REBORN_STORAGE_ROOT = Join-Path $rebornRepo '.tmp/native-storage'
$env:REBORN_CONVERTER_IMAGE = 'pythontopia-reborn-converter:local'
$env:REBORN_CONVERTER_URL = ''
./.venv/Scripts/python.exe -m alembic upgrade head
./.venv/Scripts/python.exe -m app.seed
./.venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 18081
```

先确认对应旧进程已由操作者停止，避免端口冲突；不停止其他任务。前端仍运行15174。默认容器端口会与本机相撞，同时验收时使用REBORN_API_PORT=18082、REBORN_WEB_PORT=15175，保持本机预览不变。两个环境数据库独立，账号不自动共享。

## 教师管理界面（2026-11-05）

教师后台采用 shadcn-vue 的 Button、Card、Table、Dialog 源码，基于 Reka UI 与 Tailwind CSS 4；许可证保存在 frontend/src/components/ui/LICENSE.shadcn-vue。组件配置位于 frontend/components.json，依赖使用精确版本与 package-lock.json 锁定。

后台入口仍为 /teacher。theme 只在教师路由加载；teacherTheme.css 不引入 Preflight，生成工具类限制在 .teacher-page / .teacher-dialog 的 CSS @scope 内，门户弹窗显式携带后台主题。新增后台组件时须保持这条边界，不向 main.ts 或学生页面导入后台样式。原生表单控件继续使用后台范围内的样式与浏览器语义。使用支持 CSS @scope 的现代浏览器，本次在本机 Chrome 验证。

本次独立验收使用全新 SQLite 库与存储，不复用现有演示库。实际临时启动方式（在仓库根目录；账号由 provision-user 显式配置，密码不进源码）：

```powershell
$env:REBORN_DATABASE_URL = 'sqlite:///' + (Join-Path (Get-Location) '.tmp/admin-preview-20261105.db').Replace('\','/')
$env:REBORN_STORAGE_ROOT = Join-Path (Get-Location) '.tmp/admin-preview-storage'
Set-Location backend
../.tmp/backend-venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 18086
# 另一个终端，进入 frontend
$env:API_PROXY = 'http://127.0.0.1:18086'
npm run dev -- --host 127.0.0.1 --port 15186
```

后台专项测试：设置 E2E_URL、E2E_TEACHER_EMAIL、E2E_TEACHER_PASSWORD、PLAYWRIGHT_CHANNEL=chrome 后执行 `npx playwright test adminManagement.spec.ts --project=desktop --workers=1`。用例使用1920×1080桌面视口，操作八个后台页面，并检查退出后登录页样式与零附带模型调用。教材编写测试还需要 E2E_OTHER_TEACHER_EMAIL / E2E_OTHER_TEACHER_PASSWORD；PPTX 转换测试仍须显式文件与转换服务。

验收入口15186已重建为本轮批注修订版，继续使用原隔离验收账号；API18086已重启加载真实章节完成计数，预览数据保留。临时开发端口15187仅用于本轮验证。

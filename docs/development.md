# 安装、启动与排查

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

地图工作区和门的真实交互使用`npx playwright test worldConfiguration.spec.ts --project=desktop --workers=1`：检查只展示当前图且裁掉外景、右卡建筑/人物筛选、玩家标记始终可见及走路后立即开图的位置、关闭后WASD、移动中提示立即消失、静默的未开放房屋、远端书架实际点击、同一画布与双向入口，并截图复核图书馆和390×844布局。窄屏需确认标记实际落在画布可视框内，不能只检查DOM可见。地图不增加键盘选项导航；浏览器截图仍须实际打开检查。原两章旅程仍覆盖任务驱动的跨图导航及书架证据，不能仅靠地图快照替代。

设置显式E2E教师凭据、E2E_URL与PLAYWRIGHT_CHANNEL后，运行`npx playwright test uiUsability.spec.ts --workers=1`。该验收真实创建独立班级与新学生，覆盖1440×900、1024×640、844×390触屏、2560×1080四视口；检查任务进度、折叠、键盘焦点、返回路径、200%字号、减少动效、触控热区和世界画布不重建。普通操作仍须model_calls=0。触屏项目显式hasTouch，不将鼠标点击算作实际触摸。

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

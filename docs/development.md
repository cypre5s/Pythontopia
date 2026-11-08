# 安装、启动与排查

本手册说明当前 `reborn` 的操作流程，不代表某个本机环境正在运行。产品范围与内容格式分别见[产品说明](product.md)、[内容编写](content-authoring.md)；实际检查结果见[验收记录](acceptance.md)。原先按轮次追加的完整记录保存在[整理前原文](archive/2026-11-08-documentation-baseline/development.md)，其中的端口、迁移头和运行状态仅属于当时环境。

## 环境与数据隔离

- 容器部署使用 Docker Engine / Docker Desktop 与 Compose v2。本机开发使用 Python 3.12 或 3.13、Node.js 22；依赖分别来自 `backend/requirements.lock` 和 `frontend/package-lock.json`。
- 只操作 `reborn`。不得复用 `main`、旧项目或生产的数据库、数据卷、对象存储前缀与凭据；不得自动合并 `main`。
- 每个开发、演示或验收环境使用独立的 Compose 项目名、端口、数据库与存储。下文基础环境统一为 `pythontopia-reborn`，并显式传入环境文件。
- 同一环境的启动、更新、导入、查看日志和停止，始终使用同一组 `--env-file`、`-p` 和 `-f` 参数。启用了开发工具或转换器的环境，后续更新也要保留对应 override / profile。
- 环境文件、凭据、数据库备份、截图与 trace 放在忽略目录，不提交 Git。不要输出含凭据的完整 Compose 配置或数据库连接串。
- 更新已有数据库前，备份该环境的数据库与私有文件存储，并确认恢复方法。日常停止用 `stop`；不要用 `down -v`、删除卷或清空账号来完成升级、排查或验收。

下列 Compose 命令从仓库根目录执行；本机 Python 命令从 `backend` 执行，npm / Playwright 命令从 `frontend` 执行。

## Compose 首次启动、更新与停止

### 首次启动

首次创建 `deploy/.env` 时，从 `deploy/.env.example` 复制，已有文件则保留。设置独立随机的 `REBORN_DB_PASSWORD`；推荐使用32字节的十六进制随机值，避免数据库 URL 中的特殊字符。可选服务未配置时保留空值。

```powershell
if (-not (Test-Path -LiteralPath deploy/.env)) { Copy-Item deploy/.env.example deploy/.env }
```

基础配置默认只绑定本机：Web 为 `15174`，API 为 `18081`。端口已被占用时，在新环境文件中设置其他端口，并使用新的项目名，不停止其他环境。

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml up --build -d
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml ps
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml logs --tail 100 api web
Invoke-RestMethod http://127.0.0.1:15174/api/health
```

默认入口为 `http://127.0.0.1:15174/world`，直接 API 为 `http://127.0.0.1:18081/api/health`。`/api/health` 会执行数据库 `SELECT 1`，只证明 API 和数据库连通；还要通过浏览器确认登录、世界加载、授权材料和所需外部服务。不要将 Web 返回200等同于媒体、AI或判题就绪。

`backend/Dockerfile` 的启动顺序为：`alembic upgrade head` → `python -m app.seed` → Uvicorn。每次 API 容器启动都会迁移并同步当前课程/YAML配置；原始 PDF 和视频不会自动导入。基础 Compose 固定为生产模式，未开放开发重置工具。

仓库根的 `./dev.ps1`、`./dev.sh` 是使用 `deploy/.env` 和基础 Compose 启动的简写；需要独立项目或 override 时，使用显式 Compose 命令。

### 首次管理员与后续教师账号

首次部署在环境文件设置 `REBORN_ADMIN_STUDENT_ID`（1–32位数字，保留前导零）、`REBORN_ADMIN_PASSWORD`（12–200位且不能等于学号）和可选 `REBORN_ADMIN_NAME`（默认“管理员”）。没有公开默认账号或密码。API完成迁移与seed后，在启动阶段创建首个管理员；多个PostgreSQL进程通过事务锁串行检查。已存在管理员时跳过初始化，修改环境变量或重启都不会覆盖其密码。成功创建后可清空环境文件中的管理员密码；已有账号使用界面改密。全空配置不会创建管理员，部分配置或学号被其他角色占用则启动报错，不会提升已有学生权限。

所有角色使用同一学号登录入口。学生在“设置 → 账户”改密，教师/管理员在“我的密码”改密；需要当前密码，新密码至少10位。修改后其他登录失效，当前窗口获得新会话。首次管理员不使用学生的学号初始密码规则。

教师在“学生账号”按学号与姓名创建学生，或上传UTF-8 CSV / `.xlsx`批量预览后确认。表格仅一张工作表，首行为“学号、姓名”，最多500人/2MB；Excel学号必须用文本单元格，拒绝公式。初始密码为本人学号，首次登录必须修改。重复导入本班已有学生跳过，不覆盖姓名/密码；其他已有账号先使用班级加入码加入。可选择学生批量重置密码为各自学号（撤销全部旧会话、强制再次改密）或移出当前班级（保留账号、其他班级、历史记录）。

系统不提供默认公开账号。先在当前终端安全注入仅用于创建账号的 `REBORN_TEACHER_PASSWORD` 环境变量，至少12字符；不要将密码写进命令、文档或 Git。再创建自己的教师账号：

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml exec -e REBORN_TEACHER_PASSWORD api python -m app.cli provision-user --student-id 900001 --name 教师 --role teacher --password-env REBORN_TEACHER_PASSWORD
```

命令不会覆盖已有同学号账号。该命令用于后续教师或管理员增补（管理员用 `--role admin`）；首次管理员优先使用上述启动变量。本机 API 可直接执行同一命令。用后清除临时密码环境变量。学生仍可自行注册，教师创建班级、管理学生并安排章节开放。

### 更新与停止

从邮箱账号升级前先备份数据库与私有文件。学号迁移保留历史用户和学习数据，撤销旧会话并移除邮箱字段；不会自动编造学号或清空账号。需要保留的历史账号先通过可信名册核对用户ID，再分配真实学号（只能分配给尚未设置学号的账号）：

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml exec api python -m app.cli assign-student-id --user-id 1 --student-id 00123456
```

以上ID和学号仅为格式示例。新账号使用 `provision-user --student-id`，所有角色均使用1至32位数字字符串，保留前导零。迁移后如需回退使用升级前备份；没有邮箱兼容登录。清空本地预览账号必须单独明确环境与关联记录范围，不能将测试清理加入迁移或seed。

代码、YAML或前端构建配置变化后，重建相关镜像并启动。API 中的 YAML 来自构建时复制，`start` 旧容器或只刷新网页不会加载工作区修改。

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml up --build -d api web
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml stop
```

`stop` 保留账号、学习记录、成绩及数据库/存储卷。修改 `data/world/world.json` 等 API/Web 共同使用的地图配置时，同时重建 API 和 Web。

### 已存在环境的配置示例

若操作者确认要沿用已有的独立预览配置 `.tmp/story-qa.env`、项目 `pythontopia-story-qa`，其中 Web/API 端口为 `15176/18083`，并且该环境原本需要开发工具，可使用：

```powershell
docker compose --env-file .tmp/story-qa.env -p pythontopia-story-qa -f deploy/compose.yml -f deploy/compose.dev-tools.yml up --build -d api web
```

这是已有配置的使用示例，不证明该环境存在或正在运行，也不作为新验收环境模板。实际状态通过该项目的 `ps`、日志、health 和浏览器核对；凭据文件保持私有。

## 本机开发

### 后端

在仓库根先创建忽略的 `.tmp` 目录，选用独立数据库和存储。以下 PowerShell 示例使用仅供轻量开发的 SQLite，不会连接已有 Compose 数据库：

```powershell
New-Item -ItemType Directory -Force .tmp
$rebornRepo = (Get-Location).Path
$rebornLocalName = 'reborn-local-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$env:REBORN_DATABASE_URL = 'sqlite:///' + (Join-Path $rebornRepo ".tmp/$rebornLocalName.db").Replace('\', '/')
$env:REBORN_STORAGE_ROOT = Join-Path $rebornRepo ".tmp/$rebornLocalName-storage"
$env:REBORN_ENV = 'development'
Set-Location backend
python -m venv .venv
./.venv/Scripts/Activate.ps1
python -m pip install -r requirements.lock
python -m alembic upgrade head
python -m app.seed
python -m uvicorn app.main:app --host 127.0.0.1 --port 18081
```

首次创建环境才生成新名称；记录并保留这组数据库/存储配置，后续启动复用同一环境，不重复生成名称造成存档看似消失，也不指向归档中的旧库。已有虚拟环境可直接激活并按锁文件安装。Linux/macOS 用 `source .venv/bin/activate` 激活。生产必须使用独立 PostgreSQL；SQLite 不覆盖 PostgreSQL 的行锁和并发语义。

本机开发要使用容器 PostgreSQL 时，另准备带独立密码的忽略环境文件 `.tmp/native.env`，可只启动独立项目的数据库：

```powershell
docker compose --env-file .tmp/native.env -p pythontopia-reborn-native -f deploy/compose.yml -f deploy/local-db.yml up -d db
```

该 override 只将数据库绑定在 `127.0.0.1:15433`。将本机 API 的 `REBORN_DATABASE_URL` 设置为该独立库的 `postgresql+psycopg://` 连接；不要同时让另一套 API 使用它，也不要把数据库密码写入示例源码。

### 前端

另开终端，进入 `frontend`：

```powershell
npm ci
npm run dev -- --host 127.0.0.1 --port 15174
```

显式使用 `15174` 可与默认 CORS、Playwright 地址一致。`vite.config.ts` 未覆盖时的端口是 `5178`，`/api` 默认代理至 `http://127.0.0.1:18081`；不同 API 地址通过启动终端的 `API_PROXY` 设置。直接跨域调用 API 时同步配置 `REBORN_CORS_ORIGINS`。

教师时间显示默认 `Asia/Shanghai`。本机前端在 `frontend/.env.local` 设置 `VITE_TIME_ZONE`，容器使用 `REBORN_TIME_ZONE` 构建参数；修改后重启开发前端或重建 Web。使用 IANA 时区名，服务端公开时间戳带 UTC 偏移。

## YAML、媒体导入与数据库迁移

### 同步当前教学配置

脚本入口为 `backend/content/chapters` 与 `backend/content/ambient`；当前唯一内置主线是 `arrival`（初来小镇：第一堂 Python 课），日常演出不增加主线。阅读范围、视频有效时长、重点、练习/测验和对白规则见[内容编写](content-authoring.md)。

在 `backend` 中先校验，再同步：

```powershell
python -m app.chapter_content --check
python -m alembic upgrade head
python -m app.seed
```

`--check` 校验来源，不写数据库；`seed` 按稳定 ID 更新当前配置，不创建教学内容历史版本，不清空学生进度与成绩。已导入的 PDF/视频不会因普通 seed 被替换回纯文本。不要通过改 ID 来模拟普通内容修改；删除或重排配置前，评估已有任务及学生证据的引用。`schema_version` 是 YAML 格式标识，测验草稿 `revision` 是并发控制字段，均不是内容版本管理。

本机 Uvicorn 启动本身不会执行 seed，编辑 YAML 后须手动同步。Compose 则重建 API 镜像后按启动流程同步。格式校验不依赖数据库；导入时还会根据实际材料解析和检查学习规则，缺少材料时应处理错误，不绕过校验。

### 导入课程43的真实材料包

仓库保留课程 JSON、页面身份、清单与转录来源，PDF/视频原文件由操作者提供。先完成迁移与 seed，再使用 `python -m app.course_media <材料包路径>` 显式导入。该命令核对完整包结构、哈希、PDF页数及原页面 ID，失败会回滚数据库修改；不要手工解压后直接覆盖存储文件。

容器导入前，确认当前 API 镜像已构建，并暂停同一环境的 API/Web，保留数据库运行。将下面路径替换为实际材料包路径：

```powershell
$courseMediaArchive = (Resolve-Path 'E:\path\pythontopia-course-pack-43-media.tar.gz').Path
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml stop api web
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml run --rm --no-deps -v "${courseMediaArchive}:/tmp/course-pack-43-media.tar.gz:ro" api python -m app.course_media /tmp/course-pack-43-media.tar.gz
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml up -d --no-build api web
```

本机导入使用与 API 相同的数据库和存储环境变量，在暂停本机 API 后从 `backend` 运行导入命令，成功后重启。材料包的完整导入结果应为7份 PDF、561页、34个补充视频；这不是已经执行导入的证明，还需核对授权班级中的真实文件读取和播放。

PDF原位更新原材料，保留材料 ID、页面 ID 和页码；视频按清单身份幂等更新，作为章节补充资料。导入不新增主线、不修改班级开放安排，也不删除学生任务、阅读或成绩。浏览器中的可见材料仍受班级章节授权控制。

### 数据库结构升级

操作目标始终使用 `python -m alembic upgrade head`，不要复制历史记录中的迁移 ID。既有升级链包含需要在线读取数据的迁移，不能以离线 `--sql` 输出替代实际升级。升级失败先看日志和备份，不手工修改 `alembic_version`，不盲目 downgrade 或新建空库掩盖问题。

在新的独立验收数据库验证迁移与重复 seed：

```powershell
python -m alembic upgrade head
python -m app.seed
python -m app.seed
python -m alembic check
```

上述命令会修改所选数据库，执行前必须确认连接的是验收库。对已有学生数据的升级另核对账号、班级关系、任务事件、阅读记录、提交与成绩保留情况；内容合并类迁移不能假定可自动还原，恢复依赖升级前备份。

## 开发工具

“开发工具”位于现有小镇菜单，只有学生且服务端授权时显示。必须在 API 进程中同时显式设置 `REBORN_ENV=development` 与 `REBORN_DEV_TOOLS=1`；前端开发构建、localhost或只设置一个开关都不会启用它。生产不注册重置路由。

需要此工具的独立开发环境显式叠加 override：

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml -f deploy/compose.dev-tools.yml up --build -d
```

“重新开始当前班级”需用户再次确认，仅删除当前登录学生在所选已加入班级的任务、剧情、世界标记/位置、阅读、练习、测验和成绩。保留账号、成员关系、其他班级/学生、课程内容及发布安排；服务端助教会话、调用审计和预算保留，客户端恢复指针断开。操作没有自动撤销入口。

存在未完成或状态待确认的判题、仍在回复的助教调用时返回409，须等待完成或按既有流程对账。启用工具不等于执行重置；自动化验收只用新建隔离账号，不重置真实演示账号。

## 可选服务与配置

图书馆资料助教与活动助教共用下列模型配置和预算。升级到资料级会话结构前备份当前库和私有存储；执行head迁移与重复seed即可，无需新建Activity、重新导入资料或重置账号。没有凭据时图书管理员入口仍保留，但真实显示不可用。

基础 Compose 只透传其中列出的环境变量。给 env 文件新增变量不会自动注入容器；调整额外的 AI预算、S3或上传限制时，需使用私有 Compose override 显式传入 API。所有凭据只在后端配置，不进入前端构建。

### AI助教

配置 `REBORN_AI_API_KEY`、`REBORN_AI_MODEL`，按供应商需要设置 `REBORN_AI_BASE_URL`。未配置时主动提问明确返回不可用，世界、对白、翻页、概念题和进度继续工作。只有授权活动内学生主动提问才创建 `create_agent` 请求，普通操作必须保持零模型调用。

默认每次最多3次模型调用、5次工具调用、45秒，每次模型输出最多512 token；用户每日12请求/120000 token、班级每日100请求/1200000 token。预算参数见 `backend/app/assistant.py` 的 `REBORN_AI_*`；修改后在隔离环境验证。重启中断的回复不会自动重发，也不将离线测试称为真实模型验收。

### Judge0

学生代码仅送往 Judge0，不在 API 主机执行。可选隔离栈为 `deploy/judge0.compose.yml` 与 `deploy/judge0.conf`；当前固定镜像为 `judge0/judge0:1.13.1`，不要改成 `latest`。在独立受信 Linux 环境部署 worker，保持禁网及其隔离限制，不向学生浏览器暴露判题 API。

为判题栈准备忽略的独立环境文件，设置随机的 `REBORN_JUDGE_DB_PASSWORD`、`REBORN_JUDGE_REDIS_PASSWORD`、`REBORN_JUDGE0_AUTH_TOKEN`，再启动：

```powershell
docker compose --env-file .tmp/judge0.env -f deploy/judge0.compose.yml up -d
```

业务 API 配置 `REBORN_JUDGE0_URL` 和相同鉴权 token。本机默认地址为 `http://127.0.0.1:2359`；Docker Desktop 中 API 可用 `http://host.docker.internal:2359`，Linux 容器需另配置 host-gateway或受控网络地址。通过该服务 `/languages` 核对 Python language ID，再按需设置 `REBORN_JUDGE0_PYTHON_LANGUAGE_ID`。

连通或健康检查不证明真实代码运行成功。Docker Desktop 的 cgroup 版本可能与 Judge0 内置 isolate 不兼容，出现 cgroup目录或 `/box/script.py` 错误时应修复隔离运行环境；不能禁用隔离来通过验收。未知分发状态 `submission_unknown` 必须对账，不盲目重送。练习提交与测验整卷交卷的不同契约见[API说明](api-contract.md)。

### PPT / PPTX转换

容器服务配置 `REBORN_CONVERTER_URL=http://converter:8090` 和随机、至少24字符的 `REBORN_CONVERTER_TOKEN`，使用 documents profile：

```powershell
docker compose --env-file deploy/.env -p pythontopia-reborn -f deploy/compose.yml --profile documents up --build -d
```

API与converter通过私有 conversion 网络通信，主 API 不挂 Docker socket。转换器使用受限 LibreOffice 容器；未配置、损坏文件或转换失败均显示失败，不将原 PPT 当作已生成 PDF。

本机 API 可选择一次性 Docker 转换模式：先构建 `deploy/converter`，设置 `REBORN_CONVERTER_IMAGE=pythontopia-reborn-converter:local`，保持 `REBORN_CONVERTER_URL` 为空。此模式需要本机 Docker，并由代码启动禁网、非 root、只读输入、512MiB/1CPU/pids64限制的唯一容器。

```powershell
docker build -t pythontopia-reborn-converter:local deploy/converter
```

### 私有文件存储

默认使用 `REBORN_STORAGE_ROOT` 本地私有目录，Compose 挂载独立的 `reborn-storage` 卷。S3模式设置 `REBORN_STORAGE=s3`、`REBORN_S3_ENDPOINT`、`REBORN_S3_BUCKET`、独立 `REBORN_S3_PREFIX`，凭据走后端 AWS 标准环境。禁止复用旧项目对象前缀。

普通上传默认上限128MiB，可由 `REBORN_MAX_UPLOAD_BYTES` 调整。PDF/文本解析在独立进程执行，超时25秒；Linux另有限制CPU15秒、内存512MiB，Windows不具有同等进程内存限制，正式部署使用Linux容器。存储文件可访问不等于资料已授权、转换就绪或学习目标已完成，应同时核对材料状态和班级权限。

## 检查与浏览器验收

### 基础检查

前端从 `frontend` 执行：

```powershell
npm ci
npm run typecheck
npm run lint
npm test
npm run build
```

后端使用独立虚拟环境与锁文件，从 `backend` 执行：

```powershell
python -m pip install -r requirements.lock
python -m ruff check app tests
python -m pytest -q --basetemp .pytest-local
python -m app.chapter_content --check
```

另在全新隔离数据库执行前述迁移与重复 seed 检查。PostgreSQL并发专项需要显式 `REBORN_TEST_DATABASE_URL`，测试会创建临时 schema 并清理，不能指向真实演示库或生产库。live Judge0测试需要 `REBORN_LIVE_JUDGE0_URL`，鉴权仍用 `REBORN_JUDGE0_AUTH_TOKEN`；未配置的外部专项应记录为跳过。

地图/素材相关修改从仓库根检查：

```powershell
node scripts/generate-library.mjs --check
node scripts/validate-world.mjs
node scripts/audit-assets.mjs
```

文档改动从仓库根运行链接、锚点与归档完整性检查，再检查差异格式：

```powershell
node scripts/check-docs.mjs
git diff --check
```

纯文档整理只记录这些实际运行的检查，不将既往功能、外部服务或浏览器结果作为本轮重新验收。

`town_hub.json` 的生成入口为 `scripts/generate-maps.mjs`，图书馆为 `scripts/generate-library.mjs`；实际生成会写文件，不能用来替代只读检查。资源存在审计只证明文件存在，不证明视觉排版或真实渲染已经通过。

### 浏览器环境与专项

Playwright不会自动启动服务器。先准备已启动、已迁移/seed的独立验收环境和专用教师账号，设置 `E2E_URL`、`E2E_TEACHER_STUDENT_ID`、`E2E_TEACHER_PASSWORD`；跨教师权限用例另需 `E2E_OTHER_TEACHER_STUDENT_ID`、`E2E_OTHER_TEACHER_PASSWORD`。测试应自行创建班级与新学生，不复用真实演示记录。

从 `frontend` 安装匹配浏览器并执行：

```powershell
npx playwright install chromium
npm run test:e2e -- --project=desktop --workers=1
```

也可将 `PLAYWRIGHT_CHANNEL=chrome` 指向已安装的Chrome。当前唯一 Playwright project 是桌面 Chromium、1920×1080；手机、触屏、小屏和超宽屏不作为当前CI门槛。检查具体测试中的视口覆盖，不能只看项目配置。

| 专项 | 前置条件与命令（在 `frontend`） |
| --- | --- |
| 唯一主线、刷新续玩、账号隔离 | `npx playwright test rebornJourney.spec.ts storyChapter.spec.ts --project=desktop --workers=1` |
| 练习与不可重复整卷交卷 | `npx playwright test assessmentPaper.spec.ts --project=desktop --workers=1` |
| 地图、工作区、焦点、200%字号 | `npx playwright test worldConfiguration.spec.ts uiUsability.spec.ts --project=desktop --workers=1` |
| 真实课程媒体、学习规则、页面样式 | 已导入媒体，显式设置 `E2E_COURSE_MEDIA_IMPORTED=1`；运行 `npx playwright test courseMediaPack.spec.ts learningRules.spec.ts gameDesign.spec.ts townWorkspaces.spec.ts --project=desktop --workers=1` |
| 所有原始视频首帧 | 在媒体前置条件上额外设置 `E2E_ALL_COURSE_VIDEOS=1`，运行 `courseMediaPack.spec.ts`；首帧不代表完整逐段播放 |
| 开发重置 | 隔离开发环境显式设置 `E2E_DEV_TOOLS=1`，运行 `devTools.spec.ts --grep-invert production`；只作用于测试创建的学生 |
| 生产禁用开发工具 | 独立生产配置的 `E2E_PRODUCTION_URL`，及 `E2E_PRODUCTION_TEACHER_STUDENT_ID/PASSWORD`（缺省使用测试教师变量），运行 `devTools.spec.ts --grep production` |
| 自制PPTX上传转换 | `E2E_PPTX_PATH` 指向明确提供的自制测试文件，转换服务就绪，运行 `presentationAuthoring.spec.ts` |

表中简写的测试文件同样通过 `npx playwright test` 执行。缺少条件的用例可能跳过；不得把跳过、契约模拟或“服务可连通”记录为实际外部服务成功。

长旅程验收时冻结源码或使用编译后的独立Web，避免HMR刷新干扰。截图、trace使用忽略的 `frontend/test-results`、`frontend/playwright-report` 或 `.tmp-*` 输出目录；Vite已排除这些目录，避免trace中的HTML触发整页重载。必须实际查看截图，核对布局可读、目标可达、地图标记位置、单一世界画布、焦点与回到小镇、普通操作零模型调用。测试新增任务使用私有定义，不恢复旧主线。

`node scripts/load-world.mjs` 需要显式 `REBORN_LOAD_TOKEN` 和 `REBORN_LOAD_CLASSROOM`，可通过 `REBORN_LOAD_URL` 选择隔离API；默认200次请求、10并发，仅测已登录世界读取，不能作为学生AI或判题并发能力的证明。

实际执行后，将通过、失败、跳过、环境和真实/模拟边界写入[验收记录](acceptance.md)，将仍需延续的工作写入[续做记录](reborn-migration.md)。不要在本手册反复追加某轮“当前运行”、备份文件名、测试数量或历史迁移头。

## 常见故障

| 现象 | 先检查 |
| --- | --- |
| Web能打开，但API/登录失败 | 同项目 `ps`、API日志和 `/api/health`；区分镜像构建、数据库迁移、代理与账号问题 |
| 端口被占用或启动到了另一环境 | 环境文件、项目名和端口；给新环境换端口，不停止无关服务 |
| YAML修改不生效 | `--check` 是否通过，本机是否执行seed，容器是否重建API，是否误用了另一个项目/镜像 |
| 页面显示旧地图或旧前端 | API/Web共同配置是否同时重建，Web镜像和浏览器缓存是否更新 |
| PDF/视频缺失或无权限 | 是否显式导入材料包、导入的数据库与存储是否一致、章节是否开放；普通seed不包含媒体原文件 |
| 助教不可用或回复中断 | 后端模型凭据/预算、活动授权和真实供应商状态；中断不自动重发，不能用假回复替代 |
| 判题排队、未知或执行失败 | Judge0 worker、鉴权、language ID与隔离环境；未知状态先对账，不重复送交或在API主机执行 |
| PPT转换失败 | converter profile、私有token、URL/镜像模式、原文件和处理限制；失败必须保持可见 |
| 没有开发工具或重置返回409 | API两个显式开关、学生身份、班级关系与未完成判题/助教；不通过前端改状态绕过 |
| Playwright首次运行失败 | 浏览器安装、`E2E_URL`、专用教师凭据、服务/媒体前置与用例skip原因；不要误报为业务失败或成功 |
| 迁移失败或升级后资料异常 | 保留日志，核对所选库、锁文件、在线迁移条件与备份；不要删库、改迁移标记或覆盖学生记录 |

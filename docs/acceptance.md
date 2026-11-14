# 验收摘要与已知边界

## 资料逐页讲解与音频核验2026-11-14

本轮实现资料逐页讲解的完整链路：页图渲染与逐页生成、幂等与额度、全篇校准报告、后台审校、学生阅读展示、服务端 TTS 与学生“就这页提问”。代码在 `backend/app/narration/`，共享文件只改 `main.py` 挂路由与 `alembic/env.py` 注册元数据。

实际执行的检查：

- 后端讲解五组用例 **32 通过**（`test_narration.py`、`test_narration_generation.py`、`test_narration_calibration.py`、`test_narration_audio.py`、`test_narration_ask.py`）：审校可见性、同页重写幂等、原文变更后旧审校失效、草稿不对外、生成幂等与页图路由、无凭据不可用、日额度、批量部分失败隔离、校准窗口摘要传递与只重生成草稿、音频拒绝草稿/改稿失效/学生流式取回、提问上下文只含本页与已接受讲解、越界引用丢弃、日上限429。
- `ruff check .` 通过。
- 迁移：空库 `alembic upgrade head` 后 `alembic check` 无待生成变更；链为 `f318a72c90de → c421b8e091aa → b7d3f1a0c452 → c3e9a4f17b28 → d5a17c93e206 → e1f4b23c7d90`，单 head。本轮曾因队友新增 `c421b8e091aa` 与本地新 head 并列导致迁移用例与浏览器建栈失败，已把本分支迁移挂到队head之后修复，原失败不再保留为当前结论。
- 学生前端 **39 文件 215 项**、后台 **38 项** 单测通过，两侧 typecheck 与 lint 通过（学生 5 项讲解面板用例，含提问与翻页清空）。
- 真实模型单页端到端一次：`deepseek-flash` 关思考，1600×900 页图 217KB，prompt_tokens 1237，耗时 2–3 秒；取出页上真实代码名（`train_test_split`、`StandardScaler`、`PCA(n_components=0.95)`）与精度 `0.631578…`，无校名幻觉。该样本仅证明单页可用。

本轮未执行且不记为通过：

- 103 页整份 PPT 的端到端生成与整篇校准（只跑了单页与单窗口）；生成成本与时长未实测定稿。
- 真实 TTS 引擎：`deploy/tts/` 只有脚手架，B0 基准（峰值内存/RTF/长页/中文听感）未在服务器运行，接口形状仅用替身验证；音频时长由字符数估算，不是真实解码时长。
- 浏览器 e2e 未覆盖审校面板、学生讲解面板与音频播放（仅单测与类型检查）；未在真实触控、屏幕阅读器与 200% 文字偏好下检查新面板。
- `deploy/compose.yml` 尚未透传 `REBORN_AUTHORING_AI_*` 与 `REBORN_TTS_*`；以当前 Compose 启动时生成/音频不可用（显式不可用，不伪造）。
- 课程包导出/导入不包含讲解，未做往返；批量音频合成与音频过期清理未实现。
- 本分支的 P1 数据层内容在 rebase 时被并入后续提交（内容与用例完整，仅历史粒度变化）。

本轮不改账号、班级、学习记录、成绩与既有课程内容；迁移只增表与列，未删除旧数据。

部署前置补齐（同一轮，已补验）：`backend/Dockerfile` 增加 `poppler-utils`，`deploy/compose.yml` 透传 `REBORN_AUTHORING_AI_*`、`REBORN_NARRATION_ASK_DAILY` 与 `REBORN_TTS_*`，`deploy/.env.example` 补齐对应条目。

在 WSL Docker（服务器侧网络）实跑：镜像 `pythontopia-api:local` 构建成功（装 poppler 后 663 MB，加 `lame` 转码后 674 MB）；容器内 `which pdftoppm` = `/usr/bin/pdftoppm`，版本 25.03.0，运行用户 `10001(reborn)`；以普通用户身份真实渲染样本《第 1 讲》第 1 页 126108 字节、第 8 页 222541 字节，均为合法 JPEG（magic `ffd8ff`），第 8 页中文与代码清晰、无方框；容器内 `alembic upgrade head` 到达 `e1f4b23c7d90 (head)`，`/api/health` 返回 200；`docker compose config` 解析出 api 39 个环境变量，16 个新键全部为 `""`。首次构建失败是 WSL 的 docker 代理指向 `127.0.0.1:7897` 而代理实际在 Windows 侧；开启代理后构建通过。

仍未验证：真实服务器部署里这些变量是否按预期注入、未内嵌 CJK 字体的 PDF 渲染结果（样本 `lecture1.pdf` 54/54 字体内嵌，`workflow.pdf` 76 个字体中 64 个内嵌，未内嵌的为西文标准字体；镜像未装 `fonts-noto-cjk`，遇到不内嵌中文字体的课件可能出现空白字形）。

TTS 真实联调（2026-11-14）：DeepSeek 的语音接口是 `POST /v1/tools/tts`，不是 OpenAI 的`/v1/audio/speech`，本轮加了 `REBORN_TTS_PROTOCOL` 适配。用官方接口实跑两组：80 字→17.08 秒音频（耗时 2.97 秒）；315 字→58.36 秒（stella，10.76 秒）、57.04 秒（tide，10.50 秒），RTF 约 0.17–0.18。音频结构经检查：文件长度与 RIFF 声明一致，峰值 28601/24642，RMS 3349/3075，不是静音或截断；DeepSeek 原本返回 `0xFFFFFFFF` 的长度占位已改写为实际值，因此页面时长走的是真实解码值而不是“4.2 字/秒”估算。体积代价：315 字一页 wav 约 2.8MB，103 页约 290MB。

转码实测（同一轮，镜像内以 uid 10001 运行）：`lame 3.100` 把 2,801,324 字节的 wav（58.36 秒）压成 701,280 字节 mp3，压缩比 4.0 倍，mp3 帧头 `fff3a4` 正常，页面时长仍取自转码前 wav 头部的真实值；单页转码约 0.4 秒，103 页整册约 70MB。中转方案对比后放弃 apt 版ffmpeg：那一层会让镜像从 663MB 涨到 1.26GB（+479MB），`lame` 只增加 11MB。没有 lame/ffmpeg 时保留原始 wav 并如实使用 `.wav` 后缀，不把 wav 冒充成 mp3。

音频链路端到端（同一轮，镜像内、真实 DeepSeek 调用）：给一页已接受的 80 字讲解合成音频，得到 17 秒音频并转成 201,600 字节的 mp3，落在 `narrations/1/1-94308ba517e4c789.mp3`，媒体类型 `audio/mpeg`；`read_audio` 读回同尺寸文件；重复生成返回 `reused=True` 且键不变（不重复计费）；改写讲解正文后 `current_audio` 变回 `None`，旧录音立即不可用。

仍未执行：在浏览器里点击“生成音频/试听”的真实交互（后台与学生两处只做过替换引擎的单测与真实文件渲染），以及 `POST .../audio` 在真实引擎下经 HTTP 层的调用（HTTP 层目前用替身引擎覆盖）。

## 阅读卡片与目录密度核验2026-11-14

PPT/PDF页面上方新增独立图标标题栏，视频/文本使用同一紧凑标题样式；PDF继续按扣除标题栏后的空间适应。快速跳转页面及视频列表采用40px桌面/44px触控最低行高、单行省略、细分隔和当前页底色，完整题名保留在title及可访问文本，重点标签独立。

学生端npm ci、typecheck、lint、40文件224项测试、构建通过；依赖审计与大块提示保留。Edge隔离夹具使用模拟资料元数据及课程目录真实PDF文件，验证四类标题、PDF绘制/跳页、当前项、40px行高、长标题省略、1553×1243/1920×1080/1280×720/800×900及200%字号无整页横向溢出，PDF画布保持在可用区域内、分页可达，零助教消息请求。视频仅验证不可用状态下的标题与片段列表，未重跑真实播放/观看计时。

初次浏览器脚本括号错误及修复命令参数错误在启动前失败；修正后窄屏等待因焦点保留在侧栏而超时，测试改为通过已有“课程内容”入口切回阅读，最终通过。证据：.tmp/reader-browser.cjs、reader-browser.log、reader-desktop.png、reader-200.png、reader-video.png及reader-ci/typecheck/lint/tests/build.log。未重跑后台/后端、完整arrival/账号切换、真实模型/评测、数据库迁移及课程导入；本轮无数据契约或课程内容变化。原有无关文档与piston-local改动不纳入提交。


17254本地预览仅重建Web，数据库、API与私有存储保留；学生页、教师入口和API健康检查HTTP200，日志.tmp/reader-preview.log。本轮按用户授权提交并推送reborn，包含此前五个待推送的界面提交。

## 学生工作区样式统一核验2026-11-14

日志、地图、图书馆目录、班级、设置和开发工具采用居中80rem×45rem上限，100%字号/1080P对应1280×720（含页头）；阅读、课程、练习和测验保留宽屏。共享15px正文、14px小标题/输入、16px标题图标、16px内容留白、10px卡片间隙及34px桌面/44px触控按钮；目录平面化，清理旧日志全局样式和重复对白覆盖。原有学习、角色、资料、判题与权限逻辑不变。

学生端npm ci、typecheck、lint、40个测试文件224项测试及构建通过。Edge隔离夹具使用实际组件和地图素材，覆盖1920×1080、1280×720、800×900、600×800；验证紧凑窗口上限、宽屏阅读/普通练习、200%字号、无整页横向溢出、日志追踪/密码表单/地图操作滚动可达及零助教消息请求。编程页回归通过Monaco行列、居中结果空态、15px题干、紧凑题目列表、快速跳转、Markdown/新对话与200%文字；对白桌面/窄屏布局和操作可达通过。测试使用模拟课程、用户和消息，不写真实学生记录、不发真实模型请求。

浏览器复核修正顶部焦点文字对比；最初地图截图等待只覆盖异步外壳，随后改为等待真实地图绘制完成重验。补充滚动检查曾使用不存在的.map-target选择器超时，修正为真实列表按钮后通过；一次重跑日志被前次进程占用，结束后独立重跑通过。证据为.tmp/unify-browser.cjs、unify-browser.log、unify-*.png、unify-coding-regression.log、unify-dialogue.log及unify-ci/typecheck/lint/tests/build.log。

本地17254预览仅重建Web服务，现有API、数据库和私有存储保留；world、teacher入口及API健康检查HTTP200，构建日志为.tmp/unify-preview.log。

本轮仅学生展示与规范变化，未重跑后台、后端、真实AI/Judge0/Pyodide、完整arrival恢复/账号切换、全新数据库迁移或课程重复导入；已有未覆盖边界继续有效。依赖审计及大块构建提示保留，未自动升级依赖。仅本地提交、不推送，原有无关文档和piston-local改动保留。

## 焦点外框与视觉规范核验2026-11-14

定位到全局3px金色轮廓、3–4px偏移与白色外光圈叠加输入区边框。学生端共享基础/游戏控件、开关、地图、对白与拖动分隔条清理外扩焦点框；输入只强调既有边线，助教复合输入只保留外层细线，键盘按钮有背景/文字反馈，拖动条保留伸长反馈。当前题号使用细边线、底色与下划线标识，不再外加轮廓。游戏规范汇总用户历轮明确的视觉方向，修正旧16px卡片圆角为10px。

学生前端npm ci、typecheck、lint、224项测试及构建通过；Edge真实组件/模拟活动验证输入聚焦时outline与box-shadow均为none、键盘定位无粗框，并复核像素头像、复制、列表、换会话及1553×1243、1280×720、800×900、200%文字布局。证据为.tmp/focus-*.log、focus-browser.cjs及focus-composer.png。17254仅更新Web，API配置与数据保持；没有调用模型。教师后台、后端、迁移、课程导入、真实评测与完整arrival未重跑，因为本轮仅修改学生端样式和文档。npm审计18项（1低/16中/1严重）及大块构建提示保留，未自动升级依赖。仅本地提交，原有无关改动保留。

## 助教密度与模型配置核验2026-11-14

助教标题及回复署名使用角色朝下静止像素帧，完整立绘为淡背景；标题与输入框收紧，输入14px、圆角10px、桌面发送32px、复制28px，触控保留44px。题目行40px、单行省略、题型居中，单目标去除序号并收紧进度。角色展示DTO包含精灵资源描述，未放置及私有素材均使用角色自身配置。

双前端 npm ci、类型检查、lint、测试、构建通过（学生224项，后台34项）。后端按 requirements.lock 使用 uv pip sync 安装，ruff通过；全量 pytest 564通过、19跳过，最后输出上限调整后相关27项重跑通过。新增真实SDK加MockTransport验证最终DeepSeek请求为max_tokens=512、reasoning_effort=none且不发送max_completion_tokens；未访问真实生成接口。首次 pip 安装因虚拟环境未装pip失败，改用uv；局部重跑曾因系统临时目录WinError5产生9项夹具错误，改仓库隔离basetemp后通过。全量测试2项SQLite日期适配弃用警告保留。

Edge使用真实OverlayHost/Monaco、模拟活动与消息验证单目标、题型居中、单行省略、像素头像及背景、紧凑复制/发送、Markdown、新会话、空态、行列和标题间距；1553×1243、1280×720、800×900与200%文字无页面横向溢出。像素资源单测覆盖私有Blob替换与卸载释放；后端覆盖未放置身份及私有资源描述。早期浏览器断言把等价CSS值0px误作0%，修正后通过。证据：.tmp/compact-browser.cjs、compact-browser.log、compact-chat.png、compact-list.png、compact-backend-recheck.log及dense/compact检查日志。

17254 API与Web已重建，数据库和存储保留；world、teacher和API健康检查HTTP200。用户手填的Key已加载，官方模型目录HTTP200且包含deepseek-flash；没有输出凭据或发起真实生成请求。真实模型流式回复、真实Judge0/Pyodide、完整arrival及真实账号切换未重跑；本轮无结构或课程包变化，未执行新PostgreSQL迁移及现场课程重导入。依赖审计和大块构建提示保留，不自动跨版本升级。仅本地提交，不推送；用户原有文档改动和piston-local未纳入提交。

## 学习侧栏与助教交互核验2026-11-13

本轮将练习目标与题目明细合为题目列表，快速跳转按题型展示编号和真实作答状态；资料保留原页码列表，视频提供起点及配置片段跳转。编程结果空态垂直水平居中，Monaco 显示实时行列，题干为15px基准，页头上下留白对称。助教支持用户名、Markdown回复、仅回复复制、Enter/Shift+Enter与输入法保护，以及显式创建新会话；失败保留原对话与草稿，切班或新会话后忽略旧异步返回。未新增重新生成接口。

双前端 npm ci、类型检查、lint、测试和构建通过：学生223项，后台34项。新增Markdown原始HTML/危险链接/图片禁用、新会话失败保留/切班隔离/延迟历史隔离、复制、输入法键盘保护、结果空态、题型状态及视频跳转检查。首次沙箱测试因Windows临时文件重命名EPERM未执行用例；正常环境重跑通过。Edge挂载真实OverlayHost与Monaco、模拟活动和助教接口，验证结果空态几何居中、行列变化、15px题干、页头对称、题目列表/快速跳转、Markdown聊天/新会话及1553×1243、1280×720、800×900、200%文字无页面横向溢出。

17254 网页容器已更新，world、teacher、API健康检查及新版练习/编辑器静态模块均HTTP200；数据库容器与数据保留，未刷新用户现有标签页。文档检查与diff空白检查通过，本轮仅本地提交，不推送。

本地证据：.tmp/support-tests.log、support-admin-tests.log、support-typecheck.log、support-lint.log、support-build.log、support-admin-*.log、support-browser.cjs/log、support-list.png、support-jump.png、support-chat.png、support-800.png、support-200.png。真实模型、Judge0/Pyodide、完整arrival主线、真实账号切换与后端安装/ruff/pytest/迁移/重导入未重跑；本轮不修改后端、API结构、课程包或学习数据。新会话与跨班隔离属于前端契约测试，不冒充真实模型验收。npm审计仍报告学生18项（1低、16中、1严重，严重项来自既有Monaco传递依赖dompurify）及后台15项；新加markdown-it无审计条目，不执行自动破坏性依赖升级。大块构建提示保留。

## 判题反馈与顶部导航核验2026-11-13

本轮为编程卡片与共享学习支持标签补图标；翻题移到顶部两侧，普通题中间显示题号/总数及可省略题名。公开用例全部完成前、正式提交发送与排队期间合并为“正在判题”，结果标签转圈并显示骨架屏；减少动效时停止动画。结果区区分通过、解答错误、执行出错和停止运行，以Case展示公开输入、实际/预期输出及错误文字。测验公开运行时隐藏顶部交卷操作，保留整卷锁定与提交规则。

双前端锁定npm ci、类型检查、lint、测试及构建通过；学生216项、后台34项。新增多用例等待、无隐式提交、错误输入及输出对照、正式结果切换、停止/卸载中断、排队只读结果和测验操作合并测试；更新反馈容器断言，保留HTML转义与隐藏字段不渲染检查。Edge以实际OverlayHost和Monaco、模拟活动/API检查顶部导航、普通题长标题省略、图标、骨架、错误结果，1553×1243、1280×720、800×900及200%文字无页面横向溢出。

证据：.tmp/judge-browser.cjs、judge-loading.png、judge-wrong.png、judge-error.png、judge-ordinary.png、judge-800.png、judge-200.png，以及 judge-test-final.log、judge-admin-test.log 等。17254前端容器已更新，world/teacher/健康检查及新版练习模块HTTP200；未刷新用户现有标签页。本轮浏览器使用模拟提交结果；真实Pyodide/Judge0、AI、完整arrival主线、账号切换和后端测试/迁移未重跑。API、课程包和学习数据未修改。依赖审计与大块体积提示沿用上一轮边界。

## Monaco与编程页面细化核验2026-11-13

本轮收紧共享页头、边距与学习支持标签，运行/提交增加图标，示例输入输出分块，提示采用图标分隔行，翻题改为居中低强调导航。用按需加载的 Monaco 0.57.0 替换 CodeMirror，清除旧依赖、样式及地图输入守卫选择器；JetBrains Mono 本地字体与许可随前端分发。

双前端 npm ci、类型检查、lint、测试、构建通过；学生211项、后台34项。Edge挂载实际OverlayHost，活动数据模拟，真实Monaco完成中文文本输入、撤销、切题草稿恢复、同屏单一模型、关闭后模型归零、Case切换、提示展开、横纵拖动、键盘微调及1920×1080、1280×720、800×900无横向页面溢出；200%偏好同步放大代码字体。证据为 .tmp/monaco-browser.cjs、monaco-1920.png、monaco-1280.png、monaco-800.png、monaco-200.png 及 monaco-*.log。自动注入中文文本不等于真实系统拼音输入法组合测试。

初轮失败：新版Monaco模块导出路径变化、Vite首次依赖优化重载、测试误定位只读IME辅助框、测试壳漏接关闭事件；按实际包结构及真实编辑焦点调整后通过。Monaco主块约2.75MB（gzip716KB），仅进入编程编辑器时加载；构建保留大块提示。依赖安装报告学生18项、后台15项审计告警，未自动跨版本升级依赖。

17254前端容器已更新；/world、/teacher/、API健康检查、Monaco主模块/Worker和字体许可均HTTP200。未刷新用户现有标签页。

本轮不改API、课程、账号或学习数据。未重跑后端pytest/迁移、真实Judge0、真实AI、完整arrival主线/账号切换、真实触控及系统输入法组合；模拟活动的界面检查不冒充这些验收。




## 全站字体密度与编程三卡片核验2026-11-13

本轮只修改前端界面：正文统一系统无衬线、标题加粗宋体，收紧卡片与控件间距；编程题拆成题干、代码、用例独立卡片，Case切换，顶部运行/提交，测验仍整卷提交。新增横向及纵向调节，学习区手柄统一细短、悬停伸长，比例保存在本机。

双前端npm ci、类型检查、lint、测试、构建通过；学生211项、后台34项。新增/扩展Case切换、顶部操作、只读撤销操作和分隔条键盘边界及偏好保存测试。Edge挂载实际OverlayHost及学习组件，模拟活动数据，检查1920×1080、1280×720、800×900布局、Case2、顶部操作、两方向鼠标拖动和键盘微调；另检查200%文字下按钮可见及无页面横向溢出。截图位于 .tmp/dense-1920.png、dense-1280.png、dense-800.png、dense-200percent.png；日志为 .tmp/dense-drag-browser.log、dense-drag-tests.log 等。模拟数据的布局测试不冒充真实主线或后端验收。

17254前端更新，未改课程、账号或学习数据。后端未修改，未重跑pytest、迁移、Judge0或真实AI；未重走完整arrival主线或真实触控设备。初轮类型检查发现分支收窄后的冗余Python判断，已移除并复测。保留既有构建体积提示。


## 统一题库编程题核验2026-11-13

本轮在 reborn 实现标准输入输出编程题：统一题库与混排练习、提示和初始代码、浏览器公开样例试运行、正式提交继续走 Judge0；旧服务端 run 模式返回422。课程包仍为v2，本地最新包 version 4 加入“向小镇问好”，不自动覆盖演示班级。

- 双前端 npm ci、类型检查、lint、构建通过；学生38文件210测试通过，后台10文件34测试通过。构建保留现有大块体积提示。
- 后端 uv pip sync 锁定依赖、Ruff通过；独立 PostgreSQL 完整 pytest：575通过、7跳过、2条SQLite datetime弃用提示。跳过为5项真实Judge0未配置和2项Windows符号链接不可用。
- 全新 PostgreSQL 迁移到 c421b8e091aa、重复upgrade、alembic check通过。新增字段保留现有数据。真实包重复导入、导出、再次导入的9类数据及41个资源校验值一致。
- Edge真实Pyodide测试通过：标准输入输出、错误反馈、死循环5秒终止、取消/销毁、禁止请求应用API。编程题组件实际运行样例通过；自动化证明运行不会触发正式提交事件。截图为组件级证据，不冒充完整小镇视口验收。
- 17254现有体验环境已更新，健康与题库页面HTTP200；保留课程、账号和学习数据。课程包仍由教师显式导入。

初轮失败保留：Windows默认GBK读取夹具、系统pytest临时目录权限、前端沙盒临时缓存rename受限；修正UTF-8、独立basetemp及正常执行环境后通过。第一次PostgreSQL专项因数据库名不满足reborn保护规则失败，改用独立reborn_programming_tests库后全量通过。运行器提前取消单测暴露初始化时机问题，已改为创建iframe前返回，复测通过。

证据：.tmp/programming-postgres-verified.log、programming-frontend-final-suite.log、programming-admin-test-latest.log、programming-runner-e2e.log、programming-browser.log、programming-browser.png、programming-migration.log、programming-package-roundtrip.log、programming-preview-deploy.log。全部为本地忽略证据，不提交数据或凭据。

未执行：真实Judge0执行沙箱、真实模型调用、S3/PPTX、完整arrival主线浏览器重走/账号切换恢复、多视口整页验收及生产部署。本轮浏览器验证是运行器与编程题组件专项，不能替代这些范围。公开试运行首次需要访问固定Pyodide CDN，失败明确显示，不回退到服务端。


## 课程默认答疑角色与环境收敛核验2026-11-13

按用户确认拆分角色资料和课程公共设置：独立“课程设置 → AI 助教”维护 default_npc_id、开关和预算；图书馆使用默认角色，活动 npc_key=null 继承、非空独立指定。删除角色页预算控件与旧运行字段，API/课程包拒绝 library_npc_id；迁移 f318a72c90de 一次性移除存量旧字段，保留其他配置与学习证据。素材和角色单一来源要求保持。

| 检查 | 本轮执行结果 |
| --- | --- |
| 双前端 | 重新 npm ci、类型检查、lint、全量测试和构建通过；教师 34 项、学生 207 项。最终占位文字修复后类型/lint与镜像构建再次通过。保留已有构建体积提示。日志 `.tmp/settings-admin-*`、`.tmp/settings-student-*`、`.tmp/settings-final-image.log`。 |
| 后端 | 镜像使用 requirements.lock 锁定依赖；Ruff 通过，最终独立 Linux/PostgreSQL 全量 **574 通过、5 跳过**，保留 2 条 SQLite 日期适配器弃用提示。新增覆盖课程默认与活动覆盖、清除覆盖、学生权限、修订冲突、非法/旧字段、默认角色删除阻断、继承活动包往返与存量字段迁移。`.tmp/settings-postgres-final.log`、`.tmp/settings-ruff.log`。 |
| 迁移 | 新建 PostgreSQL 数据库完整 upgrade、重复 upgrade、alembic check 通过；最终全量也覆盖已有记录升级和字段改名。17254 升级后结构无差异。`.tmp/settings-migration.log`、`.tmp/settings-demo-migration.log`。 |
| 浏览器 | 角色编辑 1 项、课程设置保存/刷新/冲突草稿/活动默认选择 1 项通过；arrival 完成、恢复和账号隔离、公共流程共 2 项通过，地图及图书馆单独复跑 2 项通过。截图实看新课程设置入口、默认角色和预算布局；证据 `.tmp/settings-browser-evidence/`、`.tmp/settings-browser-verified.log`、`.tmp/settings-map-browser.log`。 |
| 最新实课包 | data/course/python-town-chapter1 使用 schema_version=2、package_version=3；4 角色、9 流程、41 媒体。阅读活动继承默认，练习保留教练覆盖。独立数据库完成导入、重复导入、导出再导入，9 类领域内容及全部资源 SHA-256 一致。`.tmp/settings-real-package.log`、`.tmp/settings-real-roundtrip.log`。临时导出及数据库验收后清理。素材存在性检查 102 项通过。 |

首次全量出现 5 个 SQLite 迁移失败，原因是可空列变更重建活动父表时已有外键引用；改为迁移期间显式保护外键、结束检查完整性，专项 29 通过/1 环境跳过后全量恢复通过。浏览器首轮断言遗漏下拉箭头，修正断言后又发现空选项显示“请选择”，已明确显示“使用课程默认角色”并复测通过。地图并行导入一次超时，顺序复跑通过。保留初始失败日志，不以初轮结果替代最终结果。

当前体验环境只保留 pythontopia-npc-demo-20261113（17254/18255）及其数据库/存储卷，最终 API/Web 已更新，1 个班级、1 个课程、4 个角色保持；未重新导入或重置现场课程。升级前备份在 `.tmp/settings-demo-backup/`，原 17234 可恢复备份也保留。API 健康、/world 和 /teacher/settings 均 200。用户授权清理本项目其他 Docker 镜像/卷：旧环境 12 容器、22 卷和 34 个镜像清理项，随后删除本轮 QA 3 容器/2 卷及剩余旧标签；保留当前 API/Web 和共享 PostgreSQL 依赖，不触及 ChefMate、ZhiCluster 等其他项目。精确清单 `.tmp/settings-cleanup-old.json`、`.tmp/settings-cleanup-final.json`。

data/course 仅保留最新版包及运行锁文件；旧包、导出、imports 和 10 个旧交换/临时包目录清理。可编辑题源移至 data/authoring/python-town-chapter1，备份与验收日志保留。清理路径先解析验证，清单 `.tmp/settings-package-cleanup.json`、`.tmp/settings-exchange-cleanup.json`。课程媒体、题源、环境凭据和备份不进入 Git。

未执行真实 AI、远端 Judge0、S3、PPTX 转换服务、真实手机触屏或生产部署。5 项后端跳过为未配置的真实 Judge0 用例；本轮桌面浏览器覆盖不代表其他设备。提交与推送限定 reborn，原有无关文档段落和 deploy/piston-local 保留在工作区。

## 角色专属素材与提交核验2026-11-13

每角色独占一份自定义立绘和一份自定义精灵，上传暂存，保存成功后回收替换/删除的旧文件；失败删除进入持久队列重试，未采用上传超过 24 小时定期回收。跨角色引用被拒绝。素材改为带缩略图的限高下拉，移除来源/授权输入；地图左侧角色项统一样式并同步地图选中。上传期间禁止保存与取消，避免迟到响应改写已放弃草稿。图书馆配置归属未改，仅明确选择器含义，后续讨论。

- 双前端重新锁定安装、类型、lint、测试和构建通过，教师 34 项、学生 207 项。最终后台调整后再次检查，最终双端镜像构建通过。日志 `.tmp/npc-owned-admin-*`、`.tmp/npc-owned-student-*`、`.tmp/npc-owned-final-image2.log`。
- 锁定后端依赖的独立 Linux/PostgreSQL 全量 570 通过、5 个真实 Judge0 用例跳过，保留 2 条 SQLite 日期适配器弃用提示。最后保存异常隔离和导入闲置素材过滤后，专项 78 通过、2 个未启用 PG 环境变量的专项跳过；其 PG 场景已由前述全量覆盖。Ruff 通过。日志 `.tmp/npc-owned-postgres.log`、`.tmp/npc-owned-final-targeted.log`、`.tmp/npc-owned-ruff-final.log`。新增回归覆盖旧文件保留至提交、修订冲突、跨角色引用、删除阻断、过期暂存与文件删除失败重试；首次测试把历史删除阻断误写为 422，修正为接口实际契约 409 后通过。
- 最终镜像在独立 QA 的角色浏览器用例 1 项通过，覆盖私有上传、下拉预览、地图摆放/撤回与真实游戏角色身份，截图实看通过。日志 `.tmp/npc-owned-browser-final.log`，截图归档 `.tmp/npc-owned-browser-evidence/`。此前本轮完整 arrival、换号、包往返与迁移验收保留在下方，未因本次布局调整重跑整套主线。
- 17254/18255 已更新最终 API/Web，保留已有课程和保存记录；更新前备份 `.tmp/npc-owned-demo-backup/database.dump` 与 `storage.tgz`。双端入口 200，API 健康，alembic check 无差异。本次无新增结构迁移。独立 QA 停止并保留卷；17234 本次未更新或清理。

本次未调用真实 AI、远端 Judge0 或 S3，未做生产部署和真实手机触屏验收。课程、媒体、凭据及备份仍未加入 Git（data 跟踪文件为 0）。提交范围为本轮统一角色与后续批注修改；原有无关文档段落和 deploy/piston-local 保留在工作区，不纳入提交。不推送。

## 独立NPC演示环境2026-11-13

按用户要求启动 `pythontopia-npc-demo-20261113`，Web 17254 / API 18255，仅绑定本机。使用独立 `reborn-db`、`reborn-storage` 项目卷及 `.tmp/npc-demo-20261113-exchange/` 课程副本，沿用本轮已构建镜像，未连接或导入 17234 原班级。专用管理员登录验证通过；新建“NPC 新版体验班”，完整 v2 包导入成功，4 个已放置角色、9 个流程、第一章已开放。后台浏览器已显示独立班级和真实课程，API/数据库健康。

初次环境文件数组拼接未正确分行，修正并仅同步本轮新建数据库的专用密码后启动成功。报告 `.tmp/npc-demo-20261113-report.json`，操作日志 `.tmp/npc-demo-start-final.log`、`.tmp/npc-demo-prepare.log`；凭据仅在忽略目录，不记录到文档。此轮为部署与登录/导入冒烟检查，没有重新运行全量前后端或真实 AI/Judge0 测试。演示环境保持运行供用户体验。

## 统一角色与v2课程包核验2026-11-13

本轮在 reborn 工作区完成 NPC-01–05：统一角色身份、私有图片与包 v2、共享角色/地图草稿、服务端说话人解析、旧装饰游走和旧对白路径退场。独立角色名单、来源克隆/转换生产入口、图书馆旧标记兼容分支移除；测试来源留在 tests，唯一内置主线仍为 arrival。改名/换外观/人设不重置学习进度，移动与历史删除受保护。

| 检查 | 本轮结果与证据 |
| --- | --- |
| 双前端 | 均重新 npm ci、类型检查、lint、全量测试和构建；教师 34 项、学生 207 项通过。保留构建大块体积提示；最终日志 `.tmp/npc-admin-last-*.log`、`.tmp/npc-student-last-*.log`。 |
| 后端 | uv 同步 requirements.lock；独立 Linux/PostgreSQL 全量 **567 通过、5 跳过**，包含真实 PG 并发、重复导入、排序及上传等待锁时的 API 响应；最后移除旧角色 ID 回退后，角色/助教/图书馆专项 34 通过。ruff 使用仓库配置通过。`.tmp/npc-postgres-last.log`、`.tmp/npc-identity-verified.log`、`.tmp/npc-ruff-last.log`。Windows 较早一轮 550 通过、19 环境跳过，不替代最终 Linux 结果。 |
| 结构与保护 | 全新 PostgreSQL 升级、重复 upgrade 和 alembic check 通过；旧 NPC 非空时拒绝迁移且不删除原数据。角色空位置、跨班图片授权、失败上传、无效布局、旧格式拒绝、缺失资源、导出再导入、历史说话人删除阻断、人设不入模型上下文有自动回归。 |
| 浏览器 | 隔离 17244/18245 环境，Chrome 教师套件 6 通过、1 个未配置 PPTX 转换器跳过；学生主线/公共流程/地图 4 通过。上传并发修复后角色专项另 1 通过。覆盖目录包往返、arrival/活动等待/恢复/账号隔离、角色上传/预览/点放/拖动/撤回/条件/并发失败草稿。日志 `.tmp/npc-admin-browser-final.log`、`.tmp/npc-student-browser-verified.log`、`.tmp/npc-character-contention-final.log`。 |
| 地图与素材 | 2 地图、11 建筑、2 门户、1838 可达格、353 美术对象检查通过；素材审计读取新角色目录，102 个保留资源存在且精灵尺寸匹配。 |
| 私有真实包 | `data/course/python-town-chapter1/` 完整语义/资源校验通过：manifest v2、4 角色、9 流程、41 资源；原媒体全部保留，日常对白转公共范围流程，图书馆角色显式配置。未导入 17234 四个现有班级。 |

17234 预览项目 `pythontopia-preview-20261110` 按明确授权清理全部四个班级的课程。实际有 3 个 CourseInstance、9 个 NPC、15 章、48 资料、48 题目、6 活动、3 任务、11 流程；相关任务/事件/阅读/提交/测验/流程/体验进度与私有运行文件已清理。5 个账号、4 个班级、1 条正式成员关系、3 个体验身份映射及认证会话保留，前后内容指纹一致。data/course 下包与资源保留。升级至 ea30c9b172f4 后模型检查无差异，API 健康，/world、/teacher/ 与 /api/health 均 200，课程实例为 0。

可恢复备份为 `.tmp/npc-preview-backup-20261113/`：database.dump 已恢复到独立 reborn_backup_verify 并对账；停写后另存 database-final.dump。storage.tgz 的 41 个文件逐一核对 SHA-256 一致；checksums.json 保存归档哈希。before-counts/restore-counts/after-counts、cleanup-dry-run/cleanup-applied 保存作用域、删除数量和保留指纹，原素材未进入 Git。原代码/包 YAML 的退场备份另在 `.tmp/npc-unification-before-retirement/`。

过程中如实保留的失败：旧测试绕过流程检查点被新引擎拒绝，已改为流程推进或与测试目的相关的位置目标；Windows 目录原子改名短暂锁占用，增加仅针对共享冲突的有限重试后通过。首次浏览器缺少随 Playwright 下载的 Chromium，改用本机 Chrome；地图文字拦截邻格点击已修复，测试拖动使用滚动后的坐标。Linux 首轮缺 Dockerfile/pyproject 和 SQLite 子进程环境导致 5 项失败及无配置 lint 报告，补齐后最终全量通过。初次角色小屏截图被欢迎层遮挡，修正测试等待后重拍，不据该首轮截图声明对白视口通过。

最终实看教师 1920×1080、1024×768 与学生 1280×800、390×844 截图，私有立绘、改名和对白可见，证据在 `.tmp/npc-character-browser-final-evidence/`。小屏为浏览器视口模拟，未据此声明真实触屏验收。地图用例原先等待已移除的书架提示对白，改为直接核验流程打开图书馆。并行复测又发现素材上传在异步处理器内等待同步课程锁导致整个 API 事件循环阻塞；改为工作线程处理同步上传，新增锁等待期间 API 响应回归，并复跑角色上传与主线地图。一次重跑遗漏隔离包环境参数，补齐后通过。

后端 5 个跳过均为未配置的真实 Judge0 用例；教师浏览器 1 个跳过为 PPTX 外部转换。本轮未调用真实 AI、S3 或远端 Judge0，未做真实手机触屏、原 GPU 黑线或生产部署验收。人设仅存储，不宣称已产生个性化 AI 回复。未提交或推送。

收尾使用最终镜像重跑地图/图书馆浏览器 2 项通过（`.tmp/npc-final-library-browser.log`）；17234 再次只读对账账号、班级、成员和体验身份指纹一致，课程实例仍为 0，结构无差异，挂载的真实包仍为 4 角色/9 流程/41 资源。证据 `final-live-counts.json`、`.tmp/npc-final-live-migration.log`、`.tmp/npc-final-live-package.log`。文档检查 22 文件/287 链接/78 锚点、差异空白检查、地图和素材检查通过。独立 QA 验收后停止并保留数据卷，17234 继续运行。

## 课程包暂存自动清理核验2026-11-13

API启动及每10分钟清理已应用/过期预览文件，每批最多100条；超过24小时的孤立imports副本与已知工作目录一起回收。正式导出、共享课程包及有效预览保留，数据库回执继续支持幂等重试。清理与交换写入使用共享目录的进程间排他锁，清理忙碌时跳过，文件删除失败后重试。

Linux隔离容器后端全量541通过、17按环境条件跳过，保留2条SQLite日期适配器弃用提示；覆盖过期/完成回收、有效副本保留、孤立文件、失败重试和链接路径保护。Windows最终专项4通过、1因链接创建权限跳过，另验证真正跨进程锁竞争。改动Ruff、文档和diff检查通过。首轮Windows交换套件39通过、3跳过、1导出失败，单项复跑通过；Linux首轮因指定夹具尚未生成发生31个初始化错误，补齐隔离合成夹具后全量通过。日志在 `.tmp/cleanup-tests.log`、`.tmp/cleanup-linux.log`、`.tmp/cleanup-linux-full.log`，镜像构建在 `.tmp/cleanup-build.log`。

本地17234 API已更新，启动清理后imports由17项/5195.8 MiB降至5项/3474.2 MiB，回收约1.68 GiB。PostgreSQL预览记录保持9条，其中4条已回收文件、5条继续保留，符合清理条件的未回收记录为0，健康接口ok。本轮无模型迁移及前端改动，未重跑双前端和浏览器主线、独立PostgreSQL全量、真实AI/Judge0。

## CI 真红修正核验2026-11-13

远端 `reborn` 自 11-10 起连续 5 次 CI 失败，逐次读日志后确认与本轮改动无关，是两处测试缺陷。

- **后端**（真实 PostgreSQL + 561 passed 的 CI 环境里唯一失败项）：`test_exchange_lock_excludes_another_process` 用 `subprocess` + `sys.executable -c` 验证跨进程文件锁，子进程 pwd 是仓库根，拿不到 pytest 注入的 `pythonpath=backend`，于是 `ModuleNotFoundError: No module named 'app'`。本机复现方式与 CI 一致（从仓库根 `pytest backend/tests/... `），修前 1 failed、修后 1 passed；`test_course_cleanup.py` + `test_course_exchange.py` 从仓库根整跑 41 passed / 3 skipped。
- **浏览器**：`admin/e2e/teacher.spec.ts` 在 `744d2734` 把“课程默认答疑角色”从课程编辑器移到“课程设置”后仍找 `getByLabel('图书馆助教')`，该下拉已删除，超时 15s 必然失败。改为发布课程后到“课程设置”选默认答疑角色并保存。**未本机执行浏览器用例**（需要整套栈），等 PR 的 browser 任务确认。
- 前端/后台 `typecheck`、`lint` 通过；`ruff check` 通过；`git diff --check`、`check-docs` 通过。
- 未执行：完整 Playwright 两套回归、真实 PostgreSQL、部署。

## 课程包表格与导出名称核验2026-11-13

课程包改为可搜索表格，展示名称、包创建日期、章节数、题目数及文件夹名，移除原下拉框和底部分隔线。列表只读取章节文件与题目索引；新导出目录包含安全课程名、北京时间和短随机后缀，保留班级前缀及已有目录兼容。

教师端锁定安装、34项测试、类型检查、Lint和构建通过；后端改动文件Ruff及课程交换37项回归通过，2项真实PostgreSQL测试按环境跳过。首次后端测试因系统临时目录权限失败，改用项目内独立目录后通过。证据在 `.tmp/package-table-final.log`、`.tmp/package-table-admin.log`、`.tmp/package-table-ci.log`。本地17234已重建API和Web，健康接口正常，浏览器1707×927实测完整名称、日期、7章/42题及目录名可见。构建日志 `.tmp/package-table-build.log`，锁定安装镜像层复用缓存。未重跑全量后端、学生端单测、独立空库/重复seed、端到端导入往返及真实AI/Judge0。

## 问题清单修复核验2026-11-13

范围：`reborn` 工作区 `953b8847` 之上的工程修复第一批（问题清单第 1、3（字符串部分）、4、6 条）。未改后端代码，未部署，未改数据库结构。

- 前端：`typecheck`、`lint`、212 项单测、`build` 通过。`e2e/worldConfiguration.spec.ts` 的书架断言改回“打开阅读文库”（删掉不存在的“先弹对白”期望）。**该 e2e 未在本机执行**：需要完整 API+双前端栈；依据是 CI 失败产物（页面快照显示工作区已直接打开）与 11-08 绿灯版同位置断言，实际结果待推送后的浏览器任务确认。
- 部署配置：本机无 Docker，仅做 `compose.yml` 的 YAML 解析与 `.env.example` 键覆盖检查（Compose 引用的变量都有对应键，含 `REBORN_COURSE_EXCHANGE_DIR`）。未 `docker compose config`、未启动容器。
- 文档与门禁：`node scripts/check-docs.mjs` 的浅克隆降级路径与 `--fetch` 全量路径均通过；`git diff --check` 干净。CI 新增 `docs` 任务执行 `--fetch`。
- 未执行：真实 PostgreSQL 空库迁移/重复 seed、浏览器全量回归、外部 AI/Judge0、任何服务器部署、ZONOS3 语音基准（`deploy/tts` 脚本未运行）。
- 已知未归属问题：`tests/test_course_exchange.py` 与 `tests/test_course_cleanup.py` 一起跑时偶发 1–2 例失败（`422 课程包读取失败`，用例每次不同）；在提交 `953b8847` 上连跑 3 次为全绿，在合并我的改动后失败率升高，疑与新增的文件锁 `exchange_guard` 和后台 `cleanup_worker` 的交互有关，尚待隔离验证；本批已把触碰该路径的后端改动挪出，不做结论。

## 目录课程包与多轮对白提交核验2026-11-13

浏览器夹具改从私有交换目录选包。隔离 SQLite 环境完成空库迁移，Edge 下教师端多轮对白编辑和目录包导出、重新导入回归通过，学生端主线完成及账号隔离回归通过，各运行一项。教师端首轮因仍引用旧的对白输入框标签失败，改用逐轮输入框后复测通过。本轮未重跑全量套件、真实 PostgreSQL、外部 AI 或 Judge0。

## 多轮对白交互核验2026-11-12

- 学生端将动画启动统一到完整属性更新后的当前会话、修订与文本监听，取消角色同步监听中读取旧文本的启动路径。同一节点续轮、打字中替换文本、点击显示全文、末轮选项和减少动效均有组件回归；既有恢复、换号与异步导航测试一并通过。
- 后台对白改为逐轮输入框，支持增删，已有空行文本自动拆分，保存沿用原课程包字段和总计8000字符限制。组件测试覆盖旧内容加载、增删修改、重新打开、单轮保留及粘贴换行；任务和助教标签补齐图标。按 React 检查清单复核标签关联、受控输入、按钮名称和装饰图标隐藏。
- 双端本轮重新执行 npm ci、类型检查、Lint、全量测试和构建：教师端34通过，学生端212通过。学生端首轮测试因 Windows 沙箱临时文件rename权限失败，沙箱外复测通过。证据：`.tmp/dialogue-admin-tests.log`、`.tmp/dialogue-frontend-tests.log`、`.tmp/dialogue-frontend-tests-outside.log`，构建日志为`.tmp/dialogue-{admin,frontend}-build.log`；保留既有大包体积提示。
- 本轮未修改后端和数据格式，不重跑后端、迁移、seed或外部AI/Judge0检查；未执行真实学生完整剧情、换号及多视口端到端回归。本地17234仅重建更新Web，双端HTTP入口200；已登录浏览器实测旧两轮对白自动分框、添加按钮、逐轮删除控件和四标签图标正常显示，保存按钮保持禁用，未改课程或游玩进度。学生打字动画通过组件计时器回归验证，未推进用户现场存档。文档检查和差异空白检查通过。

## 目录课程交换与多轮对白核验2026-11-12

- ZIP上传关闭，目录预览直接创建受限私有副本和内容指纹，应用时复验指纹、授权、修订与学习影响。导出原子写入交换目录，本班导出目录按班级隔离；历史ZIP预览保留应用兼容。第一章目录 `data/course/python-town-chapter1/` 保留41媒体与42题，剧情移除固定题量和知识点数量，5个流程共使用44个就近结束节点。
- 对白节点按空行逐轮展示，服务端保存轮次，末轮才显示选项、触发交谈检查点。专项覆盖刷新、重复事件与末轮才解锁；全量后端535通过、18按环境条件跳过，保留2条SQLite日期适配器弃用提示。日志 `.tmp/directory-backend-full.log`。目录交换专项36通过、2个真实PostgreSQL专项未配置跳过，日志 `.tmp/directory-exchange-final.log`。
- 双前端锁定安装、类型检查、Lint及构建通过；教师端32测试通过，学生端210测试通过。学生端首次沙箱临时缓存rename权限错误，沙箱外复测通过，日志 `.tmp/directory-student-tests.log`。旧上传/下载交互测试已按目录接口更新，首轮失败记录保留。后端锁定依赖与改动文件Ruff通过，文档和差异空白检查通过。
- 本地17234已重建部署API和Web，交换挂载改为 `data/course/`，已有imports迁入新目录。浏览器实测新版页面无ZIP上传、目录选择、动态等待秒数、完整媒体包差异预览，以及导出成功返回 `data/course/export-4-e5b1c21ac9da4767aeeaa4b9edeb1a58`。核对服务器目录包含manifest与全部内容目录，健康接口ok。当前班级仅生成预览与导出，课程应用仍待教师确认。
- 未新增数据库结构迁移；本轮未额外执行独立PostgreSQL空库/重复seed、完整浏览器剧情与换号回归、媒体全程播放或真实外部AI/Judge0。目录校验仍需复制与校验媒体，等待反馈显示实际用时，没有计算完成百分比。

## 课程发布与包目录核验2026-11-10

- 独立 PostgreSQL 和已编译双前端的桌面 Chrome 端到端验收通过。覆盖从挂载目录选包、差异预览和确认导入，在活动中跨章节勾选并确认持久化，以及从真实游戏会话产生保存警告、取消保留会话、确认后清理并保存。仅在新建验收班级实际执行重置。证据见 `.tmp/reborn-publish-browser.log`。
- 锁定后端依赖后，独立 PostgreSQL 空库迁移、Alembic 对照、Ruff 和两次测试课程 seed 通过。完整套件 548 项通过、5 项未配置真实 Judge0 跳过，保留 2 条 SQLite 日期适配器弃用提示。覆盖确认令牌过期、同班无关任务与跨班保留、成绩和阅读证据保留、课程包更新保护及跨章知识点包往返。证据见 `.tmp/reborn-publish-backend-final.log`。首次容器 Ruff 漏挂项目配置，补挂后通过。
- 后台锁定安装、类型检查、Lint、34 项单测、构建通过。学生端锁定安装、类型检查、Lint、208 项单测及构建通过，随后增加的旧会话恢复分支通过 16 项会话专项与最终镜像编译。文档和差异检查通过。
- 最终镜像的桌面学生回归 2 项通过，覆盖无章节公共流程、第一章对白和活动恢复及账号隔离。后台浏览器首轮 4 项通过、1 项视频超时、1 项未配置 PPTX 转换跳过。将无头录制替换为可解码的自制 VP8 样本后，视频上传、私有预览与播放复测通过。证据见 `.tmp/reborn-publish-student-browser.log`、`.tmp/reborn-publish-admin-chrome.log` 和 `.tmp/reborn-publish-video-chrome.log`。
- 17234 本地预览只更新 Web 和 API 并提供已存在的文本课程包。原数据库和卷保留，班级、账号、课程实例、成员、学习事件、剧情会话和世界标记数量一致。健康接口、后台三个入口和游戏入口均返回 200。现有班级只读检查目录，重置和导入验收均在新建班级进行。

## 教学流程校验与地图核验2026-11-10

- 17234 预览的对白 500 来自 `world_flags.key` 原有 100 字符的限制。教师编排的 NPC、流程和任务目标 ID 拼成完成记录后超限。独立 PostgreSQL 上复现旧版本 500，迁移扩到 320 字符后，同一会话继续成功，重复请求只记录一次完成事件。剧情及迁移专项 75 项通过。
- 后端校验、流程保护、任务和助教专项 110 项通过，覆盖结构化定位、旧入口 ID 映射、校验与保存保护一致以及进行中流程的阻挡提示。正在进行或等待活动结果的会话会保留，允许重命名流程和编辑其他不冲突的内容。
- 桌面 Chrome 实测游戏同源的小镇与图书馆底图、拖画 3×2 格区域、旧版「东侧练习场」查看属性与课程未保存状态。地图专项 2 项、学生地图专项 17 项通过。学生端类型检查、Lint 和 208 项单测通过。
- 后台类型检查、Lint、30 项单测及双前端镜像构建通过。隔离环境中 Chrome 后台用例 4 项通过，涵盖添加入口、校验定位、分层 NPC 选择、手动连线、保存和课程包往返。视频专项在 Chrome 和 Edge 均等待播放器就绪超时，Edge 单独复跑也超时。脱离应用复用 Edge 的测试录制器，仅生成 110 字节且浏览器无法直接解码，因此本轮未核验真实视频播放。PPTX 外部转换跳过。学生端 Edge 公共流程及地图用例 3 项通过。证据见 `.tmp/authoring-followup-chrome.log`、`.tmp/authoring-followup-final-video.log`、`.tmp/authoring-followup-video-fixture.log`、`.tmp/authoring-followup-student.log`。
- 17234 更新 API 与 Web，保留原数据库和存储卷。迁移到 `c34d851ae60b`，Alembic 对照通过。迁移前后班级、账号、课程实例、成员、学习事件、剧情会话及世界标记计数一致。健康接口、教师编辑器和游戏入口均返回 200。另在预览中只读复测实际底图、旧交互属性和公共／章节两层菜单，证据见 `.tmp/authoring-followup-preview-smoke.log` 和 `.tmp/authoring-followup-flow-smoke.log`。剧情推进只在隔离数据上验证，原有学生剧情保持原位置。

## 四标签编辑工作区核验2026-11-10

- 后台类型检查、Lint、28 项单测与静态镜像构建通过。独立 Docker 环境的桌面 Chrome 回归 4 项通过，覆盖右键属性、独立新增、手动连接入口与流程、任务依赖、取消编辑、保存和课程包往返。真实视频首轮等待就绪超时，同一构建在 Edge Chromium 的视频专项通过。外部 PPTX 转换专项跳过。
- Chrome 的 20 节点夹具在一次拖动中采样 74 帧，拖动节点及静止节点均无隐藏帧，松手只提交一次坐标。另验证点击与拖入积木、键盘移动、右键与 Shift+F10 属性及删除连线。地图和助教在 1674×1244 的浏览器检查覆盖选对象、拖出 3×2 区域、绑定流程、建筑和角色条件入口及助教默认角色。任务图检查新增独立目标、手动依赖和拒绝循环。
- 17234 本地预览只更新 Web 容器，API 和数据库容器 ID 一致。用现有课程只读检查左侧积木、右键浮层及 Escape 关闭。证据位于 `.tmp/scratch-admin-unit.log`、`.tmp/scratch-admin-chrome.log`、`.tmp/scratch-admin-video.log`、`.tmp/scratch-flow-browser.log` 和 `.tmp/scratch-preview-inspector.png`。本轮仅改后台，未重跑后端和学生游戏全量回归。

## 教师后台批注核验2026-11-10

本轮覆盖 shadcn/ui 下拉、线型标签和筛选表格、学生单条及批量操作、批量删除部分失败、教师体验存档、课程包传输进度和 React Flow 教学画布。

- 后台锁定安装、类型检查、Lint、27 项单测和编译构建通过。隔离 Docker 上的桌面 Chrome 浏览器 5 项通过，外部 PPTX 转换专项跳过。覆盖行菜单、跨页选择、题目批量删除引用检查、PDF/视频私有预览、拖线保存和课程包往返。教师体验身份在独立标签打开并恢复，同一浏览器的教师会话仍可用。
- 学生端类型检查、Lint、208 项单测和编译构建通过。隔离 Docker 上的桌面 Edge Chromium 回归 11 项通过、8 项按条件跳过。完整第一课和生产配置下普通学生重置权限均通过，最终镜像另重跑无提示打开工作区专项通过。
- 课程包进度在 Chromium 的限速 HTTP 夹具中验证 8 MiB 分段上传与下载、真实字节和 ZIP 内容一致。完整 ZIP 仍由浏览器收齐后保存，2 GiB 上限、低内存和断点续传未实测。
- PostgreSQL 空库升级、Alembic 模型一致性、重复导入及包交换并发专项通过。后端全量容器首轮 526 项通过、5 项外部判题跳过、5 项因缺少 Dockerfile 挂载或生产配置拒绝 SQLite 而失败，补齐测试环境后原 5 项通过。体验后端测试另覆盖真实任务节点、位置与解锁持久化、名册和报告隔离、自身确认重置及父会话撤销。新转换包的默认打开页面动作省去旧提示，原包内的说明仍可在编辑器转成对白。
- 初轮浏览器检查记录保留在 `.tmp/feedback-admin-e2e.log`、`.tmp/feedback-admin-edge.log` 和 `.tmp/feedback-admin-final.log`。默认 Chromium 可执行文件缺失，旧控件断言随后已调整，Edge 原生 PDF 查看器与该用例的 Chrome 查看器断言不同。最终证据位于 `.tmp/feedback-admin-chrome.log`、`.tmp/feedback-student-edge.log`、`.tmp/feedback-student-final.log`、`.tmp/feedback-backend-pg-full.log`、`.tmp/feedback-backend-pg-rerun.log`、`.tmp/package-progress-browser.log` 和 `.tmp/feedback-admin-chrome/`。
- 将验收过的 Web 和 API 镜像应用到现有 17234 本地预览。更新前保存私有 PostgreSQL 备份，原数据库与存储卷继续使用。迁移到 `5a3d09d4c2b1`，Alembic 对照通过。升级前后班级、账号、课程实例、成员、学习事件和提交计数一致。健康接口、教师后台三个深链和学生入口均返回 200。

## 课程体系与React后台核验2026-11-10

范围为reborn本轮最终工作区，P1–P6已按[阶段计划](content-authoring.md#分阶段实施计划与验收标准)实现并完成下述验证。未更新原15176预览、用户服务器、生产、main或已有持久卷，未提交/推送；运行验收只使用本轮新建环境。未配置外部能力不算通过。

| 阶段 | 本轮实际结果 |
| --- | --- |
| P1 契约与预检 | 固化完整ZIP/稳定ID/世界边界与清单限制；离线预检拒绝非法路径、资源哈希/大小、未知格式和超限。Windows符号链接用例跳过后，在本轮Linux容器用真实符号链接补验拒绝，未读取链接指向内容。 |
| P2 班级实例与迁移 | CourseInstance、九类内容归属、严格班级授权、旧来源/学生证据保留映射与显式check/apply完成；未实例班级409，无共享来源运行回退。真实PostgreSQL新库迁移、Alembic模型对照、重复导入与并发门闩通过。 |
| P3 包交换与数据外置 | 差异/学习影响预览、修订/哈希/幂等事务导入、一致完整导出、私有媒体及旧来源离线转换完成。完整包保留草稿、未选用题目和未归类内容；跨班或破坏历史/活跃会话的更新拒绝。原16个课程源文件逐一比对，完整保留在data/course/legacy/；固定世界移至shared/world。 |
| P4 React后台 | 独立React + TypeScript + shadcn/ui真实业务重建；班级/名册/账号/章节/KC/资料/题库/活动/包管理通过真实浏览器操作。草稿取消、旧账号迟到响应和保存期间输入保护有行为测试；时区/DST保留原业务规则。 |
| P5 编辑器与运行 | 公共/章节流程、条件树、任务DAG、NPC出现、对象/区域交互、建筑策略、助教教学配置、图布局和真实学生只读预览完成。学生浏览器验证完整arrival、活动等待/恢复、窄区域、跨图落点、刷新不重放、账号切换及普通行为零AI调用。 |
| P6 退场与部署 | 旧Vue后台路由实现、组件、主题及专用依赖移除，保留原许可；测试按新业务替换。双前端Docker/Nginx部署、同域深链与静态资源、全新PG、空库启动、完整包导入/编辑/导出和重启保留均实测。 |

### 代码、数据库与构建

- 后端锁文件74个依赖安装、全app/tests Ruff通过。完整测试527通过、6跳过、2条既有SQLite datetime adapter弃用警告，日志`.tmp/p6-backend-final.log`。6跳过为Windows宿主符号链接权限1项和未配置live Judge0的5项；Linux符号链接补验另见`.tmp/course-linux-symlink.log`，不改写原pytest统计。
- 最后修正首次开启剧情中途commit释放用户锁的问题后，真实PG + story/enrichment/world重点复验91通过，4个并发首次请求返回同一会话，4个并发推进只写一次证据；日志`.tmp/p6-start-race.log`。完整527项是该单行事务修复前快照，不能称为修复后再次全跑。
- 全新PG迁移到a31d5f9b7c20、再次upgrade、Alembic check及两次合成来源seed一致，另2项通过，见`.tmp/p6-fresh-head.log`。已应用a31结构未在后续修复中修改。课堂实例/包导入/流程重点包含权限、真实证据、幂等和并发校验；SQLite不替代PG结果。
- 学生前端npm ci、typecheck、lint、208项测试（35文件）、build通过；React后台同组检查、25项测试（6文件）、build通过。React生产依赖审计0，开发工具依赖仍有15项（2low/13moderate）报告；Vue既有工具链16项moderate及Phaser大chunk提示保留，没有强制升级无关工具链。
- 固定世界及资源检查通过：2图、11建筑、2门户、1838可达格、353美术对象；68项资源存在检查；图书馆10书架、38原素材对象、137可达格。资源存在不等于外部服务或设备兼容验收。

### 编译产物真实浏览器与保留验证

最终环境为本轮独立Docker项目pythontopia-course-release-20261110、Web17234、API18235、新PostgreSQL与私有存储/交换区；无旧数据库/卷复用。1920×1080系统Chromium浏览器，测试创建全新班级/学生，不复用真实记录。

- 学生完整19条：10通过、9条件跳过、0失败，2.9分钟。覆盖主线、正式测验、阅读位置、工作区、200%字号、地图/建筑/公共区域、恢复及账号隔离。9跳过为7项需要原课程PDF/视频的用例、开发重置和独立生产配置2项；本轮后端测试覆盖相应授权开关，不把未执行浏览器用例算通过。统计、具体原因及截图在`.tmp/student-compiled-final-evidence/RESULTS.md`和其test-results/。
- 教师完整5条：4通过、1条件跳过、0失败，48秒。覆盖60人名册分页/跨页选择/筛选清空、CSV导入、真实密码重置和移出后账号保留、题目私有答案/两活动有序复用、TXT转换及同ID替换、私有PDF真实阅读器页码、WebM真实播放、NPC/流程/任务/角色编辑及真实学生预览、ZIP导出→新班导入→独立修改。唯一跳过为未配置外部PPT转换器。证据`.tmp/admin-release-browser/`。
- 已实际查看最终公共流程室内对白、恢复阅读43页/3项阅读目标、200%设置、流程画布与私有PDF阅读器截图；文字和控件可读，单一游戏画布/工作区，室内箭头正常。游戏和后台分别加载各自框架/主题，深链刷新和跨入口导航正常。
- Docker空库可启动；真实文本ZIP导入、幂等确认、作者编辑、私有no-store导出、世界编辑目录及API权限通过，重启后编辑标题与导出保持，见`.tmp/course-release-smoke.log`、`.tmp/course-release-persistence.log`和最终构建日志。镜像不含课程源，data/course作为私有绑定目录，已有库/文件卷未改。
- 原16文件的移动保留清单及哈希在`.tmp/course-source-preservation.json`。真实旧文本课程离线转换产物为`data/course/python-foundations-text-20261110.zip`，包含7章/561页/65知识点及当前主线；未提供原PDF/视频，产物明确为提取文本课程，不能声称已恢复原全媒体包。

### 替代关系、失败与限制

旧accountManagement/adminManagement由React accounts/teacher套件及会话/草稿行为测试替代；旧contentLibrary/curriculumAuthoring由content/teacher中的知识点、题库、活动复用及跨班编辑覆盖；旧mediaAuthoring由真实TXT/PDF/WebM用例替代；旧presentationAuthoring的外部转换门槛在React media套件保留。旧Vue包装组件没有机械迁移，相关许可保存在frontend/legal/。

首轮Windows沙箱临时文件/浏览器启动限制、Vite占用依赖导致的EPERM、旧测试全局ID/共享来源假设、地图书架操作确认的旧断言都保留失败日志。真实浏览器还抓出建筑交互使标签消失、1.5秒位置采样漏过窄区域、跨图落点未触发三个问题，修复后最终编译组通过；证据保留在`.tmp/student-broad-browser-first/`、`.tmp/student-region-regression/`，不回写首轮为成功。完整后端首轮523通过/1旧None断言失败也保留于`.tmp/p6-backend-full.log`，最终结果独立记录。

未验证真实模型、Judge0、S3、原课程全媒体、外部PPT转换、生产发布、移动设备或真实课堂容量；AI生成题目仍须审核，未伪造外部成功。远端CI配置已更新但本轮未推送/触发；上述通过均为本机隔离实测。文档链接/锚点/归档完整性和git diff --check已通过，最终范围复核只含授权改动，保留无关deploy/piston-local/。

验收结束后已按PID/命令行核对停止本轮原生API与两个Vite服务，并停止本轮两个隔离Docker项目的容器；新建持久卷、私有课程、截图及日志保留。原15176预览和其他既有服务未操作，17234不作为持续运行的预览地址。

## 本地预览启动与教师登录核验 2026-11-10

启动 Docker Desktop 后，现有 `pythontopia-story-qa` 的 Web、API 和数据库恢复运行。核验本机 15176 的 `/world` 返回 HTTP 200、`/api/health` 返回 `ok`。使用账号管理命令创建一位教师，经登录 API 确认返回 `teacher` 角色后退出测试会话。未执行浏览器交互或全量功能回归。

## 本地预览账号清理核验 2026-11-10

按用户授权，只清理本机 `15176` 的 `pythontopia-story-qa`。事务内删除 2 个教师、145 个学生及其关联的 12 个测试班、会话和学习记录。核验账号、班级、会话均为 0，保留 41 份资料和 48 道题，API 健康检查通过。数据库和私有存储备份在 `.tmp/pre-account-clear-20261110-003807/`；已校验数据库归档可读取、私有文件归档与原卷一致。未重新创建账号或重跑功能验收。


## 本地预览更新核验 2026-11-09

本机独立预览 `pythontopia-story-qa` 从 `0caa9974` 快进到 `74fd0290` 后重建 API 与 Web。升级前的数据库与私有存储备份在 `.tmp/pre-content-library-20261109-190426/`。首次启动因 `ch2-code` 引用的 `course-476` 仍归属同章 `archive-476` 而中止。只读确认归档活动没有提交、进度、测验、助教会话或学习记录后，在本地预览事务中修复该题归属，重启后迁移到 `d7213a8f9c06`。Web、API、数据库均运行，`15176/world` 与两条 API 健康入口均返回 200。未运行浏览器登录、课程交互、媒体播放、真实 AI、Judge0 或全量回归。

四套已停止的独立验收环境清理了 12 个容器、8 个测试卷；逐卷归档并与原卷比对，备份位于 `.tmp/retired-qa-volumes-20261109-190517/`。共删除 17 个 Pythontopia 旧镜像，范围为废弃架构、旧预览构建与上述独立验收镜像。


## 独立内容库核验2026-11-09

基于`0caa997`进行资料/题库独立化：多章关联、唯一有序活动选题、两种题目来源和审核状态；后台独立管理，启动不再自动导入课程。迁移头`d7213a8f9c06`。当前只在隔离环境验证，没有更新用户服务器或清理线上数据。

- 迁移专项4项通过，覆盖新旧SQLite、关联复用、原题目/答案/提交/锁卷凭据保留和外键检查；空库Alembic metadata check无差异。SQLAlchemy/Alembic官方Context7文档核对通过；未配置真实PostgreSQL，相关语义仍待CI/独立PG验证。
- 首轮核心题库/学习路径/迁移/媒体专项35项通过，日志`.tmp/content-core-check.log`；开发清理与外部导入等生命周期专项38项通过。后续完整检查结果在本节补充，不把阶段通过算成整体验收。
- 对本会话创建的旧开发库做独立副本升级，48道旧题保留6道当前第一堂课题目，清理42道废弃题与40个归档活动；再次显式导入两次后仍为6题、0归档活动，6条选题关系，无外键问题，alembic check通过。报告`.tmp/content-upgrade-qa.json`。该副本没有正式学生提交，不代表真实成绩库已执行清理。
- 后端最终全量365通过、10跳过，Ruff通过。日志`.tmp/content-backend-final.log`。5个PostgreSQL并发/迁移专项和5个真实Judge0结果专项因未配置跳过；2个Python3.12 SQLite datetime弃用警告。模型工厂编译测试仅移除当前终端代理环境变量以避免缺少socksio，不发真实模型请求，不修改依赖或外部服务。
- 新空库完成迁移后实际启动Uvicorn两次，健康检查均ok，章节/题目/资料/活动仍为0，证明启动不执行课程导入；证据`.tmp/content-empty-startup.json`。
- 前端npm ci、typecheck、lint、production build通过；39文件244项单测通过，覆盖独立题目CRUD、多章节、两来源/审核、同义答案及旧value结构保留、PUT不带ID、选题排序/100题限制、共享只读、失败草稿/异步预览与分页。日志`.tmp/content-frontend-final/`，构建保留既有Phaser大chunk提示。
- 已更新旧浏览器用例并新增独立题库完整UI用例；Playwright发现检查24项/19文件通过，但当前环境浏览器此前启动被socket权限阻断，内置浏览器无法连接隔离端口，本轮没有实际执行Chromium页面断言或产生新页面截图。终端无Git推送凭据，已连接GitHub的发布操作又被自动审批拒绝（用户未明确授权发布到目标仓库），因此新CI尚未触发；本地提交可供审阅，不能把发现检查写成浏览器通过。
- 推送前补核对（2026-11-09）：用户已明确授权提交推送reborn；静态检查发现共享资料行包含状态与共享只读两个徽章，adminManagement断言改为限定[data-status]，e2e发现24项/19文件及该文件ESLint通过。GitHub连接写入返回403 Resource not accessible by integration；账号有仓库push权限，但当前App仅安装于个人账号，终端尚未认证。代码仍为本地提交，等待官方CLI登录后正常推送，未触发新CI。
- 远端推送（2026-11-09）：用户完成GitHub官方CLI认证，6笔本地提交已正常快进推送至reborn，代码提交`fd9b36bc5d341ec4e9f54c8bd037243700685d90`；没有强制推送或修改main。GitHub Actions [首次运行42506396828](https://github.com/cypre5s/Pythontopia/actions/runs/42506396828)已触发，启动时尚未结束，最终结论需另查，不能把本地通过当作远端通过。此前自动审批拒绝与连接403属于当时的失败记录，现已通过用户授权及官方CLI认证解决。
- 远端CI首轮结果：文档提交`7b1f263`对应[运行42506508214](https://github.com/cypre5s/Pythontopia/actions/runs/42506508214)的后端与前端任务通过；后端365通过/10跳过，PostgreSQL新库迁移及两次显式seed通过，未配置专用测试URL的5项PG并发/迁移pytest仍跳过。Chromium桌面24项为12通过、2失败、10条件跳过，耗时8.7分钟；失败为独立题库select标签定位和嵌入式PDF预览的Escape关闭假设。日志`.tmp/content-ci-browser.log`、产物`.tmp/content-ci-artifacts/`保留原失败。修复用例使用combobox可访问名称，并通过可见关闭按钮退出PDF预览，普通内容仍检查Escape；未新增skip或放宽业务断言，修复后远端结果待核对。
- 修复后完整远端验证：代码提交`6f5c93082bb420038fa350399b4bfce07959031f`的[运行42507565813](https://github.com/cypre5s/Pythontopia/actions/runs/42507565813)三个任务均成功。Chromium桌面24项为14通过、10条件跳过，页面执行耗时7.1分钟；覆盖独立题库录入/同义答案/多章关联/两活动复用及顺序/引用删除保护，以及真实自制文本/PDF/视频上传、预览、替换和学生学习记录。日志`.tmp/content-ci-browser-fixed.log`。未增加跳过；10项仍按原媒体包、转换器和开发环境配置条件跳过，不计为通过。两份E2E文件ESLint、24项发现检查、文档检查与diff空白检查通过。随后仅同步本验收和续做记录，不重复运行功能检查。
- 原始课程包仍可作为显式来源，源清单的39条problem数不代表实际题库数量；运行镜像不携带课程JSON/YAML。真实AI出题、付费模型、Judge0/S3/转换服务未作为本轮已通过能力。

## 隔离环境启动尝试2026-11-08

源码`0caa997`，本轮仅启动与验证，未修改应用。前后端锁依赖安装成功；独立SQLite数据库迁移与seed成功（7文档、561页、7章节、65知识点、46示例、39原题）。同一执行环境中Vite与Uvicorn联通，`/api/health`返回ok；前端build（含vue-tsc）通过，保留Phaser大chunk提示。证据`.tmp/browser-qa/test.log`、`.tmp/browser-qa/build.log`。

连接的Edge无法访问此隔离环境端口。Playwright浏览器下载返回无效ZIP；改用系统Chromium运行现有studentIdLogin与rebornJourney两项，两项均在浏览器启动阶段因`socket() failed: Operation not permitted`失败，未执行页面断言，不算业务失败或通过。证据`frontend/.tmp-browser-results/`。没有生成业务截图，未验证登录、世界或教学流程；未测试真实媒体、AI、Judge0及PostgreSQL。临时测试进程已结束，不提供可用公网预览链接。

## HTTP部署UUID兼容核验2026-11-08

基于`bb75609a`工作区，修复非localhost HTTP缺少`crypto.randomUUID`引起的异常。任务事件、剧情变更、视频播放会话、单题与整卷提交5处统一使用共享UUID函数；原生方法缺失时由`getRandomValues`产生UUID v4，原有失败重试的键复用不变。不新增依赖、不修改后端或数据库。

- 前端锁依赖安装、typecheck、lint通过；39文件233项单测通过，覆盖原生方法接收者、回退UUID版本/变体和缺少安全随机源时明确失败。日志`.tmp/http-uuid-install.log`、`.tmp/http-uuid-checks.log`。
- Chrome专项1项通过：拦截提供非localhost HTTP页面，实际断言`isSecureContext=false`及原生randomUUID不存在，执行当前UUID源码并检查100个UUID格式与去重。未模拟浏览器Crypto能力；页面由测试提供，未访问用户服务器。证据`.tmp/http-uuid-browser-final.log`、`frontend/.tmp-http-uuid-final/`。初跑因测试源码含中文而btoa编码失败，改为URI编码后通过；原失败保留在`.tmp/http-uuid-browser.log`与`frontend/.tmp-http-uuid/`。
- 未重跑后端、迁移、完整业务浏览器旅程或远端Actions；本轮不启动已停止的业务服务，不声称用户服务器已更新。生产构建通过；文档链接及diff空白检查通过。

## Actions浏览器回归修复2026-11-08

基于`91b9341d`修复浏览器用例与当前界面不一致的问题，不改应用、数据模型和工作流门槛。[失败运行42456232086](https://github.com/cypre5s/Pythontopia/actions/runs/42456232086)的前后端任务通过，浏览器6通过、6失败、10跳过：旧后台用例仍点击已撤销活动/任务入口；阅读用例依赖纯文本阅读器已不存在的strong；可用性用例在异步课程面板挂载前误判仍需点击“立即前往”。此前仅在已导入PDF环境运行部分专项，未覆盖这些CI路径。

活动与任务测试夹具改用真实鉴权API创建，现有课程/材料管理、学生学习、服务器完成证据检查继续保留；断言撤销入口不存在，教师退出改走账号菜单。阅读位置使用文本/PDF共同提供的progress；标题在课程内容区域核对；课程导航限定教师导航，避免班级卡片同名按钮歧义。PPTX专项同类旧入口同步修正，未具备转换器条件时仍跳过，不宣称已验证转换。

本地使用新SQLite库、私有存储、两位临时教师及新学生，在18085/15178复现无课程媒体导入的CI环境，不访问15176预览记录。首次完整执行被运行中npm ci的Windows文件锁打断，失败证据`.tmp/actions-full-browser.log`、`.tmp/actions-fix-install.log`保留；停止独立Web后重新安装成功。随后完整回归为9通过、3失败、10跳过，补出PDF标题、旧learning-meta及同名导航问题，证据`.tmp/actions-full-browser-rerun.log`和`frontend/.tmp-actions-full-rerun/`。最终完整22项为12通过、10跳过，耗时3.2分钟，证据`.tmp/actions-full-browser-final.log`和`frontend/.tmp-actions-final/`；跳过条件与原CI一致，未新增skip。文档链接及diff空白检查通过。推送后的GitHub Actions结果须另行核对，不将本地通过当作远端通过。

前端npm ci、typecheck、lint、build通过，38文件230项单测通过；日志`.tmp/actions-fix-install-rerun.log`、`.tmp/actions-fix-build.log`、`.tmp/actions-fix-unit.log`。仅修改浏览器测试和文档，未本地重跑后端测试或PostgreSQL专项；隔离SQLite新库迁移与seed成功。真实AI/Judge0/S3/PPTX转换、导入课程包和开发工具专项不在本次本地覆盖内。

## 密码与后台交互核验2026-11-08

按用户要求先将账号管理功能提交为`44600d44`，本轮修改基于该提交。注册及自助改密最低6位，管理员首次初始化/CLI仍保持12位；教师名册改用shadcn风格分页、筛选、排序和跨页选择；左下角账号菜单提供改密弹窗及退出，暂撤学习体验分组与活动/NPC、任务配置入口。现有业务数据与学生任务保留。

- 前端npm ci、typecheck、lint、build通过，38文件230测试通过，新增126人班级的分页、跨页选择与过滤清选验证；依赖未改，审计仍16项moderate。日志`.tmp/teacher-refine-install.log`、`.tmp/teacher-refine-frontend.log`、`.tmp/teacher-refine-build.log`。
- 后端锁依赖安装与Ruff通过，341通过、10跳过、2个既有SQLite datetime警告；新增5位拒绝/6位接受的注册与改密边界。`.tmp/teacher-refine-backend.log`。
- 浏览器4项通过：126人班级分页、跨页选50人、搜索定位、账号菜单改密弹窗/焦点恢复/退出、六位密码实测，以及gameDesign、arrival恢复和账号隔离回归。基准1920×1080，名册额外检查用户截图的1168×871；截图`frontend/.tmp-teacher-roster-126.png`和`frontend/.tmp-teacher-password-dialog.png`已查看。日志`.tmp/teacher-refine-browser.log`，产物`frontend/.tmp-teacher-refine-e2e/`。本地15176健康检查通过，文档链接/归档与空白检查通过。
- 本轮无模型或迁移变化，迁移头仍`c3187f5db204`；未重复新PostgreSQL库/seed专项，上一轮结果保留原日期。15176预览更新前数据库备份为`.tmp/pre-teacher-refine-20261108.dump`，未重置既有账号或学习数据。
- 世界静态检查2图/11建筑/2门户/1838可达格/353美术对象，资源存在检查68项通过。真实AI/Judge0/S3/转换器、生产与移动设备未重测，10项可选/live后端跳过仍不算通过。

## 账号管理核验2026-11-08

源码为`47e637e1`后的工作区，分支reborn；本机15176独立预览已升级到`c3187f5db204`，不连接main/生产。首位管理员由环境变量创建；学生和教师自助改密；教师按学号创建、CSV/XLSX预览导入、批量重置密码与移出班级。现有账号不强制改密，创建/重置学生必须先修改学号临时密码。

| 检查 | 本轮结果与证据 |
| --- | --- |
| 前端 | npm ci、typecheck、lint、build通过；37文件229项测试通过。首次测试因沙箱临时文件rename EPERM全部未启动，正常本机环境重跑通过。`.tmp/account-frontend-tests.log`保留失败，成功见`.tmp/account-frontend-tests-rerun.log`，构建见`.tmp/account-frontend-build.log`。依赖审计仍报16项moderate，未在本轮改动前端依赖。 |
| 后端 | 锁依赖安装、Ruff通过；340通过、10跳过、2个既有SQLite datetime弃用警告。初跑及定位重跑因系统pytest临时目录权限失败，换项目内全新basetemp后通过；记录分别为`.tmp/account-backend-tests.log`、`.tmp/account-backend-first-failure.log`、`.tmp/account-backend-tests-rerun.log`。最终Excel维度限制后，账号专项8项再次通过。 |
| 管理员及迁移 | 新PostgreSQL库完整迁移、重复seed两次全部行一致；四个独立事务并发初始化仅创建一个管理员，改变环境密码后重试不覆盖原密码。`.tmp/account-pg-check.log`。新迁移只添加临时密码标记，既有用户默认false。 |
| 数据保全 | 升级前数据库与私有存储备份在`.tmp/pre-account-management-20261108.dump`和`.tmp/pre-account-management-storage-20261108/`；恢复到独立临时库核对28表468条既有完整字段全部保留，新增验收记录单独排除，见`.tmp/account-preservation.log`。 |
| 账号浏览器 | 1920×1080桌面Chrome，新增专项1项通过：教师创建、CSV预览确认、学生首次强制改密及刷新、设置中再次改密、教师重置、旧token失效、移出班级后账号保留、教师密码入口。`.tmp/account-browser.log`；截图`frontend/.tmp-account-password.png`、`frontend/.tmp-account-students.png`已人工查看。XLSX解析、文本学号保零、数字/公式拒绝由后端真实解析测试覆盖。 |
| 游戏回归 | gameDesign、rebornJourney、storyChapter共3项通过，覆盖100%–200%布局、arrival主线、恢复与账号隔离、资料助教入口；`.tmp/account-browser-regression.log`和`frontend/.tmp-account-regression/`。世界静态检查2图/11建筑/2门户/1838可达格/353美术对象，资源存在检查68项通过。 |

10项后端跳过仍是可选PostgreSQL与live Judge0检查；本轮独立PostgreSQL专项不等于把这些跳过改成通过。未验收真实AI/Judge0/S3/转换器、生产或移动设备；不对外部服务可用性作新结论。普通导航/改密/学生管理不调用模型。部署与配置的可执行说明只维护在[开发手册](development.md#首次管理员与后续教师账号)。文档链接/归档完整性和diff空白检查通过。

## 如何解释这份摘要

最新工作及远端CI结果见[独立内容库核验](#独立内容库核验2026-11-09)，基于`0caa997`后的工作区。[HTTP部署UUID兼容](#http部署uuid兼容核验2026-11-08)、[Actions浏览器回归修复](#actions浏览器回归修复2026-11-08)、[密码与后台交互](#密码与后台交互核验2026-11-08)和[账号管理](#账号管理核验2026-11-08)保留原始范围。此前[普通侧栏与图书馆助教核验](#普通侧栏与图书馆助教核验2026-11-08)、[学号登录与账号清理](#学号登录与账号清理核验2026-11-08)及下方旧基线矩阵保留各自执行范围，不把旧结论提升为本轮验证。

当前应满足的行为见[已确认要求](requirements.md)及其指向的规范，执行方法见[开发手册](development.md)。本页保存最新有效范围和限制，详细失败过程、旧部署状态及历次结果见[完整验收归档](archive/2026-11-08-documentation-baseline/acceptance.md)。记录后的代码变更必须按影响范围重新验证，不能直接继承通过结论。

## 最近功能验收矩阵

此表为此前功能基线；后续学号、侧栏与图书馆助教改动的实际重跑与未执行范围见文末各专项。

以下日期均为2026-11-08；环境为本机与独立Reborn QA，不连接main或旧生产库。

| 范围 | 最近记录的结果 | 证据与范围 |
| --- | --- | --- |
| 前端依赖、类型、lint、单测和构建 | 37文件228项通过；本机与Docker构建通过 | [原始记录](archive/2026-11-08-documentation-baseline/acceptance.md#百分制条件剧情独立日常与排版修正2026-11-08)；不等于实体设备验收 |
| 后端依赖、Ruff和单测 | 71项锁依赖匹配；313通过、10跳过 | 同一原始记录；跳过为5项可选PostgreSQL及5项live Judge0，不能计为通过 |
| 独立PostgreSQL新库迁移 | 新schema迁移到f4b92c71a806、模型check、两次seed通过；29表272行摘要一致；schema已清理 | `.tmp/story-enrichment-pg-validation-20261108.json`；该JSON本轮只读复核，不证明任意既有库升级安全 |
| 桌面浏览器业务流程 | Chromium 1920×1080，9项通过 | `frontend/.tmp-story-enrichment-e2e/`；`.last-run.json`只证明该轮通过，9项数量引用原验收记录 |
| 地图与资源静态检查 | 2图、11建筑、2portal、1838可达格、353美术对象；68项资源存在检查通过 | 原始记录与对应地图/资产检查脚本；不代表原GPU间歇问题已根治 |
| 当时本地预览升级的数据保全 | 27张既有业务表1769条记录完整字段一致；仅迁移头和当前演出配置改变 | `.tmp/pre-story-enrichment-20261108.sql`、`.tmp/story-enrichment-data-before.json`、`.tmp/story-enrichment-data-after.json`；属于当次升级证据，不是实时状态 |

浏览器九项实际覆盖：练习错题重提与目录；整卷交卷、66/100分和答案锁定；满分与66分镇长回应；任务外随机日常的幂等和刷新恢复；章节180开放前后的选项；闲聊不改变任务/标记/试卷且model_calls为0；七类工作区、折叠、100%–200%文字；真实PDF/视频规则；隔离学生开发重玩、主线续玩、账号切换及地图/键盘/焦点。

上述九项使用新建隔离班级和学生，没有重置演示账号，没有发送真实助教问题。测试覆盖不等于未执行的外部服务已可用。

## 失败、跳过与未覆盖项

| 类别 | 必须保留的结论 | 后续验证入口 |
| --- | --- | --- |
| 真实Judge0 | 旧真实AC/WA/运行错/超时/内存五项曾得到infrastructure_error；最后轮仍跳过，不声称已修复 | [故障原文](archive/2026-11-08-documentation-baseline/acceptance.md#明确限制)、[当前判题配置](development.md#可选服务与配置)；需要兼容的真实隔离环境 |
| 真实AI与S3 | 最后轮未验证付费模型回复、真实外部S3；零附带调用不等于模型回复能力通过 | [外部服务边界](development.md#可选服务与配置)；显式授权和独立环境下另做live验证 |
| PPT/PPTX转换 | 旧PPTX有单独历史通过记录；最后轮未复验，原生PPT未完成完整E2E；原PDF导入不能代替转换验证 | [历史完整转换链路](archive/2026-11-08-documentation-baseline/acceptance.md#已验证结果)和当前转换器配置；旧后台轮次的跳过不算通过 |
| 生产开发工具关闭 | 有旧独立生产模式验证，最后功能轮未重跑；生产代码双开关约束仍需保持 | [开发工具旧验收](archive/2026-11-08-documentation-baseline/acceptance.md#开发菜单与当前班级重玩2026-11-07)；对应生产隔离专项 |
| 手机/触摸与实体设备 | 旧横屏触摸找不到对白的根因未证；已按用户要求将CI范围定为1080P桌面，范围调整不等于修复 | [范围调整原文](archive/2026-11-08-documentation-baseline/acceptance.md#2026-11-05-桌面1080p验收范围调整)；按需另行立项 |
| 原GPU黑线 | 用户原始间歇场景未稳定复现；其他截图未见不能证明根治 | [原GPU问题记录](archive/2026-11-08-documentation-baseline/acceptance.md#2026-11-04-对话界面)中的2026-11-04黑线条目；需要原环境复核 |
| 容量与构建体积 | 旧负载仅单账号世界读取200请求/10并发；不代表班级AI/判题容量。大chunk仍有构建提示 | [历史已验证结果](archive/2026-11-08-documentation-baseline/acceptance.md#已验证结果)；按实际负载另测 |
| 许可与来源 | 保留素材和上游许可；书面条款缺口不能因功能通过而视为补齐 | 根目录许可和保留素材来源；[项目介绍](../README.md) |

历史条目若没有独立的执行日期或源码标识，按原文保留为历史证据，不推定为最近轮次重新通过；例如旧PPTX转换结果不能提升为2026-11-08的live结论。

已修复的测试问题也保留原失败：旧导入测试假定只有1份StoryDefinition；旧浏览器脚本假定任务数组首项为主线、沿用旧测验题序；初轮目录状态文字窄列竖排。对应修正后有独立成功结果，失败轮次没有改标为通过。[完整原文](archive/2026-11-08-documentation-baseline/acceptance.md)保留其错误和修复经过。

## 证据如何查找

| 证据 | 能证明什么 | 共享边界 |
| --- | --- | --- |
| [归档原文及清单](archive/2026-11-08-documentation-baseline/README.md) | 当时记录的结果、失败、来源和部署过程；可核对整理前原文 | 随Git保存，作为历史文本证据 |
| `.tmp/story-enrichment-pg-validation-20261108.json` | 当次PG迁移/check/重复seed的结构化摘要和执行日志 | 本机忽略文件，其他克隆可能没有 |
| `frontend/.tmp-story-enrichment-e2e/.last-run.json`、截图和trace目录 | 该轮运行状态与界面证据；元数据不独立证明数量、源码SHA或所有场景 | 本机忽略文件，不含在文档提交中 |
| SQL备份及前后字段摘要 | 指定时刻、指定数据库的恢复与数据保全证据 | 保持私有，不提交凭据、真实账号或数据库内容 |

旧失败目录 `.tmp-assessment-final-e2e`、`.tmp-assessment-accepted-e2e`、`.tmp-assessment-journey-e2e`、学习目标和开发工具的初轮目录仍是原失败证据。后续独立目录通过不能将这些目录改成成功。外部路径的记载不保证其仍存在；缺失时明确写缺失，保留Git中的文字记录，不补造运行结果。

## 每轮如何维护

1. 从[已确认要求](requirements.md)确定影响范围，按[开发手册](development.md)执行相应检查。
2. 记录日期、源码提交或未提交工作区、实际环境、通过/失败/跳过、未覆盖项及真实证据位置；没有执行就写没有执行。
3. 本页只更新受影响的矩阵和边界；需要保留的详细经过进入[历史索引](archive/README.md)，不将同一整轮过程复制进开发与续做文档。
4. 纯文档修改只记录链接、归档完整性与一致性检查，不重新宣称功能验收通过。

## 本轮文档整理核验

本轮范围：整理当前流程、归档旧记录、沉淀已确认要求和每轮维护规则，并新增文档维护检查脚本。未改变应用代码、YAML、数据库或服务；未重新执行上面的功能验收。

| 实际检查 | 结果与证据 |
| --- | --- |
| 当前与归档Markdown的本地链接/锚点 | 20份Markdown通过；具体数量见 `.tmp/docs-check-final-20261108.log`，检查入口为 `scripts/check-docs.mjs` |
| 三份完整原文 | 来源提交摘要、字节数与调整链接后的全文一致，3份均通过；[归档清单](archive/2026-11-08-documentation-baseline/manifest.json)随Git保留 |
| 检查工具的正反例 | 有效文档、缺文件、缺锚点、归档删改及恢复共5组符合预期；错误样例在独立临时目录中被拒绝，实际归档未修改。报告 `.tmp/docs-checker-selfcheck-20261108.json` |
| 空白、要求归属与修改范围 | diff空白检查通过；复核28项既定要求，修改限文档、AGENTS与文档检查脚本；无关 `deploy/piston-local/` 保留不提交 |

整合时首次文档检查发现四处尚未接通的引用：两处可选服务标题、一处每轮维护流程锚点及一处许可证相对路径。完成接线与路径修正后检查通过；内容复核另将PPTX历史通过与原GPU黑线两处证据链接改为实际支持结论的段落。这些中间问题没有记为功能验收通过。已有功能的通过、失败、跳过和本轮未执行项保持分开记录。

## 本地启动核验（2026-11-08）

在 `reborn` 提交 `e1d26f5e` 工作区启动 Docker Desktop 后，现有独立 `pythontopia-story-qa` 的 db/api/web 自动恢复。数据库与 API 健康状态正常，`http://127.0.0.1:15176/api/health` 返回200及 `status=ok`，`/world` 返回200；直接API端口为18083。启动日志曾出现短暂的db解析失败，随后服务启动完成、健康检查通过。

本轮仅恢复现有容器，未重建镜像、手动迁移、重置账号或清理数据；API沿用容器启动流程执行迁移与seed。未执行浏览器登录/世界交互、真实AI、Judge0、媒体播放或功能回归，HTTP可用不代表这些能力已验收。

## 学号登录与账号清理核验（2026-11-08）

源码：`reborn` 的 `e1d26f5e` 之后未提交工作区。全站认证、账户信息、教师学生查询、CLI、CI及测试夹具统一使用 `student_id`。新迁移头为 `a91c7d20e648`；旧邮箱接口不兼容。学号是1–32位ASCII数字字符串，保留前导零。普通迁移保留用户及历史证据，仅撤销会话、删除邮箱字段，待运维分配真实学号。

| 实际检查 | 本轮结果与证据 |
| --- | --- |
| 前端 | npm ci、typecheck、lint、37文件228项单测、构建通过；最终教师可空学号展示另复查typecheck/lint，并由Docker重新构建。npm报告16项moderate依赖审计提示，构建仍有大chunk提示；本轮未升级依赖。 |
| 后端 | uv按requirements.lock核对71项依赖；Ruff通过。完整最终回归326通过、10跳过，见 `.tmp/student-id-backend.log`；随后补充CLI分配/重复学号测试，学号专项15项通过。跳过仍为可选PG并发/迁移与live Judge0，不冒充真实判题通过。 |
| SQLite迁移 | 全新库升级、带历史学习数据升级、前导零学号保存、旧字段移除、旧会话撤销、外键完整性与模型check通过，见 `backend/tests/test_student_id_auth.py`。 |
| 独立PostgreSQL | 从真实预览备份恢复到 `student_id_restore_20261108` 后升级，64个旧账号及全部非会话业务表行数保留；另在 `student_id_fresh_20261108` 完成全新迁移。两库均两次seed行数稳定、29表、alembic check无差异；临时库已删除。脚本 `.tmp/check_student_id_pg.py`。 |
| 桌面浏览器 | Chrome 1920×1080，7项既有流程及1项新增学号专项通过。覆盖真实学号注册登录、前导零/重复/错误账号、教师管理、arrival完成与续玩、账号隔离、零附带模型调用、开发重玩边界、地图/键盘/焦点/文字偏好。日志 `.tmp/student-id-browser.log`，证据 `frontend/.tmp-student-id-chrome-e2e/` 和 `frontend/.tmp-student-id-auth-e2e/`。实际查看学号登录页和教师学习记录截图。 |
| 地图与素材 | validate-world：2图、11建筑、2portal、1838可达格、353美术对象；audit-assets：68项存在检查通过。 |
| 本地清理与部署 | 用户明确授权清空当前15176预览环境全部旧账号及关联记录。删除64个旧账号、42个班级及关联会话/进度/成绩；课程材料、题库和共享剧情删除数为0。API/Web重建部署，health为200且容器healthy。 |
| 最终演示状态 | 本轮11个自动验收账号按精确ID、学号与姓名核对后清理；最终仅2个新测试账号、1个测试班，旧用户ID数量为0，41份材料、7章、65知识点仍在。学生已入班，首章已发布，world返回唯一arrival主线；两账号登录均通过。凭据仅保存在本机忽略文件，不写入Git。 |
| 文档与范围 | check-docs通过：20份Markdown、203本地链接、28锚点、3份归档全文完整；git diff --check通过。修改限学号相关代码/夹具/CI及当前文档，未修改旧迁移、历史归档、素材归属或无关 `deploy/piston-local/`。 |

备份：`.tmp/pre-student-id-20261108.dump`（250624字节，已实际恢复验证）及 `.tmp/pre-student-id-storage-20261108/`。一次性清理脚本在 `.tmp/reset_preview_accounts.py`；该操作未加入迁移、seed或产品接口。第二次全量清理被自动审批拒绝后，改为只核对并清理本轮11个已知验收账号，见 `.tmp/cleanup_student_id_acceptance.py`，未删除其他账号。

保留失败经过：系统Python/虚拟环境首次在沙箱内无法启动，改用沙箱外已有虚拟环境；该环境没有pip，改用已有uv核对锁定依赖。首轮pytest因系统临时目录访问被拒而中止，改用项目内独立basetemp。第一轮有效全量测试325通过/1失败/10跳过，失败为旧迁移回退测试预期旧错误信息，修正后完整回归通过。默认Playwright缺少匹配的chromium_headless_shell，初轮6项启动失败/1项静态检查通过，原证据 `frontend/.tmp-student-id-e2e/` 保持失败；使用已安装Chrome后独立重跑7项通过。

未执行：真实AI、Judge0、S3、PPT转换、完整媒体播放和生产部署专项；原有外部服务及GPU限制保持。此次只操作本地story-qa，未触及其他旧环境或生产；未提交或推送代码。

## 普通侧栏与图书馆助教核验（2026-11-08）

范围：在上一轮未提交学号改动上继续修改UI-05、AI-02/03。任务日志、学习资料列表、地图、我的班级、设置不再显示侧栏折叠/展开按钮，也移除折叠状态和占位样式；阅读、练习、测验的学习支持侧卡继续支持折叠。图书馆直接阅读当前授权资料可创建资料级图书管理员会话，不要求绑定活动；恢复历史、发消息均重新鉴权，只有主动提问才调用模型，测验策略保持。

- 前端：npm ci、typecheck、lint、build通过；37文件229项单测通过。最初2项失败是旧测试仍要求资料阅读不挂助教，已按最新要求替换为资料会话与零附带请求断言，独立重跑全部通过。依赖审计16项moderate及大chunk提示保持，未升级依赖。
- 后端：71项锁依赖检查、Ruff通过；完整回归332通过、10跳过，跳过仍为可选PG并发/旧迁移和live Judge0。资料专项覆盖无活动绑定、双目标拒绝、权限撤销、用户隔离、当前资料引用、流式契约和无凭据503；流式响应使用明确测试替身，不等于真实供应商调用成功。SQLite迁移保留旧助教会话和调用记录，foreign_key_check与alembic check通过；有2条Python SQLite datetime适配弃用提示。
- PostgreSQL：备份恢复库迁移到 `b2076e4ca193` 前后28张业务表完整行摘要一致；独立新库从零迁移通过。两库均重复seed两次，完整行摘要稳定，alembic check无差异。验证脚本 `.tmp/check_library_assistant_pg.py`，两个临时验证库已删除。
- 浏览器：Chrome桌面3项通过，覆盖七工作区、普通侧栏无折叠、学习支持仍可折叠、图书馆独立PDF中的图书管理员面板、100%–200%字号、主线完成/刷新恢复/账号切换及零附带模型调用。日志 `.tmp/library-assistant-browser.log`，截图/证据 `frontend/.tmp-library-assistant-e2e/`。实际查看图书馆目录及200%阅读助教截图。
- 地图/素材：2图、11建筑、2portal、1838可达格、353美术对象检查通过；68项素材存在检查通过。
- 部署：当前15176预览API/Web已重建，容器healthy。升级前数据库备份 `.tmp/pre-library-assistant-20261108.dump`（191287字节）已在独立库恢复核验，私有文件备份 `.tmp/pre-library-assistant-storage-20261108/`。未重置既有演示学生、教师、班级和学习记录；浏览器使用独立新建验收账号/班级。

本地真实 `/assistant/status` 返回available=false，原因是未配置模型凭据，model_calls=0。因此界面和授权链路已接通，但尚不能生成真实回复；没有伪造成功结果。未执行真实模型、Judge0、S3、PPT转换和生产部署验收。现有其他外部服务限制保持。

本轮文档检查通过：20份Markdown、206本地链接、31锚点、3份归档完整；git diff --check通过。后续补强无活动绑定资料用例后，资料助教专项4项再次通过。改动仍未提交或推送，上一轮学号改动与无关本地目录均保留。

提交推送复核（2026-11-08）：按用户要求将上述学号与侧栏/资料助教改动纳入同次提交，复核需求记录、文档链接、diff空白及文件范围；功能结果沿用本页实际执行记录，没有在提交轮重新跑功能测试。此前四个本地Reborn提交随本次正常推送一并同步，不改写历史，无关目录与本地凭据/备份不纳入。

## 课程包设计文档核验2026-11-10

本节保留最初仅设计时的范围；后续实施结果以上方完整核验为准。

范围：仅同步课程完整包、公共/章节流程、固定地图边界、班级独立导入导出及React/Vue可选架构设计。源文件核对包括world.json、入口箭头渲染、服务端传送、现有课程导入和YAML加载；React独立子路径方案经Context7官方文档核对。现有未提交的本地预览记录保留。

未执行：应用实现、依赖安装、数据库迁移、课程搬移/导入、卷修改、浏览器、前后端功能测试、部署与提交推送。方案中的验收场景均为未来要求，历史功能通过不计为本轮重跑。文档链接、空白及改动范围检查结果在本节记录。

检查结果：node scripts/check-docs.mjs通过（21份Markdown、251个本地链接、60个锚点，3份归档源完整）；git diff --check通过。改动范围复核为7份项目文档，无应用代码、依赖、课程数据或部署配置改动，保留原有未提交内容。

## 后台重建与课程升级启动核验2026-11-10

本节保留P1启动时的原始结果与限制，后续阶段已在上方完整核验单列。

用户确认React + shadcn/ui后台及数据先行的阶段实施。新增离线课程包manifest/资源预检及测试，未修改HTTP接口、模型、迁移、现有Vue页面、课程源或部署配置。同步主阶段计划与旧数据映射、协作约定、需求、架构、产品、后台规范、接口边界、开发与续做入口。

检查环境：原backend/.venv引用的Windows Store Python无法启动；沙箱创建解释器启动器被拒后，在项目忽略目录.tmp/course-plan-checks创建Python3.12独立环境，按requirements.lock安装74个锁定依赖，不替换原环境。前端npm ci完成。所有测试数据库使用本轮临时目录/内存，不连接旧库或生产；真实AI和Judge0未配置。

前端：typecheck、lint通过；首次Vitest在沙箱临时缓存rename时报EPERM，39个文件未执行测试，不是断言失败。沙箱外重跑39文件244测试通过，build通过，保留既有Phaser大chunk提示。日志位于.tmp/course-plan-frontend-*.log。

当前范围不包含React页面交付、班级数据库迁移、完整课程语义校验/导入导出、浏览器实测、真实PostgreSQL并发、Judge0或AI、发布部署。未执行项不能记为阶段完成；P1只读预检不等于完整包可导入。

后端专项：新增预检测试65通过、1项真实符号链接因Windows权限跳过，重解析点拒绝有独立模拟测试通过；审计发现的超长整数字面量异常逃逸、Unicode代理字符输出异常已修复并覆盖。首次专项默认临时目录权限失败，改用项目basetemp后完成。PyYAML6.0.3安全加载依据经Context7核对，采用SafeLoader子类，不构造任意Python对象。

全量验证保留失败经过：首次在编码修正过程中运行得到408通过/11跳过/1失败/17错误，CLI子进程输出编码问题与旧夹具按GBK读UTF-8有关；沙箱外复核为413通过/11跳过/17错误，证明17项是编码环境问题而非沙箱权限。显式PYTHONUTF8=1、PYTHONIOENCODING=utf-8后对这18项复测全部通过，开发手册已补充Windows测试编码设置。日志分别为.tmp/course-plan-backend.log、.tmp/course-plan-backend-retry.log和.tmp/course-plan-backend-utf8.log；最终完整复测结果另列。

独立空SQLite库迁移至当前head、连续两次显式seed及alembic check通过（No new upgrade operations detected）；只用.tmp/course-plan-fresh.sqlite，不连接现有预览卷。后端ruff通过。未创建新的数据迁移，本检查验证现有基线仍可初始化，不代表P2的班级隔离迁移已完成。

最终完整后端复测：统一UTF-8环境下430通过、11跳过、0失败（193.27秒），含预检专项；跳过为未配置的外部专项及宿主机符号链接权限，保留2项既有SQLite datetime弃用警告。日志.tmp/course-plan-backend-final.log。文档检查通过（21份Markdown、267个本地链接、72个锚点、3份归档源完整），git diff --check通过；最终范围为协作/项目文档与新增只读预检模块、测试，保留原有本地记录及deploy/piston-local目录。未提交或推送。

## 新版预览重建2026-11-10
按用户授权删除旧story-qa三容器及course-release三容器、临时PG验收容器，保留旧持久卷。新项目pythontopia-preview-20261110使用全新数据库和存储卷，Web17234/API18235已运行；构建、健康接口、后台深链、课程导入/导出和体验学生world均实测通过。第一章已发布，预览为真实提取文本课程，不含未提供的原PDF/视频。本轮未重复全套功能或浏览器测试；账号仅保存于忽略文件.tmp/新版预览账号.txt。

2026-11-13 NPC 界面批注：后台类型检查、Lint、34 项测试及构建通过，独立 17254 Web 更新，数据库和课程保留。Git 跟踪 data 文件数为 0，课程目录忽略规则有效。本轮只修改后台布局与说明，未重跑学生端和后端全量、真实 AI/Judge0。

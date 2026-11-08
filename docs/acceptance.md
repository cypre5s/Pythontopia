# 验收摘要与已知边界

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
